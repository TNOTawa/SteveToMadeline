package com.stevetomadeline.client.input;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamepadPadTest {
    private static final float DEADZONE = 0.3f;

    private record Edge(String binding, boolean down) {
    }

    private static List<Edge> poll(GamepadPad pad, boolean[] buttons, float[] axes) {
        List<Edge> edges = new ArrayList<>();
        pad.standard(buttons, axes, DEADZONE, (binding, down) -> edges.add(new Edge(binding, down)));
        return edges;
    }

    @Test void standardButtonsEmitOnePressAndOneRelease() {
        GamepadPad pad = new GamepadPad();
        boolean[] buttons = new boolean[GamepadBinding.BUTTONS.size()];
        float[] axes = rest();
        buttons[0] = true;
        assertEquals(List.of(new Edge("gamepad:button:a", true)), poll(pad, buttons, axes));
        assertEquals(List.of(), poll(pad, buttons, axes));
        assertTrue(pad.down().contains("gamepad:button:a"));
        buttons[0] = false;
        assertEquals(List.of(new Edge("gamepad:button:a", false)), poll(pad, buttons, axes));
        assertTrue(pad.down().isEmpty());
    }

    @Test void stickPressesOutsideDeadzoneAndReleasesBelowTheLowerThreshold() {
        GamepadPad pad = new GamepadPad();
        float[] axes = rest();
        axes[0] = DEADZONE - 0.01f;
        assertEquals(List.of(), poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
        axes[0] = DEADZONE + 0.01f;
        assertEquals(List.of(new Edge("gamepad:axis:leftx:positive", true)),
                poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
        axes[0] = DEADZONE * 0.8f;
        assertEquals(List.of(), poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
        axes[0] = DEADZONE * 0.6f;
        assertEquals(List.of(new Edge("gamepad:axis:leftx:positive", false)),
                poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
    }

    @Test void oppositePolarityIsASeparateBinding() {
        GamepadPad pad = new GamepadPad();
        float[] axes = rest();
        axes[1] = -0.9f;
        assertEquals(List.of(new Edge("gamepad:axis:lefty:negative", true)),
                poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
        axes[1] = 0.9f;
        assertEquals(List.of(new Edge("gamepad:axis:lefty:negative", false), new Edge("gamepad:axis:lefty:positive", true)),
                poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
    }

    @Test void releasedTriggerRestingAtMinusOneStaysSilent() {
        GamepadPad pad = new GamepadPad();
        float[] axes = rest();
        axes[5] = -1.0f;
        assertEquals(List.of(), poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
        axes[5] = 0.8f;
        assertEquals(List.of(new Edge("gamepad:axis:triggerright:positive", true)),
                poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
    }

    @Test void triggerNegativeDirectionIsNeverPressed() {
        GamepadPad pad = new GamepadPad();
        float[] axes = rest();
        axes[4] = -1.0f;
        assertEquals(List.of(), poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
        axes[4] = -0.99f;
        assertEquals(List.of(), poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
        axes[4] = 0.7f;
        assertEquals(List.of(new Edge("gamepad:axis:triggerleft:positive", true)),
                poll(pad, new boolean[GamepadBinding.BUTTONS.size()], axes));
    }

    @Test void disconnectOrFocusLossReleasesEveryHeldBinding() {
        GamepadPad pad = new GamepadPad();
        boolean[] buttons = new boolean[GamepadBinding.BUTTONS.size()];
        buttons[2] = true;
        poll(pad, buttons, rest());
        List<Edge> released = new ArrayList<>();
        pad.releaseAll((binding, down) -> released.add(new Edge(binding, down)));
        assertEquals(List.of(new Edge("gamepad:button:x", false)), released);
        assertTrue(pad.down().isEmpty());
    }

    @Test void rawJoysticksUseStableIndices() {
        GamepadPad pad = new GamepadPad();
        boolean[] buttons = new boolean[4];
        float[] axes = new float[3];
        buttons[2] = true;
        axes[1] = -0.8f;
        List<Edge> edges = new ArrayList<>();
        pad.raw(buttons, 4, axes, 3, DEADZONE, (binding, down) -> edges.add(new Edge(binding, down)));
        assertEquals(List.of(new Edge("joystick:button:2", true), new Edge("joystick:axis:1:negative", true)), edges);
        edges.clear();
        pad.raw(new boolean[4], 0, new float[3], 0, DEADZONE, (binding, down) -> edges.add(new Edge(binding, down)));
        assertEquals(List.of(new Edge("joystick:button:2", false), new Edge("joystick:axis:1:negative", false)), edges);
    }

    private static float[] rest() {
        float[] axes = new float[GamepadBinding.AXES.size()];
        axes[4] = -1.0f;
        axes[5] = -1.0f;
        return axes;
    }
}
