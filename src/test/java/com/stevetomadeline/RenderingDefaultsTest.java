package com.stevetomadeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderingDefaultsTest {
    @Test void cameraCollisionIsOnAndLegacy2dOverridesAreOptIn() {
        assertTrue(Config.CAMERA_COLLISION.getDefault());
        assertTrue(Config.NEAR_WALL_SLICE.getDefault());
        assertFalse(Config.LEGACY_SKY_FOG.getDefault());
        assertFalse(Config.HIDE_OFF_PLANE_ENTITIES.getDefault());
        assertFalse(Config.FOREGROUND_SLICE.getDefault());
    }
}
