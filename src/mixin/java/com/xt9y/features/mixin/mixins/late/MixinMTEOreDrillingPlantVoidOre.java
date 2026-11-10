package com.xt9y.features.mixin.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.VirtualExtraction;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEOreDrillingPlantBase;

/** Substitutes a GTNH ore pool for the real blocks, preserving drill fluid, EU and output checks. */
@Mixin(value = MTEOreDrillingPlantBase.class, remap = false)
public abstract class MixinMTEOreDrillingPlantVoidOre {

    @Shadow
    private boolean tryConsumeDrillingFluid(boolean simulate) {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(method = { "workingDownward", "workingAtBottom" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void xt9y$virtualOre(ItemStack stack, int xDrill, int yDrill, int zDrill, int xPipe, int zPipe, int yHead,
        int oldYHead, CallbackInfoReturnable<Boolean> cir) {
        MTEOreDrillingPlantBase drill = (MTEOreDrillingPlantBase) (Object) this;
        IGregTechTileEntity base = drill.getBaseMetaTileEntity();
        if (base == null || !VirtualExtraction.isVirtual(base.getWorld(), xDrill, zDrill)) return;

        ItemStack ore = VirtualExtraction.nextOre(base.getWorld(), xDrill, zDrill);
        if (ore != null) {
            if (!((MixinGTMultiblockOutputAccess) (Object) this).xt9y$canOutputAll(new ItemStack[] { ore })
                || !tryConsumeDrillingFluid(false)) {
                cir.setReturnValue(false);
                return;
            }
            ((MTEMultiBlockBase) (Object) this).mOutputItems = new ItemStack[] { ore };
        }
        // Do not descend pipes or destroy blocks; return a successful recipe-cycle state.
        cir.setReturnValue(true);
    }
}
