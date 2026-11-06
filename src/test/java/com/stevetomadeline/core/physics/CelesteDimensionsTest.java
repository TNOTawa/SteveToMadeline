package com.stevetomadeline.core.physics;

import com.stevetomadeline.core.plane.Heading;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CelesteDimensionsTest {
    @Test
    void standingAndDuckingHaveEightPixelWidthAndDepth() {
        for (boolean ducking : new boolean[]{false, true}) {
            AABB box = CelesteDimensions.forPose(ducking).makeBoundingBox(0, 0, 0);
            assertEquals(0.5, box.getXsize());
            assertEquals(ducking ? 0.375 : 0.6875, box.getYsize());
            assertEquals(0.5, box.getZsize());
        }
    }

    @Test
    void hurtboxExcludesBottomTwoPixelsAndSharesColliderTop() {
        for (boolean ducking : new boolean[]{false, true}) {
            var feet = new net.minecraft.world.phys.Vec3(-2.5, 3, 8.5);
            var body = CelesteDimensions.forPose(ducking).makeBoundingBox(feet);
            var hurt = CelesteDimensions.hurtbox(feet, ducking);
            assertEquals(ducking ? 4.0 / 16 : 9.0 / 16, hurt.getYsize());
            assertEquals(body.maxY, hurt.maxY);
            assertEquals(body.minY + 2.0 / 16, hurt.minY);
            assertEquals(0.5, hurt.getXsize()); assertEquals(0.5, hurt.getZsize());
            assertTrue(body.clip(new net.minecraft.world.phys.Vec3(-4, 3.0625, 8.5), new net.minecraft.world.phys.Vec3(0, 3.0625, 8.5)).isPresent());
            assertTrue(hurt.clip(new net.minecraft.world.phys.Vec3(-4, 3.0625, 8.5), new net.minecraft.world.phys.Vec3(0, 3.0625, 8.5)).isEmpty());
        }
    }

    @Test
    void feetAndCenterStayAnchoredAtNegativeCoordinates() {
        AABB box = CelesteDimensions.ACTIVE.makeBoundingBox(-2.5, 7, -4.5);
        assertEquals(-2.75, box.minX);
        assertEquals(-2.25, box.maxX);
        assertEquals(-4.75, box.minZ);
        assertEquals(-4.25, box.maxZ);
        assertEquals(7, box.minY);
        assertEquals(7.6875, box.maxY);
        assertTrue(CelesteDimensions.ACTIVE.eyeHeight() > 0);
        assertTrue(CelesteDimensions.ACTIVE.eyeHeight() < box.getYsize());
    }

    @Test
    void fitsThreeQuarterBlockTunnelButCannotEnterHalfBlockGap() {
        AABB box = CelesteDimensions.ACTIVE.makeBoundingBox(0, 0, 0);
        var tunnel = Shapes.box(1, 0.75, -1, 4, 2, 1);
        var lowTunnel = Shapes.box(1, 0.5, -1, 4, 2, 1);
        assertEquals(2, Shapes.collide(Direction.Axis.X, box, List.of(tunnel), 2), 1.0e-9);
        assertEquals(0.75, Shapes.collide(Direction.Axis.X, box, List.of(lowTunnel), 2), 1.0e-9);
        AABB duck = CelesteDimensions.forPose(true).makeBoundingBox(0, 0, 0);
        assertEquals(2, Shapes.collide(Direction.Axis.X, duck, List.of(lowTunnel), 2), 1.0e-9);
    }

    @Test
    void sweptDashStopsAtThinWallsInAllFourHeadings() {
        AABB box = CelesteDimensions.ACTIVE.makeBoundingBox(0, 0, 0);
        for (Heading heading : Heading.values()) {
            boolean x = heading.locksZ();
            int sign = x ? heading.rightX() : heading.rightZ();
            double near = sign > 0 ? 1 : -1.125;
            double far = sign > 0 ? 1.125 : -1;
            var wall = x ? Shapes.box(near, 0, -1, far, 1, 1) : Shapes.box(-1, 0, near, 1, 1, far);
            assertEquals(sign * 0.75,
                    Shapes.collide(x ? Direction.Axis.X : Direction.Axis.Z, box, List.of(wall), sign * 3), 1.0e-9);
        }
    }

    @Test
    void ceilingAndFloorUseTheSameFeetAnchoredHeight() {
        AABB box = CelesteDimensions.ACTIVE.makeBoundingBox(0, 0.25, 0);
        var floor = Shapes.box(-1, -1, -1, 1, 0, 1);
        var ceiling = Shapes.box(-1, 1, -1, 1, 2, 1);
        assertEquals(-0.25, Shapes.collide(Direction.Axis.Y, box, List.of(floor), -2), 1.0e-9);
        assertEquals(0.0625, Shapes.collide(Direction.Axis.Y, box, List.of(ceiling), 2), 1.0e-9);
    }
}
