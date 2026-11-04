package com.stevetomadeline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import org.slf4j.Logger;

/**
 * 2D / 2.5D 的附加渲染层。
 *
 * <p>2D 模式：{@code LevelRendererMixin} 已经禁掉了所有 3D 区块渲染，
 * 这里用玩家所在轴的一层方块贴图铺满屏幕（泰拉瑞亚式），
 * 每格按到玩家的平面距离做 50% 起步、越远越浓的雾，最后让实体正常渲染在上面。
 *
 * <p>2.5D 模式：保留原版世界渲染，只补一层深度雾。
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final Logger LOG = LogUtils.getLogger();

    private static final float BACKGROUND_R = 0.02f;
    private static final float BACKGROUND_G = 0.02f;
    private static final float BACKGROUND_B = 0.03f;
    private static final float ASPECT = 960.0f / 540.0f;
    private static final float MARGIN = 1.2f;

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

        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float halfWidth = halfHeight * ASPECT;
        double slice = Config.SLICE_HALF_THICKNESS.get();
        double tileOffset = slice * 0.9;
        double backgroundOffset = slice * 0.99;

        float fogStart = (float) Config.FOG_START.get().doubleValue();
        float fogEnd = (float) Config.FOG_END.get().doubleValue();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            drawBackground(eye, forward, right, halfWidth, halfHeight, backgroundOffset);
            drawTiles(level, eye, forward, halfWidth, halfHeight, tileOffset, fogStart, fogEnd);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void drawBackground(Vec3 eye, Vec3 forward, Vec3 right,
                                       float halfWidth, float halfHeight, double offset) {
        Vec3 center = eye.add(forward.scale(offset));
        Vec3 left = center.subtract(right.scale(halfWidth * MARGIN));
        Vec3 rightEdge = center.add(right.scale(halfWidth * MARGIN));
        double y0 = center.y - halfHeight * MARGIN;
        double y1 = center.y + halfHeight * MARGIN;

        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        backgroundVertex(buffer, left.x, y0, left.z);
        backgroundVertex(buffer, left.x, y1, left.z);
        backgroundVertex(buffer, rightEdge.x, y1, rightEdge.z);
        backgroundVertex(buffer, rightEdge.x, y0, rightEdge.z);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void backgroundVertex(BufferBuilder buffer, double x, double y, double z) {
        buffer.addVertex((float) x, (float) y, (float) z)
                .setColor(BACKGROUND_R, BACKGROUND_G, BACKGROUND_B, 1.0f);
    }

    private static void drawTiles(ClientLevel level, Vec3 eye, Vec3 forward,
                                  float halfWidth, float halfHeight, double offset,
                                  float fogStart, float fogEnd) {
        boolean zPlane = Math.abs(forward.z) >= Math.abs(forward.x);
        double planeCoordinate = zPlane
                ? eye.z + forward.z * offset
                : eye.x + forward.x * offset;
        int planeBlock = zPlane ? Mth.floor(eye.z) : Mth.floor(eye.x);

        int radiusX = Mth.ceil(halfWidth * MARGIN) + 1;
        int radiusY = Mth.ceil(halfHeight * MARGIN) + 1;
        int centerX = Mth.floor(zPlane ? eye.x : eye.z);
        int centerY = Mth.floor(eye.y);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean any = false;

        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                int horizontal = centerX + dx;
                int vertical = centerY + dy;
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

                float distance = (float) Math.sqrt((double) dx * dx + (double) dy * dy);
                float fog = fogEnd > fogStart
                        ? Mth.clamp((distance - fogStart) / (fogEnd - fogStart), 0.0f, 1.0f)
                        : 0.0f;
                float alpha = 0.5f * (1.0f - fog);
                if (alpha <= 0.01f) {
                    continue;
                }

                int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
                float red = ((tint >> 16) & 0xFF) / 255.0f;
                float green = ((tint >> 8) & 0xFF) / 255.0f;
                float blue = (tint & 0xFF) / 255.0f;

                double x0 = horizontal;
                double x1 = horizontal + 1.0;
                double y0 = vertical;
                double y1 = vertical + 1.0;

                if (zPlane) {
                    tileVertex(buffer, x0, y0, planeCoordinate, sprite.getU0(), sprite.getV1(), red, green, blue, alpha);
                    tileVertex(buffer, x0, y1, planeCoordinate, sprite.getU0(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, x1, y1, planeCoordinate, sprite.getU1(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, x1, y0, planeCoordinate, sprite.getU1(), sprite.getV1(), red, green, blue, alpha);
                } else {
                    tileVertex(buffer, planeCoordinate, y0, x0, sprite.getU0(), sprite.getV1(), red, green, blue, alpha);
                    tileVertex(buffer, planeCoordinate, y1, x0, sprite.getU0(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, planeCoordinate, y1, x1, sprite.getU1(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, planeCoordinate, y0, x1, sprite.getU1(), sprite.getV1(), red, green, blue, alpha);
                }
                any = true;
            }
        }

        if (any) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
    }

    private static void tileVertex(BufferBuilder buffer, double x, double y, double z,
                                   float u, float v, float red, float green, float blue, float alpha) {
        buffer.addVertex((float) x, (float) y, (float) z)
                .setUv(u, v)
                .setColor(red, green, blue, alpha);
    }
}
