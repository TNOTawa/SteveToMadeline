package com.stevetomadeline.client.windowx;

import com.mojang.logging.LogUtils;
import com.stevetomadeline.Config;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.xutrain.windowx.api.WindowXClientApi;
import com.xutrain.windowx.api.window.CameraOptions;
import com.xutrain.windowx.api.window.CameraProjection;
import com.xutrain.windowx.api.window.ClientWindow;
import com.xutrain.windowx.api.window.WindowDisplayMode;
import com.xutrain.windowx.api.window.WindowId;
import com.xutrain.windowx.api.window.WindowOptions;
import com.xutrain.windowx.camera.CameraRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/**
 * 用 WindowX 公开 API 建一个跟随玩家的正交摄像机窗口，供 2D / 2.5D 两种模式共用。
 *
 * <p>WindowX 在 ORTHOGRAPHIC 模式下把 {@link CameraOptions#fov()} 直接当作正交半高
 * （见 CameraRenderManager 里的 {@code Matrix4f.ortho}），单位是方块而非角度。
 *
 * <p>2D 模式：相机与玩家等高、pitch=0，由 mixin 把近/远裁剪面收成一层切片；
 * 2.5D 模式：相机抬高并下俯 12°，能看到方块顶面，但仍按横版侧视取景；
 * 相机位置会沿视线做方块射线检测，避免整个镜头埋进山体里。
 */
public final class WindowXCameraBridge {
    private static final Logger LOG = LogUtils.getLogger();

    private static final WindowId WINDOW_ID = WindowId.of("stevetomadeline", "camera");
    public static final String CAMERA_ID = "stevetomadeline:follow";
    private static final Object CAMERA_OWNER = new Object();

    private static final int TEXTURE_WIDTH = 960;
    private static final int TEXTURE_HEIGHT = 540;
    private static final float RENDER_DISTANCE = 32.0f;
    private static final double CAMERA_CLEARANCE = 0.3;

    private static ClientWindow window;
    private static volatile CameraRegistry.Pose lastPose;
    private static boolean active;
    private static float appliedHalfHeight = Float.NaN;

    private WindowXCameraBridge() {
    }

    public static boolean isActive() {
        return active;
    }

    /** 最近一帧的相机姿态；渲染附加层用它换算相机相对坐标。 */
    public static CameraRegistry.Pose lastPose() {
        return lastPose;
    }

    public static void start() {
        if (active) {
            return;
        }
        if (!WindowXClientApi.isAvailable()) {
            LOG.warn("[WindowX] API unavailable - is WindowX installed?");
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
            LOG.info("[WindowX] camera started (id={}, mode={})", CAMERA_ID, RenderModeManager.mode());
        } catch (Throwable t) {
            LOG.error("[WindowX] failed to start camera", t);
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
        active = false;
        appliedHalfHeight = Float.NaN;
        lastPose = null;
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
    }

    private static void applyCameraOptions(float halfHeight) {
        window.configureCamera(new CameraOptions(
                0.0, 0.0, halfHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT,
                1, RENDER_DISTANCE,
                CameraProjection.ORTHOGRAPHIC));
        appliedHalfHeight = halfHeight;
    }

    private static float currentHalfHeight() {
        return (float) Config.ORTHO_HALF_HEIGHT.get().doubleValue();
    }

    private static double currentDistance() {
        return Config.CAMERA_DISTANCE.get();
    }

    /** WindowX 每帧调用。 */
    private static CameraRegistry.Pose locate(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return lastPose;
        }

        float yaw = Math.round(player.getYRot() / 90.0f) * 90.0f;

        float pitch = 0.0f;
        double upOffset = 0.0;
        if (RenderModeManager.isIsometric()) {
            // 下俯 12°，相机相应抬高，让玩家保持在画面中心。
            pitch = 12.0f;
            upOffset = Math.tan(Math.toRadians(pitch)) * currentDistance();
        }

        Vec3 eye = player.getEyePosition(partialTick);
        double lookX = -Math.sin(Math.toRadians(yaw));
        double lookZ = Math.cos(Math.toRadians(yaw));
        Vec3 desired = eye.add(-lookX * currentDistance(), upOffset, -lookZ * currentDistance());

        if (RenderModeManager.isIsometric()) {
            desired = clampOutOfBlocks(mc.level, player, eye, desired);
        }

        lastPose = new CameraRegistry.Pose(desired, yaw, pitch, new Quaternionf());
        return lastPose;
    }

    /** 沿眼睛到目标位置的连线做方块检测，撞墙时把相机停在墙前，避免镜头埋进山体看到天空。 */
    private static Vec3 clampOutOfBlocks(ClientLevel level, LocalPlayer player, Vec3 eye, Vec3 desired) {
        BlockHitResult hit = level.clip(new ClipContext(
                eye, desired,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        if (hit.getType() == HitResult.Type.MISS) {
            return desired;
        }
        Vec3 offset = desired.subtract(eye);
        double length = offset.length();
        if (length < 0.001) {
            return eye;
        }
        double available = eye.distanceTo(hit.getLocation()) - CAMERA_CLEARANCE;
        double scale = Math.max(0.02, Math.min(1.0, available / length));
        return eye.add(offset.scale(scale));
    }
}
