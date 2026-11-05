package dev.lunartiers.tagger.mixin;

import dev.lunartiers.tagger.Tags;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class TabNameMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void lunar$tab(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(Tags.decorate(info.getProfile().id(), callback.getReturnValue(), true));
    }
}
