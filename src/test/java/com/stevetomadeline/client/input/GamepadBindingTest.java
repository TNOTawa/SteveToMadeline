package com.stevetomadeline.client.input;

import java.util.List;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamepadBindingTest {
    @Test void distinguishesControllerBindingsFromKeyboardAndMouse() {
        assertTrue(GamepadBinding.isGamepad("gamepad:button:a"));
        assertTrue(GamepadBinding.isGamepad("joystick:axis:3:negative"));
        assertFalse(GamepadBinding.isGamepad("key.keyboard.a"));
        assertFalse(GamepadBinding.isGamepad("key.mouse.left"));
        assertFalse(GamepadBinding.isGamepad(""));
    }

    @Test void everyGeneratedBindingIsAccepted() {
        for (String button : GamepadBinding.BUTTONS) assertTrue(GamepadBinding.valid(GamepadBinding.button(button)), button);
        for (String axis : GamepadBinding.AXES) {
            assertTrue(GamepadBinding.valid(GamepadBinding.axis(axis, true)), axis);
            assertTrue(GamepadBinding.valid(GamepadBinding.axis(axis, false)), axis);
        }
        assertTrue(GamepadBinding.valid(GamepadBinding.rawButton(0)));
        assertTrue(GamepadBinding.valid(GamepadBinding.rawAxis(31, true)));
    }

    @Test void rejectsUnknownNamesPolaritiesAndIndices() {
        assertFalse(GamepadBinding.valid("gamepad:button:trigger"));
        assertFalse(GamepadBinding.valid("gamepad:axis:leftx:up"));
        assertFalse(GamepadBinding.valid("gamepad:axis:leftx"));
        assertFalse(GamepadBinding.valid("joystick:button:-1"));
        assertFalse(GamepadBinding.valid("joystick:button:two"));
        assertFalse(GamepadBinding.valid("joystick:axis:1"));
        assertFalse(GamepadBinding.valid("key.keyboard.a"));
    }

    @Test void tablesFollowGlfwGamepadIndices() {
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A, GamepadBinding.BUTTONS.indexOf("a"));
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X, GamepadBinding.BUTTONS.indexOf("x"));
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, GamepadBinding.BUTTONS.indexOf("leftbumper"));
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT, GamepadBinding.BUTTONS.indexOf("dpleft"));
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, GamepadBinding.AXES.indexOf("leftx"));
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, GamepadBinding.AXES.indexOf("triggerleft"));
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, GamepadBinding.AXES.indexOf("triggerright"));
    }

    @Test void displayNamesPointAtLocalisedControllerLabels() {
        assertEquals("stevetomadeline.gamepad.button.a", key(GamepadBinding.name(GamepadBinding.button("a"))));
        assertEquals("stevetomadeline.gamepad.axis.triggerright.positive", key(GamepadBinding.name(GamepadBinding.axis("triggerright", true))));
        assertEquals("stevetomadeline.gamepad.raw.button", key(GamepadBinding.name(GamepadBinding.rawButton(4))));
        TranslatableContents raw = (TranslatableContents) GamepadBinding.name(GamepadBinding.rawAxis(2, false)).getContents();
        assertEquals("stevetomadeline.gamepad.raw.axis", raw.getKey());
        assertEquals("2", raw.getArgs()[0]);
        assertEquals("stevetomadeline.gamepad.negative", key((net.minecraft.network.chat.Component) raw.getArgs()[1]));
    }

    private static String key(net.minecraft.network.chat.Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
