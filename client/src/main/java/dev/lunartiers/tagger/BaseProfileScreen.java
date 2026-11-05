package dev.lunartiers.tagger;

import dev.lunartiers.tagger.core.Ranks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.UUID;

/** Public rank data only; no Discord identifiers or private profile fields. */
public abstract class BaseProfileScreen extends Screen {
    protected final UUID playerId;
    protected final Component playerName;
    protected BaseProfileScreen(UUID playerId, Component playerName) {
        super(Component.literal("Lunar Tiers · Player Profile").withColor(0xB48EFF));
        this.playerId = playerId; this.playerName = playerName.copy();
    }
    @Override protected void init() {
        LunarTagger.ranks.refresh(List.of(playerId), false);
        int x = width / 2 - 140;
        addRenderableWidget(Button.builder(Component.literal("Refresh ranks"), button -> LunarTagger.ranks.refresh(List.of(playerId), true)).bounds(x, height - 30, 136, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(x + 144, height - 30, 136, 20).build());
    }
    protected Component summary() {
        Ranks.Tag best = Ranks.select(LunarTagger.ranks.ranks(playerId), "auto");
        return Component.literal(best == null ? "No visible Lunar ranks" : "Highest: " + Ranks.label(best.mode()) + " " + best.tier());
    }
    protected Component modeRow(int index) {
        String mode = Ranks.MODES.get(index), tier = LunarTagger.ranks.ranks(playerId).get(mode);
        return Component.empty().append(Component.literal(String.valueOf((char) (0xE000 + index)))
            .withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("lunartiers", "modes")))))
            .append(Component.literal(" " + Ranks.label(mode) + "  ").withStyle(Style.EMPTY))
            .append(Component.literal(tier == null ? "—" : tier).withStyle(Style.EMPTY.withColor(tier == null ? 0xAAA3B5 : Ranks.color(tier))));
    }
    protected int rowY(int index) {return 68 + (index % 5) * Math.min(20, Math.max(12, (height - 128) / 4));}
    protected int rowX(int index) {return width / 2 - 140 + (index / 5) * 144;}
    @Override public void onClose() {minecraft.setScreenAndShow(null);}
    @Override public boolean isPauseScreen() {return false;}
}
