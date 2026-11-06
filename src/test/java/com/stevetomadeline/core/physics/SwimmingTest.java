package com.stevetomadeline.core.physics;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwimmingTest {
    private static final PhysicsParameters P = PhysicsParameters.DEFAULT;
    private static final PhysicsInput RIGHT = input(1, 0);
    private static final PhysicsInput JUMP = new PhysicsInput(0, 0, 0, 0, true, false, true, false);
    private static PhysicsInput input(int x, int y) { return new PhysicsInput(x, y, x, y, false, false, false, false); }
    private static final class Pool implements PhysicsWorld {
        double surface = 30, wall = Double.POSITIVE_INFINITY;
        boolean filled = true;
        @Override public boolean solid(double x, double y, boolean ducking) { return y < 0 || x > wall; }
        @Override public boolean solidPoint(double x, double y) { return y < 0 || x >= wall + .25; }
        @Override public boolean water(double x, double y, boolean ducking) {
            return filled && y < surface && y + (ducking ? 6.0 : 11.0) / 16 > 0;
        }
    }
    private static PhysicsState submerged() { var s = new PhysicsState(); s.y = 10; return s; }
    private static void steps(PhysicsState s, PhysicsInput input, Pool world, int count) {
        for (int i = 0; i < count; i++) CelestePhysics.step(s, input, P, world);
    }

    @Test void enteringWaterChangesStateAfterMovementAndRefillsOnTheNextFrame() {
        var s = submerged(); s.vy = -10; s.stamina = 0; s.dashes = 0;
        CelestePhysics.step(s, PhysicsInput.NONE, P, new Pool());
        assertEquals(PhysicsState.Motion.SWIM, s.motion);
        assertEquals(-5f, s.vy);
        assertEquals(110, s.stamina); assertEquals(0, s.dashes);
        CelestePhysics.step(s, PhysicsInput.NONE, P, new Pool());
        assertEquals(1, s.dashes);
        assertFalse(s.grounded); assertFalse(s.locksTurn());
    }

    @Test void deepSwimmingUsesSeparateHorizontalAndVerticalLimitsAndNormalizedDiagonals() {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
            var s = submerged(); steps(s, input(x, y), new Pool(), 30);
            double length = Math.max(1, Math.hypot(x, y));
            assertEquals(60.0 / 16 * x / length, s.vx, 1e-6);
            assertEquals(80.0 / 16 * y / length, s.vy, 1e-6);
            assertEquals(PhysicsState.Motion.SWIM, s.motion);
        }
    }

    @Test void releasingInputStopsDeepUnderwaterWithoutGravity() {
        var s = submerged(); var pool = new Pool(); steps(s, input(1, -1), pool, 30);
        steps(s, PhysicsInput.NONE, pool, 20);
        double x = s.x, y = s.y;
        steps(s, PhysicsInput.NONE, pool, 60);
        assertEquals(x, s.x); assertEquals(y, s.y);
        assertEquals(0, s.vx); assertEquals(0, s.vy);
    }

    @Test void nearSurfaceFloatsStablyAndMovesFasterThanDeepWater() {
        var pool = new Pool(); pool.surface = 5;
        var s = new PhysicsState(); s.y = 4;
        steps(s, PhysicsInput.NONE, pool, 120);
        assertEquals(4.4375, s.y); assertEquals(PhysicsState.Motion.SWIM, s.motion);
        double y = s.y;
        steps(s, RIGHT, pool, 60);
        assertEquals(y, s.y); assertEquals(80.0 / 16, s.vx);
        steps(s, input(0, -1), pool, 20);
        assertTrue(s.y < y - .5, "down input leaves the surface");
    }

    @Test void jumpLeavesSurfaceButDoesNotCreateAnUnderwaterGroundJump() {
        var pool = new Pool(); var deep = submerged(); deep.motion = PhysicsState.Motion.SWIM;
        CelestePhysics.step(deep, JUMP, P, pool);
        assertEquals(0, deep.jumpSequence); assertEquals(0, deep.vy);
        pool.surface = 5;
        var s = new PhysicsState(); s.y = 4.4375; s.motion = PhysicsState.Motion.SWIM;
        CelestePhysics.step(s, JUMP, P, pool);
        assertEquals(PhysicsState.Motion.NORMAL, s.motion);
        assertEquals(105f / 16, s.vy); assertEquals(1, s.jumpSequence);
        steps(s, JUMP.heldOnly(), pool, 12);
        assertTrue(s.y > pool.surface, "the swim transition must not cancel a surface jump");
    }

    @Test void swimmingDashHasReducedSpeedAndPreservesItsCharge() {
        var s = submerged(); s.motion = PhysicsState.Motion.SWIM; var pool = new Pool();
        var dash = new PhysicsInput(1, 0, 1, 0, false, false, false, true);
        CelestePhysics.step(s, dash, P, pool);
        assertEquals(1, s.dashes);
        steps(s, dash.heldOnly(), pool, 4);
        assertEquals(180f / 16, s.vx); assertEquals(PhysicsState.Motion.DASH, s.motion);
        steps(s, RIGHT, pool, 30);
        assertEquals(PhysicsState.Motion.SWIM, s.motion); assertEquals(1, s.dashes);
    }

    @Test void dashRefillCooldownStillAppliesInWater() {
        var s = submerged(); s.dashes = 0; s.refillCooldown = .1f;
        var pool = new Pool(); steps(s, PhysicsInput.NONE, pool, 3);
        assertEquals(0, s.dashes);
        steps(s, PhysicsInput.NONE, pool, 6);
        assertEquals(1, s.dashes);
    }

    @Test void swimmingAndDashingCannotTunnelThroughPoolWalls() {
        var pool = new Pool(); pool.wall = .5;
        var s = submerged();
        boolean stoppedAtWall = false;
        for (int i = 0; i < 30; i++) {
            CelestePhysics.step(s, RIGHT, P, pool);
            assertTrue(s.x <= .5, "no intermediate frame may cross the wall");
            stoppedAtWall |= s.x == .5 && s.vx == 0;
        }
        // Subpixel acceleration can resume before the next integer collision step.
        assertEquals(.5, s.x); assertTrue(stoppedAtWall);
        s.x = 0;
        CelestePhysics.step(s, new PhysicsInput(1, 0, 1, 0, false, false, false, true), P, pool);
        steps(s, RIGHT, pool, 10);
        assertEquals(.5, s.x); assertEquals(0, s.vx);
    }

    @Test void leavingWaterRestoresAirPhysicsAndShallowWaterDoesNotStartSwimming() {
        var pool = new Pool(); var s = submerged();
        steps(s, PhysicsInput.NONE, pool, 1); pool.filled = false;
        steps(s, PhysicsInput.NONE, pool, 2);
        assertEquals(PhysicsState.Motion.NORMAL, s.motion); assertTrue(s.vy < 0);
        pool.filled = true; pool.surface = .4;
        var shallow = new PhysicsState(); steps(shallow, RIGHT, pool, 30);
        assertEquals(PhysicsState.Motion.NORMAL, shallow.motion); assertEquals(90f / 16, shallow.vx);
    }

    @Test void surfaceGrabCanLeaveWaterAndClimbTheBank() {
        var pool = new Pool(); pool.surface = 5; pool.wall = .0625;
        var s = new PhysicsState(); s.y = 4.4375; s.motion = PhysicsState.Motion.SWIM;
        CelestePhysics.step(s, new PhysicsInput(1, 1, 1, 1, false, true, false, false), P, pool);
        assertEquals(PhysicsState.Motion.CLIMB, s.motion);
        assertTrue(s.y >= 4.5); assertTrue(s.locksTurn());
        steps(s, new PhysicsInput(0, 1, 0, 1, false, true, false, false), pool, 20);
        assertEquals(PhysicsState.Motion.CLIMB, s.motion); assertTrue(s.y > 4.5);
    }

    @Test void swimmingIntoThePoolFloorDoesNotTriggerLandingEffects() {
        var pool = new Pool(); var s = new PhysicsState(); s.y = .125; s.motion = PhysicsState.Motion.SWIM;
        steps(s, input(0, -1), pool, 30);
        assertEquals(0, s.y); assertEquals(PhysicsState.Motion.SWIM, s.motion);
        assertEquals(0, s.landingSequence);
    }

    @Test void snapshotCopyReplayRemainsDeterministicAcrossWaterTransitions() throws Exception {
        var pool = new Pool(); pool.surface = 5;
        var s = new PhysicsState(); s.y = 6;
        steps(s, RIGHT, pool, 20);
        var copy = s.copy();
        for (int i = 0; i < 120; i++) {
            PhysicsInput input = i == 60 ? JUMP : i < 30 ? input(1, -1) : PhysicsInput.NONE;
            CelestePhysics.step(s, input, P, pool); CelestePhysics.step(copy, input, P, pool);
            for (var field : PhysicsState.class.getFields()) if (!Modifier.isStatic(field.getModifiers()))
                assertEquals(field.get(s), field.get(copy), "frame " + i + " " + field.getName());
        }
    }

    @Test void fallingOntoWaterConsumesAnEarlierJumpPressWithinTwoPixelsOfTheSurface() {
        var pool = new Pool(); pool.surface = 5;
        var s = new PhysicsState(); s.y = 5.25; s.vy = -3;
        CelestePhysics.step(s, JUMP, P, pool);
        assertEquals(0, s.jumpSequence);
        steps(s, PhysicsInput.NONE, pool, 3);
        assertEquals(1, s.jumpSequence); assertTrue(s.vy > 0); assertEquals(0, s.jumpBuffer);
    }

    @Test void expiredSurfaceJumpBufferDoesNotAutobounceAndHoldingDoesNotRepeat() {
        var pool = new Pool(); pool.surface = 5;
        var s = new PhysicsState(); s.y = 8;
        CelestePhysics.step(s, JUMP, P, pool);
        steps(s, JUMP.heldOnly(), pool, 120);
        assertEquals(0, s.jumpSequence); assertEquals(PhysicsState.Motion.SWIM, s.motion);
        CelestePhysics.step(s, JUMP, P, pool);
        steps(s, JUMP.heldOnly(), pool, 120);
        assertEquals(1, s.jumpSequence);
    }

    @Test void upwardAndDiagonalSwimmingDashesKeepChargeAfterLeavingTheWater() {
        for (int x : new int[]{-1, 0, 1}) {
            var pool = new Pool(); pool.surface = 5;
            var s = new PhysicsState(); s.y = 4.4375; s.motion = PhysicsState.Motion.SWIM;
            var dash = new PhysicsInput(x, 1, x, 1, false, false, false, true);
            CelestePhysics.step(s, dash, P, pool);
            for (int i = 0; i < 20; i++) {
                CelestePhysics.step(s, dash.heldOnly(), P, pool);
                assertEquals(1, s.dashes, "swim dash never spends a charge, even before refill cooldown");
            }
            assertTrue(s.y > pool.surface); assertEquals(PhysicsState.Motion.NORMAL, s.motion);
        }
    }

    @Test void noChargeCannotSwimDashBeforeRefillAndAnAirDashStillSpendsItsCharge() {
        var pool = new Pool(); pool.surface = 5;
        var s = new PhysicsState(); s.y = 4; s.motion = PhysicsState.Motion.SWIM; s.dashes = 0; s.refillCooldown = .1f;
        var dash = new PhysicsInput(0, 1, 0, 1, false, false, false, true);
        CelestePhysics.step(s, dash, P, pool);
        assertEquals(0, s.dashSequence); assertEquals(0, s.dashes);
        var air = new PhysicsState(); air.y = 6;
        CelestePhysics.step(air, dash, P, pool);
        assertEquals(1, air.dashSequence); assertEquals(0, air.dashes);
    }
}
