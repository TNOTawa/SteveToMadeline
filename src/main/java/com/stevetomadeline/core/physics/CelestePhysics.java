package com.stevetomadeline.core.physics;

import static com.stevetomadeline.core.physics.PhysicsState.Motion.*;

/**
 * Static-terrain Normal/Climb/Dash port of desk-madeline Player.cs, 8404a89.
 * Swim adapts NoelFB/Celeste's public Player.cs sample, 7fd7ae7.
 * Reference float arithmetic, timer order, axis order and integer-pixel remainders;
 * world coordinates use blocks and positive-up Y. See THIRD_PARTY_NOTICES.md.
 */
public final class CelestePhysics {
    public static final float STEP = 1f / 60f;
    public static final double PIXEL = 1.0 / 16;
    private CelestePhysics() { }

    private static float px(float value) { return value / 16f; }
    private static float down(float value) { return value > 0 ? value - STEP : value; }
    private static float approach(float value, float target, float amount) {
        return value < target ? Math.min(value + amount, target) : Math.max(value - amount, target);
    }
    private static boolean hit(PhysicsState s, PhysicsWorld w, double dx, double dy) {
        return w.solid(s.x + dx * PIXEL, s.y + dy * PIXEL, s.ducking);
    }
    public static boolean grounded(PhysicsState s, PhysicsWorld w) {
        return groundAt(s, w, s.x, s.y) && s.vy <= 0;
    }
    private static boolean groundAt(PhysicsState s, PhysicsWorld w, double x, double y) {
        return w.solid(x, y - PIXEL, s.ducking) && !w.solid(x, y, s.ducking);
    }
    private static boolean canStand(PhysicsState s, PhysicsWorld w) { return !w.solid(s.x, s.y, false); }
    private static boolean tired(PhysicsState s) { return s.stamina + (s.wallBoost > 0 ? 27.5f : 0) < 20; }
    private static boolean superWallAngle(PhysicsState s) { return Math.abs(s.dashX) <= 0.2f && s.dashY >= 0.75f; }
    private static boolean wallJumpCheck(PhysicsState s, PhysicsWorld w, int dir) {
        return hit(s, w, dir * (s.dashAttack > 0 && s.dashX == 0 && s.dashY == 1 ? 5 : 3), 0);
    }
    private static boolean canDash(PhysicsState s) {
        return (s.dashBuffer > 0 || s.crouchDashBuffer > 0) && s.dashCooldown <= 0 && s.dashes > 0;
    }

    public static void tick(PhysicsState s, PhysicsInput input, PhysicsParameters p, PhysicsWorld w) {
        tick(s, PhysicsFrame.held(input), p, w);
    }
    public static void tick(PhysicsState s, PhysicsFrame input, PhysicsParameters p, PhysicsWorld w) {
        for (int i = 0; i < 3; i++) step(s, input.at(i), p, w);
    }

    public static void step(PhysicsState s, PhysicsInput input, PhysicsParameters p, PhysicsWorld w) {
        step(s, input, p, w, PhysicsEffects.NONE);
    }

