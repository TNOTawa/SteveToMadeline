package com.stevetomadeline.client;

import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.core.plane.Heading;
import com.stevetomadeline.core.plane.PlanePlayerAccess;
import com.stevetomadeline.core.plane.PlaneState;
import com.stevetomadeline.core.plane.TurnAnimation;
import com.stevetomadeline.core.physics.PhysicsPlayerAccess;
import com.stevetomadeline.core.physics.PhysicsController;
import com.stevetomadeline.network.PlaneActionPayload;
import com.stevetomadeline.network.PlaneActionPayload.Action;
import com.stevetomadeline.network.PlaneSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;
import java.util.Objects;

public final class ClientPlaneController {
    private static LocalPlayer observedPlayer;
    private static ClientLevel observedLevel;
    private static TurnAnimation animation;
    private static boolean pendingTurn;
    private static boolean queuedToggle;
    private static boolean configExitBlocked;
    private static int nextRequest;
    private static int pendingRequest;
    private static int lastResponse;
    private static int pendingSince;
    private static Action pendingAction;
    private static Vec3 velocityBeforeSnap;
    private static float entryYaw;
    private static float entryPitch;
    private static boolean savedView;
    private static RenderModeManager.Mode lastRenderMode = RenderModeManager.Mode.VANILLA;

    private ClientPlaneController() {
    }

