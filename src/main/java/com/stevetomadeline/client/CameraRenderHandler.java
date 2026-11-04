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
import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.client.mode.RenderModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * 2D 模式的方块切片层。
 *
 * <p>在屏幕空间的正交投影里，按世界方块坐标计算每格的屏幕位置（含小数偏移），
 * 因此摄像机移动时整幅贴图会连续滚动，而不是一格一跳。
 * 近处方块完全不透明，只有远处逐渐淡入背景雾色。
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final Logger LOG = LogUtils.getLogger();

    private static final float Z_BACKGROUND = 500.0f;
    private static final float Z_TILES = 400.0f;

    private static boolean loggedOnce;

    private CameraRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        if (!RenderModeManager.isCameraMode()) {
            return;
        }
        try {
            if (RenderModeManager.isFlat2D()) {
                drawFlat2D(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        } catch (Throwable t) {
            LOG.warn("[WindowX] camera overlay failed", t);
        }
    }

    private static void drawFlat2D(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return;
        }

        Vec3 eye = player.getEyePosition(partialTick);
        float yaw = CameraController.yaw(player);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        boolean zPlane = Math.abs(forward.z) >= Math.abs(forward.x);

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int viewWidth = Math.max(1, viewport[2]);
        int viewHeight = Math.max(1, viewport[3]);

        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float pixelsPerBlock = viewHeight / (2.0f * halfHeight);
        int planeBlock = zPlane ? Mth.floor(eye.z) : Mth.floor(eye.x);

        float fogStart = (float) Config.FOG_START.get().doubleValue();
        float fogEnd = (float) Config.FOG_END.get().doubleValue();

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0f, viewWidth, viewHeight, 0.0f, -1000.0f, 1000.0f),
                VertexSorting.DISTANCE_TO_ORIGIN);

        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();
        try {
            boolean skyVisible = level.canSeeSky(player.blockPosition());
            float fogR = skyVisible ? 0.62f : 0.03f;
            float fogG = skyVisible ? 0.74f : 0.03f;
            float fogB = skyVisible ? 0.92f : 0.04f;
            drawBackground(viewWidth, viewHeight, fogR, fogG, fogB);
            int tiles = drawTiles(level, eye, right, zPlane, planeBlock, pixelsPerBlock,
                    fogStart, fogEnd, viewWidth, viewHeight);
            if (!loggedOnce) {
                loggedOnce = true;
                LOG.info("[STM] 2D overlay: {} tiles, yaw={}, viewport={}x{}", tiles, yaw, viewWidth, viewHeight);
            }
        } finally {
            RenderSystem.depthFunc(previousDepthFunc);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.ORTHOGRAPHIC_Z);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawBackground(int width, int height, float red, float green, float blue) {
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colorVertex(buffer, 0.0f, 0.0f, red, green, blue);
        colorVertex(buffer, 0.0f, height, red, green, blue);
        colorVertex(buffer, width, height, red, green, blue);
        colorVertex(buffer, width, 0.0f, red, green, blue);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void colorVertex(BufferBuilder buffer, float x, float y, float red, float green, float blue) {
        buffer.addVertex(x, y, Z_BACKGROUND).setColor(red, green, blue, 1.0f);
    }

    private static int drawTiles(ClientLevel level, Vec3 eye, Vec3 right, boolean zPlane,
                                 int planeBlock, float pixelsPerBlock, float fogStart, float fogEnd,
                                 int width, int height) {
        double eyeHorizontal = zPlane ? eye.x : eye.z;
        double rightSign = zPlane ? right.x : right.z;
        int centerHorizontal = Mth.floor(eyeHorizontal);
        int centerVertical = Mth.floor(eye.y);
        int radiusX = Mth.ceil(width * 0.5f / pixelsPerBlock) + 2;
        int radiusY = Mth.ceil(height * 0.5f / pixelsPerBlock) + 2;
        float half = pixelsPerBlock * 0.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;

        for (int dx = -radiusX; dx <= radiusX; dx++) {
            int horizontal = centerHorizontal + dx;
            double horizontalWorld = horizontal + 0.5;
            float screenX = width * 0.5f
                    + (float) ((horizontalWorld - eyeHorizontal) * rightSign * pixelsPerBlock);
            if (screenX < -pixelsPerBlock || screenX > width + pixelsPerBlock) {
                continue;
            }
            BlockPos columnPos = zPlane
                    ? new BlockPos(horizontal, 0, planeBlock)
                    : new BlockPos(planeBlock, 0, horizontal);
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                int vertical = centerVertical + dy;
                float screenY = height * 0.5f
                        - (float) ((vertical + 0.5 - eye.y) * pixelsPerBlock);
                if (screenY < -pixelsPerBlock || screenY > height + pixelsPerBlock) {
                    continue;
                }
                BlockPos pos = zPlane
                        ? new BlockPos(horizontal, vertical, planeBlock)
                        : new BlockPos(planeBlock, vertical, horizontal);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
                        .getBlockModel(state).getParticleIcon(ModelData.EMPTY);
                if (sprite == null) {
                    continue;
                }
                double distance = Math.sqrt(
                        (horizontalWorld - eyeHorizontal) * (horizontalWorld - eyeHorizontal)
                                + (vertical + 0.5 - eye.y) * (vertical + 0.5 - eye.y));
                float fog = fogEnd > fogStart
                        ? Mth.clamp((float) ((distance - fogStart) / (fogEnd - fogStart)), 0.0f, 1.0f)
                        : 0.0f;
                float alpha = 1.0f - fog;
                if (alpha <= 0.01f) {
                    continue;
                }

                float light = Mth.clamp(level.getMaxLocalRawBrightness(pos) / 15.0f, 0.0f, 1.0f);
                float bright = 0.18f + 0.82f * light;
                int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
                float red = ((tint >> 16) & 0xFF) / 255.0f * bright;
                float green = ((tint >> 8) & 0xFF) / 255.0f * bright;
                float blue = (tint & 0xFF) / 255.0f * bright;

                float x0 = screenX - half;
                float x1 = screenX + half;
                float y0 = screenY - half;
                float y1 = screenY + half;

                tileVertex(buffer, x0, y1, sprite.getU0(), sprite.getV1(), red, green, blue, alpha);
                tileVertex(buffer, x0, y0, sprite.getU0(), sprite.getV0(), red, green, blue, alpha);
                tileVertex(buffer, x1, y0, sprite.getU1(), sprite.getV0(), red, green, blue, alpha);
                tileVertex(buffer, x1, y1, sprite.getU1(), sprite.getV1(), red, green, blue, alpha);
                drawn++;
            }
        }

        if (drawn > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        return drawn;
    }

    private static void tileVertex(BufferBuilder buffer, float x, float y,
                                   float u, float v, float red, float green, float blue, float alpha) {
        buffer.addVertex(x, y, Z_TILES)
                .setUv(u, v)
                .setColor(red, green, blue, alpha);
    }
}
