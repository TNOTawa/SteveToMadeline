package com.stevetomadeline.client.render;

import com.stevetomadeline.client.assets.PlayerSpriteBank;
import com.stevetomadeline.core.physics.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerVisualTest {
    private static PlayerSpriteBank bank() {
        var bank = new PlayerSpriteBank();
        for (String name : new String[]{"idle", "idlea", "idleb", "idlec", "dash", "jumpslow", "fallslow"})
            bank.animations.put(name, new PlayerSpriteBank.Animation(List.of(name, name), .1f, name.equals("idle") ? name : "idle"));
        return bank;
    }
    private static final PhysicsWorld EMPTY = new PhysicsWorld() {
        @Override public boolean solid(double x, double y, boolean ducking) { return y < 0; }
        @Override public boolean solidPoint(double x, double y) { return y < 0; }
    };
    @Test void idleFidgetAndLowStaminaFlashFollowSixtyHertz() {
        var visual = new PlayerVisual(new Random(4), sound -> { });
        var state = new PhysicsState(); state.grounded = true; state.y = 0; state.stamina = 10;
        for (int i = 0; i < 3; i++) { visual.before(state, EMPTY, bank(), true); visual.after(state, state, EMPTY, bank(), false); }
        assertEquals(0xFFFF0000, visual.bodyColor(state));
        for (int i = 0; i < 3; i++) { visual.before(state, EMPTY, bank(), true); visual.after(state, state, EMPTY, bank(), false); }
        assertEquals(0xFFFFFFFF, visual.bodyColor(state));
        state.wallBoost = .2f;
        assertEquals(0xFFFFFFFF, visual.bodyColor(state));
        state.wallBoost = 0; state.stamina = 110;
        var sprites = bank();
        var fidgets = new ArrayList<String>();
        visual.animation.onChange = (old, next) -> fidgets.add(next);
        for (int i = 0; i < 1200; i++) { visual.before(state, EMPTY, sprites, true); visual.after(state, state, EMPTY, sprites, false); }
        assertTrue(fidgets.stream().anyMatch(id -> id.equals("idlea") || id.equals("idleb") || id.equals("idlec")));
    }
    @Test void freezeDoesNotAgeEffectsOrToggleFlash() {
        var visual = new PlayerVisual(new Random(1), sound -> { });
        var state = new PhysicsState(); state.stamina = 10; state.dashWindup = .05f;
        var sprites = bank();
        for (int i = 0; i < 8; i++) { visual.before(state, EMPTY, sprites, true); visual.after(state, state, EMPTY, sprites, false); }
        assertFalse(visual.flash);
        assertEquals(0, visual.animation.frame);
        state.dashWindup = 0;
        for (int i = 0; i < 3; i++) { visual.before(state, EMPTY, sprites, true); visual.after(state, state, EMPTY, sprites, false); }
        assertTrue(visual.flash);
    }
    @Test void dashDefersSoundUntilAimThenCapturesThreeFadingTrails() {
        var sounds = new ArrayList<PlayerVisual.Sound>();
        var visual = new PlayerVisual(new Random(2), sounds::add);
        var sprites = bank(); var state = new PhysicsState(); state.y = 10;
        var dash = new PhysicsInput(1, 0, 1, 0, false, false, false, true);
        int firstDashSound = -1;
        for (int i = 0; i < 28; i++) {
            var before = state.copy(); visual.before(before, EMPTY, sprites, true);
            CelestePhysics.step(state, i == 0 ? dash : dash.heldOnly(), PhysicsParameters.DEFAULT, EMPTY, visual::event);
            visual.after(before, state, EMPTY, sprites, false);
            if (firstDashSound < 0 && sounds.stream().anyMatch(s -> s.name().startsWith("dash_"))) firstDashSound = i;
        }
        assertTrue(firstDashSound >= 3, "dash sound waits for freeze and aim");
        assertEquals(1, sounds.stream().filter(s -> s.name().equals("dash_red_right")).count());
        assertEquals(3, visual.trails.size());
        assertTrue(visual.trails.getFirst().age > visual.trails.getLast().age);
        assertTrue(visual.trails.getFirst().opacity() < .75f);
        assertTrue(visual.particles.stream().anyMatch(p -> !p.dust));
        assertTrue(visual.particles.stream().filter(p -> !p.dust).allMatch(p -> p.color() == 0x44B7FF || p.color() == 0x75C9FF));
    }
    @Test void toggledGloveStartsAtFullWiggleAndLowStaminaHonorsWallboostRefund() {
        var visual = new PlayerVisual(new Random(0), sound -> { });
        var state = new PhysicsState(); state.grounded = true; state.stamina = 19;
        visual.before(state, EMPTY, bank(), true); visual.after(state, state, EMPTY, bank(), true);
        assertTrue(visual.glove); assertEquals(1.2f, visual.gloveScale, .0001f);
        for (int i = 0; i < 7; i++) { visual.before(state, EMPTY, bank(), true); visual.after(state, state, EMPTY, bank(), true); }
        assertEquals(1, visual.gloveScale, .0001f);
        assertTrue(PlayerVisual.tired(state));
        state.wallBoost = .1f; assertFalse(PlayerVisual.tired(state));
        state.wallBoost = 0; state.stamina = 20; assertFalse(PlayerVisual.tired(state));
        visual.before(state, EMPTY, bank(), true); visual.after(state, state, EMPTY, bank(), false);
        assertFalse(visual.glove);
    }
    @Test void jumpEventCreatesDustAndOneSoundButDoesNotChangePhysics() {
        var sounds = new ArrayList<PlayerVisual.Sound>();
        var visual = new PlayerVisual(new Random(0), sounds::add);
        var state = new PhysicsState(); state.y = 1;
        var sprites = bank(); visual.before(state, EMPTY, sprites, true);
        state.jumpSequence++;
        visual.event(PhysicsEffects.Event.JUMP, state);
        visual.event(PhysicsEffects.Event.JUMP, state);
        assertEquals(1, sounds.size()); assertEquals("jump", sounds.getFirst().name());
        assertEquals(4, visual.particles.size());
        state.stamina = 10;
        assertEquals(0xFFFFFFFF, visual.bodyColor(state));
        visual.after(state, state, EMPTY, sprites, false);
        assertEquals(10, state.stamina);
        assertEquals(1, state.y);
    }
}
