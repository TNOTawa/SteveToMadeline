package com.stevetomadeline.core.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CelestePhysicsTest {
    private static final PhysicsParameters P = PhysicsParameters.DEFAULT;
    private static final PhysicsInput RIGHT = new PhysicsInput(1, 0, 1, 0, false, false, false, false);
    private static final PhysicsInput JUMP = new PhysicsInput(0, 0, 0, 0, true, false, true, false);

    /** Independent rectangular fixture; wall/ceiling are limits for the standing feet position. */
    private static final class Room implements PhysicsWorld {
        double floor = 0, wall = Double.POSITIVE_INFINITY, ceiling = Double.POSITIVE_INFINITY;
        boolean hasFloor = true;
        @Override public boolean solid(double x, double y, boolean ducking) {
            return (hasFloor && y < floor) || x > wall || y + (ducking ? 6.0 / 16 : 11.0 / 16) > ceiling + 11.0 / 16;
        }
        @Override public boolean solidPoint(double x, double y) { return x >= wall + 4.0 / 16 || (hasFloor && y < floor); }
    }

    @Test
    void runningAcceleratesThenStopsWithoutOscillation() {
        var s = new PhysicsState(); var world = new Room();
        for (int i = 0; i < 60; i++) CelestePhysics.step(s, RIGHT, P, world);
        assertEquals(P.runSpeed(), s.vx, 1e-9);
        for (int i = 0; i < 60; i++) CelestePhysics.step(s, PhysicsInput.NONE, P, world);
        assertEquals(0, s.vx, 1e-9);
        assertEquals(0, s.y, 1e-9);
    }

    @Test
    void ordinaryJumpUsesTheReferenceScaledImpulse() {
        var s = new PhysicsState();
        CelestePhysics.step(s, JUMP, P, new Room());
        assertEquals(105.0 / 16, s.vy, 1e-9);
        assertEquals(2.0 / 16, s.y, 1e-9); // RoundToEven(105 / 60) = 2 pixels.
        assertFalse(CelestePhysics.grounded(s, new Room()));
        assertEquals(0, s.jumpBuffer);
    }

    @Test
    void heldJumpReachesHigherThanATapAndCeilingStopsAscent() {
        var held = new PhysicsState(); var tap = new PhysicsState(); var world = new Room();
        CelestePhysics.step(held, JUMP, P, world); CelestePhysics.step(tap, JUMP, P, world);
        double high = 0, low = 0;
        for (int i = 0; i < 90; i++) {
            CelestePhysics.step(held, JUMP.heldOnly(), P, world);
            CelestePhysics.step(tap, PhysicsInput.NONE, P, world);
            high = Math.max(high, held.y); low = Math.max(low, tap.y);
        }
        assertTrue(high > low + 0.5);
        Room ceiling = new Room(); ceiling.ceiling = 2.0 / 16;
        var s = new PhysicsState();
        CelestePhysics.step(s, JUMP, P, ceiling);
        CelestePhysics.step(s, JUMP.heldOnly(), P, ceiling);
        assertEquals(ceiling.ceiling, s.y, 1e-9);
        assertEquals(0, s.vy, 1e-9);
        assertTrue(s.variableJump >= 0.15f); // Reference keeps the early variable-jump window on a headbonk.
        for (int i = 0; i < 15; i++) CelestePhysics.step(s, JUMP.heldOnly(), P, ceiling);
        assertTrue(s.variableJump <= 0);
    }

    @Test
    void coyoteAllowsAnEarlyJumpButExpiresInMidair() {
        Room world = new Room(); var s = new PhysicsState();
        CelestePhysics.step(s, PhysicsInput.NONE, P, world);
        world.hasFloor = false;
        for (int i = 0; i < 3; i++) CelestePhysics.step(s, PhysicsInput.NONE, P, world);
        CelestePhysics.step(s, JUMP, P, world);
        assertTrue(s.vy > 0);
        var late = new PhysicsState(); late.coyote = 0.1f;
        for (int i = 0; i < 7; i++) CelestePhysics.step(late, PhysicsInput.NONE, P, world);
        CelestePhysics.step(late, JUMP, P, world);
        assertTrue(late.vy < 0);
    }

    @Test
    void landingConsumesABufferedJumpAndHeldInputDoesNotAutoJumpAgain() {
        var s = new PhysicsState(); s.y = 2.0 / 16; s.vy = -3;
        Room world = new Room();
        CelestePhysics.step(s, JUMP, P, world);
        for (int i = 0; i < 3; i++) CelestePhysics.step(s, JUMP.heldOnly(), P, world);
        assertTrue(s.vy > 0);
        for (int i = 0; i < 120; i++) CelestePhysics.step(s, JUMP.heldOnly(), P, world);
        assertEquals(0, s.y, 1e-9);
        assertEquals(0, s.vy, 1e-9);
    }

    @Test
    void dashUsesAimAfterWindupAndNormalizesDiagonalSpeed() {
        var s = new PhysicsState(); s.y = 10;
        Room world = new Room();
        var pressDown = new PhysicsInput(0, -1, 0, -1, false, false, false, true);
        var aimDiagonal = new PhysicsInput(0, 0, 1, 1, false, false, false, false);
        CelestePhysics.step(s, pressDown, P, world);
        assertEquals(0, s.dashes);
        assertEquals(0, s.vx);
        for (int i = 0; i < 4; i++) CelestePhysics.step(s, aimDiagonal, P, world);
        assertEquals(P.dashSpeed(), Math.hypot(s.vx, s.vy), 2e-6);
        assertTrue(s.vx > 0 && s.vy > 0);
        assertTrue(s.locksTurn());
    }

    @Test
    void onePressCannotSpendTwoChargesAcrossThreeSubsteps() {
        var two = new PhysicsParameters(16, P.runSpeed(), P.jumpSpeed(), P.dashSpeed(), P.gravity(), P.maxFall(), 2);
        var s = new PhysicsState(); s.y = 20; s.dashes = 2;
        var press = new PhysicsInput(0, 0, 1, 0, false, false, false, true);
        CelestePhysics.tick(s, press, two, new Room());
        for (int i = 0; i < 6; i++) CelestePhysics.tick(s, press.heldOnly(), two, new Room());
        assertEquals(1, s.dashes);
    }

    @Test
    void dashHitsTheWallInsteadOfTestingOnlyItsEndpoint() {
        Room world = new Room(); world.wall = 1.0 / 16;
        var s = new PhysicsState(); s.y = 5;
        var press = new PhysicsInput(1, 0, 1, 0, false, false, false, true);
        CelestePhysics.step(s, press, P, world);
        for (int i = 0; i < 8; i++) CelestePhysics.step(s, press.heldOnly(), P, world);
        assertEquals(1.0 / 16, s.x, 1e-9);
        assertEquals(0, s.vx, 1e-9);
    }

    @Test
    void resourcesRefillOnGroundButNotInMidair() {
        var s = new PhysicsState(); s.y = 10; s.dashes = 0; s.stamina = 0;
        Room world = new Room();
        CelestePhysics.step(s, PhysicsInput.NONE, P, world);
        assertEquals(0, s.dashes); assertEquals(0, s.stamina);
        s.y = 0;
        CelestePhysics.step(s, PhysicsInput.NONE, P, world);
        assertEquals(1, s.dashes); assertEquals(110, s.stamina);
    }

    @Test
    void wallJumpPushesAwayAndRestoresNormalState() {
        Room world = new Room(); world.wall = 0;
        var s = new PhysicsState(); s.y = 4;
        CelestePhysics.step(s, JUMP, P, world);
        assertEquals(-130.0 / 16, s.vx, 1e-9);
        assertEquals(P.jumpSpeed(), s.vy, 1e-9);
        assertEquals(PhysicsState.Motion.NORMAL, s.motion);
    }

    @Test
    void grabDrainsStillStaminaAndReleasingDropsThePlayer() {
        Room world = new Room(); world.wall = 0;
        var s = new PhysicsState(); s.y = 4; s.motion = PhysicsState.Motion.CLIMB; s.stamina = 50;
        var grab = new PhysicsInput(0, 0, 0, 0, false, true, false, false);
        for (int i = 0; i < 60; i++) CelestePhysics.step(s, grab, P, world);
        assertEquals(40, s.stamina, 0.0002); // Reference accumulates stamina in float32.
        assertEquals(4, s.y, 1e-7);
        CelestePhysics.step(s, PhysicsInput.NONE, P, world);
        CelestePhysics.step(s, PhysicsInput.NONE, P, world);
        assertFalse(s.locksTurn()); assertTrue(s.vy < 0);
    }

    @Test
    void climbingUpCostsMoreThanHoldingAndExhaustionReleasesTheWall() {
        Room world = new Room(); world.wall = 0;
        var s = new PhysicsState(); s.y = 4; s.motion = PhysicsState.Motion.CLIMB; s.stamina = 1;
        var up = new PhysicsInput(0, 1, 0, 1, false, true, false, false);
        for (int i = 0; i < 3; i++) CelestePhysics.step(s, up, P, world);
        assertEquals(0, s.stamina, 1e-9);
        assertEquals(PhysicsState.Motion.NORMAL, s.motion);
    }

    @Test
    void replayFromACopyMatchesUninterruptedSimulation() {
        Room world = new Room(); var s = new PhysicsState();
        for (int i = 0; i < 12; i++) CelestePhysics.tick(s, RIGHT, P, world);
        PhysicsState restored = s.copy();
        PhysicsInput[] inputs = {JUMP, RIGHT, RIGHT, JUMP.heldOnly(), PhysicsInput.NONE};
        for (PhysicsInput input : inputs) {
            CelestePhysics.tick(s, input, P, world);
            CelestePhysics.tick(restored, input, P, world);
        }
        assertEquals(s.x, restored.x); assertEquals(s.y, restored.y);
        assertEquals(s.vx, restored.vx); assertEquals(s.vy, restored.vy);
        assertEquals(s.variableJump, restored.variableJump); assertEquals(s.stamina, restored.stamina);
        assertEquals(s.dashes, restored.dashes); assertEquals(s.motion, restored.motion);
        restored.x += 1;
        assertNotEquals(s.x, restored.x);
    }

    @Test
    void clearingActionsDoesNotRefillAirborneResources() {
        var s = new PhysicsState(); s.dashes = 0; s.stamina = 12; s.motion = PhysicsState.Motion.DASH;
        s.clearAction();
        assertEquals(0, s.dashes); assertEquals(12, s.stamina); assertFalse(s.locksTurn());
    }
}
