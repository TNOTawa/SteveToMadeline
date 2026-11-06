package com.stevetomadeline.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.stevetomadeline.client.assets.CelesteAssets;
import com.stevetomadeline.client.windowx.WindowXCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class MadelineSettingsScreen extends Screen {
    private static final String[] TABS = {"settings.controls", "settings.gamepad", "settings.character",
            "settings.effects", "settings.hud", "settings.camera"};
    private final Screen parent;
    private int page, padPage, tab, rows, padRows, start, contentWidth;
    private ControlBindings.Action listening;
    private int listeningSlot;
    private boolean listeningGamepad;
    private EditBox path;
    public MadelineSettingsScreen(Screen parent) { super(Component.translatable("stevetomadeline.settings.title")); this.parent = parent; }
    private static Component text(String key) { return Component.translatable("stevetomadeline." + key); }
    private Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, 20).build());
    }
    private int labelWidth() { return Math.min(140, contentWidth * 2 / 5); }
    private void stopListening() { listening = null; GamepadInput.endCapture(); }
    @Override protected void init() {
        CelesteAssets.ensure();
        contentWidth = Math.min(440, width - 24); start = (width - contentWidth) / 2;
        for (int index = 0; index < TABS.length; index++) {
            int current = index;
            int x = start + contentWidth * index / TABS.length;
            int w = contentWidth * (index + 1) / TABS.length - contentWidth * index / TABS.length - (index < TABS.length - 1 ? 2 : 0);
            Component tabLabel = text(TABS[index]);
            Button entry = button(Component.literal(font.plainSubstrByWidth(tabLabel.getString(), w - 8)), x, 30, w, () -> {
                if (current == TABS.length - 1) {
                    ModList.get().getModContainerById("stevetomadeline").ifPresent(container -> minecraft.setScreen(
                            new ConfigurationScreen(container, this, (context, key, element) ->
                                    showCameraOption(key, WindowXCompat.available()) ? element : null)));
                    return;
                }
                tab = current; stopListening(); rebuildWidgets();
            });
            entry.active = current == TABS.length - 1 || tab != current;
            entry.setTooltip(Tooltip.create(tabLabel));
            if (current == 1) entry.setTooltip(Tooltip.create(text("gamepad.hint")));
        }
        if (tab == 0) controls(); else if (tab == 1) gamepad(); else if (tab == 2) character(); else if (tab == 3) effects();
        else if (tab == 4) hud();
        button(Component.translatable("gui.done"), width / 2 - 70, height - 26, 140, this::onClose);
    }
    static boolean showCameraOption(String key, boolean windowXAvailable) {
        return windowXAvailable || (!key.equals("parallaxMode") && !key.equals("parallaxRatio"));
    }

    private void controls() {
        int modeLabelWidth = Math.min(150, contentWidth * 2 / 5);
        int modeWidth = (contentWidth - modeLabelWidth) / 3;
        for (GrabInput.Mode mode : GrabInput.Mode.values()) {
            Button selected = button(text("controls.grab." + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    start + modeLabelWidth + mode.ordinal() * modeWidth, 59, modeWidth - 2, () -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(mode, old.soundEnabled(), old.soundVolume(), old.effectsEnabled(), old.showStamina(), old.showDashes()));
                    rebuildWidgets();
                });
            selected.active = mode != ControlBindings.presentation().grabMode();
        }
        rows = Math.max(1, (height - 154) / 23);
        int count = ControlBindings.Action.values().length, pages = (count + rows - 1) / rows;
        page = Math.min(page, pages - 1);
        int labelWidth = labelWidth(), keyWidth = (contentWidth - labelWidth) / ControlBindings.SLOTS;
        for (int row = 0; row < rows && page * rows + row < count; row++) {
            var action = ControlBindings.Action.values()[page * rows + row];
            for (int slot = 0; slot < ControlBindings.SLOTS; slot++) {
                final int index = slot;
                Component name = listening(action, slot, false) ? text("controls.listening")
                        : ControlBindings.name(ControlBindings.get(action, false, slot));
                String shown = font.plainSubstrByWidth(name.getString(), keyWidth - 25);
                Button button = button(Component.literal(shown).withColor(ControlBindings.conflict(action, false, slot) ? 0xFFC46A : 0xFFFFFF),
                        start + labelWidth + slot * keyWidth, 90 + row * 23, keyWidth - 19,
                        () -> listen(action, index, false));
                Component tooltip = action.label().copy().append("\n").append(name);
                button.setTooltip(Tooltip.create(ControlBindings.conflict(action, false, slot)
                        ? tooltip.copy().append("\n").append(text("controls.conflict")) : tooltip));
                button(Component.literal("x"), start + labelWidth + (slot + 1) * keyWidth - 18, 90 + row * 23, 16,
                        () -> { ControlBindings.set(action, false, index, ""); stopListening(); rebuildWidgets(); })
                        .setTooltip(Tooltip.create(text("controls.clear")));
            }
        }
        button(Component.literal("<"), start, height - 54, 24,
                () -> { page--; stopListening(); rebuildWidgets(); }).active = page > 0;
        button(Component.literal(">"), start + 28, height - 54, 24,
                () -> { page++; stopListening(); rebuildWidgets(); }).active = page + 1 < pages;
        button(text("controls.reset.keyboard"), start + contentWidth - 120, height - 54, 120,
                () -> { ControlBindings.defaults(false); stopListening(); rebuildWidgets(); })
                .setTooltip(Tooltip.create(text("controls.defaults.keyboard")));
    }

    private void gamepad() {
        GamepadOptions options = ControlBindings.gamepad();
        addRenderableWidget(Checkbox.builder(text("gamepad.enabled"), font).pos(start, 59).maxWidth(contentWidth - 120)
                .selected(options.enabled()).onValueChange((checkbox, enabled) -> {
                    GamepadOptions old = ControlBindings.gamepad();
                    ControlBindings.gamepad(new GamepadOptions(enabled, old.deadzone()));
                    stopListening(); rebuildWidgets();
                }).build());
        button(text("gamepad.rescan"), start + contentWidth - 110, 59, 110, GamepadInput::rescan);
        addRenderableWidget(new AbstractSliderButton(start, 84, contentWidth, 20, Component.empty(),
                (options.deadzone() - GamepadOptions.MIN_DEADZONE) / (GamepadOptions.MAX_DEADZONE - GamepadOptions.MIN_DEADZONE)) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.translatable("stevetomadeline.gamepad.deadzone", percent())); }
            @Override protected void applyValue() {
                GamepadOptions old = ControlBindings.gamepad();
                ControlBindings.gamepad(new GamepadOptions(old.enabled(),
                        GamepadOptions.MIN_DEADZONE + value * (GamepadOptions.MAX_DEADZONE - GamepadOptions.MIN_DEADZONE)));
            }
            private int percent() {
                return (int) Math.round(100 * (GamepadOptions.MIN_DEADZONE + value * (GamepadOptions.MAX_DEADZONE - GamepadOptions.MIN_DEADZONE)));
            }
        });
        padRows = Math.max(1, (height - 141 - 58) / 23);
        int count = ControlBindings.Action.values().length, pages = (count + padRows - 1) / padRows;
        padPage = Math.min(padPage, pages - 1);
        int labelWidth = labelWidth(), keyWidth = (contentWidth - labelWidth) / ControlBindings.SLOTS;
        for (int row = 0; row < padRows && padPage * padRows + row < count; row++) {
            var action = ControlBindings.Action.values()[padPage * padRows + row];
            for (int slot = 0; slot < ControlBindings.SLOTS; slot++) {
                final int index = slot;
                Component name = listening(action, slot, true) ? text("controls.listening")
                        : ControlBindings.name(ControlBindings.get(action, true, slot));
                String shown = font.plainSubstrByWidth(name.getString(), keyWidth - 25);
                Button button = button(Component.literal(shown).withColor(ControlBindings.conflict(action, true, slot) ? 0xFFC46A : 0xFFFFFF),
                        start + labelWidth + slot * keyWidth, 141 + row * 23, keyWidth - 19,
                        () -> listen(action, index, true));
                Component tooltip = action.label().copy().append("\n").append(name);
                button.setTooltip(Tooltip.create(ControlBindings.conflict(action, true, slot)
                        ? tooltip.copy().append("\n").append(text("controls.conflict")) : tooltip));
                button(Component.literal("x"), start + labelWidth + (slot + 1) * keyWidth - 18, 141 + row * 23, 16,
                        () -> { ControlBindings.set(action, true, index, ""); stopListening(); rebuildWidgets(); })
                        .setTooltip(Tooltip.create(text("controls.clear")));
            }
        }
        button(Component.literal("<"), start, height - 54, 24,
                () -> { padPage--; stopListening(); rebuildWidgets(); }).active = padPage > 0;
        button(Component.literal(">"), start + 28, height - 54, 24,
                () -> { padPage++; stopListening(); rebuildWidgets(); }).active = padPage + 1 < pages;
        button(text("controls.reset.gamepad"), start + contentWidth - 120, height - 54, 120,
                () -> { ControlBindings.defaults(true); stopListening(); rebuildWidgets(); })
                .setTooltip(Tooltip.create(text("controls.defaults.gamepad")));
    }

    private boolean listening(ControlBindings.Action action, int slot, boolean gamepad) {
        return listening == action && listeningSlot == slot && listeningGamepad == gamepad;
    }
    private void listen(ControlBindings.Action action, int slot, boolean gamepad) {
        listening = action; listeningSlot = slot; listeningGamepad = gamepad;
        if (gamepad) GamepadInput.beginCapture(); else GamepadInput.endCapture();
        rebuildWidgets();
    }
    private void character() {
        path = addRenderableWidget(new EditBox(font, start, 78, contentWidth - 64, 20, text("assets.path")));
        path.setMaxLength(4096); path.setValue(ControlBindings.assetPath());
        button(text("assets.browse"), start + contentWidth - 60, 78, 60, () -> {
            String selected = TinyFileDialogs.tinyfd_selectFolderDialog("Celeste", path.getValue());
            if (selected != null) { path.setValue(selected); reloadAssets(); }
        });
        button(text("assets.reload"), start, 105, contentWidth / 2 - 2, this::reloadAssets);
        button(text("assets.detect"), start + contentWidth / 2, 105, contentWidth / 2, () -> { path.setValue(""); reloadAssets(); });
        addRenderableWidget(Checkbox.builder(text("assets.show"), font).pos(start, 132).maxWidth(contentWidth)
                .selected(ControlBindings.showCharacter())
                .onValueChange((checkbox, enabled) -> ControlBindings.appearance(path.getValue(), enabled)).build());
    }
    private void reloadAssets() {
        ControlBindings.appearance(path.getValue(), ControlBindings.showCharacter());
        CelesteAssets.reload();
        com.stevetomadeline.client.audio.CelesteAudio.reload();
    }
    private void effects() {
        var options = ControlBindings.presentation();
        addRenderableWidget(Checkbox.builder(text("effects.sound"), font).pos(start, 62).maxWidth(contentWidth)
                .selected(options.soundEnabled()).onValueChange((box, value) -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(old.grabMode(), value, old.soundVolume(), old.effectsEnabled(), old.showStamina(), old.showDashes()));
                }).build());
        addRenderableWidget(new AbstractSliderButton(start, 90, contentWidth, 20, Component.empty(), options.soundVolume() / 100.0) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.translatable("stevetomadeline.effects.volume", Math.round(value * 100))); }
            @Override protected void applyValue() {
                var old = ControlBindings.presentation();
                ControlBindings.presentation(new PresentationOptions(old.grabMode(), old.soundEnabled(), (int) Math.round(value * 100), old.effectsEnabled(), old.showStamina(), old.showDashes()));
            }
        });
        addRenderableWidget(Checkbox.builder(text("effects.visual"), font).pos(start, 119).maxWidth(contentWidth)
                .selected(options.effectsEnabled()).onValueChange((box, value) -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(old.grabMode(), old.soundEnabled(), old.soundVolume(), value, old.showStamina(), old.showDashes()));
                }).build());
    }
    private void hud() {
        var options = ControlBindings.presentation();
        addRenderableWidget(Checkbox.builder(text("hud.show_stamina"), font).pos(start, 62).maxWidth(contentWidth)
                .selected(options.showStamina()).onValueChange((box, value) -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(old.grabMode(), old.soundEnabled(), old.soundVolume(), old.effectsEnabled(), value, old.showDashes()));
                }).build());
        addRenderableWidget(Checkbox.builder(text("hud.show_dashes"), font).pos(start, 90).maxWidth(contentWidth)
                .selected(options.showDashes()).onValueChange((box, value) -> {
                    var old = ControlBindings.presentation();
                    ControlBindings.presentation(new PresentationOptions(old.grabMode(), old.soundEnabled(), old.soundVolume(), old.effectsEnabled(), old.showStamina(), value));
                }).build());
    }
    private List<Component> gamepadStatus() {
        List<Component> lines = new ArrayList<>();
        if (!ControlBindings.gamepad().enabled()) lines.add(text("gamepad.disabled"));
        else if (GamepadInput.devices().isEmpty()) lines.add(text("gamepad.none"));
        else for (GamepadInput.Device device : GamepadInput.devices()) lines.add(Component.translatable(
                device.standard() ? "stevetomadeline.gamepad.standard" : "stevetomadeline.gamepad.raw", device.name()));
        var held = GamepadInput.held();
        if (!held.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (String binding : held) {
                if (!names.isEmpty()) names.append(' ');
                names.append(ControlBindings.name(binding).getString());
            }
            lines.add(Component.translatable("stevetomadeline.gamepad.held", names.toString()));
        }
        return lines;
    }
    @Override public void tick() {
        super.tick();
        if (listening == null || !listeningGamepad) return;
        String captured = GamepadInput.takeCapture();
        if (captured == null) return;
        ControlBindings.set(listening, true, listeningSlot, captured);
        stopListening(); rebuildWidgets();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (listeningGamepad) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { stopListening(); rebuildWidgets(); }
            return true;
        }
        if (listening != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                String name = key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? "" : InputConstants.getKey(key, scan).getName();
                ControlBindings.set(listening, false, listeningSlot, name);
            }
            stopListening(); rebuildWidgets(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (listeningGamepad) {
            stopListening(); rebuildWidgets();
            return true;
        }
        if (listening != null) {
            ControlBindings.set(listening, false, listeningSlot, InputConstants.Type.MOUSE.getOrCreate(button).getName());
            stopListening(); rebuildWidgets(); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        gui.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (tab == 0) {
            gui.drawString(font, text("controls.grab"), start, 65, 0xFFFFFF);
            for (int row = 0; row < rows && page * rows + row < ControlBindings.Action.values().length; row++) {
                var action = ControlBindings.Action.values()[page * rows + row];
                gui.drawString(font, font.plainSubstrByWidth(action.label().getString(), labelWidth() - 4), start, 96 + row * 23, 0xFFFFFF);
            }
        } else if (tab == 1) {
            int y = 108;
            for (Component line : gamepadStatus()) {
                if (y > 118) break;
                gui.drawString(font, font.plainSubstrByWidth(line.getString(), contentWidth), start, y, 0xD6D9DE);
                y += 10;
            }
            gui.drawString(font, text("gamepad.bindings"), start, 129, 0xFFFFFF);
            for (int row = 0; row < padRows && padPage * padRows + row < ControlBindings.Action.values().length; row++) {
                var action = ControlBindings.Action.values()[padPage * padRows + row];
                gui.drawString(font, font.plainSubstrByWidth(action.label().getString(), labelWidth() - 4), start, 147 + row * 23, 0xFFFFFF);
            }
        } else if (tab == 2) {
            gui.drawString(font, text("assets.path"), start, 64, 0xFFFFFF);
            int available = Math.max(0, (height - 194) / 10);
            var lines = font.split(CelesteAssets.status(), contentWidth);
            for (int i = 0; i < Math.min(available, lines.size()); i++) gui.drawString(font, lines.get(i), start, 160 + i * 10, 0xD6D9DE);
        } else if (tab == 3) {
            var lines = font.split(com.stevetomadeline.client.audio.CelesteAudio.status(), contentWidth);
            for (int i = 0; i < Math.min(Math.max(0, (height - 186) / 10), lines.size()); i++)
                gui.drawString(font, lines.get(i), start, 152 + i * 10, 0xD6D9DE);
        }
        if (!ControlBindings.error().isEmpty()) gui.drawString(font,
                font.plainSubstrByWidth(text("controls.save_error").getString(), contentWidth), start, height - 66, 0xFF7777);
    }
    @Override public void onClose() { stopListening(); PhysicsKeys.clearClicks(); minecraft.setScreen(parent); }
}
