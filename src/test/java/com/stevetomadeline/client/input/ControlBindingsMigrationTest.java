package com.stevetomadeline.client.input;

import com.stevetomadeline.client.input.ControlBindings.Action;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlBindingsMigrationTest {
    @Test void versionTwoMixedSlotsAreSplitByDevice() throws Exception {
        String[] keyboard = blank(), controller = blank();
        ControlBindings.splitMixed(new String[]{"key.keyboard.a", "gamepad:axis:leftx:negative",
                "gamepad:button:dpleft", ""}, keyboard, controller);
        assertArrayEquals(new String[]{"key.keyboard.a", "", "", ""}, keyboard);
        assertArrayEquals(new String[]{"gamepad:axis:leftx:negative", "gamepad:button:dpleft", "", ""}, controller);
    }

    @Test void versionTwoKeptFourKeyboardSlotsWithoutLosingAny() throws Exception {
        String[] keyboard = blank(), controller = blank();
        ControlBindings.splitMixed(new String[]{"key.keyboard.left.shift", "key.keyboard.z", "key.keyboard.v",
                "key.keyboard.c"}, keyboard, controller);
        assertArrayEquals(new String[]{"key.keyboard.left.shift", "key.keyboard.z", "key.keyboard.v", "key.keyboard.c"}, keyboard);
        assertArrayEquals(blank(), controller);
    }

    @Test void splitRejectsMalformedOrOversizedGroups() {
        assertThrows(IOException.class, () -> ControlBindings.splitMixed(
                new String[]{"gamepad:button:trigger"}, blank(), blank()));
        assertThrows(IOException.class, () -> ControlBindings.splitMixed(
                new String[]{"key.keyboard.a", "key.keyboard.b", "key.keyboard.c", "key.keyboard.d", "key.keyboard.e"},
                blank(), blank()));
    }

    @Test void keyboardAndControllerDefaultsLiveInSeparateGroups() {
        assertArrayEquals(new String[]{"key.keyboard.a", "", "", ""}, Action.LEFT.keyboard);
        assertArrayEquals(new String[]{"gamepad:axis:leftx:negative", "gamepad:button:dpleft", "", ""}, Action.LEFT.controller);
        assertArrayEquals(new String[]{"key.keyboard.space", "key.keyboard.c", "", ""}, Action.JUMP.keyboard);
        assertArrayEquals(new String[]{"gamepad:button:a", "", "", ""}, Action.JUMP.controller);
        assertArrayEquals(new String[]{"key.keyboard.left.shift", "key.keyboard.z", "key.keyboard.v", ""}, Action.GRAB.keyboard);
        assertArrayEquals(new String[]{"gamepad:axis:triggerright:positive", "", "", ""}, Action.GRAB.controller);
        assertArrayEquals(new String[]{"key.keyboard.r", "", "", ""}, Action.TOGGLE.keyboard);
        assertArrayEquals(new String[]{"gamepad:button:start", "", "", ""}, Action.TOGGLE.controller);
        for (Action action : Action.values()) {
            assertEquals(4, action.keyboard.length, action.id);
            assertEquals(4, action.controller.length, action.id);
            for (String binding : action.keyboard) assertFalse(binding.startsWith("gamepad:"), action.id);
            for (String binding : action.controller) if (!binding.isEmpty()) assertTrue(binding.startsWith("gamepad:"), action.id);
        }
        assertEquals(4, ControlBindings.SLOTS);
    }

    private static String[] blank() {
        return new String[]{"", "", "", ""};
    }
}
