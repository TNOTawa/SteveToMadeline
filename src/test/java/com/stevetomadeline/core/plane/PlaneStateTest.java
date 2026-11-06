package com.stevetomadeline.core.plane;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaneStateTest {
    @Test
    void centeringUsesFloorForNegativeCoordinatesAndIsIdempotent() {
        double[][] cases = {{0, 0.5}, {0.99, 0.5}, {1, 1.5}, {-0.01, -0.5}, {-1, -0.5}, {-1.01, -1.5}};
        for (double[] pair : cases) {
            assertEquals(pair[1], PlaneState.blockCenter(pair[0]));
            assertEquals(pair[1], PlaneState.blockCenter(PlaneState.blockCenter(pair[0])));
        }
    }

    @Test
    void fourTurnsAtTheSameBlockCenterDoNotDrift() {
        PlaneState original = PlaneState.centered(Heading.NORTH, -215.36, -27.03);
        PlaneState current = original;
        for (int i = 0; i < 4; i++) {
            current = PlaneState.centered(current.heading().clockwise(), current.anchorX(), current.anchorZ());
        }
        assertEquals(original, current);
    }

    @Test
    void projectionOnlyKeepsTheHorizontalMovementAxis() {
        for (Heading heading : Heading.values()) {
            PlaneState state = PlaneState.centered(heading, 10, -20);
            assertEquals(heading.locksZ() ? 3 : 0, state.projectX(3));
            assertEquals(heading.locksZ() ? 0 : 4, state.projectZ(4));
            assertEquals(state.horizontal(3, 4), state.horizontal(state.projectX(3), state.projectZ(4)));
        }
    }

    @Test
    void velocityConversionPreservesSignedScreenSpeed() {
        for (Heading heading : Heading.values()) {
            PlaneState before = PlaneState.centered(heading, 0, 0);
            Heading after = heading.clockwise();
            for (double speed : new double[]{-0.2, 0, 0.31}) {
                double signed = before.horizontal(heading.rightX() * speed, heading.rightZ() * speed);
                PlaneState next = PlaneState.centered(after, 0, 0);
                assertEquals(speed, next.horizontal(after.rightX() * signed, after.rightZ() * signed), 1e-10);
            }
        }
    }

    @Test
    void aNewNormalCoordinateDoesNotMatchTheOldPlane() {
        PlaneState state = PlaneState.centered(Heading.SOUTH, 1, 2);
        assertTrue(state.aligned(999, 2.5));
        assertFalse(state.aligned(1.5, 3.5));
        assertThrows(IllegalArgumentException.class, () -> new PlaneState(Heading.SOUTH, Double.NaN, 0));
    }
}
