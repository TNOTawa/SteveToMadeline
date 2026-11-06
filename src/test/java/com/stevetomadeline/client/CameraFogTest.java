package com.stevetomadeline.client;

import net.minecraft.world.level.material.FogType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraFogTest {
    @Test void onlyWaterAndLavaLoseTheirLiquidFog() {
        assertTrue(CameraFog.liquid(FogType.WATER)); assertTrue(CameraFog.liquid(FogType.LAVA));
        assertFalse(CameraFog.liquid(FogType.NONE)); assertFalse(CameraFog.liquid(FogType.POWDER_SNOW));
    }
    @Test void normalFogStartsBeyondThePlayerAndSubmergedFogIsBeyondTheProjection() {
        for (int chunks : new int[]{2, 8, 16, 32}) for (double distance : new double[]{1, 3, 32, 256}) {
            var normal = CameraFog.range(distance, chunks, false);
            assertTrue(normal.start() > distance + 100); assertTrue(normal.end() > normal.start());
            var liquid = CameraFog.range(distance, chunks, true);
            assertTrue(liquid.start() > distance + 512); assertTrue(liquid.end() > liquid.start());
        }
    }
}
