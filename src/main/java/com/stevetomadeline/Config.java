package com.stevetomadeline;

import com.stevetomadeline.core.plane.EntryOrientation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端可调参数。数值范围会由 NeoForge 的配置界面渲染成滑块。
 */
public final class Config {
    public enum ParallaxMode { DEPTH, LAYERED }
    public enum TerrainClippingMode { ADAPTIVE, SLICE, NONE }

    private static final double DEFAULT_CAMERA_DISTANCE = 8.0;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CELESTE_MODE_ENABLED = BUILDER
            .comment("Celeste 模式总开关。关闭时退出模式并恢复原版控制。")
            .define("celesteModeEnabled", true);

    public static final ModConfigSpec.EnumValue<EntryOrientation> ENTRY_ORIENTATION = BUILDER
            .comment("R 进入时的选平面规则。FACING_RIGHT：进入前面向的方向成为屏幕右侧；FACING_LEFT 为左侧；",
                    "CAMERA_FORWARD 保持旧相机朝向规则；SOUTH/WEST/NORTH/EAST 可固定相机朝向。")
            .defineEnum("entryOrientation", EntryOrientation.FACING_RIGHT);

    public static final ModConfigSpec.DoubleValue TURN_DURATION = BUILDER
            .comment("Tab 顺时针转向的摄像机缓动时长（秒）。逻辑平面立即切换，转动中暂不接受新的横向输入。")
            .defineInRange("turnDuration", 0.3, 0.1, 1.0);

    public static final ModConfigSpec.DoubleValue ORTHO_HALF_HEIGHT = BUILDER
            .comment("玩家平面视图半高（方块）。正交与 DEPTH 模式在玩家平面保持相同缩放；数值越小画面越近。")
            .defineInRange("orthoHalfHeight", 10.0, 2.0, 30.0);

    public static final ModConfigSpec.DoubleValue SLICE_HALF_THICKNESS = BUILDER
            .comment("2D 模式的切片半厚度（方块）：只渲染玩家所在轴前后各该距离内的方块与实体。")
            .defineInRange("sliceHalfThickness", 1.0, 0.25, 2.5);

    public static final ModConfigSpec.DoubleValue DEPTH_DISTANCE = BUILDER
            .comment("连续景深的参考距离（方块）。背景距离玩家 d 格时尺寸约为 D/(D+d)；越小远景越小，玩家尺寸不变。")
            .defineInRange("depthDistance", 32.0, 16.0, 256.0);

    public static final ModConfigSpec.BooleanValue CAMERA_COLLISION = BUILDER
            .comment("摄像头自动向玩家收近避障；忽略透明/装饰方块，半砖和楼梯按实际占用形状检测。")
            .define("cameraCollision", true);

    public static final ModConfigSpec.BooleanValue NEAR_WALL_SLICE = BUILDER
            .comment("固定侧视下空间不足 3 格时保留镜头距离，淡出前景并补绘切片。默认开启。",
                    "关闭后同时停用缓动过程的自动切片。保留旧配置键以兼容已保存的选择。")
            .define("experimentalNearWallSlice", true);

    public static final ModConfigSpec.BooleanValue LEGACY_SKY_FOG = BUILDER
            .comment("旧纯 2D 天空色/雾覆盖。默认关闭，保留原版天空与雾。")
            .define("legacySkyFog", false);

    public static final ModConfigSpec.BooleanValue HIDE_OFF_PLANE_ENTITIES = BUILDER
            .comment("旧纯 2D 平面外实体隐藏。默认关闭，正常显示生物和其他实体。")
            .define("hideOffPlaneEntities", false);

    public static final ModConfigSpec.BooleanValue FOREGROUND_SLICE = BUILDER
            .comment("旧纯 2D 前景切片与贴面补绘。默认关闭，保留完整地形；开启后使用裁切策略。")
            .define("foregroundSlice", false);

    public static final ModConfigSpec.DoubleValue FOG_START = BUILDER
            .comment("雾开始距离（方块）。")
            .defineInRange("fogStart", 96.0, 0.0, 512.0);

    public static final ModConfigSpec.DoubleValue FOG_END = BUILDER
            .comment("雾完全遮蔽距离（方块）。小于等于开始距离时不做雾处理。")
            .defineInRange("fogEnd", 448.0, 16.0, 512.0);

