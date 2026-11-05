package dev.lunartiers.tagger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.UUID;

public final class ProfileScreen extends BaseProfileScreen {
    public ProfileScreen(UUID id, Component name) {super(id, name);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 14, 0xFFB48EFF);
        graphics.centeredText(font, playerName, width / 2, 34, 0xFFFFFFFF);
        graphics.centeredText(font, summary(), width / 2, 50, 0xFFCCC3DA);
        for (int i = 0; i < 9; i++) graphics.text(font, modeRow(i), rowX(i), rowY(i), 0xFFFFFFFF);
        graphics.centeredText(font, LunarTagger.ranks.status(), width / 2, height - 48, 0xFFCCC3DA);
    }
}