    public static void step(PhysicsState s, PhysicsInput input, PhysicsParameters p, PhysicsWorld w, PhysicsEffects effects) {
        if (input.jumpPressed()) s.jumpBuffer = 0.08f;
        if (input.dashPressed()) s.dashBuffer = 0.08f;
        if (input.crouchDashPressed()) s.crouchDashBuffer = 0.08f;
        boolean jumpAvailable = s.jumpBuffer > 0;
        // Freeze returns even on the frame its timer crosses zero. Inputs still buffer.
        if (s.dashWindup > 0) { s.dashWindup -= STEP; return; }
        s.simulationFrame++;
        if (s.sliding) s.wallSlide = Math.max(0, s.wallSlide - STEP);
        s.sliding = false;
        if (!input.jumpPressed()) s.jumpBuffer = down(s.jumpBuffer);
        if (!input.dashPressed()) s.dashBuffer = down(s.dashBuffer);
        if (!input.crouchDashPressed()) s.crouchDashBuffer = down(s.crouchDashBuffer);
        s.dashCooldown = down(s.dashCooldown);
        boolean refillReady = s.refillCooldown <= 0;
        s.refillCooldown = down(s.refillCooldown);
        s.dashAttack = down(s.dashAttack);
        s.variableJump = down(s.variableJump);
        s.grounded = s.motion != SWIM && grounded(s, w);
        if (s.motion == SWIM) {
            s.stamina = 110;
            if (refillReady) s.dashes = p.maxDashes();
        }
        if (s.grounded) {
            s.stamina = 110;
            s.wallSlide = 1.2f;
            if (s.motion != CLIMB) s.autoJump = false;
            if (refillReady) s.dashes = p.maxDashes();
            s.coyote = 0.1f;
        } else s.coyote = down(s.coyote);

        if (s.wallBoost > 0) {
            s.wallBoost -= STEP;
            if (s.previousMove == s.wallBoostDirection) {
                s.vx = px(130) * s.wallBoostDirection;
                s.stamina += 27.5f;
                s.wallBoost = 0;
            }
        }
        int move;
        if (s.forceTime > 0) { s.forceTime -= STEP; move = s.forceDirection; }
        else move = input.moveX();
        if (move != 0 && s.motion != CLIMB) {
            if (s.facing != move && s.ducking) { s.spriteScaleX = 0.8f; s.spriteScaleY = 1.2f; }
            s.facing = move;
        }
        s.previousMove = move;
        s.previousMoveY = input.moveY();

        // Restoring wall speed before Jump is necessary for corner boosts.
        if (s.wallRetention > 0) {
            int sign = (int) Math.signum(s.retainedSpeed);
            if (Math.signum(s.vx) == -sign) s.wallRetention = 0;
            else if (!hit(s, w, sign, 0)) { s.vx = s.retainedSpeed; s.wallRetention = 0; }
            else s.wallRetention -= STEP;
        }
        if (s.hopWaitX != 0) {
            if (Math.signum(s.vx) == -s.hopWaitX || s.vy < 0) s.hopWaitX = 0;
            else if (!hit(s, w, s.hopWaitX, 0)) { s.vx = s.hopWaitSpeed; s.hopWaitX = 0; }
        }
        var previous = s.motion;
        switch (s.motion) {
            case NORMAL -> normal(s, input, move, jumpAvailable, w, effects);
            case CLIMB -> climb(s, input, move, jumpAvailable, w, effects);
            case DASH -> dash(s, input, move, jumpAvailable, w, effects);
            case SWIM -> swim(s, input, move, jumpAvailable, w, effects);
        }
        // State update precedes the dash coroutine. A first-frame super cancels aim.
        if (s.dashAimPending && previous == DASH) {
            s.dashAimPending = false;
            if (s.motion == DASH) {
                applyDashAim(s, input);
                if (w.water(s.x, s.y, s.ducking)) { s.vx *= 0.75f; s.vy *= 0.75f; }
            }
        }
        if (!s.grounded && s.dashAttack > 0 && s.dashY == 0 && hit(s, w, 0, -3)) moveVExact(s, w, -3, true);
        if (s.vy < 0 && canStand(s, w) && !s.grounded && s.coyote <= 0 && s.motion != CLIMB) s.ducking = false;
        moveH(s, w, s.vx * 16f * STEP);
        double beforeY = s.y;
        moveV(s, w, s.vy * 16f * STEP);
        if (s.motion == SWIM && s.vy > 0 && s.vy <= px(60)) {
            // Float at the surface without repeatedly switching to falling physics.
            while (s.y > beforeY && !swimming(s, w) && !hit(s, w, 0, -1)) {
                s.y -= PIXEL; s.vy = s.remainderY = 0;
            }
        }
        // Water transitions follow movement, as in Player.Update. Refilling above
        // therefore uses the state at the start of the frame, not a future contact.
        if ((s.motion == NORMAL || s.motion == CLIMB) && swimming(s, w)) enterSwim(s);
        s.spriteScaleX = approach(s.spriteScaleX, 1, 1.75f * STEP);
        s.spriteScaleY = approach(s.spriteScaleY, 1, 1.75f * STEP);
    }

