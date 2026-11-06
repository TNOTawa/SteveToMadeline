package com.stevetomadeline.client;

import org.joml.Matrix4f;

/** One continuous projection, calibrated to the old orthographic scale at the player plane. */
public final class DepthProjection {
    private DepthProjection() {
    }

    public static Matrix4f create(float halfHeight, float aspect, float distance, float near, float far) {
        float fov = 2.0f * (float) Math.atan(halfHeight / distance);
        return new Matrix4f().perspective(fov, aspect, near, far);
    }

    public static Matrix4f culling(float halfHeight, float aspect, float distance, float far) {
        // Enclose even the farthest perspective corners, while retaining the
        // camera-cube guarantee needed by vanilla's unbounded frustum-offset loop.
        return CameraCulling.orthographic(halfHeight * Math.max(1.0f, far / distance), aspect, far);
    }
}
