package com.stevetomadeline.client;

import org.joml.Matrix4f;

/** Conservative CPU culling; the GPU projection still controls the visible slice. */
public final class CameraCulling {
    // Vanilla offsets the frustum until it contains the camera's 8-block grid cube.
    // Its corners are at most sqrt(3) * 8 blocks away, regardless of view rotation.
    private static final float CAMERA_CLEARANCE = 16.0f;

    private CameraCulling() {
    }

    public static Matrix4f orthographic(float halfHeight, float aspect, float farPlane) {
        float halfWidth = Math.max(CAMERA_CLEARANCE, halfHeight * aspect);
        float cullHalfHeight = Math.max(CAMERA_CLEARANCE, halfHeight);
        // Include the camera cube immediately: translating a thin orthographic
        // frustum cannot make it contain the cube and vanilla's loop never ends.
        return new Matrix4f().ortho(-halfWidth, halfWidth, -cullHalfHeight, cullHalfHeight,
                -CAMERA_CLEARANCE, Math.max(CAMERA_CLEARANCE, farPlane));
    }
}
