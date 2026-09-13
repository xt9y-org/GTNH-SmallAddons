package com.xt9y.features.mixin.mixins.late;

import net.minecraft.inventory.InventoryCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.xtprofile.XTProfileDispatchContext;
import com.xt9y.features.xtprofile.XTProfileManager;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterXTProfile {

    @Unique
    private long xtprofile$logicStartNs;

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"))
    private void xtprofile$observe(IGrid grid, IEnergyGrid energyGrid, CraftingGridCache cache, CallbackInfo ci) {
        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        xtprofile$logicStartNs = XTProfileManager.INSTANCE.isRunning() ? System.nanoTime() : 0;
        XTProfileManager.INSTANCE.observeCpu(cpu);
    }

    @Inject(method = "updateCraftingLogic", at = @At("RETURN"))
    private void xtprofile$timing(IGrid grid, IEnergyGrid energyGrid, CraftingGridCache cache, CallbackInfo ci) {
        long started = xtprofile$logicStartNs;
        xtprofile$logicStartNs = 0;
        if (started <= 0) return;
        XTProfileManager.INSTANCE.recordCpuTick(
            (CraftingCPUCluster) (Object) this,
            Math.max(0, System.nanoTime() - started));
    }

    @Redirect(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern"
                + "(Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                + "Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean xtprofile$dispatch(ICraftingMedium medium, ICraftingPatternDetails pattern,
        InventoryCrafting inventory) {
        if (!XTProfileManager.INSTANCE.isRunning()) return medium.pushPattern(pattern, inventory);

        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        XTProfileManager.INSTANCE.observeCpu(cpu);
        XTProfileDispatchContext.begin(cpu, medium);
        try {
            boolean pushed = medium.pushPattern(pattern, inventory);
            if (pushed && !XTProfileDispatchContext.hasRecordedTarget()) {
                XTProfileManager.INSTANCE.recordUnresolvedMedium(cpu, medium, pattern);
            }
            return pushed;
        } finally {
            XTProfileDispatchContext.end();
        }
    }

    @Inject(method = "completeJob", at = @At("RETURN"))
    private void xtprofile$complete(CallbackInfo ci) {
        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        if (!cpu.isBusy()) XTProfileManager.INSTANCE.finishCpu(cpu, "complete");
    }

    @Inject(method = "cancel", at = @At("TAIL"))
    private void xtprofile$cancel(CallbackInfo ci) {
        XTProfileManager.INSTANCE.finishCpu((CraftingCPUCluster) (Object) this, "cancelled");
    }
}
