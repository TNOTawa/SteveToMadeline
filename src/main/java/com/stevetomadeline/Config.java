package com.stevetomadeline;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 公共配置。真正的摄像机/物理参数在 P0/P2 校验后再逐项加入，避免先造出无人使用的配置项。
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CELESTE_MODE_ENABLED = BUILDER
            .comment("Celeste 模式总开关（P0 占位，P1 起真正生效）")
            .define("celesteModeEnabled", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
