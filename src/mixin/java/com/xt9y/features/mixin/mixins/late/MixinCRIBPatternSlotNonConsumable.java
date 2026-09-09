package com.xt9y.features.mixin.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.NonConsumableCRIBRuntime;

import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

@Mixin(value = PatternSlot.class, remap = false)
public abstract class MixinCRIBPatternSlotNonConsumable {

    @Inject(method = "getItemInputs", at = @At("RETURN"), cancellable = true)
    private void xt9y$appendSyntheticNonConsumables(CallbackInfoReturnable<ItemStack[]> cir) {
        ItemStack[] synthetic = NonConsumableCRIBRuntime.get((IDualInputInventoryWithPattern) (Object) this);
        if (synthetic.length == 0) return;

        ItemStack[] real = cir.getReturnValue();
        int realLength = real == null ? 0 : real.length;
        ItemStack[] combined = new ItemStack[realLength + synthetic.length];
        if (realLength > 0) System.arraycopy(real, 0, combined, 0, realLength);
        System.arraycopy(synthetic, 0, combined, realLength, synthetic.length);
        cir.setReturnValue(combined);
    }
}
