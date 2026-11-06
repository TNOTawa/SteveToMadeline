package com.stevetomadeline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.client.windowx.WindowXCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/** Composites the optional, last published layered frame; never re-enters LevelRenderer. */
public final class ParallaxRenderer {
    private static final ParallaxTracking TRACKING = new ParallaxTracking();
    private static ClientLevel level;
    private static Settings settings;
    private static int frameTexture;
    private static boolean logged;
    private static boolean loggedFallback;

    private record Settings(int width, int height, float halfHeight, double distance, double slice,
                            double ratio, double fogStart, double fogEnd, float renderDistance) {
    }

    private ParallaxRenderer() {
    }

    public static boolean enabled() {
        return RenderModeManager.isFlat2D() && Config.depthEffectEnabled() && Config.cameraFov() <= 0.5;
    }

    public static boolean usesDepthPerspective() {
        return enabled() && effectiveMode(Config.parallaxMode(), WindowXCompat.available()) == Config.ParallaxMode.DEPTH;
    }

    public static Config.ParallaxMode effectiveMode(Config.ParallaxMode requested, boolean windowXAvailable) {
        return requested == Config.ParallaxMode.LAYERED && !windowXAvailable ? Config.ParallaxMode.DEPTH : requested;
    }

    public static boolean usesLayeredBackground() {
        return enabled() && Config.parallaxMode() == Config.ParallaxMode.LAYERED
                && WindowXCompat.available() && !ClientPlaneController.turning(0);
    }

    /** Frozen before projection/culling so clipping and composition always agree. */
    public static boolean hasBackground() {
        return frameTexture > 0 && usesLayeredBackground() && !WindowXCompat.isRendering();
    }

    public static void beginFrame(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (enabled() && Config.parallaxMode() == Config.ParallaxMode.LAYERED
                && !WindowXCompat.available() && !loggedFallback) {
            loggedFallback = true;
            LogUtils.getLogger().warn("[STM] layered parallax needs WindowX; using depth perspective instead");
        }
        if (!usesLayeredBackground() || mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (level != mc.level) {
            reset();
            level = mc.level;
        }
        int screenWidth = Math.max(1, mc.getWindow().getWidth());
        int screenHeight = Math.max(1, mc.getWindow().getHeight());
        double scale = Math.max(0.5, 64.0 / Math.min(screenWidth, screenHeight));
        Settings next = new Settings((int) Math.round(screenWidth * scale), (int) Math.round(screenHeight * scale),
                Config.ORTHO_HALF_HEIGHT.get().floatValue(), Config.cameraDistance(),
                Config.SLICE_HALF_THICKNESS.get(), Config.PARALLAX_RATIO.get(),
                Config.FOG_START.get(), Config.FOG_END.get(), mc.options.getEffectiveRenderDistance());
        boolean changed = !next.equals(settings);
        if (changed) {
            TRACKING.reset();
        }
        settings = next;
        Vec3 position = CameraController.position(mc.player, partialTick);
        float yaw = CameraController.yaw(mc.player);
        ParallaxTracking.Sample sample = TRACKING.update(position.x, position.y, position.z,
                Math.round(yaw / 90.0f), next.ratio);
        ParallaxTracking.Pose pose = sample.pose();
        frameTexture = WindowXCompat.prepare(new Vec3(pose.x(), pose.y(), pose.z()), yaw,
                next.width, next.height, next.halfHeight, next.renderDistance, changed || sample.reset());
        if (frameTexture > 0 && !logged) {
            logged = true;
            LogUtils.getLogger().info("[STM] layered parallax active: ratio={}, target={}x{}",
                    next.ratio, next.width, next.height);
        }
    }

    /** WindowX owns the projection object in its render context, including sky restoration. */
    public static void clipBackground(Matrix4f projection) {
        if (!WindowXCompat.isRenderingBackground() || settings == null) {
            return;
        }
        float halfWidth = settings.halfHeight * settings.width / settings.height;
        projection.setOrtho(-halfWidth, halfWidth, -settings.halfHeight, settings.halfHeight,
                (float) (settings.distance + settings.slice),
                Math.max(2048.0f, WindowXCompat.renderDistance()));
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    public static void composite() {
        if (!hasBackground()) {
            return;
        }
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        ShaderInstance previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        float[] previousColor = RenderSystem.getShaderColor().clone();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, frameTexture);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(-1.0f, -1.0f, 0.0f).setUv(0.0f, 0.0f);
            buffer.addVertex(1.0f, -1.0f, 0.0f).setUv(1.0f, 0.0f);
            buffer.addVertex(1.0f, 1.0f, 0.0f).setUv(1.0f, 1.0f);
            buffer.addVertex(-1.0f, 1.0f, 0.0f).setUv(0.0f, 1.0f);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthMask);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    public static void reset() {
        frameTexture = 0;
        settings = null;
        level = null;
        TRACKING.reset();
        WindowXCompat.stop();
        logged = false;
    }
}
