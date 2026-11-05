package dev.lunartiers.tagger;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class SettingsScreen extends BaseSettingsScreen {
    public SettingsScreen(Screen parent) {super(parent);}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, top() - 20, 0xFFB48EFF);
        graphics.drawCenteredString(font, LunarTagger.ranks.status(), width / 2, top() + 124, 0xFFCCC3DA);
    }
}
