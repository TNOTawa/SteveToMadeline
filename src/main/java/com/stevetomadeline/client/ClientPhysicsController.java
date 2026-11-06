package com.stevetomadeline.client;

import com.stevetomadeline.client.input.PhysicsKeys;
import com.stevetomadeline.core.physics.*;
import com.stevetomadeline.network.PhysicsInputPayload;
import com.stevetomadeline.network.PhysicsSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayDeque;

/** Prediction uses the exact server step function and replays only unacknowledged input. */
public final class ClientPhysicsController {
    private static final ArrayDeque<PhysicsInputPayload> HISTORY = new ArrayDeque<>();
    private static int sequence;
    private static int lastTick = Integer.MIN_VALUE;
    private static boolean waitingForEpoch = true;

    private ClientPhysicsController() {
    }

    public static void onPlaneChanged() {
        HISTORY.clear();
        sequence = 0;
        lastTick = Integer.MIN_VALUE;
        waitingForEpoch = true;
        PhysicsKeys.clearClicks();
        com.stevetomadeline.client.render.MadelineRenderer.reset();
        Player player = Minecraft.getInstance().player;
        if (player != null) PhysicsController.stop(player, false);
    }

    public static void travel(Player player) {
        if (player != Minecraft.getInstance().player || lastTick == player.tickCount) return;
        lastTick = player.tickCount;
        PhysicsFrame input = PhysicsKeys.sampleFrame();
        PhysicsSession session = PhysicsController.session(player);
        if (waitingForEpoch || session == null || !session.active || session.state.nativeMovement
                || !PhysicsController.controlsMovement(player)) return;
        if (HISTORY.size() >= 120) {
            // Bounded prediction: don't accumulate seconds of unacknowledged movement.
            return;
        }
        var packet = new PhysicsInputPayload(session.epoch, ++sequence, input);
        HISTORY.addLast(packet);
        PacketDistributor.sendToServer(packet);
        MinecraftPhysicsWorld world = new MinecraftPhysicsWorld(player, PhysicsController.plane(player));
        for (int i = 0; i < 3; i++) {
            PhysicsState before = session.state.copy();
            PhysicsEffects effects = com.stevetomadeline.client.render.MadelineRenderer.before(player, before, world);
            CelestePhysics.step(session.state, input.at(i), session.parameters, world, effects);
            com.stevetomadeline.client.render.MadelineRenderer.after(player, before, session.state, world, input.at(i));
        }
        PhysicsController.apply(player, session, false);
    }

    public static void receive(PhysicsSnapshotPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().location().equals(payload.dimension())) return;
        if (mc.player.getId() != payload.entityId()) {
            if (mc.level.getEntity(payload.entityId()) instanceof PhysicsPlayerAccess access && access.stevetomadeline$hasCelesteDimensions()) {
                PhysicsSession remote = access.stevetomadeline$getPhysics();
                if (remote == null) { remote = new PhysicsSession(); access.stevetomadeline$setPhysics(remote); }
                remote.heading = payload.heading(); remote.state = payload.state(); remote.parameters = payload.parameters();
                access.stevetomadeline$setDucking(remote.state.ducking);
            }
            return;
        }
        if (ClientPlaneController.plane() == null) return;
        PhysicsSession session = PhysicsController.session(mc.player);
        if (session == null) {
            session = new PhysicsSession();
            ((PhysicsPlayerAccess) mc.player).stevetomadeline$setPhysics(session);
        }
        if (payload.epoch() < session.epoch || (!waitingForEpoch && payload.epoch() == session.epoch && payload.acknowledged() < session.acknowledged)) return;
        if (payload.epoch() != session.epoch || waitingForEpoch) {
            HISTORY.clear();
            sequence = payload.acknowledged();
            session.epoch = payload.epoch();
        }
        waitingForEpoch = false;
        session.active = true;
        session.acknowledged = payload.acknowledged();
        session.parameters = payload.parameters();
        session.heading = payload.heading();
        session.state = payload.state().copy();
        HISTORY.removeIf(input -> input.sequence() <= payload.acknowledged());
        if (session.state.nativeMovement || !PhysicsController.controlsMovement(mc.player)) {
            HISTORY.clear();
            ((PhysicsPlayerAccess) mc.player).stevetomadeline$setDucking(false);
            ((com.stevetomadeline.core.plane.PlanePlayerAccess) mc.player).stevetomadeline$setTurnLocked(false);
            return;
        }
        MinecraftPhysicsWorld world = new MinecraftPhysicsWorld(mc.player, ClientPlaneController.plane());
        for (PhysicsInputPayload input : HISTORY) CelestePhysics.tick(session.state, input.input(), session.parameters, world);
        Vec3 before = mc.player.position();
        PhysicsController.apply(mc.player, session, false);
        // Preserve interpolation across small corrections; large corrections settle immediately.
        Vec3 correction = mc.player.position().subtract(before);
        if (correction.lengthSqr() < 4) {
            mc.player.xo += correction.x; mc.player.yo += correction.y; mc.player.zo += correction.z;
            mc.player.xOld += correction.x; mc.player.yOld += correction.y; mc.player.zOld += correction.z;
        } else {
            mc.player.xo = mc.player.xOld = mc.player.getX();
            mc.player.yo = mc.player.yOld = mc.player.getY();
            mc.player.zo = mc.player.zOld = mc.player.getZ();
        }
    }

    public static void reset() {
        HISTORY.clear();
        sequence = 0;
        lastTick = Integer.MIN_VALUE;
        waitingForEpoch = true;
        PhysicsKeys.clearClicks();
        PhysicsKeys.resetGrab();
        com.stevetomadeline.client.render.MadelineRenderer.reset();
    }
}
