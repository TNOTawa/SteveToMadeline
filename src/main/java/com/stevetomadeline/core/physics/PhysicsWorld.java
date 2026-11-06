package com.stevetomadeline.core.physics;

/** Collision queries never mutate entities, so the same world can be used during replay. */
public interface PhysicsWorld {
    boolean solid(double x, double y, boolean ducking);
    boolean solidPoint(double x, double y);
}
