package com.stevetomadeline.core.physics;

import com.stevetomadeline.PhysicsConfig;
import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.core.mode.ServerPlaneController;
import com.stevetomadeline.core.plane.PlanePlayerAccess;
import com.stevetomadeline.core.plane.PlaneState;
import com.stevetomadeline.mixin.EntityPhysicsAccess;
import com.stevetomadeline.mixin.ServerPhysicsAccess;
import com.stevetomadeline.network.PhysicsInputPayload;
import com.stevetomadeline.network.PhysicsSnapshotPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.Consumer;

@EventBusSubscriber(modid = SteveToMadeline.MODID)
public final class PhysicsController {
    private static Consumer<Player> clientTravel = player -> {};
    private PhysicsController() {
    }

    public static PhysicsSession session(Player player) { return ((PhysicsPlayerAccess) player).stevetomadeline$getPhysics(); }
    public static PlaneState plane(Player player) { return ((PlanePlayerAccess) player).stevetomadeline$getPlane(); }
    public static boolean active(Player player) {
        return plane(player) != null && session(player) != null && session(player).active;
    }
    public static void setClientTravel(Consumer<Player> handler) { clientTravel = handler; }

    public static boolean travel(Player player) {
        if (plane(player) == null) return false;
        if (player.level().isClientSide()) clientTravel.accept(player);
        return true;
    }

    public static void start(ServerPlayer player, boolean turning) {
        PhysicsSession session = session(player);
        boolean fresh = session == null;
        if (fresh) {
            session = new PhysicsSession();
            ((PhysicsPlayerAccess) player).stevetomadeline$setPhysics(session);
        }
        var state = session.state;
        session.parameters = PhysicsConfig.parameters();
        if (fresh) state.dashes = session.parameters.maxDashes();
        else state.dashes = Math.min(state.dashes, session.parameters.maxDashes());
        PlaneState plane = plane(player);
        if (!turning) {
            state.clearAction();
            Vec3 velocity = player.getKnownMovement();
            state.vx = (float) (plane.horizontal(velocity.x, velocity.z) * 20);
            state.vy = (float) (velocity.y * 20);
            double yaw = Math.toRadians(player.getYRot());
            state.facing = plane.horizontal(-Math.sin(yaw), Math.cos(yaw)) >= 0 ? 1 : -1;
        }
        state.x = plane.horizontal(player.getX(), player.getZ());
        state.y = player.getY();
        state.grounded = CelestePhysics.grounded(state, new MinecraftPhysicsWorld(player, plane));
        if (state.grounded && state.refillCooldown <= 0) { state.dashes = session.parameters.maxDashes(); state.stamina = 110; }
        session.heading = plane.heading();
        session.active = true;
        state.jumpBuffer = state.dashBuffer = state.crouchDashBuffer = 0;
        session.newEpoch();
        remember(player, session);
        ((PlanePlayerAccess) player).stevetomadeline$setTurnLocked(state.locksTurn());
        snapshot(player, session);
    }

    public static void stop(Player player, boolean discard) {
        PhysicsSession session = session(player);
        if (session == null) return;
        session.active = false;
        session.inputs.clear();
        session.held = PhysicsInput.NONE;
        session.state.clearAction();
        ((PlanePlayerAccess) player).stevetomadeline$setTurnLocked(false);
        if (discard) ((PhysicsPlayerAccess) player).stevetomadeline$setPhysics(null);
    }

    public static void knockedBack(Player player) {
        if (!(player instanceof ServerPlayer server) || !active(player)) return;
        PhysicsSession session = session(player);
        Vec3 velocity = player.getDeltaMovement();
        session.state.clearAction();
        session.state.vx = (float) (plane(player).horizontal(velocity.x, velocity.z) * 20);
        session.state.vy = (float) (velocity.y * 20);
        ((PlanePlayerAccess) player).stevetomadeline$setTurnLocked(false);
        snapshot(server, session);
    }

    public static void input(ServerPlayer player, PhysicsInputPayload payload) {
        PhysicsSession session = session(player);
        if (!active(player) || payload.epoch() != session.epoch || payload.sequence() <= session.lastReceived
                || payload.sequence() - (long) session.lastReceived > 128 || session.inputs.size() >= 10) return;
        session.lastReceived = payload.sequence();
        session.inputs.addLast(payload);
    }

