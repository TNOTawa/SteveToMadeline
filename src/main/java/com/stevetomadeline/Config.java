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
            .comment("侧视摄像机与眼睛的水平距离（方块）。正交投影下它不改变缩放，只决定相机站位与近处裁剪。")
            .defineInRange("cameraDistance", 8.0, 0.5, 12.0);

    public static final ModConfigSpec.DoubleValue ORTHO_HALF_HEIGHT = BUILDER
            .comment("正交视图的半高（方块，屏幕上垂直方向可见世界高度的一半）。数值越小画面越近。")
            .defineInRange("orthoHalfHeight", 10.0, 2.0, 30.0);

    public static final ModConfigSpec.DoubleValue FOREGROUND_CUT = BUILDER
            .comment("前景切片距离（方块）：相机到该距离内的所有几何体都不绘制，用来让挡在镜头与玩家之间的墙透明。0 表示关闭。")
            .defineInRange("foregroundCut", 6.0, 0.0, 12.0);

    public static final ModConfigSpec.DoubleValue FOG_START = BUILDER
            .comment("雾开始距离（方块）。用于把远处背景和近处前景区分开。")
            .defineInRange("fogStart", 12.0, 0.0, 256.0);

    public static final ModConfigSpec.DoubleValue FOG_END = BUILDER
            .comment("雾完全遮蔽距离（方块）。小于等于开始距离时不做雾处理。")
            .defineInRange("fogEnd", 64.0, 4.0, 512.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
