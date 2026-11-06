package com.stevetomadeline.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * 游戏内 R 进入/退出，Tab 顺时针切换运动平面；GUI 不消费这些请求。
 */
public final class SteveToMadelineKeys {
    public static final String CATEGORY = "key.categories.stevetomadeline";
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping("key.stevetomadeline.settings", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_F8, CATEGORY);

    private SteveToMadelineKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        com.stevetomadeline.client.input.ControlBindings.initialize();
        event.register(OPEN_SETTINGS);
    }
}
