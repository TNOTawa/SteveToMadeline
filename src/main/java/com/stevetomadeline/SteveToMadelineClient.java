package com.stevetomadeline;

import com.stevetomadeline.client.SteveToMadelineKeys;
import com.stevetomadeline.client.ClientPlaneController;
import com.stevetomadeline.client.ClientPhysicsController;
import com.stevetomadeline.client.PhysicsHud;
import com.stevetomadeline.client.input.GamepadInput;
import com.stevetomadeline.client.input.PhysicsKeys;
import com.stevetomadeline.client.input.MadelineSettingsScreen;
import com.stevetomadeline.client.render.MadelineRenderer;
import com.stevetomadeline.client.assets.CelesteAssets;
import com.stevetomadeline.client.audio.CelesteAudio;
import com.stevetomadeline.core.physics.PhysicsController;
import com.stevetomadeline.network.PlaneNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
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
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new MadelineSettingsScreen(parent));
        modEventBus.addListener(SteveToMadelineKeys::register);
        modEventBus.addListener(PhysicsHud::register);
        NeoForge.EVENT_BUS.addListener(PhysicsKeys::key);
        NeoForge.EVENT_BUS.addListener(PhysicsKeys::mouse);
        NeoForge.EVENT_BUS.addListener(PhysicsKeys::interaction);
        NeoForge.EVENT_BUS.addListener(MadelineRenderer::render);
        NeoForge.EVENT_BUS.addListener(SteveToMadelineClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SteveToMadelineClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(SteveToMadelineClient::onPlayerClone);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> {
            CelesteAudio.tick();
            GamepadInput.poll();
        });
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> CelesteAudio.close());
        NeoForge.EVENT_BUS.addListener(ClientPlaneController::updateInput);
        PlaneNetwork.setClientHandler(ClientPlaneController::receive);
        PlaneNetwork.setPhysicsHandler(ClientPhysicsController::receive);
        PhysicsController.setClientTravel(ClientPhysicsController::travel);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientPlaneController.tick();
        Minecraft mc = Minecraft.getInstance();
        boolean inGame = mc.player != null && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive();
        if (!inGame || ClientPlaneController.plane() == null) PhysicsKeys.clearClicks();
        while (SteveToMadelineKeys.OPEN_SETTINGS.consumeClick()) if (inGame) mc.setScreen(new MadelineSettingsScreen(null));
        if (mc.player != null) CelesteAssets.ensure();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CelesteAudio.close();
        GamepadInput.reset();
        ClientPlaneController.reset();
        MadelineRenderer.reset();
    }

    private static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        GamepadInput.reset();
        ClientPlaneController.reset();
        MadelineRenderer.reset();
    }
}
