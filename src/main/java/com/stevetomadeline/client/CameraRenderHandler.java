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
import org.lwjgl.opengl.GL11;
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
    /** 屏幕空间正交投影的 z 范围 -1000..1000；写入小于远平面的深度，避免被天空蒙版当成天空。 */
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
        if (!WindowXCameraBridge.isActive() || !CameraRenderManager.isRendering()) {
            return;
        }
        if (!WindowXCameraBridge.CAMERA_ID.equals(CameraRenderManager.getCurrentCameraId())) {
            return;
        }
        try {
            if (RenderModeManager.isFlat2D()) {
                drawFlat2D(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
            // 2.5D 不施加自定义雾：WindowX 在 renderLevel 前会 setupNoFog()，
            // 之前强加的 12–64 格雾会把远处地形渐隐成天空色，看起来像被切断。
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
        float yaw = Math.round(player.getYRot() / 90.0f) * 90.0f;
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        boolean zPlane = Math.abs(forward.z) >= Math.abs(forward.x);

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int viewWidth = Math.max(1, viewport[2]);
        int viewHeight = Math.max(1, viewport[3]);

        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float blocksPerPixel = (2.0f * halfHeight) / viewHeight;
        int tileSize = Math.max(1, Mth.ceil(1.0f / blocksPerPixel));
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

        // 必须写深度：WindowX 的 CameraSkyMask 会用深度纹理把“天空”像素清成透明。
        // 但深度缓冲的清除值与比较方向由 WindowX/管线决定，这里用 ALWAYS 保证每一层都落笔，
        // 同时把深度写进去（叠加层彼此用绘制顺序覆盖）。
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
            int tiles = drawTiles(level, eye, right, zPlane, planeBlock, blocksPerPixel, tileSize,
                    fogStart, fogEnd, viewWidth, viewHeight);
            if (!loggedOnce) {
                loggedOnce = true;
                LOG.info("[WindowX] 2D overlay: {} tiles, yaw={}, viewport={}x{}, window={}x{}",
                        tiles, yaw, viewWidth, viewHeight,
                        Minecraft.getInstance().getWindow().getWidth(),
                        Minecraft.getInstance().getWindow().getHeight());
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
                                 int planeBlock, float blocksPerPixel, int tileSize,
                                 float fogStart, float fogEnd, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;

        for (int screenY = 0; screenY < height; screenY += tileSize) {
            int screenX = 0;
            while (screenX < width) {
                float centerX = screenX + tileSize * 0.5f;
                float centerY = screenY + tileSize * 0.5f;
                double horizontalOffset = (centerX - width * 0.5f) * blocksPerPixel;
                double verticalOffset = (height * 0.5 - centerY) * blocksPerPixel;

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
        buffer.addVertex(x, y, Z_TILES)
                .setUv(u, v)
                .setColor(red, green, blue, alpha);
    }
}
