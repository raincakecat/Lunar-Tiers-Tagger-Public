package dev.lunartiers.tagger;

import dev.lunartiers.tagger.core.RankCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import java.util.Map;

/** Test-only mod; never included in distributed JARs. Uses synthetic identities and no external API. */
public final class LunarClientTest implements FabricClientGameTest {
    private static void check(boolean valid, String message) {if (!valid) throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("lunar.gametest.uiOnly")) {smokeTest(context); return;}
        // Software-rendered CI shares limited CPU with the integrated server.
        context.runOnClient(client -> {
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(2);
            client.options.framerateLimit().set(30);
            client.options.enableVsync().set(false);
        });
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.level != null && client.player != null);
            context.waitTicks(40);
            context.runOnClient(client -> {
                try {
                LunarTagger.settings.enabled = true; LunarTagger.settings.mode = "auto";
                LunarTagger.settings.icons = true; LunarTagger.settings.separator = true;
                LunarTagger.settings.nametags = true; LunarTagger.settings.tab = true;
                var field = LunarTagger.ranks.getClass().getDeclaredField("cache"); field.setAccessible(true);
                RankCache cache = (RankCache) field.get(LunarTagger.ranks);
                var id = client.player.getUUID(); cache.put(Map.of(id, Map.of("crystal", "HT3", "sword", "LT2")));
                Component original = Component.literal("OriginalName").withColor(0x123456);
                Component decorated = Tags.decorate(id, original, false);
                check(decorated.getString().equals("\uE001 LT2 | OriginalName"), "Highest tier and mode icon");
                check(original.getString().equals("OriginalName"), "Original text remains unchanged");
                check(decorated.getSiblings().getLast().getStyle().getColor().getValue() == 0x123456, "Original name color preserved");
                LunarTagger.settings.mode = "crystal";
                check(Tags.decorate(id, original, false).getString().equals("\uE000 HT3 | OriginalName"), "Manual mode");
                LunarTagger.settings.icons = false; LunarTagger.settings.separator = false;
                check(Tags.decorate(id, original, false).getString().equals("HT3 OriginalName"), "Icon and separator toggles");
                LunarTagger.settings.icons = true; LunarTagger.settings.separator = true;
                var info = client.getConnection().getPlayerInfo(id);
                check(info != null, "Player info exists");
                check(Platform.tab(client).getNameForDisplay(info).getString().contains("HT3 | "), "Tab mixin applies");
                var method = EntityRenderer.class.getDeclaredMethod("getNameTag", Entity.class); method.setAccessible(true);
                var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
                check(((Component) method.invoke(renderer, client.player)).getString().contains("HT3 | "), "Nametag mixin applies");
                check(!client.player.getDisplayName().getString().contains("HT3 | "), "Entity/chat name is untouched");
                LunarTagger.settings.nametags = false;
                check(Tags.decorate(id, original, false) == original, "Nametag toggle");
                LunarTagger.settings.tab = false;
                check(Tags.decorate(id, original, true) == original, "Tab toggle");
                LunarTagger.settings.nametags = true; LunarTagger.settings.tab = true;
                } catch (ReflectiveOperationException error) {throw new AssertionError(error);}
            });
            context.getInput().pressKey(LunarTagger.openSettings);
            context.waitForScreen(SettingsScreen.class);
            context.waitTicks(20); context.takeScreenshot("lunar-settings-default");
            context.clickScreenButton("Mode: Crystal");
            context.runOnClient(client -> check(LunarTagger.settings.mode.equals("sword"), "Mode button cycles"));
            context.clickScreenButton("Tier tags: ON");
            context.runOnClient(client -> check(!LunarTagger.settings.enabled, "Enable button works"));
            context.clickScreenButton("Tier tags: OFF");
            context.clickScreenButton("Key bindings");
            context.waitForScreen(net.minecraft.client.gui.screens.options.controls.KeyBindsScreen.class);
            context.runOnClient(client -> {
                check(java.util.Arrays.asList(client.options.keyMappings).contains(LunarTagger.openSettings), "Settings listed in Controls");
                check(java.util.Arrays.asList(client.options.keyMappings).contains(LunarTagger.openProfile), "Profile listed in Controls");
                check(LunarTagger.openProfile.getCategory().label().getString().equals("Lunar Tiers"), "Lunar key category translated");
                check(LunarTagger.openSettings.getDefaultKey().getValue() == com.mojang.blaze3d.platform.InputConstants.KEY_I, "Settings defaults to I");
                check(LunarTagger.openProfile.getDefaultKey().getValue() == com.mojang.blaze3d.platform.InputConstants.KEY_P, "Profile defaults to P");
                LunarTagger.openProfile.setKey(LunarTagger.openProfile.getDefaultKey().getType().getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_O));
                check(!LunarTagger.openProfile.isDefault() && LunarTagger.openSettings.isDefault(), "Profile rebind is independent");
                LunarTagger.openProfile.setKey(LunarTagger.openProfile.getDefaultKey());
                net.minecraft.client.KeyMapping.resetMapping();
                Platform.screen(client).onClose();
            });
            context.waitForScreen(SettingsScreen.class);
            context.clickScreenButton("Done");
            context.waitTicks(2);
            context.runOnClient(client -> check(Platform.screen(client) == null, "Done closes settings"));
            context.runOnClient(client -> {
                check(LunarTagger.closestPlayer(client) == null, "Own player is excluded");
                var near = new net.minecraft.client.player.RemotePlayer(client.level, new com.mojang.authlib.GameProfile(java.util.UUID.fromString("12345678-1234-4234-8234-123456789abc"), "LunarTestPlayer"));
                var far = new net.minecraft.client.player.RemotePlayer(client.level, new com.mojang.authlib.GameProfile(java.util.UUID.fromString("12345678-1234-4234-8234-123456789abd"), "FarPlayer"));
                near.setId(123456); far.setId(123457);
                near.setPos(client.player.getX() + 40, client.player.getY() + 3, client.player.getZ());
                far.setPos(client.player.getX() + 42, client.player.getY() + 3, client.player.getZ());
                client.level.addEntity(near); client.level.addEntity(far);
                check(LunarTagger.closestPlayer(client) == null, "Players outside 32 blocks excluded");
                near.setPos(client.player.getX() + 2, client.player.getY() + 3, client.player.getZ());
                far.setPos(client.player.getX() + 5, client.player.getY() + 3, client.player.getZ());
                check(LunarTagger.closestPlayer(client) == near, "Nearest visible player selected");
                near.setInvisible(true);
                check(LunarTagger.closestPlayer(client) == far, "Invisible player excluded");
                near.setInvisible(false);
                try {
                    var field = LunarTagger.ranks.getClass().getDeclaredField("cache"); field.setAccessible(true);
                    ((RankCache) field.get(LunarTagger.ranks)).put(Map.of(near.getUUID(), Map.of("crystal","LT3","sword","LT2","smp","HT3")));
                } catch (ReflectiveOperationException error) {throw new AssertionError(error);}
            });
            context.getInput().pressKey(LunarTagger.openProfile);
            context.waitForScreen(ProfileScreen.class);
            context.waitTicks(10);
            context.runOnClient(client -> {
                var profile = (ProfileScreen) Platform.screen(client);
                check(profile.playerName.getString().equals("LunarTestPlayer"), "Profile is bound to selected player");
                check(profile.summary().getString().equals("Highest: Sword LT2"), "Profile highest rank");
                for (int index=0; index<9; index++) check(profile.modeRow(index).getString().contains(dev.lunartiers.tagger.core.Ranks.label(dev.lunartiers.tagger.core.Ranks.MODES.get(index))), "All nine profile modes");
                check(profile.modeRow(0).getString().contains("LT3"), "Crystal profile rank visible");
                check(profile.modeRow(2).getString().contains("—"), "Missing rank has neutral placeholder");
            });
            context.takeScreenshot("lunar-player-profile");
            context.clickScreenButton("Refresh ranks");
            context.clickScreenButton("Done");
            context.runOnClient(client -> check(Platform.screen(client) == null, "Profile closes"));
        }
        context.runOnClient(client -> check(LunarTagger.ranks.ranks(client.getGameProfile().id()).isEmpty(), "Disconnect clears cached ranks"));
    }
    private void smokeTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            check(LunarTagger.openSettings != null && LunarTagger.openProfile != null, "Both keys registered");
            check(LunarTagger.settings != null && LunarTagger.ranks != null, "Client initialized");
            LunarTagger.settings.enabled = true; LunarTagger.settings.mode = "auto";
        });
        context.setScreen(() -> new SettingsScreen(null)); context.waitTicks(20);
        context.takeScreenshot("lunar-settings-smoke");
        context.clickScreenButton("Mode: Auto (highest)");
        context.runOnClient(client -> check("crystal".equals(LunarTagger.settings.mode), "Mode control works"));
        context.clickScreenButton("Tier tags: ON");
        context.runOnClient(client -> check(!LunarTagger.settings.enabled, "Enable control works"));
        context.clickScreenButton("Tier tags: OFF");
        context.clickScreenButton("Refresh ranks");
        context.runOnClient(client -> check(!LunarTagger.ranks.working(), "No network fetch without player UUIDs or endpoint"));
        context.clickScreenButton("Done"); context.waitTicks(2);
        context.runOnClient(client -> check(!(Platform.screen(client) instanceof SettingsScreen), "Done leaves settings"));
        context.setScreen(() -> new ProfileScreen(java.util.UUID.fromString("12345678-1234-4234-8234-123456789abc"), Component.literal("LunarTestPlayer")));
        context.waitTicks(10);
        context.runOnClient(client -> {
            var profile = (ProfileScreen) Platform.screen(client);
            check(profile.summary().getString().equals("No visible Lunar ranks"), "Empty profile summary");
            for (int index=0; index<9; index++) check(profile.modeRow(index).getString().contains("—"), "Empty profile mode placeholder");
        });
        context.takeScreenshot("lunar-profile-smoke");
        context.clickScreenButton("Refresh ranks");
        context.clickScreenButton("Done");
        context.runOnClient(client -> check(!(Platform.screen(client) instanceof ProfileScreen), "Done leaves profile"));
    }
}
