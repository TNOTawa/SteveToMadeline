package com.stevetomadeline.client.input;

import com.stevetomadeline.SteveToMadeline;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

/**
 * Polls the gamepads recognised by GLFW once per rendered frame and feeds their edges into the
 * same timestamped input timeline as the keyboard, so 60 Hz sampling, multi-slot bindings and
 * grab modes behave identically for both devices.
 *
 * <p>Controllers that report a standard mapping share device-independent names and work with the
 * default binds. Unrecognised joysticks fall back to raw button and axis indices, which the
 * settings screen can capture too. Only the client thread may call this class.</p>
 */
public final class GamepadInput {
    public record Device(int index, String name, boolean standard) {
    }

    private static final int PADS = GLFW.GLFW_JOYSTICK_LAST + 1;
    private static final int MAX_RAW_BUTTONS = 64, MAX_RAW_AXES = 32;
    private static final GamepadPad PAD = new GamepadPad();
    private static final GamepadPad.Sink IGNORE = (binding, down) -> { };
    private static final boolean[] STANDARD_BUTTONS = new boolean[GamepadBinding.BUTTONS.size()];
    private static final float[] STANDARD_AXES = new float[GamepadBinding.AXES.size()];
    private static final boolean[] RAW_BUTTONS = new boolean[MAX_RAW_BUTTONS];
    private static final float[] RAW_AXES = new float[MAX_RAW_AXES];
    private static final boolean[] PRESENT = new boolean[PADS];
    private static final boolean[] STANDARD = new boolean[PADS];
    private static final ArrayDeque<String> CAPTURES = new ArrayDeque<>();
    private static List<Device> devices = List.of();
    private static boolean capturing, active, rescan = true;
    private static int rawButtons, rawAxes;

    private GamepadInput() {
    }

    /** Called once per rendered frame on the client thread. */
    public static void poll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) return;
        updateDevices();
        GamepadOptions options = ControlBindings.gamepad();
        boolean usable = options.enabled() && minecraft.isWindowActive();
        if (!usable) {
            if (active) {
                active = false;
                PAD.releaseAll(GamepadInput::emit);
            }
            return;
        }
        read();
        GamepadPad.Sink sink = active ? GamepadInput::emit : IGNORE;
        PAD.standard(STANDARD_BUTTONS, STANDARD_AXES, (float) options.deadzone(), sink);
        PAD.raw(RAW_BUTTONS, rawButtons, RAW_AXES, rawAxes, (float) options.deadzone(), sink);
        if (!active) active = true;
    }

    public static List<Device> devices() {
        return devices;
    }

    public static Set<String> held() {
        return PAD.down();
    }

    public static void beginCapture() {
        capturing = true;
        CAPTURES.clear();
    }

    public static void endCapture() {
        capturing = false;
        CAPTURES.clear();
    }

    /** Latest controller edge captured while the settings screen listens for a binding. */
    public static String takeCapture() {
        return CAPTURES.pollFirst();
    }

    public static void rescan() {
        rescan = true;
        active = false;
        PAD.releaseAll(GamepadInput::emit);
    }

    /** Forgets held edges when leaving the world; the next frame re-primes from the live state. */
    public static void reset() {
        active = false;
        capturing = false;
        CAPTURES.clear();
        PAD.releaseAll(GamepadInput::emit);
        rescan = true;
        devices = List.of();
        Arrays.fill(PRESENT, false);
        Arrays.fill(STANDARD, false);
    }

    private static void emit(String binding, boolean down) {
        PhysicsKeys.device(binding, down);
        if (down && capturing && CAPTURES.size() < 8) CAPTURES.addLast(binding);
    }

    private static void updateDevices() {
        boolean changed = rescan;
        for (int index = 0; index < PADS; index++) {
            boolean present = GLFW.glfwJoystickPresent(index);
            boolean standard = present && GLFW.glfwJoystickIsGamepad(index);
            if (present != PRESENT[index] || standard != STANDARD[index]) changed = true;
            PRESENT[index] = present;
            STANDARD[index] = standard;
        }
        if (!changed) return;
        rescan = false;
        List<Device> found = new ArrayList<>();
        for (int index = 0; index < PADS; index++) {
            if (!PRESENT[index]) continue;
            String name = STANDARD[index] ? GLFW.glfwGetGamepadName(index) : null;
            if (name == null) name = GLFW.glfwGetJoystickName(index);
            found.add(new Device(index, name == null ? "Joystick " + (index + 1) : name, STANDARD[index]));
        }
        devices = List.copyOf(found);
        SteveToMadeline.LOGGER.debug("Controller devices changed: {}", devices);
    }

    private static void read() {
        Arrays.fill(STANDARD_BUTTONS, false);
        Arrays.fill(STANDARD_AXES, 0.0f);
        Arrays.fill(RAW_BUTTONS, false);
        Arrays.fill(RAW_AXES, 0.0f);
        rawButtons = 0;
        rawAxes = 0;
        for (int index = 0; index < PADS; index++) {
            if (!PRESENT[index]) continue;
            if (STANDARD[index]) readStandard(index);
            else readRaw(index);
        }
    }

    private static void readStandard(int index) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState state = GLFWGamepadState.malloc(stack);
            if (!GLFW.glfwGetGamepadState(index, state)) return;
            ByteBuffer buttons = state.buttons();
            FloatBuffer axes = state.axes();
            for (int button = 0; button < STANDARD_BUTTONS.length && button < buttons.capacity(); button++)
                STANDARD_BUTTONS[button] |= buttons.get(button) == GLFW.GLFW_PRESS;
            for (int axis = 0; axis < STANDARD_AXES.length && axis < axes.capacity(); axis++) {
                float value = axes.get(axis);
                if (Math.abs(value) > Math.abs(STANDARD_AXES[axis])) STANDARD_AXES[axis] = value;
            }
        }
    }

    private static void readRaw(int index) {
        ByteBuffer buttons = GLFW.glfwGetJoystickButtons(index);
        if (buttons != null) {
            rawButtons = Math.max(rawButtons, Math.min(buttons.capacity(), MAX_RAW_BUTTONS));
            for (int button = 0; button < rawButtons; button++) RAW_BUTTONS[button] |= buttons.get(button) == GLFW.GLFW_PRESS;
        }
        FloatBuffer axes = GLFW.glfwGetJoystickAxes(index);
        if (axes != null) {
            rawAxes = Math.max(rawAxes, Math.min(axes.capacity(), MAX_RAW_AXES));
            for (int axis = 0; axis < rawAxes; axis++) {
                float value = axes.get(axis);
                if (Math.abs(value) > Math.abs(RAW_AXES[axis])) RAW_AXES[axis] = value;
            }
        }
    }
}
