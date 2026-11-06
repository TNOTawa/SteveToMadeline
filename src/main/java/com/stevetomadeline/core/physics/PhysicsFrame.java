package com.stevetomadeline.core.physics;

/** Exactly one Minecraft tick, containing three independently sampled Celeste frames. */
public record PhysicsFrame(PhysicsInput first, PhysicsInput second, PhysicsInput third) {
    public static final PhysicsFrame NONE = held(PhysicsInput.NONE);
    public static PhysicsFrame held(PhysicsInput input) {
        return new PhysicsFrame(input, input.heldOnly(), input.heldOnly());
    }
    public PhysicsInput at(int index) {
        return switch (index) { case 0 -> first; case 1 -> second; case 2 -> third; default -> throw new IndexOutOfBoundsException(index); };
    }
}
