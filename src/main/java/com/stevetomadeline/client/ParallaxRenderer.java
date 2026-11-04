package com.stevetomadeline.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
import com.stevetomadeline.mixin.CameraPoseAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * 分层视差：把远景用“滞后相机”单独渲染到半分辨率 RenderTarget，
 * 主画面只保留切片；合成时远景位于切片之后。
 *
 * <p>性能上：远景用半分辨率、跳过实体/粒子；相机横向只按 ratio 跟随，
 * 玩家世界坐标与碰撞完全不受影响。锚点会在大幅移动后重置，避免无限漂移。
 */
public final class ParallaxRenderer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final float Z_BACKGROUND = 900.0f;

    private static TextureTarget target;
    private static boolean rendering;
    private static Vec3 anchor;
    private static boolean logged;

    private ParallaxRenderer() {
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static boolean enabled() {
        return RenderModeManager.isCameraMode() && Config.LAYERED_PARALLAX.get();
    }

    public static void renderBackground(float partialTick) {
        if (!enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 truePosition = camera.getPosition();
        if (anchor == null || anchor.distanceTo(truePosition) > 48.0) {
            anchor = truePosition;
        }
        double ratio = Config.PARALLAX_RATIO.get();
        Vec3 lagged = new Vec3(
                anchor.x + (truePosition.x - anchor.x) * ratio,
                truePosition.y,
                anchor.z + (truePosition.z - anchor.z) * ratio);

        ensureTarget(mc);
        RenderTarget main = mc.getMainRenderTarget();
        Vec3 savedPosition = camera.getPosition();
        float savedYaw = camera.getYRot();
        float savedPitch = camera.getXRot();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        CameraPoseAccess access = (CameraPoseAccess) (Object) camera;
        access.stevetomadeline$setPosition(lagged);
        access.stevetomadeline$setRotation(savedYaw, savedPitch);
        rendering = true;
        try {
            target.bindWrite(true);
            RenderSystem.viewport(0, 0, target.width, target.height);
            Matrix4f projection = projection(mc, camera);
            Matrix4f view = new Matrix4f().rotationYXZ(
                    (float) Math.toRadians(-camera.getYRot()),
                    (float) Math.toRadians(camera.getXRot()),
                    0.0f);
            Matrix4f frustum = new Matrix4f(projection).mul(view);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            mc.levelRenderer.renderLevel(mc.getTimer(), true, camera, mc.gameRenderer,
                    mc.gameRenderer.lightTexture(), frustum, projection);
        } catch (Throwable t) {
            LOG.warn("[STM] parallax background pass failed", t);
        } finally {
            rendering = false;
            main.bindWrite(true);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.ORTHOGRAPHIC_Z);
            access.stevetomadeline$setPosition(savedPosition);
            access.stevetomadeline$setRotation(savedYaw, savedPitch);
        }
        if (!logged) {
            logged = true;
            LOG.info("[STM] layered parallax active: ratio={}, target={}x{}", ratio, target.width, target.height);
        }
    }

    private static Matrix4f projection(Minecraft mc, Camera camera) {
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float halfWidth = halfHeight * aspect;
        double distance = Config.CAMERA_DISTANCE.get();
        float far = (float) Math.max(512.0, distance + 512.0);
        return new Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, 0.05f, far);
    }

    public static void composite() {
        if (!enabled() || target == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int width = Math.max(1, viewport[2]);
        int height = Math.max(1, viewport[3]);

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0f, width, height, 0.0f, -1000.0f, 1000.0f),
                VertexSorting.DISTANCE_TO_ORIGIN);

        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        try {
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(0.0f, 0.0f, Z_BACKGROUND).setUv(0.0f, 1.0f);
            buffer.addVertex(0.0f, height, Z_BACKGROUND).setUv(0.0f, 0.0f);
            buffer.addVertex(width, height, Z_BACKGROUND).setUv(1.0f, 0.0f);
            buffer.addVertex(width, 0.0f, Z_BACKGROUND).setUv(1.0f, 1.0f);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthFunc(previousDepthFunc);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.ORTHOGRAPHIC_Z);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    public static void reset() {
        anchor = null;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    private static void ensureTarget(Minecraft mc) {
        int width = Math.max(64, mc.getWindow().getWidth() / 2);
        int height = Math.max(64, mc.getWindow().getHeight() / 2);
        if (target == null || target.width != width || target.height != height) {
            if (target != null) {
                target.destroyBuffers();
            }
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        }
    }
}
