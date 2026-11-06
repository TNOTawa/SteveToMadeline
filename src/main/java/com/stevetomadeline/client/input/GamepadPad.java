package com.stevetomadeline.client.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts one merged controller state per device family into canonical binding edges.
 *
 * <p>Analog axes use separate press and release thresholds, so a stick resting around the
 * deadzone edge cannot chatter. Triggers only expose their positive travel: GLFW reports a
 * released trigger at -1, which must not read as a held negative direction.</p>
 */
public final class GamepadPad {
    public interface Sink {
        void edge(String binding, boolean down);
    }

    private static final float RELEASE_RATIO = 0.7f;
    private final Set<String> down = new LinkedHashSet<>();
    private final boolean[] standardAxes = new boolean[GamepadBinding.AXES.size() * 2];
    private final Map<Integer, boolean[]> rawAxes = new HashMap<>();

    public void standard(boolean[] buttons, float[] axes, float deadzone, Sink sink) {
        Map<String, Boolean> current = new LinkedHashMap<>();
        for (int i = 0; i < GamepadBinding.BUTTONS.size() && i < buttons.length; i++)
            current.put(GamepadBinding.button(GamepadBinding.BUTTONS.get(i)), buttons[i]);
        for (int i = 0; i < GamepadBinding.AXES.size() && i < axes.length; i++) {
            String name = GamepadBinding.AXES.get(i);
            boolean trigger = name.startsWith("trigger");
            current.put(GamepadBinding.axis(name, false),
                    !trigger && axis(standardAxes, i * 2, axes[i], false, deadzone));
            current.put(GamepadBinding.axis(name, true), axis(standardAxes, i * 2 + 1, axes[i], true, deadzone));
        }
        update(current, sink);
    }

    public void raw(boolean[] buttons, int buttonCount, float[] axes, int axisCount, float deadzone, Sink sink) {
        Map<String, Boolean> current = new LinkedHashMap<>();
        for (int i = 0; i < buttonCount; i++) current.put(GamepadBinding.rawButton(i), buttons[i]);
        for (int i = 0; i < axisCount; i++) {
            boolean[] gate = rawAxes.computeIfAbsent(i, key -> new boolean[2]);
            current.put(GamepadBinding.rawAxis(i, false), axis(gate, 0, axes[i], false, deadzone));
            current.put(GamepadBinding.rawAxis(i, true), axis(gate, 1, axes[i], true, deadzone));
        }
        update(current, sink);
    }

    private static boolean axis(boolean[] gate, int index, float value, boolean positive, float deadzone) {
        float threshold = gate[index] ? deadzone * RELEASE_RATIO : deadzone;
        boolean pressed = positive ? value >= threshold : value <= -threshold;
        gate[index] = pressed;
        return pressed;
    }

    /** Releases first so a direction change cannot leave the old direction held. */
    private void update(Map<String, Boolean> current, Sink sink) {
        for (var iterator = down.iterator(); iterator.hasNext(); ) {
            String binding = iterator.next();
            if (!Boolean.TRUE.equals(current.get(binding))) {
                sink.edge(binding, false);
                iterator.remove();
            }
        }
        for (Map.Entry<String, Boolean> entry : current.entrySet())
            if (entry.getValue() && down.add(entry.getKey())) sink.edge(entry.getKey(), true);
    }

    public void releaseAll(Sink sink) {
        for (String binding : new ArrayList<>(down)) sink.edge(binding, false);
        down.clear();
        Arrays.fill(standardAxes, false);
        rawAxes.clear();
    }

    public Set<String> down() {
        return Set.copyOf(down);
    }
}
