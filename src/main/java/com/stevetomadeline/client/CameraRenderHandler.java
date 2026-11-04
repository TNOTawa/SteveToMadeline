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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.util.List;

/**
 * 方案 A：正交切片合成。
 *
 * <ul>
 *   <li>远景：真实世界由正交相机照常渲染，切片后方开始加雾；</li>
 *   <li>前景：在切片平面上用方块“朝向相机那一面”的贴图叠加一层，空气格留空透出远景；</li>
 *   <li>天空盒由与雾色一致的底图覆盖；手持物品不渲染。</li>
 * </ul>
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final Logger LOG = LogUtils.getLogger();
    private static final float Z_BACKDROP = 900.0f;
    private static boolean fogLogged;

    private CameraRenderHandler() {
    }

    private static boolean flat() {
        return RenderModeManager.isFlat2D();
    }

    private static float[] fogColor() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean sky = player != null && mc.level != null && mc.level.canSeeSky(player.blockPosition());
        return sky ? new float[]{0.62f, 0.74f, 0.92f} : new float[]{0.02f, 0.02f, 0.03f};
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (!RenderModeManager.isCameraMode()) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (flat()) {
                drawBackdrop();
                applyFog();
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && flat()) {
            drawForegroundSlice(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
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
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
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

    private static void applyFog() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double distance = Config.CAMERA_DISTANCE.get();
        double slice = Config.SLICE_HALF_THICKNESS.get();
        float start = (float) (distance + slice);
        float end = (float) Math.max(start + 16.0, Config.FOG_END.get());
        float[] color = fogColor();
        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogColor(color[0], color[1], color[2], 1.0f);
        if (!fogLogged) {
            fogLogged = true;
            LOG.info("[STM] fog applied: {} -> {}", start, end);
        }
    }

    private static void drawForegroundSlice(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return;
        }
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 camera = CameraController.position(player, partialTick);
        float yaw = CameraController.yaw(player);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        boolean zPlane = Math.abs(forward.z) >= Math.abs(forward.x);
        Direction face = zPlane
                ? (forward.z > 0 ? Direction.NORTH : Direction.SOUTH)
                : (forward.x > 0 ? Direction.WEST : Direction.EAST);

        double distance = Config.CAMERA_DISTANCE.get();
        double slice = Config.SLICE_HALF_THICKNESS.get();
        double offset = distance + slice * 0.5;
        double planeCoordinate = zPlane
                ? eye.z + forward.z * offset
                : eye.x + forward.x * offset;
        int planeBlock = zPlane ? Mth.floor(eye.z) : Mth.floor(eye.x);

        float halfHeight = (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        int radiusHorizontal = Mth.ceil(halfHeight * aspect) + 2;
        int radiusVertical = Mth.ceil(halfHeight) + 2;
        int centerHorizontal = Mth.floor(zPlane ? eye.x : eye.z);
        int centerVertical = Mth.floor(eye.y);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;

        for (int dx = -radiusHorizontal; dx <= radiusHorizontal; dx++) {
            for (int dy = -radiusVertical; dy <= radiusVertical; dy++) {
                int horizontal = centerHorizontal + dx;
                int vertical = centerVertical + dy;
                BlockPos pos = zPlane
                        ? new BlockPos(horizontal, vertical, planeBlock)
                        : new BlockPos(planeBlock, vertical, horizontal);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                TextureAtlasSprite sprite = faceSprite(mc, state, face);
                if (sprite == null) {
                    continue;
                }
                int tint = mc.getBlockColors().getColor(state, level, pos, 0);
                float bright = brightness(level, pos, partialTick);
                float red = ((tint >> 16) & 0xFF) / 255.0f * bright;
                float green = ((tint >> 8) & 0xFF) / 255.0f * bright;
                float blue = (tint & 0xFF) / 255.0f * bright;

                double x0 = horizontal - camera.x;
                double x1 = x0 + 1.0;
                double y0 = vertical - camera.y;
                double y1 = y0 + 1.0;
                if (zPlane) {
                    double z = planeCoordinate - camera.z;
                    tileVertex(buffer, x0, y0, z, sprite.getU0(), sprite.getV1(), red, green, blue);
                    tileVertex(buffer, x0, y1, z, sprite.getU0(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x1, y1, z, sprite.getU1(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x1, y0, z, sprite.getU1(), sprite.getV1(), red, green, blue);
                } else {
                    double x = planeCoordinate - camera.x;
                    double z0 = horizontal - camera.z;
                    double z1 = z0 + 1.0;
                    tileVertex(buffer, x, y0, z0, sprite.getU0(), sprite.getV1(), red, green, blue);
                    tileVertex(buffer, x, y1, z0, sprite.getU0(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x, y1, z1, sprite.getU1(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x, y0, z1, sprite.getU1(), sprite.getV1(), red, green, blue);
                }
                drawn++;
            }
        }

        if (drawn > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static TextureAtlasSprite faceSprite(Minecraft mc, BlockState state, Direction face) {
        List<BakedQuad> quads = mc.getBlockRenderer().getBlockModel(state)
                .getQuads(state, face, RandomSource.create(42L));
        if (!quads.isEmpty()) {
            return quads.get(0).getSprite();
        }
        return mc.getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY);
    }

    private static float brightness(ClientLevel level, BlockPos pos, float partialTick) {
        int sky = level.getBrightness(LightLayer.SKY, pos);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        float darken = level.getSkyDarken(partialTick);
        float light = Math.max(block, sky * darken) / 15.0f;
        return 0.15f + 0.85f * Mth.clamp(light, 0.0f, 1.0f);
    }

    private static void tileVertex(BufferBuilder buffer, double x, double y, double z,
                                   float u, float v, float red, float green, float blue) {
        buffer.addVertex((float) x, (float) y, (float) z)
                .setUv(u, v)
                .setColor(red, green, blue, 1.0f);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (RenderModeManager.isCameraMode()) {
            event.setCanceled(true);
        }
    }
}
