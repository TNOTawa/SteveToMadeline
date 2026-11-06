package com.stevetomadeline;

import com.mojang.logging.LogUtils;
import com.stevetomadeline.network.PlaneNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * SteveToMadeline 主类。
 *
 * <p>P1 的平面状态与协议属于公共逻辑；图形和用户选项只在客户端入口注册。
 */
@Mod(SteveToMadeline.MODID)
public final class SteveToMadeline {
    public static final String MODID = "stevetomadeline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SteveToMadeline(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, PhysicsConfig.SPEC);
        modEventBus.addListener(PlaneNetwork::register);
        LOGGER.info("SteveToMadeline loaded");
    }
}
