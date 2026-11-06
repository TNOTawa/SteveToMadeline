package com.stevetomadeline.core.plane;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TurnAnimationTest {
    @Test
    void easingHasExactEndpointsAndMonotonicClockwiseProgress() {
        TurnAnimation animation = new TurnAnimation(270, 100, 6);
        assertEquals(270, animation.yaw(99));
        assertEquals(270, animation.yaw(100));
        assertEquals(315, animation.yaw(103));
        assertEquals(360, animation.yaw(106));
        assertEquals(360, animation.yaw(110));
        float previous = 270;
        for (int i = 0; i <= 600; i++) {
            float angle = animation.yaw(100 + i / 100.0);
            assertTrue(angle >= previous && angle <= 360);
            previous = angle;
        }
        assertTrue(animation.active(105.99));
        assertFalse(animation.active(106));
    }

    @Test
    void easingDoesNotDependOnRenderFrameCountAndStartsGently() {
        TurnAnimation animation = new TurnAnimation(0, 0, 6);
        for (int i = 0; i < 1000; i++) animation.yaw(i / 200.0);
        assertEquals(45, animation.yaw(3));
        assertTrue(animation.yaw(0.01) < 0.001);
        assertTrue(90 - animation.yaw(5.99) < 0.001);
        assertThrows(IllegalArgumentException.class, () -> new TurnAnimation(0, 0, 0));
    }
}
