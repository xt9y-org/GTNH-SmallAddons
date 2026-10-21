package com.xt9y.features.mixin.mixins.late;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.WildcardPatternHelper;
import com.xt9y.features.XT9YFeatures;
import com.xt9y.features.api.IWildcardToggleable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.items.misc.ItemEncodedPattern;
import appeng.me.helpers.AENetworkProxy;

@Mixin(value = DualityInterface.class, remap = false)
@Implements(@Interface(iface = IWildcardToggleable.class, prefix = "xt9y$"))
public abstract class MixinDualityInterface implements IWildcardToggleable {

    @Shadow
    protected AENetworkProxy gridProxy;

    @Shadow
    public List<ICraftingPatternDetails> craftingList;

    @Shadow
    private IInterfaceHost iHost;

    @Unique
    private boolean xt9y$enableWildcardExpansion = false;

    @Unique
    private final Map<ICraftingPatternDetails, ICraftingPatternDetails> xt9y$expandedToOriginal = new HashMap<>();

    public boolean xt9y$isWildcardEnabled() {
        return xt9y$enableWildcardExpansion;
    }

    public void xt9y$setWildcardEnabled(boolean enabled) {
        if (xt9y$enableWildcardExpansion != enabled) {
            XT9YFeatures.LOGGER.info("Interface wildcard pattern expansion {}", enabled);
            xt9y$enableWildcardExpansion = enabled;
            xt9y$expandedToOriginal.clear();
            iHost.saveChanges();
            ((DualityInterface) (Object) this).updateCraftingList();
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void onWriteToNBT(NBTTagCompound data, CallbackInfo ci) {
        data.setBoolean("xt9yEnableWildcardExpansion", xt9y$enableWildcardExpansion);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void onReadFromNBT(NBTTagCompound data, CallbackInfo ci) {
        if (data.hasKey("xt9yEnableWildcardExpansion")) {
            xt9y$enableWildcardExpansion = data.getBoolean("xt9yEnableWildcardExpansion");
        }
    }

    @Inject(method = "provideCrafting", at = @At("RETURN"))
    private void onProvideCrafting(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        xt9y$expandedToOriginal.clear();
        if (!gridProxy.isActive() || !xt9y$enableWildcardExpansion || craftingList == null) return;

        World world = iHost.getTileEntity()
            .getWorldObj();
        if (world == null) return;

        for (ICraftingPatternDetails originalDetails : craftingList) {
            // AE2 already keeps the installed encoded stack on its registered pattern.
            // Re-decoding creates another PatternHelper and is unnecessary here.
            ItemStack patternStack = originalDetails.getPattern();
            if (patternStack == null || !(patternStack.getItem() instanceof ItemEncodedPattern)) continue;

            List<ICraftingPatternDetails> expanded = WildcardPatternHelper
                .expandWildcard(originalDetails, patternStack, world);
            for (ICraftingPatternDetails expandedDetail : expanded) {
                craftingTracker
                    .addCraftingOption((appeng.api.networking.crafting.ICraftingProvider) this, expandedDetail);
                xt9y$expandedToOriginal.put(expandedDetail, originalDetails);
            }
        }
    }

    @Redirect(
        method = "pushPattern",
        at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    // Redirect handlers must not use the xt9y$ soft-implements prefix.
    private boolean acceptExpandedPattern(List<ICraftingPatternDetails> registered, Object pattern) {
        // Only bypass AE2's membership check. The expanded pattern and its actual
        // crafting table must reach the target machine unchanged.
        return registered.contains(pattern)
            || (xt9y$enableWildcardExpansion && xt9y$expandedToOriginal.containsKey(pattern));
    }
}
