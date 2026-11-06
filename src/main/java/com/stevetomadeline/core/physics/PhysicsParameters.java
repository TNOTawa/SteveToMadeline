package com.stevetomadeline.core.physics;

/** Server-owned block/second units. Ratios and timings derive from desk-madeline Player.cs. */
public record PhysicsParameters(double pixelsPerBlock, double runSpeed, double jumpSpeed,
                                double dashSpeed, double gravity, double maxFall, int maxDashes) {
    public static final PhysicsParameters DEFAULT = new PhysicsParameters(16, 90.0 / 16, 105.0 / 16,
            240.0 / 16, 900.0 / 16, 160.0 / 16, 1);

    public PhysicsParameters {
        if (pixelsPerBlock != 16
                || !valid(runSpeed, 0.1, 40) || !valid(jumpSpeed, 0.1, 40)
                || !valid(dashSpeed, 0.1, 80) || !valid(gravity, 1, 200)
                || !valid(maxFall, 1, 80) || maxDashes < 1 || maxDashes > 2) {
            throw new IllegalArgumentException("Invalid physics parameters");
        }
    }

    private static boolean valid(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    public float pixels(double value) { return (float) value / 16f; }
}
