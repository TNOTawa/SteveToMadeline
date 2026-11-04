package com.stevetomadeline;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端可调参数。数值范围会由 NeoForge 的配置界面渲染成滑块。
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CELESTE_MODE_ENABLED = BUILDER
            .comment("Celeste 模式总开关（P0 占位，P1 起真正生效）")
            .define("celesteModeEnabled", true);

    public static final ModConfigSpec.DoubleValue CAMERA_DISTANCE = BUILDER
            .comment("侧视摄像机与眼睛的水平距离（方块）。正交投影下它不改变缩放，只决定相机站位与切片范围。")
            .defineInRange("cameraDistance", 8.0, 0.5, 128.0);

    public static final ModConfigSpec.DoubleValue ORTHO_HALF_HEIGHT = BUILDER
            .comment("正交视图的半高（方块，屏幕上垂直方向可见世界高度的一半）。数值越小画面越近。")
            .defineInRange("orthoHalfHeight", 10.0, 2.0, 30.0);

    public static final ModConfigSpec.DoubleValue SLICE_HALF_THICKNESS = BUILDER
            .comment("2D 模式的切片半厚度（方块）：只渲染玩家所在轴前后各该距离内的方块与实体。")
            .defineInRange("sliceHalfThickness", 1.0, 0.25, 2.5);

    public static final ModConfigSpec.BooleanValue LAYERED_PARALLAX = BUILDER
            .comment("分层视差：远景用滞后相机单独渲染到半分辨率缓冲，前景保持 100% 跟随。")
            .define("layeredParallax", false);

    public static final ModConfigSpec.DoubleValue PARALLAX_RATIO = BUILDER
            .comment("远景跟随比例（0.2-0.8）。越小纵深越强，但远景偏移越大。")
            .defineInRange("parallaxRatio", 0.4, 0.2, 0.8);

    public static final ModConfigSpec.DoubleValue PARALLAX_STRENGTH = BUILDER
            .comment("正交伪视差强度：前景层随移动速度产生的横向超前量。0 = 关闭。")
            .defineInRange("parallaxStrength", 0.0, 0.0, 8.0);

    public static final ModConfigSpec.BooleanValue ENABLE_ISOMETRIC = BUILDER
            .comment("启用 2.5D 模式（彩蛋）。关闭时 R 只在原版与 2D 之间切换。")
            .define("enableIsometric", false);

    public static final ModConfigSpec.DoubleValue CAMERA_FOV = BUILDER
            .comment("摄像机视野角（度）。0 = 严格正交；20-45 之间是轻微透视，会有前后景视差。")
            .defineInRange("cameraFov", 0.0, 0.0, 110.0);

    public static final ModConfigSpec.DoubleValue FOG_START = BUILDER
            .comment("雾开始距离（方块）。")
            .defineInRange("fogStart", 96.0, 0.0, 512.0);

    public static final ModConfigSpec.DoubleValue FOG_END = BUILDER
            .comment("雾完全遮蔽距离（方块）。小于等于开始距离时不做雾处理。")
            .defineInRange("fogEnd", 448.0, 16.0, 512.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
