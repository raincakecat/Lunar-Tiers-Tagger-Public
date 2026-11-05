package dev.lunartiers.tagger.core;

import java.util.List;
import java.util.Map;

public final class Ranks {
    private Ranks() {}
    public static final List<String> MODES = List.of("crystal", "sword", "axe", "smp", "pot", "mace", "uhc", "nethpot", "spear-mace");
    public static final List<String> TIERS = List.of("HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5");
    public record Tag(String mode, String tier) {}
    public static Tag select(Map<String, String> ranks, String mode) {
        if (!"auto".equals(mode)) {String tier = ranks.get(mode); return tier != null && TIERS.contains(tier) ? new Tag(mode, tier) : null;}
        for (String tier : TIERS) for (String key : MODES) if (tier.equals(ranks.get(key))) return new Tag(key, tier);
        return null;
    }
    public static String label(String mode) {
        return switch (mode) {
            case "auto" -> "Auto (highest)"; case "smp" -> "SMP"; case "uhc" -> "UHC";
            case "nethpot" -> "Nethpot"; case "spear-mace" -> "Spear Mace";
            default -> mode.substring(0, 1).toUpperCase() + mode.substring(1);
        };
    }
    public static int color(String tier) {
        return switch (tier.substring(2)) {
            case "1" -> 0xF5CE4D; case "2" -> 0xBFCDD6; case "3" -> 0xB06453;
            case "4" -> 0xD45B68; default -> 0xBCBBC1;
        };
    }
}
