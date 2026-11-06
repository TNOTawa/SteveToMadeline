package com.stevetomadeline.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stevetomadeline.client.assets.CelesteAssets;
import com.stevetomadeline.client.assets.PlayerSpriteBank;
import com.stevetomadeline.client.audio.CelesteAudio;
import com.stevetomadeline.client.input.ControlBindings;
import com.stevetomadeline.client.input.GrabInput;
import com.stevetomadeline.core.physics.*;
import com.stevetomadeline.core.plane.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

public final class MadelineRenderer {
    private static final Map<Player, Visual> VISUALS = new WeakHashMap<>();
    private static final class Visual {
        final PlayerVisual view;
        final CelesteAssets.Loaded assets;
        final Heading heading;
        PhysicsState previous;
        Visual(Player player, CelesteAssets.Loaded assets, Heading heading) {
            this.assets = assets; this.heading = heading;
            boolean local = player == Minecraft.getInstance().player;
            view = new PlayerVisual(new Random(), sound -> {
                if (!local) return;
                if (sound.stop()) CelesteAudio.stop(sound.key());
                else CelesteAudio.play(sound.name(), sound.key(), sound.surface() ? "surface_index" : null, 8);
            });
        }
    }
    private MadelineRenderer() { }
    public static void reset() { VISUALS.clear(); CelesteAudio.reset(); }
    private static Visual visual(Player player) {
        var assets = CelesteAssets.current();
        if (assets == null) return null;
        PhysicsSession session = PhysicsController.session(player);
        Heading heading = session == null ? Heading.fromYaw(player.getYRot()) : session.heading;
        Visual v = VISUALS.get(player);
        if (v == null || v.assets != assets || v.heading != heading) {
            v = new Visual(player, assets, heading); VISUALS.put(player, v);
        }
        return v;
    }
    /** Called around first-pass prediction only. Reconciliation has no sound or visual callbacks. */
    public static PhysicsEffects before(Player player, PhysicsState state, PhysicsWorld world) {
        Visual v = visual(player);
        if (v == null) return PhysicsEffects.NONE;
        v.view.before(state, world, v.assets.bank(), ControlBindings.presentation().effectsEnabled());
        return v.view::event;
    }
    public static void after(Player player, PhysicsState before, PhysicsState state, PhysicsWorld world, PhysicsInput input) {
        Visual v = visual(player);
        if (v == null) return;
        v.view.after(before, state, world, v.assets.bank(),
                ControlBindings.presentation().grabMode() == GrabInput.Mode.TOGGLE && input.grabHeld());
        v.previous = state.copy();
    }
    public static void render(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!ControlBindings.showCharacter() || !((PhysicsPlayerAccess) player).stevetomadeline$hasCelesteDimensions()) return;
        Visual visual = visual(player);
        if (visual == null) return;
        var assets = visual.assets; var v = visual.view;
        PhysicsSession session = PhysicsController.session(player);
        PhysicsState state = session == null ? new PhysicsState() : session.state;
        // Remote players receive 20 Hz snapshots; their visuals follow those without audio.
        if (player != Minecraft.getInstance().player) {
            var world = new MinecraftPhysicsWorld(player, new PlaneState(visual.heading, player.getX(), player.getZ()));
            int steps = visual.previous == null ? 1 : Math.clamp(state.simulationFrame - visual.previous.simulationFrame, 0, 12);
            for (int i = 0; i < steps; i++) {
                PhysicsState before = visual.previous == null ? state.copy() : visual.previous;
                v.before(before, world, assets.bank(), ControlBindings.presentation().effectsEnabled());
                v.after(before, state, world, assets.bank(), false);
                visual.previous = state.copy();
            }
        }
        String texture = v.animation.texture(assets.bank());
        if (!assets.frames().containsKey(texture)) return;
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack(); pose.pushPose();
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(1f / 16, 1f / 16, 1f / 16);
        boolean fx = ControlBindings.presentation().effectsEnabled();
        float partial = event.getPartialTick();
        double anchorX = Mth.lerp(partial, player.xOld, player.getX()) * visual.heading.rightX()
                + Mth.lerp(partial, player.zOld, player.getZ()) * visual.heading.rightZ();
        double anchorY = Mth.lerp(partial, player.yOld, player.getY());
        VertexConsumer transparent = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(CelesteAssets.TEXTURE));
        if (fx) {
            for (var trail : v.trails) {
                pose.pushPose(); pose.translate((trail.x - anchorX) * 16, (trail.y - anchorY) * 16, -.004);
                int tint = (Math.clamp((int) (trail.opacity() * 255), 0, 255) << 24) | trail.color;
                var meta = assets.bank().hair.get(trail.texture);
                if (meta != null && meta.visible()) for (int i = trail.count - 1; i >= 0; i--)
                    hairPiece(transparent, pose, assets, trail.hairX, trail.hairY, trail.count, trail.facing, trail.sx,
                            meta, i, 0, 0, tint, 0, LightTexture.FULL_BRIGHT, true);
                draw(transparent, pose, assets, trail.texture + "#silhouette", 0, 0, assets.bank().originX, assets.bank().originY,
                        trail.facing * trail.sx, trail.sy, tint, .001f, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
            for (var p : v.particles) {
                var f = assets.frames().get(p.texture);
                if (f == null) continue;
                float size = Math.max(1, p.scale());
                int tint = (Math.clamp((int) (p.opacity() * 255), 0, 255) << 24) | p.color();
                draw(transparent, pose, assets, p.texture, (float) ((p.x - anchorX) * 16), (float) ((anchorY - p.y) * 16),
                        f.width() / 2f, f.height() / 2f, size / f.width(), size / f.height(), tint, -.002f, LightTexture.FULL_BRIGHT);
            }
            if (v.slashActive) {
                pose.pushPose(); pose.translate((v.slashX - anchorX) * 16, (v.slashY - anchorY) * 16, -.001);
                float angle = v.slashAngle;
                if (Math.abs(angle) > .01f && Math.abs(Math.abs(angle) - Math.PI) > .01f) pose.mulPose(Axis.ZP.rotation(angle));
                draw(transparent, pose, assets, "effects/slash/0" + Math.min(3, (int) (v.slashAge / .1f)),
                        0, 0, 12, 4, 1, 1, 0xFFFFFFFF, 0, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        }
        VertexConsumer vertices = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(CelesteAssets.TEXTURE));
        var meta = assets.bank().hair.get(texture);
        if (meta != null && meta.visible()) {
            for (int i = 0; i < v.hair.count; i++) for (int[] offset : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
                hairPiece(vertices, pose, assets, v.hair.x, v.hair.y, v.hair.count, state.facing, state.spriteScaleX,
                        meta, i, offset[0], offset[1], 0xFF000000, 0, event.getPackedLight(), false);
            for (int i = v.hair.count - 1; i >= 0; i--)
                hairPiece(vertices, pose, assets, v.hair.x, v.hair.y, v.hair.count, state.facing, state.spriteScaleX,
                        meta, i, 0, 0, 0xFF000000 | v.hair.color, .001f, event.getPackedLight(), false);
        }
        draw(vertices, pose, assets, texture, 0, 0, assets.bank().originX, assets.bank().originY,
                state.facing * state.spriteScaleX, state.spriteScaleY, v.bodyColor(state), .002f, event.getPackedLight());
        draw(vertices, pose, assets, texture + "#hair", 0, 0, assets.bank().originX, assets.bank().originY,
                state.facing * state.spriteScaleX, state.spriteScaleY, 0xFF000000 | v.hair.color, .003f, event.getPackedLight());
        if (fx) {
            var sweat = assets.bank().sweat.get(v.sweat);
            if (sweat != null) draw(vertices, pose, assets, sweat.frames().get(Math.min(v.sweatFrame, sweat.frames().size() - 1)),
                    0, 0, 16, 32, state.facing * state.spriteScaleX, state.spriteScaleY, 0xFFFFFFFF, .004f, event.getPackedLight());
            var glove = assets.frames().get("util/glove");
            if (v.glove && glove != null) draw(vertices, pose, assets, "util/glove", 0, -16, glove.width() / 2f, glove.height(),
                    v.gloveScale, v.gloveScale, 0xFFFFFFFF, .005f, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
    private static void hairPiece(VertexConsumer v, PoseStack pose, CelesteAssets.Loaded assets, float[] x, float[] y,
                                  int count, int facing, float sx, PlayerSpriteBank.Hair meta, int index, int dx, int dy,
                                  int color, float depth, int light, boolean silhouette) {
        String texture = index == 0 ? "characters/player/bangs0" + Math.clamp(meta.bangs(), 0, 2) : "characters/player/hair00";
        if (silhouette) texture += "#silhouette";
        float scale = .25f + (1f - (float) index / count) * .75f;
        draw(v, pose, assets, texture, x[index] + dx, y[index] + dy, 5, 5,
                (index == 0 ? facing : scale) * Math.abs(sx), scale, color, depth, light);
    }
    private static void draw(VertexConsumer v, PoseStack pose, CelesteAssets.Loaded assets, String texture,
                             float x, float y, float originX, float originY, float sx, float sy, int color, float depth, int light) {
        var f = assets.frames().get(texture);
        if (f == null) return;
        float left = x - originX * sx, right = left + f.width() * sx;
        float top = -y + originY * sy, bottom = top - f.height() * sy;
        float u0 = (float) f.x() / assets.width(), u1 = (float) (f.x() + f.width()) / assets.width();
        float v0 = (float) f.y() / assets.height(), v1 = (float) (f.y() + f.height()) / assets.height();
        vertex(v, pose.last(), left, bottom, depth, u0, v1, color, light);
        vertex(v, pose.last(), right, bottom, depth, u1, v1, color, light);
        vertex(v, pose.last(), right, top, depth, u1, v0, color, light);
        vertex(v, pose.last(), left, top, depth, u0, v0, color, light);
    }
    private static void vertex(VertexConsumer v, PoseStack.Pose pose, float x, float y, float z, float u, float t, int color, int light) {
        v.addVertex(pose, x, y, z).setColor(color).setUv(u, t).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }
}
