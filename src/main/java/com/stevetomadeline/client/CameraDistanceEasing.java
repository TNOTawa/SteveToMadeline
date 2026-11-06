package com.stevetomadeline.client;

/** Frame-rate independent distance response; zero elapsed time never snaps to a new target. */
public final class CameraDistanceEasing {
    private double distance = Double.NaN;
    public double update(double target, double seconds) {
        if (!Double.isFinite(distance)) distance = target;
        else distance += (target - distance) * -Math.expm1(-7 * Math.clamp(seconds, 0, .1));
        if (Math.abs(target - distance) < .0001) distance = target;
        return distance;
    }
    public void reset() { distance = Double.NaN; }
    public static boolean useSlice(boolean enabled, boolean obstructed, double available) {
        return enabled && obstructed && available < 3;
    }
    public static boolean transitionSlice(boolean enabled, boolean obstructed, double distance, double available) {
        return enabled && obstructed && distance > available + .001;
    }
}
