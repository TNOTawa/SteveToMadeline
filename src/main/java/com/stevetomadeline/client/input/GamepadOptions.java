package com.stevetomadeline.client.input;

import com.google.gson.JsonObject;

/** Optional controller fields; version-one files keep working without them. */
public record GamepadOptions(boolean enabled, double deadzone) {
    public static final GamepadOptions DEFAULT = new GamepadOptions(true, 0.3);
    public static final double MIN_DEADZONE = 0.05, MAX_DEADZONE = 0.9;

    public GamepadOptions {
        deadzone = Math.clamp(deadzone, MIN_DEADZONE, MAX_DEADZONE);
    }

    public static GamepadOptions read(JsonObject root) {
        if (!root.has("gamepad") || !root.get("gamepad").isJsonObject()) return DEFAULT;
        JsonObject value = root.getAsJsonObject("gamepad");
        return new GamepadOptions(!value.has("enabled") || value.get("enabled").getAsBoolean(),
                value.has("deadzone") ? value.get("deadzone").getAsDouble() : DEFAULT.deadzone());
    }

    public void write(JsonObject root) {
        JsonObject value = new JsonObject();
        value.addProperty("enabled", enabled);
        value.addProperty("deadzone", deadzone);
        root.add("gamepad", value);
    }
}