    private static boolean swimming(PhysicsState s, PhysicsWorld w) {
        return w.water(s.x, s.y, s.ducking) && w.water(s.x, s.y + 8 * PIXEL, s.ducking);
    }

    private static void enterSwim(PhysicsState s) {
        if (s.vy < 0) s.vy *= 0.5f;
        s.motion = SWIM;
        s.stamina = 110;
    }

    private static void swim(PhysicsState s, PhysicsInput input, int move, boolean jumpAvailable,
                             PhysicsWorld w, PhysicsEffects effects) {
        if (!swimming(s, w)) { enterNormal(s); return; }
        if (canStand(s, w)) s.ducking = false;
        if (canDash(s)) { beginDash(s, input, w); return; }
        boolean underwater = w.water(s.x, s.y + 9 * PIXEL, s.ducking);
        if (!underwater && s.vy <= 0 && input.grabHeld() && canStand(s, w)
                && Math.signum(s.vx) != -s.facing && hit(s, w, s.facing * 2, 0) && !hit(s, w, 0, 1)) {
            moveVExact(s, w, 1, false);
            enterClimb(s, w, effects);
            return;
        }
        float x = move, y = input.moveY();
        float length = (float) Math.hypot(x, y);
        if (length > 0) { x /= length; y /= length; }
        float accelX = Math.abs(s.vx) > px(80) && Math.signum(s.vx) == Math.signum(x) ? px(400) : px(600);
        s.vx = approach(s.vx, px(underwater ? 60 : 80) * x, accelX * STEP);
        if (y == 0 && !w.water(s.x, s.y + 18 * PIXEL, s.ducking)) {
            s.vy = approach(s.vy, px(60), px(600) * STEP);
        } else if (y <= 0 || underwater) {
            float accelY = Math.abs(s.vy) > px(80) && Math.signum(s.vy) == Math.signum(y) ? px(400) : px(600);
            s.vy = approach(s.vy, px(80) * y, accelY * STEP);
        }
        if (!underwater && move != 0 && hit(s, w, move, 0) && !hit(s, w, move, 3)) climbHop(s, effects);
        if (jumpAvailable && !w.water(s.x, s.y + 14 * PIXEL, s.ducking)) {
            jump(s, move);
            enterNormal(s);
            effects.emit(PhysicsEffects.Event.JUMP, s);
        }
    }

