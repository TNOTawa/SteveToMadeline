package com.stevetomadeline.core.physics;

/** Optional observation only. Servers and correction replay use NONE. */
@FunctionalInterface
public interface PhysicsEffects {
    enum Event { JUMP, SUPER, HYPER, WALL_JUMP, WALLBOUNCE, CLIMB_JUMP, GRAB, LET_GO, CLIMB_HOP }
    PhysicsEffects NONE = (event, state) -> { };
    void emit(Event event, PhysicsState state);
}
