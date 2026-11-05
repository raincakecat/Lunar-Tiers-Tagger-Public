package dev.lunartiers.tagger;

import dev.lunartiers.tagger.core.RankClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;

public final class LunarTagger implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lunartiers_tagger");
    public static Settings settings;
    public static RankClient ranks;
    public static KeyMapping openSettings;
    public static KeyMapping openProfile;
    private int ticks;
    private Object connection;
    @Override public void onInitializeClient() {
        settings = Settings.load(); ranks = new RankClient(); ranks.endpoint(settings.apiUrl);
        KeyMapping key = openSettings = Platform.settingsKey();
        openProfile = Platform.profileKey();
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {if (Platform.screen(client) == null) client.setScreenAndShow(new SettingsScreen(null));}
            while (openProfile.consumeClick()) {
                if (Platform.screen(client) == null && client.player != null) {
                    // P is also vanilla's Social Interactions default. Give the explicitly
                    // requested profile shortcut priority without changing saved bindings.
                    for (KeyMapping other : client.options.keyMappings) if (other != openProfile && other.same(openProfile)) while (other.consumeClick()) {}
                    Player target = closestPlayer(client);
                    if (target == null) Platform.message(client, Component.literal("No visible player within 32 blocks."));
                    else client.setScreenAndShow(new ProfileScreen(target.getUUID(), target.getName().copy()));
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getConnection() != connection) {connection = client.getConnection(); ranks.disconnect();}
            if (++ticks >= 20) {ticks = 0; if (settings.enabled && connection != null) ranks.refresh(players(client), false);}
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> ranks.close(), "lunar-tagger-shutdown"));
        LOGGER.info("Lunar Tiers Tagger ready");
    }
    public static List<UUID> players(Minecraft client) {
        return client.getConnection() == null ? List.of() : client.getConnection().getOnlinePlayers().stream().map(info -> info.getProfile().id()).limit(4096).toList();
    }
    public static Player closestPlayer(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        return client.level.players().stream().filter(p -> p != client.player && p.isAlive() && !p.isSpectator()
            && !p.isInvisibleTo(client.player) && client.player.distanceToSqr(p) <= 32 * 32 && client.player.hasLineOfSight(p))
            .min(Comparator.comparingDouble((Player p) -> client.player.distanceToSqr(p)).thenComparing(p -> p.getUUID().toString())).orElse(null);
    }
}
