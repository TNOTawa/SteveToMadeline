package com.stevetomadeline.core.physics;

import com.stevetomadeline.network.PhysicsInputPayload;
import java.util.ArrayDeque;

/** Logical-side-local transient simulation and network bookkeeping. */
public final class PhysicsSession {
    public com.stevetomadeline.core.plane.Heading heading = com.stevetomadeline.core.plane.Heading.SOUTH;
    public PhysicsState state = new PhysicsState();
    public PhysicsParameters parameters = PhysicsParameters.DEFAULT;
    public boolean active;
    public int epoch;
    public int lastReceived;
    public int acknowledged;
    public int idleTicks;
    public PhysicsInput held = PhysicsInput.NONE;
    public final ArrayDeque<PhysicsInputPayload> inputs = new ArrayDeque<>();
    public double lastX, lastY, lastZ;

    public void newEpoch() {
        epoch++;
        lastReceived = acknowledged = idleTicks = 0;
        held = PhysicsInput.NONE;
        inputs.clear();
    }
}
