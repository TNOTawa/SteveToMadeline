package com.stevetomadeline.client.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MadelineSettingsScreenTest {
    @Test
    void hidesOnlyWindowXControlsWhenUnavailable() {
        assertFalse(MadelineSettingsScreen.showCameraOption("parallaxMode", false));
        assertFalse(MadelineSettingsScreen.showCameraOption("parallaxRatio", false));
        assertTrue(MadelineSettingsScreen.showCameraOption("layeredParallax", false));
        assertTrue(MadelineSettingsScreen.showCameraOption("terrainClipping", false));
        assertTrue(MadelineSettingsScreen.showCameraOption("parallaxMode", true));
        assertTrue(MadelineSettingsScreen.showCameraOption("parallaxRatio", true));
    }
}
