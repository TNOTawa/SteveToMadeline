package com.stevetomadeline.core.mode;

import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.core.physics.PhysicsController;
import com.stevetomadeline.core.physics.CelesteDimensions;
import com.stevetomadeline.network.PhysicsInputPayload;
import com.stevetomadeline.network.PhysicsSnapshotPayload;
import com.stevetomadeline.core.plane.Heading;
import com.stevetomadeline.core.plane.PlanePlayerAccess;
import com.stevetomadeline.core.plane.PlaneState;
import com.stevetomadeline.network.PlaneActionPayload;
import com.stevetomadeline.network.PlaneSnapshotPayload;
import com.stevetomadeline.network.PlaneSnapshotPayload.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SteveToMadeline.MODID)
public final class ServerPlaneController {
    private ServerPlaneController() {
    }

    public static void handle(ServerPlayer player, PlaneActionPayload payload) {
        if (payload.requestId() <= 0 || !player.connection.hasChannel(PlaneSnapshotPayload.TYPE)) return;
        PlanePlayerAccess access = (PlanePlayerAccess) player;
        PlaneState previous = access.stevetomadeline$getPlane();
        if (payload.action() == PlaneActionPayload.Action.EXIT) {
            if (previous != null && !PhysicsController.nativeMovement(player) && CelesteDimensions.exitPose(player) == null) {
                send(player, payload.requestId(), Result.EXIT_BLOCKED);
                return;
            }
            if (PhysicsController.active(player) && PhysicsController.controlsMovement(player)) {
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
            PhysicsController.stop(player, false);
            access.stevetomadeline$setPlane(null);
            send(player, payload.requestId(), Result.EXITED);
            return;
        }
        if (!player.connection.hasChannel(PhysicsInputPayload.TYPE) || !player.connection.hasChannel(PhysicsSnapshotPayload.TYPE)
                || !player.level().dimension().location().equals(payload.dimension())
                || player.containerMenu != player.inventoryMenu) {
            send(player, payload.requestId(), Result.UNSUPPORTED);
            return;
        }
        if (payload.action() == PlaneActionPayload.Action.ENTER && previous != null) {
            send(player, payload.requestId(), Result.BUSY);
            return;
        }
        if (payload.action() == PlaneActionPayload.Action.TURN && previous == null) {
            send(player, payload.requestId(), Result.RESET);
            return;
        }
        long tick = player.level().getGameTime();
        long lastTurn = access.stevetomadeline$getLastTurnTick();
        if (payload.action() == PlaneActionPayload.Action.TURN
                && (access.stevetomadeline$isTurnLocked() || (lastTurn != Long.MIN_VALUE && tick - lastTurn < 2))) {
            send(player, payload.requestId(), Result.BUSY);
            return;
        }
        Heading heading = previous == null ? payload.heading() : previous.heading().clockwise();
        if (PhysicsController.nativeMovement(player)) {
            access.stevetomadeline$setPlane(new PlaneState(heading, player.getX(), player.getZ()));
            access.stevetomadeline$setLastTurnTick(tick);
            send(player, payload.requestId(), previous == null ? Result.ENTERED : Result.TURNED);
            PhysicsController.start(player, previous != null);
            return;
        }
        PlaneState next = PlaneState.centered(heading, player.getX(), player.getZ());
        Vec3 offset = new Vec3(next.anchorX() - player.getX(), 0, next.anchorZ() - player.getZ());
        AABB enteringBox = previous == null ? CelesteDimensions.ACTIVE.makeBoundingBox(player.position()) : player.getBoundingBox();
        AABB swept = enteringBox.expandTowards(offset);
        if (!player.level().hasChunkAt(BlockPos.containing(next.anchorX(), player.getY(), next.anchorZ()))
                || !player.level().getWorldBorder().isWithinBounds(swept)
                || !player.level().noCollision(player, swept)) {
            send(player, payload.requestId(), Result.BLOCKED);
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        double horizontal = (previous == null ? next : previous).horizontal(velocity.x, velocity.z);
        Vec3 rotatedVelocity = new Vec3(heading.rightX() * horizontal, velocity.y, heading.rightZ() * horizontal);
        Vec3 known = player.getKnownMovement();
        double knownHorizontal = (previous == null ? next : previous).horizontal(known.x, known.z);
        // Absolute coordinates guarantee centering even if the client moved while
        // awaiting this response. The client retains velocity across this snap packet.
        player.connection.teleport(next.anchorX(), player.getY(), next.anchorZ(), player.getYRot(), player.getXRot());
        access.stevetomadeline$setPlane(next);
        access.stevetomadeline$setLastTurnTick(tick);
        player.setDeltaMovement(rotatedVelocity);
        player.setKnownMovement(new Vec3(heading.rightX() * knownHorizontal, known.y, heading.rightZ() * knownHorizontal));
        send(player, payload.requestId(), previous == null ? Result.ENTERED : Result.TURNED);
        PhysicsController.start(player, previous != null);
    }

    private static void send(ServerPlayer player, int requestId, Result result) {
        if (!player.connection.hasChannel(PlaneSnapshotPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, snapshot(player, requestId, result));
        if (result == Result.ENTERED || result == Result.EXITED || result == Result.RESET || result == Result.TURNED) {
            // Mode transitions are infrequent. Filter optional channels explicitly;
            // observers use this only for dimensions, never for movement prediction.
            var update = snapshot(player, 0, result);
            for (ServerPlayer observer : player.serverLevel().players()) {
                if (observer != player && observer.connection.hasChannel(PlaneSnapshotPayload.TYPE)) {
                    PacketDistributor.sendToPlayer(observer, update);
                }
            }
        }
    }

    private static PlaneSnapshotPayload snapshot(ServerPlayer player, int requestId, Result result) {
        PlaneState state = ((PlanePlayerAccess) player).stevetomadeline$getPlane();
        return new PlaneSnapshotPayload(requestId, player.getId(),
                player.level().dimension().location(), state != null, state == null ? Heading.SOUTH : state.heading(),
                state == null ? player.getX() : state.anchorX(), state == null ? player.getZ() : state.anchorZ(), result);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target
                && observer.connection.hasChannel(PlaneSnapshotPayload.TYPE)) {
            PacketDistributor.sendToPlayer(observer, snapshot(target, 0, Result.RESET));
        }
    }

    public static void followPosition(ServerPlayer player) {
        PlanePlayerAccess access = (PlanePlayerAccess) player;
        PlaneState previous = access.stevetomadeline$getPlane();
        if (previous != null && !previous.aligned(player.getX(), player.getZ())) {
            access.stevetomadeline$setPlane(new PlaneState(previous.heading(), player.getX(), player.getZ()));
        }
    }

    public static void rebase(ServerPlayer player) {
        PlanePlayerAccess access = (PlanePlayerAccess) player;
        PlaneState previous = access.stevetomadeline$getPlane();
        if (previous == null) return;
        access.stevetomadeline$setPlane(new PlaneState(previous.heading(), player.getX(), player.getZ()));
        send(player, 0, Result.RESET);
        PhysicsController.start(player, false);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        PlaneState state = ((PlanePlayerAccess) player).stevetomadeline$getPlane();
        if (state == null || !PhysicsController.controlsMovement(player)) return;
        if (player instanceof ServerPlayer serverPlayer && !state.aligned(player.getX(), player.getZ())) {
            rebase(serverPlayer);
            return;
        }
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(state.projectX(velocity.x), velocity.y, state.projectZ(velocity.z));
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        PlaneState previous = ((PlanePlayerAccess) event.getOriginal()).stevetomadeline$getPlane();
        if (previous != null) {
            Player player = event.getEntity();
            ((PlanePlayerAccess) player).stevetomadeline$setPlane(new PlaneState(previous.heading(), player.getX(), player.getZ()));
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) rebase(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PhysicsController.stop(player, true);
            if (PhysicsController.plane(player) != null) rebase(player);
            else send(player, 0, Result.RESET);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PhysicsController.stop(event.getEntity(), true);
        ((PlanePlayerAccess) event.getEntity()).stevetomadeline$setPlane(null);
    }
}
