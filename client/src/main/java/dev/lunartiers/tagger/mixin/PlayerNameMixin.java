package dev.lunartiers.tagger.mixin;

import dev.lunartiers.tagger.Tags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decorate only the renderer's existing name, never entity display names used in chat. */
@Mixin(EntityRenderer.class)
public abstract class PlayerNameMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void lunar$name(Entity entity, CallbackInfoReturnable<Component> callback) {
        if (entity instanceof Player) callback.setReturnValue(Tags.decorate(entity.getUUID(), callback.getReturnValue(), false));
    }
}
