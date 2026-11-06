package com.stevetomadeline.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.stevetomadeline.client.ClientPlaneController;
import com.stevetomadeline.core.physics.PhysicsInput;
import com.stevetomadeline.core.physics.PhysicsFrame;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import java.util.Set;
import static com.stevetomadeline.client.input.ControlBindings.Action.*;

public final class PhysicsKeys {
    private static final InputTimeline TIMELINE = new InputTimeline();
    private static final IntegerAxis X = new IntegerAxis(), Y = new IntegerAxis();
    private static final GrabInput GRAB_INPUT = new GrabInput();
    public static void resetGrab() { GRAB_INPUT.reset(); }
    private static long sampleTime;
    private PhysicsKeys() { }
    private static boolean inGame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive();
    }
    private static void input(InputConstants.Key key, int action, int modifiers) {
        if (action != InputConstants.PRESS && action != InputConstants.RELEASE) return;
        TIMELINE.add(System.nanoTime(), key.getName(), action == InputConstants.PRESS, modifiers);
        if (!inGame() || action != InputConstants.PRESS) return;
        if (key.equals(com.stevetomadeline.client.SteveToMadelineKeys.OPEN_SETTINGS.getKey())) return;
        var press = new InputTimeline.Sample(Set.of(), Set.of(new InputTimeline.Press(key.getName(), modifiers)));
        if (ControlBindings.pressed(TOGGLE, press)) ClientPlaneController.toggle();
        else if (ControlBindings.pressed(TURN, press)) ClientPlaneController.requestTurn();
    }
    public static void key(InputEvent.Key event) {
        input(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction(), event.getModifiers());
    }
    public static void mouse(InputEvent.MouseButton.Post event) {
        input(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction(), event.getModifiers());
    }
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!inGame() || ClientPlaneController.plane() == null) return;
        for (var action : ControlBindings.Action.values()) {
            if (ControlBindings.boundTo(action, event.getKeyMapping().getKey())) {
                event.setCanceled(true); event.setSwingHand(false); return;
            }
        }
    }
    public static PhysicsFrame sampleFrame() {
        if (!inGame() || ClientPlaneController.plane() == null) { clearClicks(); return PhysicsFrame.NONE; }
        long now = System.nanoTime();
        if (sampleTime == 0 || Math.abs(now - sampleTime) > 250_000_000L) sampleTime = now - 50_000_000L;
        PhysicsInput[] inputs = new PhysicsInput[3];
        var mode = ControlBindings.presentation().grabMode();
        if (GRAB_INPUT.mode() != mode) GRAB_INPUT.setMode(mode);
        for (int i = 0; i < 3; i++) {
            var sample = TIMELINE.sample(sampleTime + (i + 1) * 50_000_000L / 3);
            int x = X.update(ControlBindings.held(LEFT, sample), ControlBindings.held(RIGHT, sample));
            int y = Y.update(ControlBindings.held(DOWN, sample), ControlBindings.held(UP, sample));
            boolean turning = ClientPlaneController.turning(0);
            inputs[i] = new PhysicsInput(turning ? 0 : x, y, x, y, ControlBindings.held(JUMP, sample),
                    GRAB_INPUT.sample(ControlBindings.held(GRAB, sample), ControlBindings.pressed(GRAB, sample)), ControlBindings.pressed(JUMP, sample),
                    !turning && ControlBindings.pressed(DASH, sample), !turning && ControlBindings.pressed(CROUCH_DASH, sample));
        }
        sampleTime += 50_000_000L;
        return new PhysicsFrame(inputs[0], inputs[1], inputs[2]);
    }
    public static void clearClicks() {
        TIMELINE.discardEdges(); sampleTime = 0; X.update(false, false); Y.update(false, false);
        if (!Minecraft.getInstance().isWindowActive()) TIMELINE.clear();
    }
}
