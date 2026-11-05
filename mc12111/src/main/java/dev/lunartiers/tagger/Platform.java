package dev.lunartiers.tagger;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class Platform {
    private static final KeyMapping.Category LUNAR = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("lunartiers", "keys"));
    public static KeyMapping settingsKey() {return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunartiers.settings", InputConstants.Type.KEYSYM, InputConstants.KEY_I, LUNAR));}
    public static KeyMapping profileKey() {return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lunartiers.profile", InputConstants.Type.KEYSYM, InputConstants.KEY_P, LUNAR));}
    public static void message(Minecraft client, net.minecraft.network.chat.Component text) {client.gui.setOverlayMessage(text, false);}
    public static Screen screen(Minecraft client) {return client.screen;}
    public static net.minecraft.client.gui.components.PlayerTabOverlay tab(Minecraft client) {return client.gui.getTabList();}
}
