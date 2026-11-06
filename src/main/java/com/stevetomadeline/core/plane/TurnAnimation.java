package com.stevetomadeline.core.plane;

/** Clockwise camera easing with zero endpoint velocity and acceleration. */
public record TurnAnimation(float startYaw, double startTick, double durationTicks) {
    public TurnAnimation {
        if (!Double.isFinite(startTick) || !Double.isFinite(durationTicks) || durationTicks <= 0) {
            throw new IllegalArgumentException("Invalid turn timing");
        }
    }

    public boolean active(double tick) { return tick < startTick + durationTicks; }

    public float yaw(double tick) {
        double t = Math.clamp((tick - startTick) / durationTicks, 0.0, 1.0);
        double eased = t * t * t * (10.0 + t * (-15.0 + 6.0 * t));
        return startYaw + (float) (90.0 * eased);
    }
}
