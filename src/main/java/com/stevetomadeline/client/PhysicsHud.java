package com.stevetomadeline.client;

import com.stevetomadeline.SteveToMadeline;
import com.stevetomadeline.core.physics.PhysicsController;
import com.stevetomadeline.client.input.ControlBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class PhysicsHud {
    private PhysicsHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SteveToMadeline.MODID, "movement"), (gui, timer) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || !PhysicsController.active(mc.player)) return;
            var options = ControlBindings.presentation();
            if (!options.showStamina() && !options.showDashes()) return;
            var session = PhysicsController.session(mc.player);
            var state = session.state;
            int x = 8, y = gui.guiHeight() - 33;
            if (options.showDashes()) gui.drawString(mc.font, Component.translatable("stevetomadeline.hud.dashes", state.dashes, session.parameters.maxDashes()), x, y, 0xFFFFFF);
            if (options.showStamina()) {
                gui.fill(x, y + 13, x + 82, y + 20, 0xC0000000);
                gui.fill(x + 1, y + 14, x + 1 + (int) Math.round(80 * Math.clamp(state.stamina / 110, 0, 1)), y + 19,
                        state.stamina > 20 ? 0xFF64D58B : 0xFFE56B6F);
                gui.drawString(mc.font, Component.translatable("stevetomadeline.hud.stamina"), x + 88, y + 12, 0xFFFFFF);
            }
        });
    }
}
