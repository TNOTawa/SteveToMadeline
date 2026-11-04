package com.stevetomadeline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stevetomadeline.Config;
import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import com.xutrain.windowx.camera.CameraRegistry;
import com.xutrain.windowx.camera.CameraRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;

/**
 * WindowX 摄像机的附加渲染：
 *
 * <ul>
 *   <li>深度雾：WindowX 在调用 LevelRenderer 前会 {@code setupNoFog()}，所以雾必须在
 *       渲染阶段里自己设置，原版的 RenderFog 事件不会触发。</li>
 *   <li>方块背景：当摄像机嵌在实体方块里时，山体内部没有面可画，原来的背景会漏出天空。
 *       这里在远景处补一张由该方块贴图铺成的背景板，表现成“世界在摄像机处被切开”。</li>
 * </ul>
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int BACKDROP_COLUMNS = 24;
    private static final int BACKDROP_ROWS = 14;
    private static final float BACKDROP_DISTANCE = 96.0f;

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
        BlockState embedded = WindowXCameraBridge.embeddedBlockState();
        applyFog(embedded != null);
        try {
            if (embedded != null) {
                drawBlockBackdrop(embedded);
            }
        } catch (Throwable t) {
            LOG.warn("[WindowX] block backdrop failed", t);
        }
    }

    private static void applyFog(boolean embedded) {
        try {
            double start = Config.FOG_START.get();
            double end = Config.FOG_END.get();
            if (end <= start || end <= 0) {
                return;
            }
            RenderSystem.setShaderFogStart((float) start);
            RenderSystem.setShaderFogEnd((float) end);
            if (embedded) {
                // 埋在方块里时用暗色雾，避免用天空色把洞穴染蓝。
                RenderSystem.setShaderFogColor(0.02f, 0.02f, 0.03f, 1.0f);
            }
        } catch (Throwable ignored) {
            // 配置未就绪时跳过。
        }
    }

    private static void drawBlockBackdrop(BlockState state) {
        CameraRegistry.Pose pose = WindowXCameraBridge.lastPose();
        if (pose == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        TextureAtlasSprite sprite = mc.getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY);
        if (sprite == null) {
            return;
        }

        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float aspect = 960.0f / 540.0f;
        float halfWidth = halfHeight * aspect * 1.35f;
        float half = halfHeight * 1.35f;
        float yaw = pose.yaw();
        double rad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
        Vec3 right = new Vec3(Math.cos(rad), 0.0, Math.sin(rad));
        Vec3 center = pose.position().add(forward.scale(BACKDROP_DISTANCE));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        for (int cx = 0; cx < BACKDROP_COLUMNS; cx++) {
            float x0 = -halfWidth + (2.0f * halfWidth) * cx / BACKDROP_COLUMNS;
            float x1 = -halfWidth + (2.0f * halfWidth) * (cx + 1) / BACKDROP_COLUMNS;
            for (int cy = 0; cy < BACKDROP_ROWS; cy++) {
                float y0 = -half + (2.0f * half) * cy / BACKDROP_ROWS;
                float y1 = -half + (2.0f * half) * (cy + 1) / BACKDROP_ROWS;

                Vec3 p00 = quadPoint(center, right, x0, y0);
                Vec3 p01 = quadPoint(center, right, x0, y1);
                Vec3 p11 = quadPoint(center, right, x1, y1);
                Vec3 p10 = quadPoint(center, right, x1, y0);

                vertex(buffer, p00, u0, v1);
                vertex(buffer, p01, u0, v0);
                vertex(buffer, p11, u1, v0);
                vertex(buffer, p10, u1, v1);
            }
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
    }

    private static Vec3 quadPoint(Vec3 center, Vec3 right, float x, float y) {
        return center.add(right.scale(x)).add(0.0, y, 0.0);
    }

    private static void vertex(BufferBuilder buffer, Vec3 pos, float u, float v) {
        buffer.addVertex((float) pos.x, (float) pos.y, (float) pos.z).setUv(u, v);
    }
}
