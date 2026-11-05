package dev.lunartiers.tagger;

import dev.lunartiers.tagger.core.Ranks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public final class Tags {
    private Tags() {}
    public static Component decorate(UUID id, Component original, boolean tab) {
        Settings cfg = LunarTagger.settings;
        if (cfg == null || !cfg.enabled || (tab ? !cfg.tab : !cfg.nametags) || original == null) return original;
        Ranks.Tag tag = Ranks.select(LunarTagger.ranks.ranks(id), cfg.mode);
        if (tag == null) return original;
        var prefix = Component.empty();
        if (cfg.icons) {
            String icon = String.valueOf((char) (0xE000 + Ranks.MODES.indexOf(tag.mode())));
            prefix.append(Component.literal(icon).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("lunartiers", "modes"))))).append(" ");
        }
        return prefix.append(Component.literal(tag.tier() + (cfg.separator ? " | " : " ")).withColor(Ranks.color(tag.tier()))).append(original);
    }
}
