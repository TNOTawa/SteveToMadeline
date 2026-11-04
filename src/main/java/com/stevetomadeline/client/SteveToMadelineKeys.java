package com.stevetomadeline.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 客户端按键。R 目前只切换 P0 的侧视摄像机原型，P1 会扩展为完整的 Celeste 模式开关。
 */
public final class SteveToMadelineKeys {
    public static final String CATEGORY = "key.categories.stevetomadeline";

    public static final KeyMapping TOGGLE_CELESTE_MODE = new KeyMapping(
            "key.stevetomadeline.toggle_celeste_mode",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY);

    private SteveToMadelineKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_CELESTE_MODE);
    }
}
