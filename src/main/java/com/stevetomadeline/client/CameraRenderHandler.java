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
import com.xutrain.windowx.camera.CameraRegistry;
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
 * <p>注意：Minecraft 的关卡渲染以摄像机位置为原点（区块、实体都用相机相对坐标），
 * 所以这里所有顶点都要先减去相机位置，否则会被视锥直接裁掉。
 *
 * <p>2D 模式：{@code LevelRendererMixin} 禁掉全部 3D 区块渲染后，这里把玩家所在方块列
 * 逐格画成面向摄像机的贴图切片（泰拉瑞亚式），并按到玩家的平面距离做 50% 起步的雾。
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final Logger LOG = LogUtils.getLogger();

    private static final float BACKGROUND_R = 0.02f;
    private static final float BACKGROUND_G = 0.02f;
    private static final float BACKGROUND_B = 0.03f;
    private static final float ASPECT = 960.0f / 540.0f;
    private static final float MARGIN = 1.2f;

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
        CameraRegistry.Pose pose = WindowXCameraBridge.lastPose();
        if (level == null || player == null || pose == null) {
            return;
        }

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 origin = pose.position();

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
            drawBackground(eye, origin, forward, right, halfWidth, halfHeight, backgroundOffset);
            int tiles = drawTiles(level, eye, origin, forward, halfWidth, halfHeight,
                    tileOffset, fogStart, fogEnd);
            if (!loggedOnce) {
                loggedOnce = true;
                LOG.info("[WindowX] 2D overlay active: {} tiles, origin={}", tiles, origin);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void drawBackground(Vec3 eye, Vec3 origin, Vec3 forward, Vec3 right,
                                       float halfWidth, float halfHeight, double offset) {
        Vec3 center = eye.subtract(origin).add(forward.scale(offset));
        Vec3 leftEdge = center.subtract(right.scale(halfWidth * MARGIN));
        Vec3 rightEdge = center.add(right.scale(halfWidth * MARGIN));
        double y0 = center.y - halfHeight * MARGIN;
        double y1 = center.y + halfHeight * MARGIN;

        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        backgroundVertex(buffer, leftEdge.x, y0, leftEdge.z);
        backgroundVertex(buffer, leftEdge.x, y1, leftEdge.z);
        backgroundVertex(buffer, rightEdge.x, y1, rightEdge.z);
        backgroundVertex(buffer, rightEdge.x, y0, rightEdge.z);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void backgroundVertex(BufferBuilder buffer, double x, double y, double z) {
        buffer.addVertex((float) x, (float) y, (float) z)
                .setColor(BACKGROUND_R, BACKGROUND_G, BACKGROUND_B, 1.0f);
    }

    private static int drawTiles(ClientLevel level, Vec3 eye, Vec3 origin, Vec3 forward,
                                 float halfWidth, float halfHeight, double offset,
                                 float fogStart, float fogEnd) {
        boolean zPlane = Math.abs(forward.z) >= Math.abs(forward.x);
        double planeWorld = zPlane
                ? eye.z + forward.z * offset
                : eye.x + forward.x * offset;
        double plane = planeWorld - (zPlane ? origin.z : origin.x);
        int planeBlock = zPlane ? Mth.floor(eye.z) : Mth.floor(eye.x);

        int radiusX = Mth.ceil(halfWidth * MARGIN) + 1;
        int radiusY = Mth.ceil(halfHeight * MARGIN) + 1;
        int centerHorizontal = Mth.floor(zPlane ? eye.x : eye.z);
        int centerVertical = Mth.floor(eye.y);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;

        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                int horizontal = centerHorizontal + dx;
                int vertical = centerVertical + dy;
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

                double h0 = horizontal - (zPlane ? origin.x : origin.z);
                double h1 = h0 + 1.0;
                double y0 = vertical - origin.y;
                double y1 = y0 + 1.0;

                if (zPlane) {
                    tileVertex(buffer, h0, y0, plane, sprite.getU0(), sprite.getV1(), red, green, blue, alpha);
                    tileVertex(buffer, h0, y1, plane, sprite.getU0(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, h1, y1, plane, sprite.getU1(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, h1, y0, plane, sprite.getU1(), sprite.getV1(), red, green, blue, alpha);
                } else {
                    tileVertex(buffer, plane, y0, h0, sprite.getU0(), sprite.getV1(), red, green, blue, alpha);
                    tileVertex(buffer, plane, y1, h0, sprite.getU0(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, plane, y1, h1, sprite.getU1(), sprite.getV0(), red, green, blue, alpha);
                    tileVertex(buffer, plane, y0, h1, sprite.getU1(), sprite.getV1(), red, green, blue, alpha);
                }
                drawn++;
            }
        }

        if (drawn > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        return drawn;
    }

    private static void tileVertex(BufferBuilder buffer, double x, double y, double z,
                                   float u, float v, float red, float green, float blue, float alpha) {
        buffer.addVertex((float) x, (float) y, (float) z)
                .setUv(u, v)
                .setColor(red, green, blue, alpha);
    }
}
