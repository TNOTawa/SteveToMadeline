package com.stevetomadeline;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 仅客户端入口。WindowX 摄像机、玛德琳渲染、Celeste 资源加载都只允许挂在这里。
 */
@Mod(value = SteveToMadeline.MODID, dist = Dist.CLIENT)
public final class SteveToMadelineClient {
    public SteveToMadelineClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
