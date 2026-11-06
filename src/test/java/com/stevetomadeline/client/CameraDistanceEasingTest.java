package com.stevetomadeline.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraDistanceEasingTest {
    @Test void contractsAndReturnsContinuouslyWithoutOvershoot() {
        var easing = new CameraDistanceEasing();
        assertEquals(32, easing.update(32, 0));
        double prior = 32;
        for (int i = 0; i < 60; i++) {
            double next = easing.update(2, 1.0 / 60);
            assertTrue(next < prior); assertTrue(next > 2); prior = next;
        }
        for (int i = 0; i < 60; i++) {
            double next = easing.update(32, 1.0 / 60);
            assertTrue(next > prior); assertTrue(next < 32); prior = next;
        }
    }
    @Test void equalElapsedTimeHasEqualResponseAtDifferentFrameRates() {
        double expected = 2 + 30 * Math.exp(-7);
        for (int fps : new int[]{30, 60, 144, 240}) {
            var easing = new CameraDistanceEasing(); easing.update(32, 0);
            double actual = 32;
            for (int i = 0; i < fps; i++) actual = easing.update(2, 1.0 / fps);
            assertEquals(expected, actual, 1e-9);
        }
    }
    @Test void zeroTimeAndLongPauseDoNotTeleportAndResetClearsPreviousWorldDistance() {
        var easing = new CameraDistanceEasing(); easing.update(32, 0);
        assertEquals(32, easing.update(2, 0));
        double next = easing.update(2, 100);
        assertTrue(next > 2); assertTrue(next < 32);
        easing.reset(); assertEquals(8, easing.update(8, 0));
    }
    @Test void narrowSpaceFallbackRequiresOptInAndARealObstructionStrictlyBelowThreeBlocks() {
        assertFalse(CameraDistanceEasing.useSlice(false, true, 2));
        assertFalse(CameraDistanceEasing.useSlice(true, false, 2));
        assertFalse(CameraDistanceEasing.useSlice(true, true, 3));
        assertTrue(CameraDistanceEasing.useSlice(true, true, 2.999));
        assertTrue(CameraDistanceEasing.useSlice(true, true, 0));
    }
    @Test void disabledCutawayAlsoDisablesTheEasingTransitionMask() {
        assertFalse(CameraDistanceEasing.transitionSlice(false, true, 32, 2));
        assertFalse(CameraDistanceEasing.transitionSlice(true, false, 32, 2));
        assertFalse(CameraDistanceEasing.transitionSlice(true, true, 2, 2));
        assertTrue(CameraDistanceEasing.transitionSlice(true, true, 16, 2));
    }
}
