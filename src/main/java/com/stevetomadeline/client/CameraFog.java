package com.stevetomadeline.client;

import net.minecraft.world.level.material.FogType;

public final class CameraFog {
    private CameraFog() { }
    public record Range(float start, float end) { }
    public static boolean liquid(FogType type) { return type == FogType.WATER || type == FogType.LAVA; }
    public static Range range(double cameraDistance, int renderChunks, boolean submerged) {
        float distance = (float) Math.max(0, cameraDistance);
        if (submerged) return new Range(distance + 1024, distance + 2048);
        float horizon = Math.max(128, renderChunks * 16f);
        return new Range(distance + horizon * .85f, distance + horizon);
    }
}
