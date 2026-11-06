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
import com.stevetomadeline.client.windowx.WindowXCompat;
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
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;

import java.util.List;

/**
 * 2D 模式的前景侧面纹图层。
 *
 * <p>背景保持普通正交相机：原版天空盒、昼夜、雾效全部保留；本类只负责在玩家所在轴上
 * 叠加一层“方块朝向摄像机那一面”的贴图，空气格透出后方真实世界。
 *
 * <p>另外：摄像机模式下隐藏准星与手持物品；雾色固定为当前天空色，避免入水时突变。
 */
@EventBusSubscriber(modid = SteveToMadeline.MODID, value = Dist.CLIENT)
public final class CameraRenderHandler {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean loggedOnce;

    private CameraRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (WindowXCompat.isRendering()) {
            return;
        }
        if (!RenderModeManager.isFlat2D()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ParallaxRenderer.composite();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            drawForegroundSlice(partialTick);
        }
    }

    private static void drawForegroundSlice(float partialTick) {
        if (!TerrainClipping.drawSlice()) {
            return;
        }
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
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        boolean zPlane = Math.abs(forward.z) >= Math.abs(forward.x);
        Direction face = zPlane
                ? (forward.z > 0 ? Direction.NORTH : Direction.SOUTH)
                : (forward.x > 0 ? Direction.WEST : Direction.EAST);

        // 切片平面：玩家所在轴，略微朝远处偏移，避免与玩家自身深度打架。
        double slice = Config.SLICE_HALF_THICKNESS.get();
        double planeOffset = slice * 0.5;
        // 正交伪视差：前景层按移动速度做一个极小的横向超前，背景保持原速。
        Vec3 velocity = player.getDeltaMovement();
        double parallax = ParallaxRenderer.enabled() ? 0.0
                : velocity.dot(new Vec3(right.x, 0.0, right.z)) * Config.parallaxStrength();
        double planeCoordinate = zPlane
                ? eye.z + forward.z * planeOffset
                : eye.x + forward.x * planeOffset;
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
        RenderSystem.disableCull();
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
                if (state.isAir() || !TerrainClipping.drawSliceBlock(state)) {
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

                double x0 = horizontal - camera.x + right.x * parallax;
                double x1 = x0 + 1.0;
                double y0 = vertical - camera.y;
                double y1 = y0 + 1.0;
                if (zPlane) {
                    double z = planeCoordinate - camera.z + right.z * parallax;
                    tileVertex(buffer, x0, y0, z, sprite.getU0(), sprite.getV1(), red, green, blue);
                    tileVertex(buffer, x0, y1, z, sprite.getU0(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x1, y1, z, sprite.getU1(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x1, y0, z, sprite.getU1(), sprite.getV1(), red, green, blue);
                } else {
                    double x = planeCoordinate - camera.x + right.x * parallax;
                    double z0 = horizontal - camera.z + right.z * parallax;
                    double z1 = z0 + 1.0;
                    tileVertex(buffer, x, y0, z0, sprite.getU0(), sprite.getV1(), red, green, blue);
                    tileVertex(buffer, x, y1, z0, sprite.getU0(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x, y1, z1, sprite.getU1(), sprite.getV0(), red, green, blue);
                    tileVertex(buffer, x, y0, z1, sprite.getU1(), sprite.getV1(), red, green, blue);
                }
                drawn++;
            }
        }

        var mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        if (!loggedOnce) {
            loggedOnce = true;
            LOG.info("[STM] 2D foreground slice: {} quads at plane {} (yaw {})", drawn, planeCoordinate, yaw);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
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

    /** 泰拉瑞亚式光照：环境底光较高，天光按昼夜衰减，方块光优先。 */
    private static float brightness(ClientLevel level, BlockPos pos, float partialTick) {
        int sky = level.getBrightness(LightLayer.SKY, pos);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        float time = level.getTimeOfDay(partialTick);
        float sun = Mth.clamp(Mth.cos(time * (float) Math.PI * 2.0f) * 2.0f + 0.5f, 0.0f, 1.0f);
        float light = Math.max(block / 15.0f, (sky / 15.0f) * sun);
        return 0.35f + 0.65f * light;
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

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiLayerEvent.Pre event) {
        if (RenderModeManager.isCameraMode() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
        if (VanillaGuiLayers.TAB_LIST.equals(event.getName()) && ClientPlaneController.capturesPlayerList()) {
            event.setCanceled(true);
        }
    }

    /** 摄像机模式下放宽雾距离，让远处地形仍然可见；滑块可调。 */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!RenderModeManager.isCameraMode()
                || (WindowXCompat.isRendering() && !WindowXCompat.isRenderingBackground())) {
            return;
        }
        float start = (float) Config.FOG_START.get().doubleValue();
        float end = (float) Config.FOG_END.get().doubleValue();
        if (end <= start + 1.0f) {
            return;
        }
        event.setNearPlaneDistance(start);
        event.setFarPlaneDistance(end);
        event.setCanceled(true);
    }

    /** 摄像机模式下固定雾色为当前天空色，避免入水/岩浆时天空盒突变。 */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!RenderModeManager.isCameraMode()
                || (WindowXCompat.isRendering() && !WindowXCompat.isRenderingBackground())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 color = mc.level.getSkyColor(mc.player.position(), (float) event.getPartialTick());
        event.setRed((float) color.x);
        event.setGreen((float) color.y);
        event.setBlue((float) color.z);
    }
}
