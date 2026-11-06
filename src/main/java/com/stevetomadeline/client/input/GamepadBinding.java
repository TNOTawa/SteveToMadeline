package com.stevetomadeline.client.input;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Canonical names for controller bindings. They share the binding file with keyboard and mouse
 * entries; the prefix keeps the two formats apart without changing the version-one layout.
 *
 * <p>{@code gamepad:button:a} and {@code gamepad:axis:leftx:negative} describe a controller that
 * GLFW recognises (standard layout, device independent). {@code joystick:button:0} and
 * {@code joystick:axis:2:positive} address an unrecognised joystick by raw index.</p>
 */
public final class GamepadBinding {
    public static final String STANDARD = "gamepad";
    public static final String RAW = "joystick";
    /** Index order matches {@code GLFW_GAMEPAD_BUTTON_*}. */
    public static final List<String> BUTTONS = List.of("a", "b", "x", "y", "leftbumper", "rightbumper",
            "back", "start", "guide", "leftthumb", "rightthumb", "dpup", "dpright", "dpdown", "dpleft");
    /** Index order matches {@code GLFW_GAMEPAD_AXIS_*}. */
    public static final List<String> AXES = List.of("leftx", "lefty", "rightx", "righty", "triggerleft", "triggerright");
    private static final String POSITIVE = "positive", NEGATIVE = "negative";

    private GamepadBinding() {
    }

    public static boolean isGamepad(String binding) {
        return binding.startsWith(STANDARD + ":") || binding.startsWith(RAW + ":");
    }

    public static String button(String name) {
        return STANDARD + ":button:" + name;
    }

    public static String axis(String name, boolean positive) {
        return STANDARD + ":axis:" + name + ":" + (positive ? POSITIVE : NEGATIVE);
    }

    public static String rawButton(int index) {
        return RAW + ":button:" + index;
    }

    public static String rawAxis(int index, boolean positive) {
        return RAW + ":axis:" + index + ":" + (positive ? POSITIVE : NEGATIVE);
    }

    public static boolean valid(String binding) {
        String[] parts = binding.split(":");
        if (parts.length == 3 && parts[0].equals(STANDARD) && parts[1].equals("button")) return BUTTONS.contains(parts[2]);
        if (parts.length == 4 && parts[0].equals(STANDARD) && parts[1].equals("axis")) return AXES.contains(parts[2]) && polarity(parts[3]);
        if (parts.length == 3 && parts[0].equals(RAW) && parts[1].equals("button")) return index(parts[2]);
        if (parts.length == 4 && parts[0].equals(RAW) && parts[1].equals("axis")) return index(parts[2]) && polarity(parts[3]);
        return false;
    }

    public static Component name(String binding) {
        String[] parts = binding.split(":");
        if (parts.length == 3 && parts[0].equals(STANDARD) && parts[1].equals("button") && BUTTONS.contains(parts[2]))
            return Component.translatable("stevetomadeline.gamepad.button." + parts[2]);
        if (parts.length == 4 && parts[0].equals(STANDARD) && parts[1].equals("axis") && AXES.contains(parts[2]))
            return Component.translatable("stevetomadeline.gamepad.axis." + parts[2] + "." + parts[3]);
        if (parts.length == 3 && parts[0].equals(RAW) && parts[1].equals("button"))
            return Component.translatable("stevetomadeline.gamepad.raw.button", parts[2]);
        if (parts.length == 4 && parts[0].equals(RAW) && parts[1].equals("axis"))
            return Component.translatable("stevetomadeline.gamepad.raw.axis", parts[2],
                    Component.translatable("stevetomadeline.gamepad." + (parts[3].equals(POSITIVE) ? "positive" : "negative")));
        return Component.literal(binding);
    }

    private static boolean polarity(String value) {
        return value.equals(POSITIVE) || value.equals(NEGATIVE);
    }

    private static boolean index(String value) {
        if (value.isEmpty() || value.length() > 3) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }
}
