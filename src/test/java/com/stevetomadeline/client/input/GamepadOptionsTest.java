package com.stevetomadeline.client.input;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamepadOptionsTest {
    @Test void missingFieldsFallBackToEnabledWithDefaultDeadzone() {
        var root = new JsonObject();
        root.addProperty("version", 1);
        assertEquals(GamepadOptions.DEFAULT, GamepadOptions.read(root));
        assertTrue(GamepadOptions.DEFAULT.enabled());
        assertEquals(0.3, GamepadOptions.DEFAULT.deadzone());
    }

    @Test void optionsRoundTripAndClampTheDeadzone() {
        var root = new JsonObject();
        var options = new GamepadOptions(false, 0.6);
        options.write(root);
        assertEquals(options, GamepadOptions.read(root));
        assertEquals(0.9, new GamepadOptions(true, 4.0).deadzone());
        assertEquals(0.05, new GamepadOptions(true, -1.0).deadzone());
        var partial = new JsonObject();
        partial.add("gamepad", new JsonObject());
        assertEquals(new GamepadOptions(true, 0.3), GamepadOptions.read(partial));
    }
}
