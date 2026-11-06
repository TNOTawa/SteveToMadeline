package com.stevetomadeline.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HairSimulationTest {
    @Test void strandsStayWithinThreePixelsAndReflectDashColor() {
        var hair = new HairSimulation();
        for (int i = 0; i < 180; i++) hair.step(10, 4, -1, 1);
        assertEquals(4, hair.count); assertEquals(0xAC3232, hair.color);
        for (int i = 1; i < hair.count; i++) assertTrue(Math.hypot(hair.x[i] - hair.x[i - 1], hair.y[i] - hair.y[i - 1]) <= 3.00001);
        for (int i = 0; i < 60; i++) hair.step(10, 4, -1, 0);
        // Reference Color.Lerp truncates channels each frame, including its approach plateau.
        assertEquals(0x44AEF6, hair.color);
        for (int i = 0; i < 60; i++) hair.step(10, 4, 1, 2);
        assertEquals(5, hair.count); assertEquals(0xFF6DEF, hair.color);
    }
}
