package com.stevetomadeline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.stevetomadeline.Config;
import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.client.windowx.WindowXCompat;
import com.stevetomadeline.mixin.ForegroundLayerAccess;
import java.io.IOException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/** Retained terrain writes depth first; fading foreground blends afterwards without writing depth. */
public final class ForegroundFadeRenderer {
    private static final ForegroundFade FADE = new ForegroundFade();
    private static ShaderInstance shader;
    private static long previousMillis;
    private static Object level;
    private static boolean requested, cameraMask, foregroundPass;
    private ForegroundFadeRenderer() { }

    public static void register(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "foreground_fade"), DefaultVertexFormat.BLOCK),
                    loaded -> shader = loaded);
        } catch (IOException ex) {
            SteveToMadeline.LOGGER.error("Unable to load foreground fade shader; using solid clipping", ex);
        }
    }

    public static void beginFrame(float partialTick) {
        var mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (level != mc.level || !RenderModeManager.isFlat2D() || ClientPlaneController.turning(partialTick)
                || (!Config.NEAR_WALL_SLICE.get() && !Config.FOREGROUND_SLICE.get())) {
            FADE.reset(); cameraMask = false; previousMillis = now;
        }
        level = mc.level;
        requested = RenderModeManager.isFlat2D() && TerrainClipping.requestedSlice();
        if (requested) cameraMask = CameraController.nearWallSliceRequired();
        FADE.update(requested, previousMillis == 0 ? 0 : (now - previousMillis) / 1000.0);
        previousMillis = now;
    }

    public static boolean active() {
        return shader != null && RenderModeManager.isFlat2D() && !WindowXCompat.isRendering()
                && (requested || FADE.active());
    }
    public static boolean cameraMask() { return active() && cameraMask; }
    public static boolean foregroundPass() { return foregroundPass; }

    public static ShaderInstance select(ShaderInstance original, RenderType layer) {
        if (!active()) return original;
        shader.safeGetUniform("SliceDepth").set((float) Math.max(.05, CameraController.distance() - Config.SLICE_HALF_THICKNESS.get()));
        shader.safeGetUniform("ForegroundPass").set(foregroundPass ? 1 : 0);
        shader.safeGetUniform("ForegroundOpacity").set(FADE.opacity());
        shader.safeGetUniform("AlphaCutoff").set(layer == RenderType.cutoutMipped() ? .5f
                : layer == RenderType.cutout() || layer == RenderType.tripwire() ? .1f : 0);
        if (foregroundPass) {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false);
        }
        return shader;
    }

    public static void renderRemovedTerrain(RenderLevelStageEvent event) {
        if (!active() || FADE.opacity() <= 0 || foregroundPass) return;
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        var camera = event.getCamera().getPosition();
        foregroundPass = true;
        try {
            for (RenderType layer : RenderType.chunkBufferLayers()) {
                ((ForegroundLayerAccess) event.getLevelRenderer()).stevetomadeline$renderLayer(layer,
                        camera.x, camera.y, camera.z, event.getModelViewMatrix(), event.getProjectionMatrix());
            }
        } finally {
            foregroundPass = false;
            RenderSystem.depthMask(depthWrite);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }
}
