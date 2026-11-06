package com.stevetomadeline.client.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputTimelineTest {
    @Test void anotherJumpKeyStillPressesWhileTheFirstIsHeld() {
        var timeline = new InputTimeline();
        timeline.add(10, "space", true, 0);
        assertEquals(1, timeline.sample(16).pressed().size());
        timeline.add(20, "c", true, 0);
        var second = timeline.sample(33);
        assertEquals(2, second.held().size());
        assertTrue(second.pressed().contains(new InputTimeline.Press("c", 0)));
        assertTrue(timeline.sample(50).pressed().isEmpty());
    }
    @Test void edgesBelongToDistinctSubframesRatherThanOneMinecraftTick() {
        var timeline = new InputTimeline();
        timeline.add(8, "jump", true, 0); timeline.add(20, "jump", false, 0);
        timeline.add(25, "dash", true, 0);
        var first = timeline.sample(16);
        var second = timeline.sample(33);
        var third = timeline.sample(50);
        assertTrue(first.held().contains("jump")); assertEquals(1, first.pressed().size());
        assertEquals("dash", second.pressed().iterator().next().key()); assertTrue(third.pressed().isEmpty());
    }
    @Test void tapEntirelyBetweenSamplesMatchesReferenceKeyboardPolling() {
        var timeline = new InputTimeline();
        timeline.add(8, "jump", true, 0); timeline.add(12, "jump", false, 0);
        assertTrue(timeline.sample(16).pressed().isEmpty());
    }
    @Test void focusResetDiscardsOldEdgesAndHeldKeys() {
        var timeline = new InputTimeline(); timeline.add(10, "jump", true, 0);
        timeline.clear(); assertTrue(timeline.sample(50).held().isEmpty());
        timeline.add(60, "jump", true, 0); timeline.discardEdges();
        assertTrue(timeline.sample(80).pressed().isEmpty());
    }
}
