package com.xt9y.features.mixin.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.VirtualExtraction;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.misc.DrillingLogicDelegate;
import gregtech.common.tileentities.machines.basic.MTEMiner;

/** Runs the existing tier's energy and progress cycle, but never removes world ore. */
@Mixin(value = MTEMiner.class, remap = false)
public abstract class MixinMTEMinerVoidOre {

    @Redirect(
        method = "onPostTick",
        at = @At(value = "INVOKE", target = "Lgregtech/common/misc/DrillingLogicDelegate;canContinueDrilling(J)Z"),
        remap = false)
    private boolean xt9y$virtualMinerNeedsNoPipe(DrillingLogicDelegate pipe, long tick) {
        MTEMiner miner = (MTEMiner) (Object) this;
        IGregTechTileEntity base = miner.getBaseMetaTileEntity();
        if (base != null && VirtualExtraction.isVirtual(base.getWorld(), base.getXCoord(), base.getZCoord())) {
            return true;
        }
        return pipe.canContinueDrilling(tick);
    }

    @Inject(
        method = "onPostTick",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;decreaseStoredEnergyUnits(JZ)Z",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private void xt9y$generateWithoutMining(IGregTechTileEntity base, long tick, CallbackInfo ci) {
        if (!VirtualExtraction.isVirtual(base.getWorld(), base.getXCoord(), base.getZCoord())) return;

        MTEMiner miner = (MTEMiner) (Object) this;
        if (miner.getProgresstime() == miner.getMachineSpeed() - 1) {
            ItemStack ore = VirtualExtraction.nextOre(base.getWorld(), base.getXCoord(), base.getZCoord());
            if (ore != null && miner.pushOutputs(ore, ore.stackSize, true, false)) {
                miner.pushOutputs(ore, ore.stackSize, false, false);
            }
        }
        // Cancel only the physical scan, pipe descent and block destruction.
        ci.cancel();
    }
}
