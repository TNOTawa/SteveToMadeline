package com.stevetomadeline.client.windowx;

import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Keeps optional WindowX classes out of the normal camera and render paths. */
public final class WindowXCompat {
    private WindowXCompat() {
    }

    public static boolean available() {
        return ModList.get().isLoaded("windowx");
    }

    public static boolean isRendering() {
        return available() && WindowXCameraBridge.isAnyRendering();
    }

    public static boolean isRenderingBackground() {
        return available() && WindowXCameraBridge.isRendering();
    }

    public static float renderDistance() {
        return available() ? WindowXCameraBridge.renderDistance() : 0.0f;
    }

    public static int prepare(Vec3 position, float yaw, int width, int height, float halfHeight,
                              float renderDistance, boolean invalidate) {
        return available() ? WindowXCameraBridge.prepare(position, yaw, width, height,
                halfHeight, renderDistance, invalidate) : 0;
    }

    public static void stop() {
        if (available()) {
            WindowXCameraBridge.stop();
        }
    }
}
