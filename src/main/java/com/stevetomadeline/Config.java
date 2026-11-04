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
            .comment("侧视摄像机与眼睛的水平距离（方块）。只影响取景位置与遮挡，不影响正交缩放。")
            .defineInRange("cameraDistance", 2.0, 0.5, 8.0);

    public static final ModConfigSpec.DoubleValue ORTHO_HALF_HEIGHT = BUILDER
            .comment("正交视图的半高（方块，即屏幕上看到垂直方向的一半世界高度）。数值越小画面越近。")
            .defineInRange("orthoHalfHeight", 7.0, 2.0, 30.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
