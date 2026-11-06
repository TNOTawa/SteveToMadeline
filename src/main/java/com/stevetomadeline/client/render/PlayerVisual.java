package com.stevetomadeline.client.render;

import com.stevetomadeline.client.assets.PlayerSpriteBank;
import com.stevetomadeline.core.physics.*;
import java.util.*;
import java.util.function.Consumer;

/** 60 Hz presentation state, separate from replayable physics and from rendering. */
public final class PlayerVisual {
    private static final float DT = 1f / 60;
    public record Sound(String name, String key, boolean stop, boolean surface) { }
    public static final class Particle {
        public double x, y;
        public float vx, vy, life, maxLife, size;
        public String texture;
        public boolean dust, two;
        public float opacity() { return Math.min(1, life / maxLife * (dust ? 1 : 4)); }
        public float scale() { return dust ? size * (1 - (float) Math.pow(1 - life / maxLife, 3)) : 1; }
        public int color() {
            if (dust) return 0xFFFFFF;
            return ((int) (life / .1f) & 1) != 0 ? two ? 0xAC3232 : 0x44B7FF : two ? 0xE05959 : 0x75C9FF;
        }
    }
    public static final class Trail {
        public double x, y;
        public String texture;
        public int color, facing, count;
        public float age, sx, sy;
        public float[] hairX, hairY;
        public float opacity() { return .75f * (float) Math.pow(1 - age, 3); }
    }
    public final PlayerAnimation animation;
    public final HairSimulation hair = new HairSimulation();
    public final List<Particle> particles = new ArrayList<>();
    public final List<Trail> trails = new ArrayList<>();
    public boolean flash, glove, slashActive;
    public float gloveScale = 1, slashAge, slashAngle;
    public double slashX, slashY;
    public String sweat = "idle";
    public int sweatFrame;
    private float flashTime, gloveCounter, gloveSine, sweatTime, sweatJump, dashParticles, skidDust, trailTime = -1;
    private int trailStage, seenDash, seenJump, seenLand;
    private boolean initialized, dashPending, dashTwo, effectsEnabled, frozen;
    private PhysicsState context;
    private PhysicsWorld world;
    private final Random random;
    private final Consumer<Sound> sound;
    public PlayerVisual(Random random, Consumer<Sound> sound) {
        this.random = random; this.sound = sound; animation = new PlayerAnimation(random);
        animation.onChange = (old, next) -> {
            if ((old.equals("idleb") || old.equals("idlec")) && !next.startsWith("idle")) stop("idle");
            if (next.equals("idleb") || next.equals("idlec")) {
                sound.accept(new Sound(next.equals("idleb") ? "idle_scratch" : "idle_sneeze", "idle", false, false));
            }
        };
        animation.onFrame = (id, frame) -> {
            if (context == null) return;
            boolean foot = ((id.equals("runslow") || id.equals("runfast") || id.equals("walk")) && (frame == 0 || frame == 6))
                    || id.equals("flip") && frame == 4 || id.equals("push") && (frame == 8 || frame == 15);
            if (foot && world.solid(context.x, context.y - 1.0 / 16, context.ducking)) play("footstep", true);
            if ((id.equals("climbup") || id.equals("climbdown")) && frame == 5
                    && world.solid(context.x + context.facing / 16.0, context.y + (context.ducking ? 6 : 11) / 32.0, context.ducking))
                play("handhold", true);
            if (id.equals("push") && (frame == 8 || frame == 15))
                dust(context.x - context.facing * 5.0 / 16, context.y + 1.0 / 16,
                        Math.atan2(.5, -context.facing), 1, 0);
        };
    }
    public static boolean tired(PhysicsState state) { return state.stamina + (state.wallBoost > 0 ? 27.5f : 0) < 20; }
    public int bodyColor(PhysicsState state) { return tired(state) && flash ? 0xFFFF0000 : 0xFFFFFFFF; }
    private void play(String name, boolean surface) { sound.accept(new Sound(name, null, false, surface)); }
    private void stop(String key) { sound.accept(new Sound("", key, true, false)); }
    public void before(PhysicsState state, PhysicsWorld world, PlayerSpriteBank bank, boolean effects) {
        this.context = state; this.world = world; effectsEnabled = effects;
        if (!initialized) { seenDash = state.dashSequence; seenJump = state.jumpSequence; seenLand = state.landingSequence; initialized = true; }
        if (!effects) { particles.clear(); trails.clear(); slashActive = false; }
        frozen = state.dashWindup > 0;
        if (frozen) { context = null; this.world = null; return; }
        animation.advance(bank);
        advanceSweat(bank);
        flashTime += DT;
        while (flashTime >= .05f) { flashTime -= .05f; flash = !flash; }
        for (Trail trail : trails) trail.age += DT;
        trails.removeIf(trail -> trail.age >= 1);
        if (slashActive) {
            slashAge += DT; slashX += Math.cos(slashAngle) * 8 * DT / 16; slashY += Math.sin(slashAngle) * 8 * DT / 16;
            if (slashAge >= .4f) slashActive = false;
        }
    }
    public void event(PhysicsEffects.Event event, PhysicsState state) {
        int direction = state.facing;
        switch (event) {
            case JUMP, SUPER, HYPER, WALL_JUMP, WALLBOUNCE, CLIMB_JUMP -> {
                if (state.jumpSequence <= seenJump) return;
                seenJump = state.jumpSequence;
                if (event == PhysicsEffects.Event.WALL_JUMP || event == PhysicsEffects.Event.WALLBOUNCE) {
                    if (event == PhysicsEffects.Event.WALL_JUMP) play("landing", true);
                    play(direction < 0 ? "jump_wall_right" : "jump_wall_left", false);
                    if (event == PhysicsEffects.Event.WALLBOUNCE) play("jump_superwall", false);
                    wallDust(state, direction);
                } else {
                    play("jump", false);
                    if (event == PhysicsEffects.Event.CLIMB_JUMP) {
                        play(direction > 0 ? "jump_climb_right" : "jump_climb_left", false);
                        wallDust(state, -direction);
                        if (!state.grounded) { setSweat("jump", true); sweatJump = .4f; }
                    } else {
                        if (event == PhysicsEffects.Event.SUPER) play("jump_super", false);
                        if (event == PhysicsEffects.Event.HYPER) play("jump_superslide", false);
                        dust(state.x, state.y, Math.PI / 2, 4, 4);
                    }
                }
            }
            case GRAB -> { play("grab", true); sweatJump = 0; setSweat("idle", false); }
            case LET_GO -> { play("grab_letgo", false); sweatJump = 0; setSweat("idle", false); }
            case CLIMB_HOP -> play("climb_ledge", false);
        }
    }
    private void wallDust(PhysicsState s, int dir) {
        dust(s.x - dir * 2.0 / 16, s.y + 5.5 / 16, dir < 0 ? Math.PI * .75 : Math.PI * .25, 4, 4);
    }
    public void after(PhysicsState before, PhysicsState state, PhysicsWorld world, PlayerSpriteBank bank, boolean showGlove) {
        context = state;
        if (state.dashSequence > seenDash) { seenDash = state.dashSequence; dashPending = true; dashTwo = before.dashes > 1; trailTime = -1; }
        if (frozen) { context = null; this.world = null; return; }
        boolean newDash = dashPending && (!state.dashAimPending || state.motion != PhysicsState.Motion.DASH);
        if (newDash) {
            dashPending = false;
            boolean right = state.dashX == 0 && state.dashY == 0 ? state.facing > 0 : state.dashY > 0 || state.dashY == 0 && state.dashX > 0;
            play("dash_" + (dashTwo ? "pink_" : "red_") + (right ? "right" : "left"), false);
        }
        if (before.ducking != state.ducking && (state.ducking || state.grounded)) play(state.ducking ? "duck" : "stand", false);
        if (state.landingSequence > seenLand) {
            seenLand = state.landingSequence;
            // Standing contact can increment the collision counter; only an airborne impact lands.
            if (!before.grounded && before.vy <= 0) {
                play("landing", true);
                if (before.vy <= -80f / 16) dust(state.x, state.y, Math.PI / 2, 8, 4);
            }
        }
        boolean sliding = animation.id().equals("wallslide") && state.vy < 0;
        if (sliding) sound.accept(new Sound("wallslide", "slide", false, true)); else stop("slide");
        animation.choose(state, bank, world);
        if (sweatJump > 0) { sweatJump -= DT; if (sweatJump <= 0) setSweat("idle", false); }
        if (state.motion == PhysicsState.Motion.CLIMB) {
            String next = state.stamina <= 20 ? "danger" : state.climbNoMove > 0 ? "idle"
                    : state.lastClimbMove > 0 ? "climb" : !state.grounded ? "still" : "idle";
            if (!(next.equals("climb") && sweat.equals("climbloop"))) setSweat(next, false);
        } else if (sweatJump <= 0) setSweat("idle", false);
        if (gloveCounter > 0) { gloveSine += (float) (Math.PI * 2 * .3) * DT; gloveCounter = Math.max(0, gloveCounter - DT / .1f); }
        if (glove != showGlove) { glove = showGlove; gloveCounter = 1; gloveSine = 0; }
        gloveScale = 1 + (float) Math.cos(gloveSine) * gloveCounter * .2f;
        double dx = (state.x - before.x) * 16, dy = (state.y - before.y) * 16;
        for (int i = 0; i < 5; i++) { hair.x[i] -= (float) dx; hair.y[i] += (float) dy; }
        var meta = bank.hair.get(animation.texture(bank));
        hair.step(meta == null ? 0 : meta.x() * state.facing, -9 * state.spriteScaleY + (meta == null ? 0 : meta.y()), state.facing, state.dashes);
        if (newDash) {
            if (effectsEnabled) { slashActive = true; slashAge = 0; slashX = state.x; slashY = state.y + 5.5 / 16; slashAngle = (float) Math.atan2(state.dashY, state.dashX); }
            capture(state, bank); trailTime = 0; trailStage = 1;
        } else if (trailTime >= 0) {
            trailTime += DT;
            if (trailStage == 1 && trailTime >= .08f) { capture(state, bank); trailStage = 2; }
            if (trailStage == 2 && trailTime >= .15f) { capture(state, bank); trailTime = -1; }
        }
        if (state.motion == PhysicsState.Motion.DASH && (state.vx != 0 || state.vy != 0)) {
            dashParticles += DT;
            while (dashParticles >= .02f) { dashParticles -= .02f; dashParticle(state); }
        } else dashParticles = 0;
        if (state.sliding && state.wallSlide / 1.2f > .65f || state.motion == PhysicsState.Motion.CLIMB && state.lastClimbMove < 0)
            dust(state.x + state.facing * 5.0 / 16, state.y + 1.5 / 16, Math.PI / 2, 1, 4);
        if (state.dashAttack > 0 && animation.id().equals("skid")) {
            skidDust += DT; while (skidDust >= .02f) { skidDust -= .02f; dust(state.x, state.y, Math.PI / 2, 1, 4); }
        } else skidDust = 0;
        for (Particle p : particles) {
            p.life -= DT; p.x += p.vx * DT / 16; p.y += p.vy * DT / 16; p.vy -= (p.dust ? 4 : 8) * DT;
        }
        particles.removeIf(p -> p.life <= 0);
        context = null; this.world = null;
    }
    private void setSweat(String id, boolean restart) {
        if (!restart && sweat.equals(id)) return;
        sweat = id; sweatFrame = 0; sweatTime = 0;
    }
    private void advanceSweat(PlayerSpriteBank bank) {
        var anim = bank.sweat.get(sweat);
        if (anim == null || anim.delay() <= 0) return;
        sweatTime += DT;
        while (sweatTime >= anim.delay()) {
            sweatTime -= anim.delay(); sweatFrame++;
            if (sweatFrame >= anim.frames().size()) {
                if (bank.sweat.containsKey(anim.next())) { sweat = anim.next(); sweatFrame = 0; anim = bank.sweat.get(sweat); }
                else { sweatFrame = anim.frames().size() - 1; sweatTime = 0; break; }
                if (anim.delay() <= 0) break;
            }
        }
    }
    private void capture(PhysicsState s, PlayerSpriteBank bank) {
        if (!effectsEnabled || trails.size() >= 24) return;
        Trail trail = new Trail(); trail.x = s.x; trail.y = s.y; trail.texture = animation.texture(bank);
        trail.color = dashTwo ? 0xAC3232 : 0x44B7FF; trail.facing = s.facing; trail.sx = s.spriteScaleX; trail.sy = s.spriteScaleY;
        trail.hairX = hair.x.clone(); trail.hairY = hair.y.clone(); trail.count = hair.count; trails.add(trail);
    }
    private void dust(double x, double y, double direction, int count, float range) {
        if (!effectsEnabled) return;
        for (int i = 0; i < count && particles.size() < 256; i++) {
            Particle p = new Particle(); double angle = direction + between(-.5f, .5f); float speed = between(5, 15);
            p.x = x + Math.abs(Math.cos(direction - Math.PI / 2)) * between(-range, range) / 16;
            p.y = y + Math.abs(Math.sin(direction - Math.PI / 2)) * between(-range, range) / 16;
            p.vx = (float) Math.cos(angle) * speed; p.vy = (float) Math.sin(angle) * speed;
            p.life = p.maxLife = between(.3f, .5f); p.size = between(4, 7.2f); p.dust = true;
            p.texture = "particles/smoke" + random.nextInt(4); particles.add(p);
        }
    }
    private void dashParticle(PhysicsState s) {
        if (!effectsEnabled || particles.size() >= 256) return;
        Particle p = new Particle(); double angle = Math.atan2(s.dashY, s.dashX) + between(-(float) Math.PI / 3, (float) Math.PI / 3);
        float speed = between(10, 20); p.vx = (float) Math.cos(angle) * speed; p.vy = (float) Math.sin(angle) * speed;
        p.x = s.x + between(-2, 2) / 16; p.y = s.y + (5.5 + between(-2, 2)) / 16;
        p.life = p.maxLife = between(1, 1.8f); p.size = 1; p.two = dashTwo; p.texture = "particles/dashparticle"; particles.add(p);
    }
    private float between(float min, float max) { return min + random.nextFloat() * (max - min); }
}
