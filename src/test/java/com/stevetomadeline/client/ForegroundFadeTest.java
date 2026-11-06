package com.stevetomadeline.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForegroundFadeTest {
    @Test void removedTerrainFadesOutAndReturnsWithoutAnOpacityJump() {
        var fade = new ForegroundFade();
        assertEquals(1, fade.opacity());
        fade.update(true, .1);
        float before = fade.opacity(); assertTrue(before > 0 && before < 1);
        fade.update(false, 0); assertEquals(before, fade.opacity());
        fade.update(false, .05); assertTrue(fade.opacity() > before);
        fade.update(true, .1); fade.update(true, .1);
        assertEquals(0, fade.opacity());
        fade.update(false, .1); assertTrue(fade.opacity() > 0 && fade.opacity() < 1);
        fade.reset(); assertEquals(1, fade.opacity()); assertFalse(fade.active());
    }
    @Test void fadeDurationDoesNotDependOnFrameRate() {
        for (int fps : new int[]{30, 60, 144, 240}) {
            var fade = new ForegroundFade();
            for (int i = 0; i < fps; i++) fade.update(true, 1.0 / fps);
            assertEquals(0, fade.opacity());
            for (int i = 0; i < fps; i++) fade.update(false, 1.0 / fps);
            assertEquals(1, fade.opacity()); assertFalse(fade.active());
        }
    }
}
