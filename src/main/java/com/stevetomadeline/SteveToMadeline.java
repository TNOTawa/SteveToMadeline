package com.stevetomadeline;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * SteveToMadeline 主类。
 *
 * <p>工程当前处于 P0：主类只做加载入口；摄像机参数属于纯客户端配置，
 * 在 {@link SteveToMadelineClient} 里以 CLIENT 类型注册，避免联机时被服务端配置覆盖。
 * Celeste 模式、平面切换与物理状态机按计划书在 {@code core.mode} / {@code core.plane}
 * / {@code core.physics} 中逐步接入。
 */
@Mod(SteveToMadeline.MODID)
public final class SteveToMadeline {
    public static final String MODID = "stevetomadeline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SteveToMadeline(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("SteveToMadeline loaded");
    }
}
