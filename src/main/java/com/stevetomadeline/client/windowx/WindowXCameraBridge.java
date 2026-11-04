package com.stevetomadeline.client.windowx;

import com.mojang.logging.LogUtils;
import com.xutrain.windowx.api.WindowXClientApi;
import com.xutrain.windowx.api.window.CameraOptions;
import com.xutrain.windowx.api.window.CameraProjection;
import com.xutrain.windowx.api.window.ClientWindow;
import com.xutrain.windowx.api.window.WindowDisplayMode;
import com.xutrain.windowx.api.window.WindowId;
import com.xutrain.windowx.api.window.WindowOptions;
import com.xutrain.windowx.camera.CameraRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/**
 * P0 技术验证：用 WindowX 公开 API 建一个跟随玩家的正交侧视摄像机窗口。
 *
 * <p>数据流：本类把自己的 {@link CameraRegistry.Locator} 以固定 cameraId 注册进 WindowX，
 * 再让新建窗口绑定该 cameraId。WindowX 每帧调用 {@link #locate(float)} 取 Pose，
 * 因此无需生成 CameraEntity，也不用 Mixin。
 *
 * <p>这是原型代码：窗口尺寸、距离、视野都是常量，P1 起再迁到配置与平面状态机。
 */
public final class WindowXCameraBridge {
    private static final Logger LOG = LogUtils.getLogger();

    private static final WindowId WINDOW_ID = WindowId.of("stevetomadeline", "camera");
    private static final String CAMERA_ID = "stevetomadeline:follow";
    private static final Object CAMERA_OWNER = new Object();

    /** 摄像机沿视线反方向后退的距离（方块）。 */
    private static final double CAMERA_DISTANCE = 8.0;
    /** 摄像机相对眼睛的高度偏移（方块）。 */
    private static final double CAMERA_HEIGHT = 0.0;

    private static final int TEXTURE_WIDTH = 960;
    private static final int TEXTURE_HEIGHT = 540;
    private static final float FOV = 70.0f;
    private static final float RENDER_DISTANCE = 32.0f;

    private static ClientWindow window;
    private static CameraRegistry.Pose lastPose;
    private static boolean active;

    private WindowXCameraBridge() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void toggle() {
        if (active) {
            stop();
        } else {
            start();
        }
    }

    public static void start() {
        if (active) {
            return;
        }
        if (!WindowXClientApi.isAvailable()) {
            LOG.warn("[WindowX] API unavailable - is WindowX installed?");
            notifyPlayer("WindowX 未加载，无法启动侧视摄像机");
            return;
        }
        try {
            CameraRegistry.update(CAMERA_OWNER, CAMERA_ID, WindowXCameraBridge::locate);

            // 上一次异常退出可能留下同名窗口，先关掉再建。
            WindowXClientApi.getWindow(WINDOW_ID).ifPresent(ClientWindow::close);

            window = WindowXClientApi.create(WINDOW_ID, WindowOptions.builder()
                    .title("SteveToMadeline")
                    .size(TEXTURE_WIDTH, TEXTURE_HEIGHT)
                    .borderless(false)
                    .build());
            window.bindCamera(CAMERA_ID, WindowDisplayMode.FIT);
            window.configureCamera(new CameraOptions(
                    0.0, 0.0, FOV,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT,
                    1, RENDER_DISTANCE,
                    CameraProjection.ORTHOGRAPHIC));

            active = true;
            LOG.info("[WindowX] orthographic camera started (id={}, window={})", CAMERA_ID, WINDOW_ID.value());
            notifyPlayer("侧视摄像机已启动（R 退出）");
        } catch (Throwable t) {
            LOG.error("[WindowX] failed to start camera", t);
            notifyPlayer("侧视摄像机启动失败，详见日志");
            stop();
        }
    }

    public static void stop() {
        boolean wasActive = active || window != null;
        active = false;
        if (window != null) {
            try {
                window.close();
            } catch (Throwable t) {
                LOG.warn("[WindowX] failed to close camera window", t);
            }
            window = null;
        }
        try {
            CameraRegistry.unregister(CAMERA_OWNER);
        } catch (Throwable t) {
            LOG.warn("[WindowX] failed to unregister camera", t);
        }
        if (wasActive) {
            LOG.info("[WindowX] orthographic camera stopped");
            notifyPlayer("已退出侧视摄像机");
        }
    }

    /** WindowX 每帧调用；返回 null 时窗口会黑屏，因此保留上一帧姿态兜底。 */
    private static CameraRegistry.Pose locate(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return lastPose;
        }

        // 吸附到最近的 90°：P0 只验证四个正交侧视方向，不做连续旋转。
        float yaw = Math.round(player.getYRot() / 90.0f) * 90.0f;
        double lookX = -Math.sin(Math.toRadians(yaw));
        double lookZ = Math.cos(Math.toRadians(yaw));

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 pos = eye.add(-lookX * CAMERA_DISTANCE, CAMERA_HEIGHT, -lookZ * CAMERA_DISTANCE);

        lastPose = new CameraRegistry.Pose(pos, yaw, 0.0f, new Quaternionf());
        return lastPose;
    }

    private static void notifyPlayer(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }
}