    // Keep legacy keys at the root so existing configuration values are not lost.
    public static final ModConfigSpec.BooleanValue EXPERIMENTAL_RENDERING = BUILDER
            .comment("启用实验性渲染选项。默认关闭：使用 DEPTH，R 只在原版与 2D 之间切换。",
                    "关闭时忽略下方实验参数，但保留其配置值；正常缩放、景深距离、切片厚度和雾距离仍可调整。")
            .define("experimentalRendering", false);

    public static final ModConfigSpec.DoubleValue CAMERA_DISTANCE = BUILDER
            .comment("[实验] 相机站位距离覆盖。仅 experimentalRendering = true 时生效；默认站位基准为 8 格。")
            .defineInRange("cameraDistance", DEFAULT_CAMERA_DISTANCE, 0.5, 128.0);

    public static final ModConfigSpec.BooleanValue LAYERED_PARALLAX = BUILDER
            .comment("[实验] 2D 深度效果开关。关闭可回退旧正交模式；仅 experimentalRendering = true 时允许覆盖默认开启状态。")
            .define("layeredParallax", true);

    public static final ModConfigSpec.EnumValue<ParallaxMode> PARALLAX_MODE = BUILDER
            .comment("[实验] DEPTH 为当前默认连续景深，LAYERED 为旧版正交离屏分层，需要安装 WindowX；未安装时回退 DEPTH。仅 experimentalRendering = true 时允许切换。")
            .defineEnum("parallaxMode", ParallaxMode.DEPTH);

    public static final ModConfigSpec.EnumValue<TerrainClippingMode> TERRAIN_CLIPPING = BUILDER
            .comment("[实验] ADAPTIVE 为当前默认。SLICE 强制切片，NONE 不切片；仅 experimentalRendering = true 时允许覆盖。",
                    "自动判断不是建筑语义识别：纯天然材料的实心建筑仍可能被识别为地形；混合区域优先保留，可能遮挡玩家。")
            .defineEnum("terrainClipping", TerrainClippingMode.ADAPTIVE);

    public static final ModConfigSpec.DoubleValue PARALLAX_RATIO = BUILDER
            .comment("[实验] 仅开启实验选项且使用 LAYERED 时生效。远景水平跟随比例（0.2-0.8），偏移最多 32 格。")
            .defineInRange("parallaxRatio", 0.4, 0.2, 0.8);

    public static final ModConfigSpec.DoubleValue PARALLAX_STRENGTH = BUILDER
            .comment("[实验] 旧版速度伪视差强度。仅开启实验选项并关闭深度效果时生效；默认 0。")
            .defineInRange("parallaxStrength", 0.0, 0.0, 8.0);

    public static final ModConfigSpec.BooleanValue ENABLE_ISOMETRIC = BUILDER
            .comment("[实验] 模式内使用 2.5D 下俯视角，必须先开启 experimentalRendering。R 始终进入/退出，默认关闭。")
            .define("enableIsometric", false);

    public static final ModConfigSpec.DoubleValue CAMERA_FOV = BUILDER
            .comment("[实验] 手动 FOV 覆盖。仅 experimentalRendering = true 时生效；0 使用自动投影，>0.5 跳过 2D 深度效果。")
            .defineInRange("cameraFov", 0.0, 0.0, 110.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static boolean depthEffectEnabled() {
        return !EXPERIMENTAL_RENDERING.get() || LAYERED_PARALLAX.get();
    }

    public static ParallaxMode parallaxMode() {
        return EXPERIMENTAL_RENDERING.get() ? PARALLAX_MODE.get() : ParallaxMode.DEPTH;
    }

    public static TerrainClippingMode terrainClippingMode() {
        if (!FOREGROUND_SLICE.get()) return TerrainClippingMode.NONE;
        return EXPERIMENTAL_RENDERING.get() ? TERRAIN_CLIPPING.get() : TerrainClippingMode.ADAPTIVE;
    }

    public static double cameraDistance() {
        return EXPERIMENTAL_RENDERING.get() ? CAMERA_DISTANCE.get() : DEFAULT_CAMERA_DISTANCE;
    }

    public static double cameraFov() {
        return EXPERIMENTAL_RENDERING.get() ? CAMERA_FOV.get() : 0.0;
    }

    public static double parallaxStrength() {
        return EXPERIMENTAL_RENDERING.get() ? PARALLAX_STRENGTH.get() : 0.0;
    }

    public static boolean isometricEnabled() {
        return EXPERIMENTAL_RENDERING.get() && ENABLE_ISOMETRIC.get();
    }
}
