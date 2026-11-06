package com.stevetomadeline.core.plane;

/** Per-player state, separate on the logical client and server; never persisted to saves. */
public interface PlanePlayerAccess {
    PlaneState stevetomadeline$getPlane();
    void stevetomadeline$setPlane(PlaneState state);
    long stevetomadeline$getLastTurnTick();
    void stevetomadeline$setLastTurnTick(long tick);
    boolean stevetomadeline$isTurnLocked();
    void stevetomadeline$setTurnLocked(boolean locked);
}