    private static void normal(PhysicsState s, PhysicsInput input, int move, boolean jumpAvailable, PhysicsWorld w, PhysicsEffects effects) {
        if (input.grabHeld() && !tired(s) && !s.ducking && s.vy <= 0 && Math.signum(s.vx) != -s.facing) {
            if (hit(s, w, s.facing * 2, 0)) { enterClimb(s, w, effects); return; }
            if (input.moveY() >= 0) for (int i = 1; i <= 2; i++) {
                if (!hit(s, w, 0, i) && hit(s, w, s.facing * 2, i)) {
                    moveVExact(s, w, i, false); enterClimb(s, w, effects); return;
                }
            }
        }
        if (canDash(s)) { beginDash(s, input, w); return; }
        if (s.ducking) {
            if (s.grounded && input.moveY() != -1 && canStand(s, w)) {
                s.ducking = false; s.spriteScaleX = 0.8f; s.spriteScaleY = 1.2f;
            } else if (s.grounded && input.moveY() != -1 && s.vx == 0) {
                for (int i = 4; i > 0; i--) {
                    if (!w.solid(s.x + i * PIXEL, s.y, false)) { moveH(s, w, 50f * STEP); break; }
                    if (!w.solid(s.x - i * PIXEL, s.y, false)) { moveH(s, w, -50f * STEP); break; }
                }
            }
        } else if (s.grounded && input.moveY() == -1 && s.vy <= 0) {
            s.ducking = true; s.spriteScaleX = 1.4f; s.spriteScaleY = 0.6f;
        }
        if (s.ducking && s.grounded) s.vx = approach(s.vx, 0, px(500) * STEP);
        else {
            float mult = s.grounded ? 1 : 0.65f;
            float accel = Math.abs(s.vx) > px(90) && Math.signum(s.vx) == move ? px(400) : px(1000);
            s.vx = approach(s.vx, px(90) * move, accel * mult * STEP);
        }
        s.maxFallSpeed = approach(s.maxFallSpeed, input.moveY() == -1 && s.vy <= -px(160) ? px(240) : px(160), px(300) * STEP);
        if (input.moveY() == -1 && s.vy <= -px(200)) {
            float stretch = Math.min(1, (-s.vy - px(200)) / px(40));
            s.spriteScaleX = 1 - 0.5f * stretch; s.spriteScaleY = 1 + 0.5f * stretch;
        }
        if (!s.grounded) {
            float fall = s.maxFallSpeed;
            if ((move == s.facing || (move == 0 && input.grabHeld())) && input.moveY() != -1 && s.vy <= 0
                    && s.wallSlide > 0 && canStand(s, w) && hit(s, w, s.facing, 0)) {
                s.ducking = false; s.sliding = true; s.vx = s.remainderX = 0;
                if (input.grabHeld() && !tired(s)) { enterClimb(s, w, effects); return; }
                fall = px(160) + (px(20) - px(160)) * (s.wallSlide / 1.2f);
            }
            float mult = Math.abs(s.vy) < px(40) && (input.jumpHeld() || s.autoJump) ? 0.5f : 1;
            s.vy = approach(s.vy, -fall, px(900) * mult * STEP);
        }
        if (s.variableJump > 0) {
            if (input.jumpHeld() || s.autoJump) s.vy = Math.max(s.vy, s.variableSpeed);
            else s.variableJump = 0;
        }
        if (jumpAvailable) {
            if (s.coyote > 0) { jump(s, move); effects.emit(PhysicsEffects.Event.JUMP, s); }
            else if (canStand(s, w)) {
                if (wallJumpCheck(s, w, 1)) wallAction(s, input, move, -1, false, effects);
                else if (wallJumpCheck(s, w, -1)) wallAction(s, input, move, 1, false, effects);
                else if (w.water(s.x, s.y - 2 * PIXEL, s.ducking)) {
                    jump(s, move);
                    effects.emit(PhysicsEffects.Event.JUMP, s);
                }
            }
        }
    }

