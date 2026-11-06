package com.stevetomadeline.client;

import com.stevetomadeline.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParallaxModeFallbackTest {
    @Test
    void layeredFallsBackOnlyWhenWindowXIsAbsent() {
        assertEquals(Config.ParallaxMode.DEPTH,
                ParallaxRenderer.effectiveMode(Config.ParallaxMode.LAYERED, false));
        assertEquals(Config.ParallaxMode.LAYERED,
                ParallaxRenderer.effectiveMode(Config.ParallaxMode.LAYERED, true));
        assertEquals(Config.ParallaxMode.DEPTH,
                ParallaxRenderer.effectiveMode(Config.ParallaxMode.DEPTH, false));
    }
}
