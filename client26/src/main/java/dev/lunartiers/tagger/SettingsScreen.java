package dev.lunartiers.tagger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class SettingsScreen extends BaseSettingsScreen {
    public SettingsScreen(Screen parent) {super(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top() - 20, 0xFFB48EFF);
        graphics.centeredText(font, LunarTagger.ranks.status(), width / 2, top() + 124, 0xFFCCC3DA);
    }
}
