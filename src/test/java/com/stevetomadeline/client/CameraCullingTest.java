package com.stevetomadeline.client;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraCullingTest {
    @Test
    void cameraCubeFitsWithoutEnteringVanillasUnboundedOffsetLoop() {
        double[][] positions = {
                {-215.36384242436725, 72.62000000476837, -27.035844333964285},
                {7.999, 0.001, -0.001}, {-8, 0, 8}, {0.001, 7.999, -7.999}
        };
        for (double[] camera : positions) {
            for (float yaw : new float[]{0, 90, 180, 270, -180}) {
                for (float pitch : new float[]{0, 12}) {
                    for (float halfHeight : new float[]{2, 10, 30}) {
                        for (float aspect : new float[]{0.01f, 0.5f, 1280.0f / 681.0f, 4.0f}) {
                            Matrix4f view = new Matrix4f().rotationX((float) Math.toRadians(pitch))
                                    .rotateY((float) Math.toRadians(yaw));
                            Matrix4f projection = CameraCulling.orthographic(halfHeight, aspect, 640.0f);
                            FrustumIntersection frustum = new FrustumIntersection(projection.mul(view));
                            assertEquals(FrustumIntersection.INSIDE, frustum.intersectAab(
                                    minimum(camera[0]), minimum(camera[1]), minimum(camera[2]),
                                    maximum(camera[0]), maximum(camera[1]), maximum(camera[2])));
                        }
                    }
                }
            }
        }
    }

    @Test
    void cullingKeepsTheFullDrawVolumeAtMinimumAndMaximumZoom() {
        for (float halfHeight : new float[]{2, 10, 30}) {
            for (float aspect : new float[]{0.01f, 0.5f, 1280.0f / 681.0f, 4.0f}) {
                FrustumIntersection frustum = new FrustumIntersection(CameraCulling.orthographic(halfHeight, aspect, 640.0f));
                for (float depth : new float[]{0.05f, 7, 9, 639}) {
                    for (int x : new int[]{-1, 1}) {
                        for (int y : new int[]{-1, 1}) {
                            assertTrue(frustum.testPoint(x * halfHeight * aspect * 0.999f, y * halfHeight * 0.999f, -depth));
                        }
                    }
                }
            }
        }
    }

    @Test
    void twoBlockDrawSliceCannotServeAsTheCameraCubeCullingFrustum() {
        Matrix4f slice = new Matrix4f().ortho(-18.8f, 18.8f, -10, 10, 7, 9);
        FrustumIntersection frustum = new FrustumIntersection(slice);
        // No translation can fit an 8-block depth interval into this 2-block slab.
        for (int offset = -64; offset <= 64; offset++) {
            assertNotEquals(FrustumIntersection.INSIDE, frustum.intersectAab(-4, -4, offset - 4, 4, 4, offset + 4));
        }
    }

    private static float minimum(double coordinate) {
        return (float) (Math.floor(coordinate / 8.0) * 8.0 - coordinate);
    }

    private static float maximum(double coordinate) {
        return (float) (Math.ceil(coordinate / 8.0) * 8.0 - coordinate);
    }
}
