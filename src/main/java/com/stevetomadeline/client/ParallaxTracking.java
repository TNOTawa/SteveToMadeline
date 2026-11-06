package com.stevetomadeline.client;

/** Position-based horizontal parallax, independent of frame rate and player velocity. */
public final class ParallaxTracking {
    private static final double MAX_LAG = 32.0;
    private static final double TELEPORT_DISTANCE = 48.0;
    private Pose previous;
    private double anchorHorizontal;
    private double previousRatio;

    public record Pose(double x, double y, double z, int direction) {
    }

    public record Sample(Pose pose, boolean reset) {
    }

    public Sample update(double x, double y, double z, int direction, double ratio) {
        direction = Math.floorMod(direction, 4);
        double rightX = switch (direction) { case 0 -> -1.0; case 2 -> 1.0; default -> 0.0; };
        double rightZ = switch (direction) { case 1 -> -1.0; case 3 -> 1.0; default -> 0.0; };
        double horizontal = x * rightX + z * rightZ;
        boolean reset = previous == null || previous.direction != direction || previousRatio != ratio
                || squaredDistance(previous, x, y, z) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
        if (reset) {
            anchorHorizontal = horizontal;
        }
        double lag = Math.clamp((horizontal - anchorHorizontal) * (1.0 - ratio), -MAX_LAG, MAX_LAG);
        previous = new Pose(x, y, z, direction);
        previousRatio = ratio;
        return new Sample(new Pose(x - rightX * lag, y, z - rightZ * lag, direction), reset);
    }

    private static double squaredDistance(Pose previous, double x, double y, double z) {
        double dx = x - previous.x;
        double dy = y - previous.y;
        double dz = z - previous.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public void reset() {
        previous = null;
    }
}
