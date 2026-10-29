package com.xt9y.features.mixin.mixins.late;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.NonConsumablePatternHelper;
import com.xt9y.features.WildcardPatternHelper;
import com.xt9y.features.XT9YConfig;
import com.xt9y.features.XT9YFeatures;
import com.xt9y.features.api.IWildcardToggleable;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.items.misc.ItemEncodedPattern;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.CommonMetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

@Mixin(value = MTEHatchCraftingInputME.class, remap = false)
@Implements(@Interface(iface = IWildcardToggleable.class, prefix = "xt9y$"))
public abstract class MixinCRIBWildcard {

    @Shadow(remap = false)
    private PatternSlot<MTEHatchCraftingInputME>[] internalInventory;

    @Shadow(remap = false)
    private Map<ICraftingPatternDetails, PatternSlot<MTEHatchCraftingInputME>> patternDetailsPatternSlotMap;

    @Shadow(remap = false)
    private boolean needPatternSync;

    @Shadow(remap = false)
    public abstract boolean isActive();

    @Unique
    private boolean xt9y$enableWildcardExpansion = false;

    @Unique
    private final Set<ICraftingPatternDetails> xt9y$syntheticPatterns = new HashSet<>();

    public boolean xt9y$isWildcardEnabled() {
        return XT9YConfig.cribWildcard && xt9y$enableWildcardExpansion;
    }

    public void xt9y$setWildcardEnabled(boolean val) {
        if (!XT9YConfig.cribWildcard) return;
        if (xt9y$enableWildcardExpansion != val) {
            XT9YFeatures.LOGGER.info("CRIB wildcard pattern expansion {}", val);
            xt9y$enableWildcardExpansion = val;
            needPatternSync = true;
        }
    }

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true)
    private void gt5u$onGetDescription(CallbackInfoReturnable<String[]> cir) {
        if (!XT9YConfig.cribWildcard) return;
        String[] original = cir.getReturnValue();
        if (original == null) return;
        String[] extended = Arrays.copyOf(original, original.length + 1);
        extended[original.length] = StatCollector.translateToLocal("xt9yfeatures.tooltip.wildcard.mallet");
        cir.setReturnValue(extended);
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void gt5u$onSaveNBT(NBTTagCompound aNBT, CallbackInfo ci) {
        aNBT.setBoolean("enableWildcardExpansion", xt9y$enableWildcardExpansion);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void gt5u$onLoadNBT(NBTTagCompound aNBT, CallbackInfo ci) {
        if (aNBT.hasKey("enableWildcardExpansion")) {
            xt9y$enableWildcardExpansion = aNBT.getBoolean("enableWildcardExpansion");
        }
    }

    @Inject(method = "provideCrafting", at = @At("HEAD"))
    private void gt5u$clearSyntheticPatternMappings(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        for (ICraftingPatternDetails details : xt9y$syntheticPatterns) {
            patternDetailsPatternSlotMap.remove(details);
        }
        xt9y$syntheticPatterns.clear();
    }

    @Redirect(
        method = "provideCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProviderHelper;addCraftingOption(Lappeng/api/networking/crafting/ICraftingMedium;Lappeng/api/networking/crafting/ICraftingPatternDetails;)V"))
    private void gt5u$wrapStockPattern(ICraftingProviderHelper helper, ICraftingMedium medium,
        ICraftingPatternDetails details) {
        // When wildcard mode is on, the RETURN injection below supplies all options, including non-wildcard patterns.
        if (XT9YConfig.cribWildcard && xt9y$enableWildcardExpansion) return;

        PatternSlot<MTEHatchCraftingInputME> slot = patternDetailsPatternSlotMap.get(details);
        ICraftingPatternDetails provided = NonConsumablePatternHelper.wrap(details, details.getPattern());
        helper.addCraftingOption(medium, provided);

        if (provided != details && slot != null) {
            patternDetailsPatternSlotMap.put(provided, slot);
            xt9y$syntheticPatterns.add(provided);
        }
    }

    @Inject(method = "provideCrafting", at = @At("RETURN"))
    private void gt5u$onProvideCrafting(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        if (!XT9YConfig.cribWildcard || !isActive() || !xt9y$enableWildcardExpansion) return;

        World world = ((IMetaTileEntity) this).getBaseMetaTileEntity()
            .getWorld();
        if (world == null) return;

        PatternSlot<MTEHatchCraftingInputME>[] inv = internalInventory;
        ItemStack[] mInv = ((CommonMetaTileEntity) (Object) this).mInventory;

        for (int slotIndex = 0; slotIndex < 4 * 9; slotIndex++) {
            ItemStack patternStack = slotIndex < mInv.length ? mInv[slotIndex] : null;
            if (patternStack == null || !(patternStack.getItem() instanceof ItemEncodedPattern)) continue;

            ICraftingPatternDetails originalDetails = ((ItemEncodedPattern) patternStack.getItem())
                .getPatternForItem(patternStack, world);
            if (originalDetails == null) continue;

            List<ICraftingPatternDetails> expanded = WildcardPatternHelper
                .expandWildcard(originalDetails, patternStack, world);
            PatternSlot<MTEHatchCraftingInputME> patternSlot = slotIndex < inv.length ? inv[slotIndex] : null;

            for (ICraftingPatternDetails expandedDetail : expanded) {
                ICraftingPatternDetails provided = NonConsumablePatternHelper.wrap(expandedDetail, patternStack);
                craftingTracker.addCraftingOption((ICraftingMedium) this, provided);

                if (patternSlot != null) {
                    patternDetailsPatternSlotMap.put(provided, patternSlot);
                    if (provided != originalDetails) xt9y$syntheticPatterns.add(provided);
                }
            }
        }
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void gt5u$onPushPattern(ICraftingPatternDetails patternDetails,
        net.minecraft.inventory.InventoryCrafting table, CallbackInfoReturnable<Boolean> cir) {
        if (!XT9YConfig.cribWildcard && !XT9YConfig.cribNonConsumable) return;
        if (patternDetailsPatternSlotMap.get(patternDetails) == null) {
            cir.setReturnValue(false);
        }
    }
}
