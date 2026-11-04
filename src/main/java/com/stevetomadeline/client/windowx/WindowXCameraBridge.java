package com.stevetomadeline.client.windowx;

import com.mojang.logging.LogUtils;
import com.stevetomadeline.Config;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/**
 * P0 技术验证：用 WindowX 公开 API 建一个跟随玩家的正交侧视摄像机窗口。
 *
 * <p>WindowX 在 ORTHOGRAPHIC 模式下把 {@link CameraOptions#fov()} 直接当作正交半高
 * （见 CameraRenderManager 里的 {@code Matrix4f.ortho}），单位是方块，而不是角度。
 * 因此“远近”由配置项的 orthoHalfHeight 控制，而不是摄像机距离。
 *
 * <p>摄像机保持固定距离，不再因墙面向玩家收缩；挡在前面的几何体由
 * {@code CameraRenderManagerMixin} 抬高近裁剪面切掉（见 foregroundCut）。
 */
public final class WindowXCameraBridge {
    private static final Logger LOG = LogUtils.getLogger();

    private static final WindowId WINDOW_ID = WindowId.of("stevetomadeline", "camera");
    public static final String CAMERA_ID = "stevetomadeline:follow";
    private static final Object CAMERA_OWNER = new Object();

    private static final int TEXTURE_WIDTH = 960;
    private static final int TEXTURE_HEIGHT = 540;
    private static final float RENDER_DISTANCE = 32.0f;

    private static ClientWindow window;
    private static CameraRegistry.Pose lastPose;
    private static boolean active;
    private static float appliedHalfHeight = Float.NaN;

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
            applyCameraOptions(currentHalfHeight());

            active = true;
            LOG.info("[WindowX] orthographic camera started (id={}, halfHeight={})", CAMERA_ID, appliedHalfHeight);
            notifyPlayer("侧视摄像机已启动（R 退出）");
        } catch (Throwable t) {
            LOG.error("[WindowX] failed to start camera", t);
            notifyPlayer("侧视摄像机启动失败，详见日志");
            stop();
        }
    }

    /** 每客户端 tick 调用：让配置滑块的改动立即生效。 */
    public static void tick() {
        if (!active || window == null) {
            return;
        }
        try {
            float halfHeight = currentHalfHeight();
            if (halfHeight != appliedHalfHeight) {
                applyCameraOptions(halfHeight);
                LOG.info("[WindowX] ortho half height -> {}", halfHeight);
            }
        } catch (Throwable t) {
            LOG.warn("[WindowX] failed to apply camera options", t);
        }
    }

    public static void stop() {
        boolean wasActive = active || window != null;
        active = false;
        appliedHalfHeight = Float.NaN;
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

    private static void applyCameraOptions(float halfHeight) {
        window.configureCamera(new CameraOptions(
                0.0, 0.0, halfHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT,
                1, RENDER_DISTANCE,
                CameraProjection.ORTHOGRAPHIC));
        appliedHalfHeight = halfHeight;
    }

    /** 最近一帧摄像机姿态，供渲染附加层（雾、背景板）读取。 */
    public static CameraRegistry.Pose lastPose() {
        return lastPose;
    }

    /** 摄像机当前是否嵌在实体方块里；是则返回该方块，用于绘制裁切背景。 */
    public static BlockState embeddedBlockState() {
        Minecraft mc = Minecraft.getInstance();
        CameraRegistry.Pose pose = lastPose;
        if (pose == null || mc.level == null) {
            return null;
        }
        BlockPos pos = BlockPos.containing(pose.position());
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || !state.canOcclude()) {
            return null;
        }
        return state;
    }

    private static float currentHalfHeight() {
        return (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
    }

    private static double currentDistance() {
        return Config.CAMERA_DISTANCE.get();
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

        Vec3 eye = player.getEyePosition(partialTick);
        double lookX = -Math.sin(Math.toRadians(yaw));
        double lookZ = Math.cos(Math.toRadians(yaw));
        Vec3 pos = eye.add(-lookX * currentDistance(), 0.0, -lookZ * currentDistance());

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