    public static PlaneState plane() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : ((PlanePlayerAccess) player).stevetomadeline$getPlane();
    }

    public static boolean turning(float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        return animation != null && player != null && animation.active(player.tickCount + partialTick);
    }

    public static float cameraYaw(float partialTick) {
        PlaneState state = plane();
        LocalPlayer player = Minecraft.getInstance().player;
        if (state == null || player == null) return player == null ? 0 : Heading.fromYaw(player.getYRot()).yaw();
        return animation == null ? state.heading().yaw() : animation.yaw(player.tickCount + partialTick);
    }

    public static void toggle() {
        if (pendingRequest != 0) {
            queuedToggle = !queuedToggle;
            pendingTurn = false;
            return;
        }
        pendingTurn = false;
        if (plane() != null) {
            send(Action.EXIT, plane().heading());
        } else if (Config.CELESTE_MODE_ENABLED.get()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) send(Action.ENTER, Config.ENTRY_ORIENTATION.get().select(player.getYRot()));
        } else {
            message("disabled_by_config");
        }
    }

    public static void requestTurn() {
        if (plane() == null) return;
        if (pendingRequest != 0 || turning(0) || turnLocked()) {
            pendingTurn = true;
        } else {
            send(Action.TURN, plane().heading());
        }
    }

    private static boolean turnLocked() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && ((PlanePlayerAccess) player).stevetomadeline$isTurnLocked();
    }

    private static void send(Action action, Heading heading) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (!mc.getConnection().hasChannel(PlaneActionPayload.TYPE) || !mc.getConnection().hasChannel(PlaneSnapshotPayload.TYPE)) {
            message("server_missing");
            pendingTurn = false;
            queuedToggle = false;
            return;
        }
        pendingRequest = ++nextRequest;
        pendingSince = mc.player.tickCount;
        pendingAction = action;
        velocityBeforeSnap = null;
        if (action == Action.ENTER) {
            entryYaw = mc.player.getYRot();
            entryPitch = mc.player.getXRot();
        }
        PacketDistributor.sendToServer(new PlaneActionPayload(pendingRequest, action, heading, mc.level.dimension().location()));
    }

    public static void beforePositionCorrection() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && pendingAction != null) {
            velocityBeforeSnap = player.getDeltaMovement();
        }
    }

    public static void receive(PlaneSnapshotPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null
                || !payload.dimension().equals(mc.level.dimension().location())) return;
        if (payload.entityId() != mc.player.getId()) {
            if (mc.level.getEntity(payload.entityId()) instanceof PhysicsPlayerAccess observer) {
                observer.stevetomadeline$setCelesteDimensions(payload.active());
            }
            return;
        }
        if (payload.requestId() != 0 && payload.requestId() <= lastResponse) return;
        observedPlayer = mc.player;
        observedLevel = mc.level;
        if (payload.requestId() != 0) lastResponse = payload.requestId();
        if (payload.requestId() == pendingRequest || payload.requestId() == 0) {
            pendingRequest = 0;
            pendingAction = null;
        }
        PlaneState previous = plane();
        PlaneState next = payload.active() ? new PlaneState(payload.heading(), payload.anchorX(), payload.anchorZ()) : null;
        ((PlanePlayerAccess) mc.player).stevetomadeline$setPlane(next);
        if (!Objects.equals(previous, next) || payload.result() == PlaneSnapshotPayload.Result.RESET) ClientPhysicsController.onPlaneChanged();
        if (previous == null || next == null) com.stevetomadeline.client.input.PhysicsKeys.resetGrab();
        if (next != null && (payload.result() == PlaneSnapshotPayload.Result.ENTERED || payload.result() == PlaneSnapshotPayload.Result.TURNED)) {
            if (payload.result() == PlaneSnapshotPayload.Result.ENTERED) savedView = true;
            if (!PhysicsController.nativeMovement(mc.player)) {
                Vec3 velocity = velocityBeforeSnap == null ? mc.player.getDeltaMovement() : velocityBeforeSnap;
                double horizontal = (previous == null ? next : previous).horizontal(velocity.x, velocity.z);
                mc.player.setDeltaMovement(next.heading().rightX() * horizontal, velocity.y, next.heading().rightZ() * horizontal);
                if (previous == null) {
                    mc.player.setYRot(Heading.fromYaw(entryYaw).yaw());
                } else {
                    double yaw = Math.toRadians(mc.player.getYRot());
                    double facing = previous.horizontal(-Math.sin(yaw), Math.cos(yaw));
                    mc.player.setYRot((facing >= 0 ? next.heading().clockwise() : next.heading().counterClockwise()).yaw());
                }
            }
            animation = previous != null && payload.result() == PlaneSnapshotPayload.Result.TURNED
                    ? new TurnAnimation(previous.heading().yaw(), mc.player.tickCount, Config.TURN_DURATION.get() * 20.0) : null;
        }
        if (next == null) {
            if (payload.result() == PlaneSnapshotPayload.Result.EXITED && savedView) {
                if (velocityBeforeSnap != null) mc.player.setDeltaMovement(velocityBeforeSnap);
                mc.player.setYRot(entryYaw);
                mc.player.setXRot(entryPitch);
                mc.player.yRotO = entryYaw;
                mc.player.xRotO = entryPitch;
            }
            savedView = false;
            animation = null;
            pendingTurn = false;
        }
        velocityBeforeSnap = null;
        if (!Objects.equals(previous, next)) resetVisuals();
        if (payload.requestId() == 0) {
            lastResponse = nextRequest;
            queuedToggle = false;
            pendingTurn = false;
        }
        switch (payload.result()) {
            case ENTERED -> message("entered");
            case EXITED -> message("exited");
            case TURNED -> message("turned", Component.translatable("stevetomadeline.heading." + payload.heading().name().toLowerCase(Locale.ROOT)));
            case BLOCKED -> { message("snap_blocked"); queuedToggle = false; pendingTurn = false; }
            case EXIT_BLOCKED -> {
                message("exit_blocked"); queuedToggle = false; pendingTurn = false; configExitBlocked = true;
            }
            case UNSUPPORTED -> { message("unsupported"); queuedToggle = false; pendingTurn = false; }
            case BUSY -> { message("busy"); queuedToggle = false; }
            case RESET -> { }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (observedPlayer != mc.player || observedLevel != mc.level) {
            reset();
            observedPlayer = mc.player;
            observedLevel = mc.level;
        }
        if (mc.player == null || mc.level == null) return;
        if (plane() != null && PhysicsController.nativeMovement(mc.player)) {
            ((PlanePlayerAccess) mc.player).stevetomadeline$setPlane(new PlaneState(plane().heading(), mc.player.getX(), mc.player.getZ()));
        }
        if (animation != null && !animation.active(mc.player.tickCount)) {
            animation = null;
            resetVisuals();
        }
        if (pendingRequest != 0 && mc.player.tickCount - pendingSince > 100) {
            pendingRequest = 0;
            pendingAction = null;
            pendingTurn = false;
            queuedToggle = false;
            message("timeout");
        }
        if (mc.screen != null || mc.getOverlay() != null || !mc.isWindowActive()) {
            pendingTurn = false;
            queuedToggle = false;
        }
        if (Config.CELESTE_MODE_ENABLED.get()) configExitBlocked = false;
        if (plane() != null && !Config.CELESTE_MODE_ENABLED.get() && !configExitBlocked && pendingRequest == 0) {
            queuedToggle = false;
            pendingTurn = false;
            send(Action.EXIT, plane().heading());
        } else if (pendingRequest == 0 && queuedToggle) {
            queuedToggle = false;
            toggle();
        } else if (pendingRequest == 0 && pendingTurn && !turning(0) && !turnLocked() && mc.screen == null) {
            pendingTurn = false;
            requestTurn();
        }
        RenderModeManager.Mode mode = RenderModeManager.mode();
        if (lastRenderMode != mode) {
            resetVisuals();
            lastRenderMode = mode;
        }
    }

    public static void updateInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        PlaneState state = plane();
        if (state == null || event.getEntity() != mc.player || !PhysicsController.controlsMovement(mc.player)) return;
        var input = event.getInput();
        input.leftImpulse = input.forwardImpulse = 0;
        input.left = input.right = input.up = input.down = false;
        // Vanilla still needs jump edges to start creative flight or elytra flight.
        // LivingPhysicsMixin prevents a second ground jump during Celeste physics.
        input.shiftKeyDown = false;
        mc.player.setSprinting(false);
    }

    public static boolean capturesPlayerList() {
        Minecraft mc = Minecraft.getInstance();
        return plane() != null && mc.screen == null
                && com.stevetomadeline.client.input.ControlBindings.boundTo(
                        com.stevetomadeline.client.input.ControlBindings.Action.TURN, mc.options.keyPlayerList.getKey());
    }

    private static void message(String key, Object... arguments) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("stevetomadeline.mode." + key, arguments), true);
    }

    private static void resetVisuals() {
        TerrainClipping.reset();
        ParallaxRenderer.reset();
    }

    public static void reset() {
        ClientPhysicsController.reset();
        if (observedPlayer != null) ((PlanePlayerAccess) observedPlayer).stevetomadeline$setPlane(null);
        observedPlayer = null;
        observedLevel = null;
        animation = null;
        pendingTurn = false;
        queuedToggle = false;
        pendingRequest = 0;
        configExitBlocked = false;
        pendingAction = null;
        savedView = false;
        velocityBeforeSnap = null;
        lastResponse = 0;
        nextRequest = 0;
        lastRenderMode = RenderModeManager.Mode.VANILLA;
        resetVisuals();
    }
}