    private static void prepareJump(PhysicsState s, float duration) {
        s.jumpBuffer = s.coyote = s.wallBoost = s.dashAttack = 0;
        s.variableJump = duration; s.wallSlide = 1.2f; s.autoJump = false;
        s.spriteScaleX = 0.6f; s.spriteScaleY = 1.4f; s.jumpSequence++;
    }
    private static void jump(PhysicsState s, int move) {
        prepareJump(s, 0.2f);
        s.vx += px(40) * move;
        s.vy = s.variableSpeed = px(105);
    }
    private static void superJump(PhysicsState s, PhysicsEffects effects) {
        boolean ducking = s.ducking;
        prepareJump(s, 0.2f);
        s.vx = px(260) * s.facing; s.vy = px(105);
        if (s.ducking) { s.ducking = false; s.vx *= 1.25f; s.vy *= 0.5f; }
        s.variableSpeed = s.vy;
        effects.emit(ducking ? PhysicsEffects.Event.HYPER : PhysicsEffects.Event.SUPER, s);
    }
    private static void wallJump(PhysicsState s, int dir, int move, boolean superJump, PhysicsEffects effects) {
        prepareJump(s, superJump ? 0.25f : 0.2f);
        s.ducking = false;
        s.vx = px(superJump ? 170 : 130) * dir;
        s.vy = s.variableSpeed = px(superJump ? 160 : 105);
        if (!superJump && move != 0) { s.forceDirection = dir; s.forceTime = 0.16f; }
        s.facing = dir;
        effects.emit(superJump ? PhysicsEffects.Event.WALLBOUNCE : PhysicsEffects.Event.WALL_JUMP, s);
    }
    private static void climbJump(PhysicsState s, int move, PhysicsEffects effects) {
        if (!s.grounded) s.stamina -= 27.5f;
        jump(s, move);
        if (move == 0) { s.wallBoostDirection = -s.facing; s.wallBoost = 0.2f; }
        effects.emit(PhysicsEffects.Event.CLIMB_JUMP, s);
    }
    private static void wallAction(PhysicsState s, PhysicsInput input, int move, int dir, boolean dashState, PhysicsEffects effects) {
        if (dashState && superWallAngle(s)) wallJump(s, dir, move, true, effects);
        else if (s.facing == -dir && input.grabHeld() && s.stamina > 0) climbJump(s, move, effects);
        else wallJump(s, dir, move, s.dashAttack > 0 && superWallAngle(s), effects);
    }
    private static void enterNormal(PhysicsState s) { s.motion = NORMAL; s.maxFallSpeed = px(160); }
    private static void enterClimb(PhysicsState s, PhysicsWorld w, PhysicsEffects effects) {
        s.autoJump = false; s.retainedSpeed = s.wallRetention = s.wallBoost = 0; s.hopWaitX = 0;
        s.vx = 0; s.vy *= 0.2f; s.ducking = false; s.wallSlide = 1.2f; s.climbNoMove = 0.1f;
        s.lastClimbMove = 0; s.motion = CLIMB;
        for (int i = 0; i < 2 && !hit(s, w, s.facing, 0); i++) s.x += s.facing * PIXEL;
        effects.emit(PhysicsEffects.Event.GRAB, s);
    }
    private static boolean slip(PhysicsState s, PhysicsWorld w) {
        double x = s.x + (s.facing > 0 ? 4 : -5) * PIXEL;
        double y = s.y + ((s.ducking ? 6 : 11) - 4) * PIXEL;
        return !w.solidPoint(x, y) && !w.solidPoint(x, y + 4 * PIXEL);
    }
    private static void climbHop(PhysicsState s, PhysicsEffects effects) {
        s.vy = Math.max(s.vy, px(120)); s.hopWaitX = s.facing; s.hopWaitSpeed = px(100) * s.facing;
        s.forceDirection = 0; s.forceTime = 0.2f;
        effects.emit(PhysicsEffects.Event.CLIMB_HOP, s);
    }
    private static void climb(PhysicsState s, PhysicsInput input, int move, boolean jumpAvailable, PhysicsWorld w, PhysicsEffects effects) {
        s.climbNoMove -= STEP;
        if (s.grounded) s.stamina = 110;
        if (jumpAvailable) {
            if (move == -s.facing) wallJump(s, -s.facing, move, false, effects); else climbJump(s, move, effects);
            enterNormal(s); return;
        }
        if (canDash(s)) { beginDash(s, input, w); return; }
        if (!input.grabHeld()) { effects.emit(PhysicsEffects.Event.LET_GO, s); enterNormal(s); return; }
        if (!hit(s, w, s.facing, 0)) { if (s.vy > 0) climbHop(s, effects); enterNormal(s); return; }
        float target = 0;
        boolean slipping = false;
        if (s.climbNoMove <= 0) {
            if (input.moveY() == 1) {
                target = px(45);
                if (hit(s, w, 0, 1)) { if (s.vy > 0) s.vy = 0; target = 0; slipping = true; }
                else if (slip(s, w)) { climbHop(s, effects); enterNormal(s); return; }
            } else if (input.moveY() == -1) {
                target = -px(80);
                if (s.grounded) { if (s.vy < 0) s.vy = 0; target = 0; }
            } else slipping = true;
        } else slipping = true;
        s.lastClimbMove = (int) Math.signum(target);
        if (slipping && slip(s, w)) target = -px(30);
        s.vy = approach(s.vy, target, px(900) * STEP);
        if (input.moveY() != -1 && s.vy < 0 && !hit(s, w, s.facing, -1)) s.vy = 0;
        if (s.climbNoMove <= 0) {
            if (s.lastClimbMove > 0) s.stamina -= 45.4545f * STEP;
            else if (s.lastClimbMove == 0) s.stamina -= 10f * STEP;
        }
        if (s.stamina <= 0) { s.stamina = 0; enterNormal(s); }
    }

