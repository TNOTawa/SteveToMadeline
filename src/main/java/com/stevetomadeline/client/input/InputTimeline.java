package com.stevetomadeline.client.input;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Timestamped device edges, divided into 60 Hz samples independently of render frequency. */
public final class InputTimeline {
    public record Press(String key, int modifiers) { }
    public record Sample(Set<String> held, Set<Press> pressed) { }
    private record Change(long time, String key, boolean down, int modifiers) { }
    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private final Set<String> held = new HashSet<>();

    public void add(long time, String key, boolean down, int modifiers) {
        if (changes.size() >= 2048) discardEdges();
        changes.addLast(new Change(time, key, down, modifiers));
    }
    public Sample sample(long deadline) {
        Set<String> before = Set.copyOf(held);
        Map<String, Integer> modifiers = new HashMap<>();
        Set<Press> pressed = new HashSet<>();
        while (!changes.isEmpty() && changes.peekFirst().time <= deadline) {
            Change change = changes.removeFirst();
            if (change.down) { held.add(change.key); modifiers.put(change.key, change.modifiers); }
            else held.remove(change.key);
        }
        // Binding.Pressed compares each key at consecutive 60 Hz sampling boundaries.
        for (String key : held) if (!before.contains(key)) pressed.add(new Press(key, modifiers.getOrDefault(key, 0)));
        return new Sample(Set.copyOf(held), Set.copyOf(pressed));
    }
    public void discardEdges() { sample(Long.MAX_VALUE); }
    public void clear() { changes.clear(); held.clear(); }
}
