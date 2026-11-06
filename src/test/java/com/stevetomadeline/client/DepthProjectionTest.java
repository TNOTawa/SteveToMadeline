package com.stevetomadeline.client;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepthProjectionTest {
    @Test
    void playerSizeIsIndependentOfTheDepthReferenceDistance() {
        for (float distance : new float[]{16, 32, 128, 256}) {
            Matrix4f projection = DepthProjection.create(10, 16.0f / 9.0f, distance, 0.05f, distance + 512);
            Vector3f point = projection.transformProject(new Vector3f(0, 1, -distance));
            assertEquals(0.1f, point.y, 1e-5f);
        }
    }

    @Test
    void distantObjectsShrinkContinuouslyWithoutASliceBoundary() {
        Matrix4f projection = DepthProjection.create(10, 1.8f, 32, 0.05f, 544);
        float last = Float.POSITIVE_INFINITY;
        for (float depth : new float[]{-1, 0, 0.99f, 1.0f, 1.01f, 16, 32, 64, 128}) {
            float size = projection.transformProject(new Vector3f(0, 1, -32 - depth)).y;
            assertEquals(0.1f * 32 / (32 + depth), size, 1e-5f);
            assertTrue(size < last);
            last = size;
        }
    }

    @Test
    void cullingContainsThePerspectiveCornersAndCameraCube() {
        for (float distance : new float[]{16, 32, 256}) {
            for (float halfHeight : new float[]{2, 10, 30}) {
                for (float aspect : new float[]{0.01f, 0.5f, 1.8f, 4}) {
                    float far = distance + 512;
                    FrustumIntersection culling = new FrustumIntersection(DepthProjection.culling(halfHeight, aspect, distance, far));
                    assertEquals(FrustumIntersection.INSIDE, culling.intersectAab(-8, -8, -8, 8, 8, 8));
                    for (float depth : new float[]{0.1f, distance, far - 1}) {
                        assertTrue(culling.testPoint(halfHeight * aspect * depth / distance * 0.999f,
                                halfHeight * depth / distance * 0.999f, -depth));
                    }
                }
            }
        }
    }
}
