package com.stevetomadeline.client;

import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 主窗口侧视摄像机：位置/朝向由这里统一计算，CameraMixin 每帧写入 {@code Camera}。
 *
 * <p>2D：与眼睛等高、pitch 0；连续景深相机后移但保持玩家平面缩放。
 * 2.5D：抬高并下俯 12°。2.5D 会沿相机方向做方块检测，
 * 避免整个镜头埋进山体。
 */
public final class CameraController {
    private static final double CLEARANCE = 0.3;
    private static final double ISOMETRIC_PITCH = 12.0;

    private CameraController() {
    }

    public static float yaw(LocalPlayer player) {
        var plane = ClientPlaneController.plane();
        return plane == null ? Math.round(player.getYRot() / 90.0f) * 90.0f : plane.heading().yaw();
    }

    public static float renderYaw(float partialTick) {
        return ClientPlaneController.cameraYaw(partialTick);
    }

    public static float pitch() {
        return RenderModeManager.isIsometric() ? (float) ISOMETRIC_PITCH : 0.0f;
    }

    public static double distance() {
        return ParallaxRenderer.usesDepthPerspective()
                ? Math.max(Config.cameraDistance(), Config.DEPTH_DISTANCE.get())
                : Config.cameraDistance();
    }

    public static Vec3 position(LocalPlayer player, float partialTick) {
        float yaw = renderYaw(partialTick);
        double radians = Math.toRadians(yaw);
        double lookX = -Math.sin(radians);
        double lookZ = Math.cos(radians);
        double distance = distance();
        double up = Math.tan(Math.toRadians(ISOMETRIC_PITCH)) * distance;

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 desired = RenderModeManager.isIsometric()
                ? eye.add(-lookX * distance, up, -lookZ * distance)
                : eye.add(-lookX * distance, 0.0, -lookZ * distance);

        if (RenderModeManager.isIsometric()) {
            desired = clampOutOfBlocks(eye, desired);
        }
        return desired;
    }

    private static Vec3 clampOutOfBlocks(Vec3 eye, Vec3 desired) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return desired;
        }
        BlockHitResult hit = level.clip(new ClipContext(
                eye, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return desired;
        }
        Vec3 offset = desired.subtract(eye);
        double length = offset.length();
        if (length < 0.001) {
            return eye;
        }
        double available = eye.distanceTo(hit.getLocation()) - CLEARANCE;
        double scale = Math.max(0.02, Math.min(1.0, available / length));
        Vec3 result = eye.add(offset.scale(scale));
        for (int guard = 0; guard < 12; guard++) {
            if (!level.getBlockState(net.minecraft.core.BlockPos.containing(result)).canOcclude() || scale <= 0.02) {
                break;
            }
            scale *= 0.75;
            result = eye.add(offset.scale(scale));
        }
        return result;
    }
}