    private static void beginDash(PhysicsState s, PhysicsInput input, PhysicsWorld w) {
        boolean crouch = s.crouchDashBuffer > 0;
        s.dashBuffer = s.crouchDashBuffer = 0;
        // SwimUpdate enters Dash directly rather than the charge-spending StartDash.
        if (s.motion != SWIM) s.dashes--;
        s.autoJump = false; s.retainedSpeed = s.wallRetention = s.wallBoost = 0; s.hopWaitX = 0;
        s.dashSequence++; s.dashStartedOnGround = s.grounded;
        s.dashCooldown = 0.2f; s.refillCooldown = 0.1f; s.dashAttack = 0.3f; s.wallSlide = 1.2f;
        s.dashWindup = 0.05f; s.beforeDashX = s.vx;
        if (!s.grounded && s.ducking && canStand(s, w)) s.ducking = false;
        else if (!s.ducking && (crouch || input.moveY() == -1)) s.ducking = true;
        s.vx = s.vy = s.dashX = s.dashY = 0;
        s.dashAimPending = true; s.dashTime = 0.15f; s.motion = DASH;
    }
    private static void applyDashAim(PhysicsState s, PhysicsInput input) {
        float x = input.aimX(), y = input.aimY();
        if (x == 0 && y == 0) x = s.facing;
        float length = (float) Math.sqrt(x * x + y * y);
        s.dashX = x / length; s.dashY = y / length;
        s.vx = s.dashX * px(240); s.vy = s.dashY * px(240);
        if (Math.signum(s.beforeDashX) == Math.signum(s.vx) && Math.abs(s.beforeDashX) > Math.abs(s.vx)) s.vx = s.beforeDashX;
        if (s.grounded && s.dashX != 0 && s.dashY < 0 && s.vy < 0) {
            s.dashX = Math.signum(s.dashX); s.dashY = 0; s.vx *= 1.2f; s.vy = 0; s.ducking = true;
        }
        if (s.dashX != 0) s.facing = (int) Math.signum(s.dashX);
    }
    private static void dash(PhysicsState s, PhysicsInput input, int move, boolean jumpAvailable, PhysicsWorld w, PhysicsEffects effects) {
        if (jumpAvailable) {
            if (Math.abs(s.dashY) < 0.1f && s.coyote > 0 && canStand(s, w)) {
                superJump(s, effects); enterNormal(s); return;
            }
            if (canStand(s, w)) {
                if (wallJumpCheck(s, w, 1)) { wallAction(s, input, move, -1, true, effects); enterNormal(s); return; }
                if (wallJumpCheck(s, w, -1)) { wallAction(s, input, move, 1, true, effects); enterNormal(s); return; }
            }
        }
        if (s.dashAimPending) return;
        if (s.dashTime > 0) { s.dashTime -= STEP; return; }
        if (s.dashY >= 0) { s.vx = s.dashX * px(160); s.vy = s.dashY * px(160); }
        if (s.vy > 0) s.vy *= 0.75f;
        s.autoJump = true; enterNormal(s);
    }

