package com.stevetomadeline.core.physics;

/** Replayable scalar state. Positions are blocks; float speeds/timers follow the reference. */
public final class PhysicsState implements Cloneable {
    public enum Motion { NORMAL, DASH, CLIMB, SWIM }
    public double x, y;
    public float vx, vy, remainderX, remainderY;
    public float dashX, dashY, beforeDashX, variableSpeed, maxFallSpeed = 10;
    public float stamina = 110, retainedSpeed, hopWaitSpeed;
    public int facing = 1, dashes = 1;
    public Motion motion = Motion.NORMAL;
    public boolean grounded, sliding, ducking, autoJump, dashAimPending, dashStartedOnGround;
    public boolean nativeMovement;
    public float coyote, jumpBuffer, dashBuffer, crouchDashBuffer, variableJump, dashCooldown, refillCooldown;
    public float dashWindup, dashTime, dashAttack, wallSlide = 1.2f, forceTime, climbNoMove;
    public float wallBoost, wallRetention;
    public int forceDirection, wallBoostDirection, previousMove, previousMoveY, hopWaitX, lastClimbMove;
    public float spriteScaleX = 1, spriteScaleY = 1;
    public int simulationFrame, dashSequence, jumpSequence, landingSequence;

    public PhysicsState copy() {
        try { return (PhysicsState) super.clone(); }
        catch (CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
    }

    public boolean locksTurn() { return motion == Motion.DASH || motion == Motion.CLIMB; }

    public void clearAction() {
        motion = Motion.NORMAL;
        jumpBuffer = dashBuffer = crouchDashBuffer = variableJump = coyote = 0;
        dashWindup = dashTime = dashAttack = forceTime = climbNoMove = wallBoost = wallRetention = 0;
        retainedSpeed = hopWaitSpeed = remainderX = remainderY = 0;
        hopWaitX = 0;
        autoJump = sliding = ducking = dashAimPending = false;
        nativeMovement = false;
        maxFallSpeed = 10;
        spriteScaleX = spriteScaleY = 1;
    }
}
