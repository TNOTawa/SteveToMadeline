package com.stevetomadeline;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * SteveToMadeline 主类。
 *
 * <p>P0 阶段只负责装配配置与生命周期入口，Celeste 模式、平面切换与物理状态机按计划书
 * 的模块表在 {@code core.mode} / {@code core.plane} / {@code core.physics} 中逐步接入。
 */
@Mod(SteveToMadeline.MODID)
public final class SteveToMadeline {
    public static final String MODID = "stevetomadeline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SteveToMadeline(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("SteveToMadeline loaded");
    }
}
