package com.stevetomadeline.core.physics;

/** One 60 Hz input sample; Y is positive upwards. */
public record PhysicsInput(int moveX, int moveY, int aimX, int aimY, boolean jumpHeld,
                           boolean grabHeld, boolean jumpPressed, boolean dashPressed, boolean crouchDashPressed) {
    public PhysicsInput(int moveX, int moveY, int aimX, int aimY, boolean jumpHeld,
                        boolean grabHeld, boolean jumpPressed, boolean dashPressed) {
        this(moveX, moveY, aimX, aimY, jumpHeld, grabHeld, jumpPressed, dashPressed, false);
    }
    public static final PhysicsInput NONE = new PhysicsInput(0, 0, 0, 0, false, false, false, false);

    public PhysicsInput {
        if (Math.abs(moveX) > 1 || Math.abs(moveY) > 1 || Math.abs(aimX) > 1 || Math.abs(aimY) > 1) {
            throw new IllegalArgumentException("Invalid input axis");
        }
    }

    public PhysicsInput heldOnly() {
        return new PhysicsInput(moveX, moveY, aimX, aimY, jumpHeld, grabHeld, false, false);
    }
}