    @SubscribeEvent
    public static void serverTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PhysicsSession session = session(player);
        if (session == null) return;
        if (!active(player)) {
            session.state.refillCooldown = Math.max(0, session.state.refillCooldown - 0.05f);
            session.state.dashCooldown = Math.max(0, session.state.dashCooldown - 0.05f);
            if (player.onGround() && session.state.refillCooldown == 0) {
                session.state.dashes = session.parameters.maxDashes(); session.state.stamina = 110;
            }
            return;
        }
        if (!player.isAlive() || player.isInWater() || player.isInLava() || player.getAbilities().flying
                || player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isSpectator()) {
            ServerPlaneController.reset(player);
            return;
        }
        if (player.position().distanceToSqr(new Vec3(session.lastX, session.lastY, session.lastZ)) > 1.0e-8) {
            ServerPlaneController.reset(player);
            return;
        }
        ServerPhysicsAccess connection = (ServerPhysicsAccess) player.connection;
        connection.stevetomadeline$setFloating(false);
        // Vanilla's teleport acknowledgement moves the server player to the snap
        // point again. Wait for it rather than advancing and then being pulled back.
        if (connection.stevetomadeline$pendingTeleport() != null) return;
        PhysicsInputPayload packet = session.inputs.pollFirst();
        PhysicsFrame input;
        if (packet != null) {
            session.acknowledged = packet.sequence();
            session.held = packet.input().third().heldOnly();
            input = packet.input();
            session.idleTicks = 0;
        } else {
            input = ++session.idleTicks <= 5 ? PhysicsFrame.held(session.held) : PhysicsFrame.NONE;
        }
        if (player.containerMenu != player.inventoryMenu) input = PhysicsFrame.NONE;
        CelestePhysics.tick(session.state, input, session.parameters, new MinecraftPhysicsWorld(player, plane(player)));
        apply(player, session, true);
        player.connection.resetPosition();
        player.serverLevel().getChunkSource().move(player);
        snapshot(player, session);
    }

    public static void apply(Player player, PhysicsSession session, boolean effects) {
        PlaneState plane = plane(player);
        if (plane == null) return;
        var state = session.state;
        session.heading = plane.heading();
        ((PhysicsPlayerAccess) player).stevetomadeline$setDucking(state.ducking);
        Vec3 before = player.position();
        Vec3 position = new MinecraftPhysicsWorld(player, plane).position(state.x, state.y);
        player.setPos(position);
        Vec3 movement = position.subtract(before);
        boolean groundedNow = CelestePhysics.grounded(state, new MinecraftPhysicsWorld(player, plane));
        player.setOnGroundWithMovement(groundedNow, movement);
        player.setDeltaMovement(plane.heading().rightX() * state.vx / 20, state.vy / 20, plane.heading().rightZ() * state.vx / 20);
        player.setSprinting(false);
        player.setYRot((state.facing > 0 ? plane.heading().clockwise() : plane.heading().counterClockwise()).yaw());
        ((PlanePlayerAccess) player).stevetomadeline$setTurnLocked(state.locksTurn());
        if (effects && player instanceof ServerPlayer server) {
            if (PhysicsConfig.FALL_DAMAGE.get()) server.doCheckFallDamage(movement.x, movement.y, movement.z, groundedNow);
            else server.resetFallDistance();
            ((EntityPhysicsAccess) player).stevetomadeline$insideBlocks();
            if (groundedNow) {
                var pos = player.getOnPos();
                var block = player.level().getBlockState(pos);
                block.getBlock().stepOn(player.level(), pos, block, player);
            }
            server.setKnownMovement(movement);
        }
        remember(player, session);
    }

    private static void remember(Player player, PhysicsSession session) {
        session.lastX = player.getX(); session.lastY = player.getY(); session.lastZ = player.getZ();
    }

    public static void snapshot(ServerPlayer player, PhysicsSession session) {
        if (!active(player)) return;
        var payload = new PhysicsSnapshotPayload(player.getId(), player.level().dimension().location(), session.heading,
                session.epoch, session.acknowledged, session.parameters, session.state.copy());
        PacketDistributor.sendToPlayer(player, payload);
        for (ServerPlayer observer : player.serverLevel().getChunkSource().chunkMap.getPlayers(player.chunkPosition(), false)) {
            if (observer != player && observer.connection.hasChannel(PhysicsSnapshotPayload.TYPE)) PacketDistributor.sendToPlayer(observer, payload);
        }
    }
}
