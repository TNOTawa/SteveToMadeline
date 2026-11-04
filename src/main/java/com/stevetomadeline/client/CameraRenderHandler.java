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
import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import com.xutrain.windowx.camera.CameraRenderManager;
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
import org.slf4j.Logger;

/**
 * 2D / 2.5D 的附加渲染层。
 *
 * <p>2D 的方块切片直接在屏幕空间绘制：临时换成像素正交投影，按 960x540 的相机纹理
 * 逐格铺方块正面贴图。这样不依赖世界坐标/视锥/摄像机朝向，四个方向表现一致，
 * 也不会再被切片近远平面裁掉。
 *
 * <p>2.5D 只补深度雾，世界本身交给原版渲染。
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final Logger LOG = LogUtils.getLogger();

    private static final int VIEW_WIDTH = 960;
    private static final int VIEW_HEIGHT = 540;
    private static final float ASPECT = (float) VIEW_WIDTH / VIEW_HEIGHT;
    private static final float BACKGROUND_R = 0.02f;
    private static final float BACKGROUND_G = 0.02f;
    private static final float BACKGROUND_B = 0.03f;

    private static boolean loggedOnce;

    private CameraRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        if (!WindowXCameraBridge.isActive() || !CameraRenderManager.isRendering()) {
            return;
        }
        if (!WindowXCameraBridge.CAMERA_ID.equals(CameraRenderManager.getCurrentCameraId())) {
            return;
        }
        try {
            if (RenderModeManager.isFlat2D()) {
                drawFlat2D(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            } else if (RenderModeManager.isIsometric()) {
                applyFog();
            }
        } catch (Throwable t) {
            LOG.warn("[WindowX] camera overlay failed", t);
        }
    }

    private static void applyFog() {
        try {
            double start = Config.FOG_START.get();
            double end = Config.FOG_END.get();
            if (end <= start || end <= 0.0) {
                return;
            }
            RenderSystem.setShaderFogStart((float) start);
            RenderSystem.setShaderFogEnd((float) end);
        } catch (Throwable ignored) {
            // 配置未就绪时跳过。
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
        float yaw = Math.round(player.getYRot() / 90.0f) * 90.0f;
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        boolean zPlane = Math.abs(forward.z) >= Math.abs(forward.x);

        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float blocksPerPixel = (2.0f * halfHeight) / VIEW_HEIGHT;
        int planeBlock = zPlane ? Mth.floor(eye.z) : Mth.floor(eye.x);

        float fogStart = (float) Config.FOG_START.get().doubleValue();
        float fogEnd = (float) Config.FOG_END.get().doubleValue();

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0f, VIEW_WIDTH, VIEW_HEIGHT, 0.0f, -1000.0f, 1000.0f),
                VertexSorting.DISTANCE_TO_ORIGIN);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            drawBackground();
            int tiles = drawTiles(level, eye, right, zPlane, planeBlock, blocksPerPixel,
                    fogStart, fogEnd);
            if (!loggedOnce) {
                loggedOnce = true;
                LOG.info("[WindowX] 2D screen-space overlay active: {} tiles, yaw={}", tiles, yaw);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.ORTHOGRAPHIC_Z);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawBackground() {
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colorVertex(buffer, 0.0f, 0.0f);
        colorVertex(buffer, 0.0f, VIEW_HEIGHT);
        colorVertex(buffer, VIEW_WIDTH, VIEW_HEIGHT);
        colorVertex(buffer, VIEW_WIDTH, 0.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void colorVertex(BufferBuilder buffer, float x, float y) {
        buffer.addVertex(x, y, 0.0f).setColor(BACKGROUND_R, BACKGROUND_G, BACKGROUND_B, 1.0f);
    }

    private static int drawTiles(ClientLevel level, Vec3 eye, Vec3 right, boolean zPlane,
                                 int planeBlock, float blocksPerPixel, float fogStart, float fogEnd) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int tileSize = Math.max(1, Mth.ceil(blocksPerPixel));
        int drawn = 0;

        for (int screenY = 0; screenY < VIEW_HEIGHT; screenY += tileSize) {
            int screenX = 0;
            while (screenX < VIEW_WIDTH) {
                float centerX = screenX + tileSize * 0.5f;
                float centerY = screenY + tileSize * 0.5f;
                double horizontalOffset = (centerX - VIEW_WIDTH * 0.5f) * blocksPerPixel;
                double verticalOffset = (VIEW_HEIGHT * 0.5 - centerY) * blocksPerPixel;

                double worldHorizontal = zPlane
                        ? eye.x + right.x * horizontalOffset
                        : eye.z + right.z * horizontalOffset;
                double worldVertical = eye.y + verticalOffset;

                int horizontalBlock = Mth.floor(worldHorizontal);
                int verticalBlock = Mth.floor(worldVertical);
                BlockPos pos = zPlane
                        ? new BlockPos(horizontalBlock, verticalBlock, planeBlock)
                        : new BlockPos(planeBlock, verticalBlock, horizontalBlock);

                screenX += tileSize;
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
                        .getBlockModel(state).getParticleIcon(ModelData.EMPTY);
                if (sprite == null) {
                    continue;
                }

                float distance = (float) Math.sqrt(horizontalOffset * horizontalOffset + verticalOffset * verticalOffset);
                float fog = fogEnd > fogStart
                        ? Mth.clamp((distance - fogStart) / (fogEnd - fogStart), 0.0f, 1.0f)
                        : 0.0f;
                float alpha = 0.5f * (1.0f - fog);
                if (alpha <= 0.01f) {
                    continue;
                }

                float light = Mth.clamp(level.getMaxLocalRawBrightness(pos) / 15.0f, 0.0f, 1.0f);
                float bright = 0.18f + 0.82f * light;

                int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
                float red = ((tint >> 16) & 0xFF) / 255.0f * bright;
                float green = ((tint >> 8) & 0xFF) / 255.0f * bright;
                float blue = (tint & 0xFF) / 255.0f * bright;

                float x0 = screenX - tileSize;
                float x1 = screenX;
                float y0 = screenY;
                float y1 = screenY + tileSize;

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
        buffer.addVertex(x, y, 0.0f)
                .setUv(u, v)
                .setColor(red, green, blue, alpha);
    }
}
