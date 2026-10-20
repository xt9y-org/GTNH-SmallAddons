package com.xt9y.features.mixin.mixins.late;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.api.IWildcardContainerState;

import appeng.container.implementations.ContainerInterface;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.packets.PacketValueConfig;

@Mixin(value = PacketValueConfig.class, remap = false)
public abstract class MixinPacketValueConfigWildcard {

    @Shadow
    @Final
    private String Name;

    @Inject(method = "serverPacketData", at = @At("HEAD"), cancellable = true)
    private void xt9y$onWildcardButton(INetworkInfo network, AppEngPacket packet, EntityPlayer player,
        CallbackInfo ci) {
        if (!"XT9Y.InterfaceWildcard".equals(Name)) return;
        ci.cancel();

        // Only permit a toggle for the interface whose valid GUI this player actually opened.
        if (!(player.openContainer instanceof ContainerInterface container) || !container.canInteractWith(player)
            || !container.isValidContainer()) {
            return;
        }
        // The container's duality works for full-block, cable-part and AE2FC interfaces.
        ((IWildcardContainerState) container).xt9y$toggleWildcard(player);
    }
}
