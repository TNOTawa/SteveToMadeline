package com.stevetomadeline.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParallaxTrackingTest {
    @Test
    void horizontalMovementUsesRatioInEveryCardinalDirection() {
        for (int direction = 0; direction < 4; direction++) {
            ParallaxTracking tracking = new ParallaxTracking();
            assertTrue(tracking.update(100, 64, 200, direction, 0.4).reset());
            ParallaxTracking.Sample sample = tracking.update(110, 67, 210, direction, 0.4);
            assertFalse(sample.reset());
            assertEquals(direction % 2 == 0 ? 104 : 110, sample.pose().x(), 1e-9);
            assertEquals(67, sample.pose().y(), 1e-9);
            assertEquals(direction % 2 == 0 ? 210 : 204, sample.pose().z(), 1e-9);
        }
    }

    @Test
    void stoppingDoesNotSnapBackAndReturningRestoresTheOriginalPosition() {
        ParallaxTracking tracking = new ParallaxTracking();
        tracking.update(-10, 64, -8, 0, 0.4);
        var moving = tracking.update(0, 64, -8, 0, 0.4);
        for (int i = 0; i < 120; i++) {
            assertEquals(moving.pose(), tracking.update(0, 64, -8, 0, 0.4).pose());
        }
        assertEquals(-10, tracking.update(-10, 64, -8, 0, 0.4).pose().x(), 1e-9);
    }

    @Test
    void resultDoesNotDependOnNumberOfFrames() {
        ParallaxTracking coarse = new ParallaxTracking();
        ParallaxTracking fine = new ParallaxTracking();
        coarse.update(0, 64, 0, 0, 0.2);
        fine.update(0, 64, 0, 0, 0.2);
        var expected = coarse.update(20, 64, 0, 0, 0.2).pose();
        for (int i = 1; i < 200; i++) {
            fine.update(i / 10.0, 64, 0, 0, 0.2);
        }
        assertEquals(expected, fine.update(20, 64, 0, 0, 0.2).pose());
    }

    @Test
    void longTravelBoundsLagWithoutPeriodicAnchorJumps() {
        for (int sign : new int[]{-1, 1}) {
            ParallaxTracking tracking = new ParallaxTracking();
            double previous = tracking.update(0, 64, 0, 0, 0.4).pose().x();
            for (int i = 1; i <= 500; i++) {
                var sample = tracking.update(sign * i, 64, 0, 0, 0.4);
                assertFalse(sample.reset());
                assertTrue(Math.abs(sample.pose().x() - sign * i) <= 32.0);
                double step = sign * (sample.pose().x() - previous);
                assertTrue(step >= 0.4 - 1e-9 && step <= 1.0 + 1e-9);
                previous = sample.pose().x();
            }
        }
    }

    @Test
    void turningAndRatioChangesInvalidateThePreviousBackground() {
        ParallaxTracking tracking = new ParallaxTracking();
        tracking.update(0, 64, 0, 0, 0.4);
        tracking.update(10, 64, 0, 0, 0.4);
        var turned = tracking.update(10, 64, 0, 1, 0.4);
        assertTrue(turned.reset());
        assertEquals(new ParallaxTracking.Pose(10, 64, 0, 1), turned.pose());
        assertTrue(tracking.update(10, 64, 10, 1, 0.8).reset());
    }

    @Test
    void teleportsAndWorldResetsDiscardTheOldAnchor() {
        ParallaxTracking tracking = new ParallaxTracking();
        tracking.update(0, 64, 0, 0, 0.4);
        var teleport = tracking.update(1000, 64, 0, 0, 0.4);
        assertTrue(teleport.reset());
        assertEquals(1000, teleport.pose().x());
        assertTrue(tracking.update(1000, 200, 0, 0, 0.4).reset());
        tracking.reset();
        assertTrue(tracking.update(1001, 200, 0, 0, 0.4).reset());
    }

    @Test
    void equivalentYawAnglesDoNotResetTracking() {
        ParallaxTracking tracking = new ParallaxTracking();
        tracking.update(0, 64, 0, -1, 0.4);
        var sample = tracking.update(0, 64, 10, 3, 0.4);
        assertFalse(sample.reset());
        assertEquals(4, sample.pose().z(), 1e-9);
        assertFalse(tracking.update(0, 64, 10, 7, 0.4).reset());
    }
}
