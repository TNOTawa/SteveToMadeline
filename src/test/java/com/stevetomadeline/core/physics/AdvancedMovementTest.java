package com.stevetomadeline.core.physics;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedMovementTest {
    private record Rect(double l, double b, double r, double t) { }
    private static final class World implements PhysicsWorld {
        final List<Rect> solids = new ArrayList<>();
        World add(double l, double b, double r, double t) { solids.add(new Rect(l / 16, b / 16, r / 16, t / 16)); return this; }
        @Override public boolean solid(double x, double y, boolean ducking) {
            double h = (ducking ? 6.0 : 11.0) / 16;
            return solids.stream().anyMatch(r -> x - 0.25 < r.r && x + 0.25 > r.l && y < r.t && y + h > r.b);
        }
        @Override public boolean solidPoint(double x, double y) {
            return solids.stream().anyMatch(r -> x >= r.l && x < r.r && y > r.b && y <= r.t);
        }
    }
    private static World floor() { return new World().add(-1000, -40, 1000, 0); }
    private static PhysicsInput input(int x, int y, boolean jump, boolean dash) {
        return new PhysicsInput(x, y, x, y, jump, false, jump, dash);
    }
    private static void step(PhysicsState s, PhysicsInput i, World w) { CelestePhysics.step(s, i, PhysicsParameters.DEFAULT, w); }
    private static PhysicsState dash(World w, boolean duck, int wait) {
        var s = new PhysicsState();
        step(s, input(1, duck ? -1 : 0, false, true), w);
        for (int i = 0; i < wait; i++) step(s, input(1, 0, false, false), w);
        return s;
    }
    @Test void superHyperAndReverseHyperUseReferenceLaunchSpeeds() {
        for (boolean duck : new boolean[]{false, true}) for (int facing : new int[]{-1, 1}) {
            World w = floor(); var s = dash(w, duck, 5);
            step(s, input(facing, 0, true, false), w);
            assertEquals((duck ? 325f : 260f) / 16 * facing, s.vx);
            assertEquals((duck ? 52.5f : 105f) / 16, s.vy);
            assertEquals(PhysicsState.Motion.NORMAL, s.motion);
            assertFalse(s.ducking);
        }
    }
    @Test void extendedHyperRefillsButAnEarlyHyperDoesNot() {
        World w = floor();
        var early = dash(w, true, 5); step(early, input(1, 0, true, false), w);
        var extended = dash(w, true, 11); step(extended, input(1, 0, true, false), w);
        assertEquals(0, early.dashes); assertEquals(1, extended.dashes);
        assertEquals(325f / 16, extended.vx);
    }
    @Test void simultaneousDashAndJumpSurvivesFreezeThenSupersBeforeAim() {
        World w = floor(); var s = new PhysicsState();
        step(s, input(1, 0, true, true), w);
        for (int i = 0; i < 3; i++) { step(s, input(1, 0, false, false), w); assertEquals(0, s.vx); }
        step(s, input(-1, 0, false, false), w);
        assertEquals(-260f / 16, s.vx); assertEquals(105f / 16, s.vy);
    }
    @Test void wavedashLandsIntoCrouchThenJumpsAsHyper() {
        World w = floor(); var s = new PhysicsState(); s.y = 12.0 / 16;
        step(s, input(1, -1, false, true), w);
        for (int i = 0; i < 20 && s.y > 0; i++) step(s, input(1, -1, false, false), w);
        assertEquals(0, s.y); assertTrue(s.ducking); assertEquals(0, s.dashY);
        step(s, input(1, 0, true, false), w);
        assertEquals(325f / 16, s.vx); assertEquals(52.5f / 16, s.vy);
    }
    @Test void ultraMultipliesRetainedHorizontalSpeedEvenAfterDashStateEnds() {
        World w = floor(); var s = new PhysicsState(); s.y = 40.0 / 16; s.vx = 400f / 16;
        step(s, input(1, -1, false, true), w);
        for (int i = 0; i < 40; i++) {
            float before = s.vx;
            var previous = s.motion;
            step(s, input(1, -1, false, false), w);
            // Reaching floor height is not a collision until MoveV tries the next pixel.
            if (s.landingSequence > 0) {
                assertEquals(0, s.y);
                assertEquals(PhysicsState.Motion.NORMAL, previous);
                assertEquals((before - 500f / 16 * CelestePhysics.STEP) * 1.2f, s.vx, 2e-5);
                assertTrue(s.vx > before); return;
            }
        }
        fail("Did not land during Ultra setup");
    }
    @Test void neutralWallJumpHasNoSteeringLockAndThreePixelReach() {
        World w = new World().add(7, -100, 30, 100);
        var s = new PhysicsState();
        // Exactly three pixels touches the boundary; overlap needs the extra fraction.
        s.x = 0.5 / 16;
        step(s, input(0, 0, true, false), w);
        assertEquals(-130f / 16, s.vx); assertEquals(0, s.forceTime);
        step(s, input(1, 0, false, false), w);
        assertTrue(s.vx > -130f / 16);
    }
    @Test void upwardDashWallbounceUsesFivePixelReachAndLongerVariableJump() {
        World w = new World().add(8, -100, 30, 200); var s = new PhysicsState();
        step(s, input(0, 1, false, true), w);
        for (int i = 0; i < 4; i++) step(s, input(0, 1, false, false), w);
        step(s, input(0, 1, true, false), w);
        assertEquals(-170f / 16, s.vx); assertEquals(160f / 16, s.vy); assertEquals(0.25f, s.variableJump);
    }
    @Test void ceilingCorrectionPreservesUpwardVelocity() {
        World w = new World().add(2, 12, 30, 30); var s = new PhysicsState(); s.vy = 105f / 16;
        step(s, PhysicsInput.NONE, w);
        step(s, PhysicsInput.NONE, w);
        assertEquals(-2.0 / 16, s.x); assertTrue(s.vy > 0);
    }
    @Test void horizontalDashCorrectsUpOntoOnePixelLedge() {
        World w = floor().add(8, 0, 100, 1); var s = dash(w, false, 4);
        for (int i = 0; i < 3; i++) step(s, input(1, 0, false, false), w);
        assertEquals(1.0 / 16, s.y); assertTrue(s.vx > 0); assertTrue(s.x > 8.0 / 16);
    }
    @Test void copyIncludesEveryReplayField() throws Exception {
        var s = dash(floor(), true, 7); PhysicsState copy = s.copy();
        for (var field : PhysicsState.class.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) assertEquals(field.get(s), field.get(copy), field.getName());
        }
        copy.remainderX += 1; assertNotEquals(s.remainderX, copy.remainderX);
    }

    @Test void crouchedClimbJumpKeepsTheSixPixelPoseUntilThereIsRoomToStand() {
        World w = new World().add(7, -100, 30, 100).add(-30, 6, 7, 10);
        var s = new PhysicsState(); s.motion = PhysicsState.Motion.CLIMB; s.ducking = true; s.stamina = 60;
        step(s, new PhysicsInput(0, 0, 0, 0, true, true, true, false), w);
        assertEquals(PhysicsState.Motion.NORMAL, s.motion);
        assertTrue(s.ducking); assertEquals(32.5f, s.stamina);
        assertEquals(0, s.vy); // Low roof cancels the impulse at the first upward pixel.
        assertEquals(0, s.y);
    }

    @Test void coffeeJumpSetupAcceptsDistinctJumpEdgesWhileTheWallIsStillInReach() {
        World w = new World().add(7, -100, 30, 200);
        var s = new PhysicsState(); s.x = 3.0 / 16;
        step(s, input(0, 0, true, false), w);
        assertEquals(1, s.jumpSequence);
        double firstHeight = s.y;
        // A second bound key can press next frame without releasing the first.
        step(s, input(0, 0, true, false), w);
        assertEquals(2, s.jumpSequence);
        assertTrue(s.y > firstHeight);
        step(s, new PhysicsInput(0, 0, 0, 0, true, false, false, false), w);
        assertEquals(2, s.jumpSequence);
    }

    @Test void halfStaminaWallboostRefundsOnlyAfterThePreviousInputWasAway() {
        World w = new World().add(7, -100, 30, 100);
        var s = new PhysicsState(); s.motion = PhysicsState.Motion.CLIMB; s.stamina = 55;
        step(s, new PhysicsInput(0, 0, 0, 0, true, true, true, false), w);
        assertEquals(27.5f, s.stamina);
        assertTrue(s.wallBoost > 0);
        step(s, input(-1, 0, false, false), w);
        assertEquals(27.5f, s.stamina);
        step(s, input(-1, 0, false, false), w);
        assertEquals(55f, s.stamina);
        assertEquals(0, s.wallBoost);
    }

    @Test void bunnyHopRetainsAboveRunSpeedThroughLandingAndImmediateJump() {
        World w = floor(); var s = new PhysicsState(); s.vx = 325f / 16; s.y = 1.0 / 16; s.vy = -160f / 16;
        for (int i = 0; i < 5 && s.landingSequence == 0; i++) step(s, input(1, 0, false, false), w);
        assertTrue(s.landingSequence > 0);
        float speed = s.vx;
        step(s, input(1, 0, true, false), w);
        assertTrue(s.vx > speed);
        assertTrue(s.vx > 90f / 16);
        assertTrue(s.vy > 0);
    }

    @Test void reverseDemoHyperLaunchesAgainstTheDashWithoutChangingTheDashAim() {
        World w = floor(); var s = dash(w, true, 5);
        assertTrue(s.dashX > 0);
        step(s, input(-1, 0, true, false), w);
        assertEquals(-325f / 16, s.vx);
        assertEquals(52.5f / 16, s.vy);
        assertEquals(1f, s.dashX);
    }
}
