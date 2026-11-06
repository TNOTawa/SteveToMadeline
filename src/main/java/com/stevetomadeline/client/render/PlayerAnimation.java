package com.stevetomadeline.client.render;

import com.stevetomadeline.client.assets.PlayerSpriteBank;
import com.stevetomadeline.core.physics.PhysicsState;
import com.stevetomadeline.core.physics.PhysicsWorld;

/** Sprite component advances before Player.UpdateSprite chooses a new animation. */
public final class PlayerAnimation {
    public String animation = "idle";
    private String last = "idle";
    public int frame;
    private float timer;
    private boolean fastJump;
    private float idleTimer;
    private final java.util.Random rng;
    public java.util.function.BiConsumer<String, Integer> onFrame = (id, frame) -> { };
    public java.util.function.BiConsumer<String, String> onChange = (old, next) -> { };
    public PlayerAnimation() { this(new java.util.Random()); }
    public PlayerAnimation(java.util.Random rng) { this.rng = rng; }
    public String id() { return animation; }
    public String texture(PlayerSpriteBank bank) {
        var anim = bank.animations.getOrDefault(last, bank.animations.get("idle"));
        return anim.frames().get(Math.min(frame, anim.frames().size() - 1));
    }
    private static boolean hit(PhysicsState s, PhysicsWorld w, int x, int y) {
        return w.solid(s.x + x / 16.0, s.y + y / 16.0, s.ducking);
    }
    public void step(PhysicsState s, PlayerSpriteBank bank, PhysicsWorld w) {
        advance(bank);
        choose(s, bank, w);
    }
    public void choose(PhysicsState s, PlayerSpriteBank bank, PhysicsWorld w) {
        boolean still = s.grounded && s.motion == PhysicsState.Motion.NORMAL && !s.ducking
                && s.previousMove == 0 && s.previousMoveY == 0 && s.vx == 0 && s.vy == 0;
        idleTimer = still ? idleTimer + 1f / 60 : 0;
        String desired = animation;
        if (s.dashAttack > 0) {
            if (s.grounded && s.dashY == 0 && !s.ducking && s.vx != 0 && s.previousMove == -Math.signum(s.vx)) desired = "skid";
            else desired = s.ducking ? "duck" : "dash";
        } else if (s.motion == PhysicsState.Motion.SWIM) {
            desired = s.previousMoveY > 0 ? "swimup" : s.previousMoveY < 0 ? "swimdown" : "swimidle";
        } else if (s.motion == PhysicsState.Motion.CLIMB) {
            if (s.lastClimbMove > 0) desired = "climbup";
            else if (s.lastClimbMove < 0) desired = "wallslide";
            else if (!hit(s, w, s.facing, -6)) desired = "dangling";
            else if (s.previousMove == -s.facing) desired = animation.equals("climblookback") ? animation : "climblookbackstart";
            else desired = "wallslide";
        } else if (s.ducking) desired = "duck";
        else if (s.grounded) {
            fastJump = false;
            if (s.previousMove != 0 && hit(s, w, s.previousMove, 0)) desired = "push";
            else if (Math.abs(s.vx) <= 25f / 16 && s.previousMove == 0) {
                if (!hit(s, w, s.facing, -2) && !hit(s, w, s.facing * 4, -2)) desired = "edge";
                else if (!hit(s, w, -s.facing, -2) && !hit(s, w, -s.facing * 4, -2)) desired = "edgeback";
                else if (s.previousMoveY == 1) { if (!last.equals("lookup")) desired = "lookup"; }
                else if (!animation.startsWith("idle")) desired = "idle";
            } else if (Math.signum(s.vx) == -s.previousMove && s.previousMove != 0) {
                if (Math.abs(s.vx) > 90f / 16) desired = "skid";
                else if (!animation.equals("skid")) desired = "flip";
            } else if (!(last.equals("flip") || last.startsWith("run"))) {
                desired = Math.abs(s.vx) < 45f / 16 ? "runslow" : "runfast";
            }
        } else if (s.sliding) desired = "wallslide";
        else if (s.vy > 0) { fastJump |= Math.abs(s.vx) > 90f / 16; desired = fastJump ? "jumpfast" : "jumpslow"; }
        else if (fastJump || s.vy <= -10) { fastJump = true; if (!last.equals("fallfast")) desired = "fallfast"; }
        else desired = "fallslow";
        if (desired.isEmpty()) return;
        if (!bank.animations.containsKey(desired)) desired = "idle";
        play(desired);
    }
    private void play(String id) {
        if (animation.equals(id)) return;
        onChange.accept(animation, id); animation = last = id; frame = 0; timer = 0;
        onFrame.accept(id, 0);
    }
    public void advance(PlayerSpriteBank bank) {
        if (animation.isEmpty()) return;
        var anim = bank.animations.getOrDefault(animation, bank.animations.get("idle"));
        if (anim.delay() <= 0) return;
        timer += 1f / 60;
        if (timer < anim.delay()) return;
        timer -= anim.delay(); frame++;
        if (frame >= anim.frames().size()) {
            if (animation.equals("idle") && idleTimer > 3 && rng.nextFloat() < 0.2f) {
                int pick = rng.nextInt(9);
                String fidget = pick < 5 ? "idlea" : pick < 8 ? "idleb" : "idlec";
                if (bank.animations.containsKey(fidget)) {
                    play(fidget);
                    return;
                }
            }
            if (bank.animations.containsKey(anim.next())) {
                if (!animation.equals(anim.next())) { play(anim.next()); return; }
                frame = 0;
            }
            else { frame = anim.frames().size() - 1; animation = ""; timer = 0; }
        }
        if (!animation.isEmpty()) onFrame.accept(animation, frame);
    }
}