    private static void moveH(PhysicsState s, PhysicsWorld w, float pixels) {
        s.remainderX += pixels;
        int amount = (int) Math.rint(s.remainderX);
        if (amount != 0) { s.remainderX -= amount; moveHExact(s, w, amount, true); }
    }
    private static void moveV(PhysicsState s, PhysicsWorld w, float pixels) {
        s.remainderY += pixels;
        int amount = (int) Math.rint(s.remainderY);
        if (amount != 0) { s.remainderY -= amount; moveVExact(s, w, amount, true); }
    }
    private static void moveHExact(PhysicsState s, PhysicsWorld w, int amount, boolean notify) {
        int sign = Integer.signum(amount);
        while (amount != 0) {
            if (hit(s, w, sign, 0)) { s.remainderX = 0; if (notify) collideH(s, w, sign); return; }
            s.x += sign * PIXEL; amount -= sign;
        }
    }
    private static void moveVExact(PhysicsState s, PhysicsWorld w, int amount, boolean notify) {
        int sign = Integer.signum(amount);
        while (amount != 0) {
            if (hit(s, w, 0, sign)) { s.remainderY = 0; if (notify) collideV(s, w, sign); return; }
            s.y += sign * PIXEL; amount -= sign;
        }
    }
    private static void collideH(PhysicsState s, PhysicsWorld w, int sign) {
        if (s.motion == DASH && s.grounded && !w.solid(s.x + sign * PIXEL, s.y, true)) { s.ducking = true; return; }
        if (s.motion == DASH && s.vy == 0 && s.vx != 0) {
            for (int i = 1; i <= 4; i++) for (int d = -1; d <= 1; d += 2) {
                if (!hit(s, w, sign, i * d) && hit(s, w, sign, (i - 1) * d)) {
                    moveVExact(s, w, i * d, false); moveHExact(s, w, sign, false); return;
                }
            }
        }
        if (s.wallRetention <= 0) { s.retainedSpeed = s.vx; s.wallRetention = 0.06f; }
        s.vx = 0; s.dashAttack = 0;
    }
    private static void collideV(PhysicsState s, PhysicsWorld w, int sign) {
        if (s.motion == SWIM) { s.vy = 0; return; }
        if (sign < 0) {
            if (s.motion == DASH && !s.dashStartedOnGround) {
                if (s.vx <= px(0.01f)) for (int n = -1; n >= -4; n--) {
                    if (!groundAt(s, w, s.x + n * PIXEL, s.y)) {
                        moveHExact(s, w, n, false); moveVExact(s, w, -1, false); return;
                    }
                }
                if (s.vx >= -px(0.01f)) for (int n = 1; n <= 4; n++) {
                    if (!groundAt(s, w, s.x + n * PIXEL, s.y)) {
                        moveHExact(s, w, n, false); moveVExact(s, w, -1, false); return;
                    }
                }
            }
            // State-agnostic diagonal landing is the Ultra momentum multiplier.
            if (s.dashX != 0 && s.dashY < 0 && s.vy < 0) {
                s.dashX = Math.signum(s.dashX); s.dashY = 0;
                s.vx *= 1.2f; s.ducking = true;
            }
            s.landingSequence++;
            if (s.motion != CLIMB) {
                float amount = Math.min(-s.vy / px(240), 1);
                s.spriteScaleX = 1 + 0.6f * amount; s.spriteScaleY = 1 - 0.6f * amount;
            }
        } else {
            int correction = s.dashAttack > 0 && Math.abs(s.vx) < px(0.01f) ? 5 : 4;
            if (s.vx <= px(0.01f)) for (int i = 1; i <= correction; i++) {
                if (!hit(s, w, -i, 1)) { s.x -= i * PIXEL; s.y += PIXEL; return; }
            }
            if (s.vx >= -px(0.01f)) for (int i = 1; i <= correction; i++) {
                if (!hit(s, w, i, 1)) { s.x += i * PIXEL; s.y += PIXEL; return; }
            }
            if (s.variableJump < 0.15f) s.variableJump = 0;
        }
        s.vy = 0; s.dashAttack = 0;
    }
}
