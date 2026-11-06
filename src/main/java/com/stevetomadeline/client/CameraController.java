package com.stevetomadeline.client;

import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.Util;

/**
 * 主窗口侧视摄像机：位置/朝向由这里统一计算，CameraMixin 每帧写入 {@code Camera}。
 *
 * <p>2D：与眼睛等高、pitch 0；FOV 由配置的参考距离确定，避障收近时仅改变距离。
 * 2.5D：抬高并下俯 12°。两个模式都沿玩家到相机的方向检测遮挡。
 */
public final class CameraController {
    private static final double ISOMETRIC_PITCH = 12.0;
    private static LocalPlayer framePlayer;
    private static ClientLevel frameLevel;
    private static Vec3 framePosition;
    private static double frameDistance;
    private static final CameraDistanceEasing EASING = new CameraDistanceEasing();
    private static long previousFrameMillis;
    private static boolean nearWallSlice;
    private static boolean transitionSlice;

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
        Minecraft mc = Minecraft.getInstance();
        return framePosition != null && framePlayer == mc.player && frameLevel == mc.level
                ? Math.max(.05, frameDistance) : requestedDistance();
    }

    public static double projectionDistance() {
        return ParallaxRenderer.usesDepthPerspective()
                ? Math.max(Config.cameraDistance(), Config.DEPTH_DISTANCE.get())
                : Config.cameraDistance();
    }

    private static double requestedDistance() { return projectionDistance(); }

    public static Vec3 position(LocalPlayer player, float partialTick) {
        if (framePosition != null && framePlayer == player && frameLevel == Minecraft.getInstance().level) return framePosition;
        Vec3 desired = desiredPosition(player, partialTick);
        return Config.CAMERA_COLLISION.get() ? CameraObstruction.resolve(player.level(), player.getEyePosition(partialTick), desired) : desired;
    }

    /** Freeze one camera pose before terrain, projection, culling and drawing consume it. */
    public static void beginFrame(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (framePlayer != mc.player || frameLevel != mc.level) {
            EASING.reset();
            previousFrameMillis = 0;
        }
        framePosition = null;
        framePlayer = mc.player;
        frameLevel = mc.level;
        nearWallSlice = false;
        transitionSlice = false;
        if (!RenderModeManager.isCameraMode() || framePlayer == null || frameLevel == null) {
            EASING.reset();
            previousFrameMillis = 0;
            return;
        }
        Vec3 eye = framePlayer.getEyePosition(partialTick);
        Vec3 desired = desiredPosition(framePlayer, partialTick);
        double desiredDistance = eye.distanceTo(desired);
        CameraObstruction.Result hit = Config.CAMERA_COLLISION.get()
                ? CameraObstruction.inspect(framePlayer.level(), eye, desired)
                : new CameraObstruction.Result(desired, desiredDistance, false);
        boolean sliceSupported = RenderModeManager.isFlat2D() && !ClientPlaneController.turning(partialTick);
        boolean autoSlice = Config.NEAR_WALL_SLICE.get() && sliceSupported;
        nearWallSlice = CameraDistanceEasing.useSlice(autoSlice,
                hit.obstructed(), hit.distance());
        double target = nearWallSlice ? desiredDistance : hit.distance();
        long now = Util.getMillis();
        double dt = previousFrameMillis == 0 ? 0 : Math.clamp((now - previousFrameMillis) / 1000.0, 0, .1);
        previousFrameMillis = now;
        double easedDistance = EASING.update(target, dt);
        // While easing through an obstruction, use the existing cutaway to cover
        // hidden rock instead of displaying the inside of the camera's block.
        transitionSlice = CameraDistanceEasing.transitionSlice(autoSlice, hit.obstructed(), easedDistance, hit.distance());
        frameDistance = easedDistance;
        framePosition = eye.add(desired.subtract(eye).scale(desiredDistance < 1e-7 ? 0 : easedDistance / desiredDistance));
    }

    public static boolean nearWallSliceRequired() { return nearWallSlice || transitionSlice; }

    private static Vec3 desiredPosition(LocalPlayer player, float partialTick) {
        double distance = requestedDistance();
        return resolvePosition(player, partialTick, renderYaw(partialTick), distance);
    }

    private static Vec3 resolvePosition(LocalPlayer player, float partialTick, float yaw, double distance) {
        double radians = Math.toRadians(yaw);
        double lookX = -Math.sin(radians);
        double lookZ = Math.cos(radians);
        double up = Math.tan(Math.toRadians(ISOMETRIC_PITCH)) * distance;
        Vec3 eye = player.getEyePosition(partialTick);
        return RenderModeManager.isIsometric()
                ? eye.add(-lookX * distance, up, -lookZ * distance)
                : eye.add(-lookX * distance, 0.0, -lookZ * distance);
    }
}
