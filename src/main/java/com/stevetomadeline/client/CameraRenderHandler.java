package com.stevetomadeline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.stevetomadeline.Config;
import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.client.mode.RenderModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * 2D / 2.5D 的合成层：
 *
 * <ul>
 *   <li>用一张远景色底替代天空盒，颜色与雾色一致，避免水下/维度切换时天空突变；</li>
 *   <li>正交切片本身由 {@code GameRendererMixin} 的近裁剪面完成，真正渲染方块模型与实体；</li>
 *   <li>雾从切片后方开始，越远越浓，形成远景层；</li>
 *   <li>摄像机模式下手持物品不渲染。</li>
 * </ul>
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final float Z_BACKDROP = 900.0f;

    private CameraRenderHandler() {
    }

    private static boolean flat() {
        return RenderModeManager.isFlat2D();
    }

    private static float[] fogColor() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean sky = player != null && mc.level != null && mc.level.canSeeSky(player.blockPosition());
        return sky
                ? new float[]{0.62f, 0.74f, 0.92f}
                : new float[]{0.02f, 0.02f, 0.03f};
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        if (!RenderModeManager.isCameraMode()) {
            return;
        }
        drawBackdrop();
    }

    private static void drawBackdrop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int width = Math.max(1, viewport[2]);
        int height = Math.max(1, viewport[3]);
        float[] color = fogColor();

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
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            colorVertex(buffer, 0.0f, 0.0f, color);
            colorVertex(buffer, 0.0f, height, color);
            colorVertex(buffer, width, height, color);
            colorVertex(buffer, width, 0.0f, color);
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

    private static void colorVertex(BufferBuilder buffer, float x, float y, float[] color) {
        buffer.addVertex(x, y, Z_BACKDROP).setColor(color[0], color[1], color[2], 1.0f);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!RenderModeManager.isCameraMode()) {
            return;
        }
        float[] color = fogColor();
        event.setRed(color[0]);
        event.setGreen(color[1]);
        event.setBlue(color[2]);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!RenderModeManager.isCameraMode()) {
            return;
        }
        double distance = Config.CAMERA_DISTANCE.get();
        double slice = Config.SLICE_HALF_THICKNESS.get();
        double start = flat() ? distance + slice : Math.max(12.0, distance * 0.5);
        double end = Config.FOG_END.get();
        if (end <= start + 1.0) {
            end = start + 32.0;
        }
        event.setNearPlaneDistance((float) start);
        event.setFarPlaneDistance((float) end);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (RenderModeManager.isCameraMode()) {
            event.setCanceled(true);
        }
    }
}
