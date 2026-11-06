package com.stevetomadeline.client.input;

import com.stevetomadeline.client.input.ControlBindings.Action;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlBindingsMigrationTest {
    @Test void controllerDefaultsOnlyFillEmptySlotsInVersionOneFiles() {
        EnumMap<Action, String[]> keys = new EnumMap<>(Action.class);
        for (Action action : Action.values()) keys.put(action, new String[]{"", "", "", ""});
        keys.get(Action.LEFT)[0] = "key.keyboard.j";
        keys.get(Action.JUMP)[1] = "key.keyboard.k";
        keys.get(Action.GRAB)[0] = "key.keyboard.left.shift";
        keys.get(Action.GRAB)[1] = "key.keyboard.z";
        keys.get(Action.GRAB)[2] = "key.keyboard.v";
        ControlBindings.fillGamepadDefaults(keys);

        assertArrayEquals(new String[]{"key.keyboard.j", "gamepad:axis:leftx:negative", "gamepad:button:dpleft", ""},
                keys.get(Action.LEFT));
        assertArrayEquals(new String[]{"", "key.keyboard.k", "gamepad:button:a", ""}, keys.get(Action.JUMP));
        assertArrayEquals(new String[]{"key.keyboard.left.shift", "key.keyboard.z", "key.keyboard.v",
                "gamepad:axis:triggerright:positive"}, keys.get(Action.GRAB));
        assertArrayEquals(new String[]{"", "gamepad:button:start", "", ""}, keys.get(Action.TOGGLE));
        assertArrayEquals(new String[]{"", "gamepad:button:leftbumper", "", ""}, keys.get(Action.TURN));
        assertArrayEquals(new String[]{"", "", "", ""}, keys.get(Action.CROUCH_DASH));
    }

    @Test void freshDefaultsBindMovementOnStickAndDpad() {
        assertArrayEquals(new String[]{"key.keyboard.a", "gamepad:axis:leftx:negative", "gamepad:button:dpleft", ""},
                Action.LEFT.defaults);
        assertArrayEquals(new String[]{"key.keyboard.d", "gamepad:axis:leftx:positive", "gamepad:button:dpright", ""},
                Action.RIGHT.defaults);
        assertArrayEquals(new String[]{"key.keyboard.space", "key.keyboard.c", "gamepad:button:a", ""}, Action.JUMP.defaults);
        assertArrayEquals(new String[]{"key.keyboard.x", "gamepad:button:x", "", ""}, Action.DASH.defaults);
        for (Action action : Action.values()) assertEquals(4, action.defaults.length);
        assertEquals(4, ControlBindings.SLOTS);
    }
}
