package com.stevetomadeline.core.plane;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeadingTest {
    @Test
    void cardinalNormalsAndScreenRightHaveTheExpectedWorldAxes() {
        int[][] expected = {{0, 1, -1, 0}, {-1, 0, 0, -1}, {0, -1, 1, 0}, {1, 0, 0, 1}};
        for (Heading heading : Heading.values()) {
            int[] axes = expected[heading.ordinal()];
            assertEquals(axes[0], heading.normalX());
            assertEquals(axes[1], heading.normalZ());
            assertEquals(axes[2], heading.rightX());
            assertEquals(axes[3], heading.rightZ());
            assertEquals(0, heading.rightX() * heading.normalX() + heading.rightZ() * heading.normalZ());
        }
    }

    @Test
    void fourClockwiseTurnsReturnToTheSameHeading() {
        for (Heading heading : Heading.values()) {
            assertEquals(heading, heading.clockwise().clockwise().clockwise().clockwise());
            assertEquals(heading, heading.clockwise().counterClockwise());
        }
    }

    @Test
    void defaultEntryMapsTheOriginalFacingToScreenRight() {
        for (float yaw : new float[]{-720, -270, -180, -90, 0, 90, 180, 270, 360, 720, 44, 46}) {
            Heading facing = Heading.fromYaw(yaw);
            Heading camera = EntryOrientation.FACING_RIGHT.select(yaw);
            assertEquals(facing.normalX(), camera.rightX());
            assertEquals(facing.normalZ(), camera.rightZ());
            Heading left = EntryOrientation.FACING_LEFT.select(yaw);
            assertEquals(facing.normalX(), -left.rightX());
            assertEquals(facing.normalZ(), -left.rightZ());
        }
    }

    @Test
    void fixedAndLegacyOrientationsDoNotUseTheDefaultRightRule() {
        assertEquals(Heading.EAST, EntryOrientation.EAST.select(0));
        assertEquals(Heading.SOUTH, EntryOrientation.SOUTH.select(180));
        assertEquals(Heading.NORTH, EntryOrientation.NORTH.select(90));
        assertEquals(Heading.WEST, EntryOrientation.WEST.select(-90));
        assertEquals(Heading.fromYaw(70), EntryOrientation.CAMERA_FORWARD.select(70));
    }
}
