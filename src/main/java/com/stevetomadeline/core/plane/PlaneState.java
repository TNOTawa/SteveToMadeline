package com.stevetomadeline.core.plane;

import java.util.Objects;

/** Logical movement plane. Camera interpolation never changes this state. */
public record PlaneState(Heading heading, double anchorX, double anchorZ) {
    public PlaneState {
        Objects.requireNonNull(heading);
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorZ)) {
            throw new IllegalArgumentException("Non-finite plane anchor");
        }
    }

    public static double blockCenter(double coordinate) {
        return Math.floor(coordinate) + 0.5;
    }

    public static PlaneState centered(Heading heading, double x, double z) {
        return new PlaneState(heading, blockCenter(x), blockCenter(z));
    }

    public double horizontal(double x, double z) {
        return x * heading.rightX() + z * heading.rightZ();
    }

    public double projectX(double x) { return heading.locksZ() ? x : 0.0; }
    public double projectZ(double z) { return heading.locksZ() ? 0.0 : z; }
    public double normalCoordinate() { return heading.locksZ() ? anchorZ : anchorX; }

    public boolean aligned(double x, double z) {
        return Math.abs((heading.locksZ() ? z : x) - normalCoordinate()) <= 1.0e-5;
    }
}
