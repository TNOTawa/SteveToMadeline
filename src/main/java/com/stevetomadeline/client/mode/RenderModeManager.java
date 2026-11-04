package com.stevetomadeline.client.mode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * R 键的三态循环：原版 → 2D 平面 → 2.5D（侧视下俯 12°）→ 原版。
 *
 * <p>两种摄像机模式都直接接管主游戏窗口的摄像机与投影，不再创建 WindowX 独立窗口。
 */
public final class RenderModeManager {
    public enum Mode {
        VANILLA("原版"),
        FLAT_2D("2D 平面"),
        ISOMETRIC("2.5D");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static Mode mode = Mode.VANILLA;

    private RenderModeManager() {
    }

    public static Mode mode() {
        return mode;
    }

    public static boolean isCameraMode() {
        return mode != Mode.VANILLA;
    }

    public static boolean isFlat2D() {
        return mode == Mode.FLAT_2D;
    }

    public static boolean isIsometric() {
        return mode == Mode.ISOMETRIC;
    }

    /** 退出世界时复位。 */
    public static void reset() {
        mode = Mode.VANILLA;
    }

    public static void cycle() {
        boolean isometric = com.stevetomadeline.Config.ENABLE_ISOMETRIC.get();
        switch (mode) {
            case VANILLA -> switchTo(Mode.FLAT_2D);
            case FLAT_2D -> switchTo(isometric ? Mode.ISOMETRIC : Mode.VANILLA);
            case ISOMETRIC -> switchTo(Mode.VANILLA);
        }
    }

    private static void switchTo(Mode next) {
        mode = next;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("SteveToMadeline: " + next.label()), true);
        }
    }
}
