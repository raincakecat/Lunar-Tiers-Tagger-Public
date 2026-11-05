package dev.lunartiers.tagger;

import dev.lunartiers.tagger.core.Ranks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

public abstract class BaseSettingsScreen extends Screen {
    protected final Screen parent;
    protected BaseSettingsScreen(Screen parent) {super(Component.literal("Lunar Tiers").withColor(0xB48EFF)); this.parent = parent;}
    @Override protected void init() {
        Settings cfg = LunarTagger.settings;
        int x = width / 2 - 120, y = top();
        toggle("Tier tags", () -> cfg.enabled, () -> cfg.enabled = !cfg.enabled, x, y, 240);
        this.addRenderableWidget(Button.builder(Component.literal("Mode: " + Ranks.label(cfg.mode)), button -> {
            int index = cfg.mode.equals("auto") ? -1 : Ranks.MODES.indexOf(cfg.mode);
            cfg.mode = index == Ranks.MODES.size() - 1 ? "auto" : Ranks.MODES.get(index + 1);
            cfg.save(); button.setMessage(Component.literal("Mode: " + Ranks.label(cfg.mode)));
        }).bounds(x, y + 24, 240, 20).build());
        toggle("Nametags", () -> cfg.nametags, () -> cfg.nametags = !cfg.nametags, x, y + 48, 118);
        toggle("Tab list", () -> cfg.tab, () -> cfg.tab = !cfg.tab, x + 122, y + 48, 118);
        toggle("Mode icons", () -> cfg.icons, () -> cfg.icons = !cfg.icons, x, y + 72, 118);
        toggle("Separator", () -> cfg.separator, () -> cfg.separator = !cfg.separator, x + 122, y + 72, 118);
        this.addRenderableWidget(Button.builder(Component.literal("Refresh ranks"), button -> LunarTagger.ranks.refresh(LunarTagger.players(minecraft), true)).bounds(x, y + 96, 240, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Key bindings"), button -> minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, minecraft.options))).bounds(x, y + 144, 240, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(x, y + 168, 240, 20).build());
    }
    private void toggle(String name, BooleanSupplier value, Runnable change, int x, int y, int width) {
        this.addRenderableWidget(Button.builder(label(name, value.getAsBoolean()), button -> {change.run(); LunarTagger.settings.save(); button.setMessage(label(name, value.getAsBoolean()));}).bounds(x, y, width, 20).build());
    }
    private static Component label(String name, boolean value) {return Component.literal(name + ": " + (value ? "ON" : "OFF"));}
    protected int top() {return Math.max(28, height / 2 - 108);}
    @Override public void onClose() {LunarTagger.settings.save(); minecraft.setScreenAndShow(parent);}
    @Override public boolean isPauseScreen() {return false;}
}
