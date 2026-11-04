package com.stevetomadeline;

import com.stevetomadeline.client.SteveToMadelineKeys;
import com.stevetomadeline.client.mode.RenderModeManager;
import com.stevetomadeline.client.windowx.WindowXCameraBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 仅客户端入口。WindowX 摄像机、玛德琳渲染、Celeste 资源加载都只允许挂在这里。
 */
@Mod(value = SteveToMadeline.MODID, dist = Dist.CLIENT)
public final class SteveToMadelineClient {
    public SteveToMadelineClient(ModContainer container, IEventBus modEventBus) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(SteveToMadelineKeys::register);
        NeoForge.EVENT_BUS.addListener(SteveToMadelineClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SteveToMadelineClient::onLoggingOut);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (SteveToMadelineKeys.TOGGLE_CELESTE_MODE.consumeClick()) {
            RenderModeManager.cycle();
        }
        WindowXCameraBridge.tick();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WindowXCameraBridge.stop();
        RenderModeManager.reset();
    }
}
