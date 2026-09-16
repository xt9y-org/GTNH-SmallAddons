package com.xt9y.features.mixin.mixins.late;

import net.minecraft.inventory.InventoryCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xt9y.features.xtprofile.XTProfileDispatchContext;
import com.xt9y.features.xtprofile.XTProfileRouteTracker;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterXTProfile {

    @WrapOperation(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern"
                + "(Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                + "Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean xtprofile$dispatch(ICraftingMedium medium, ICraftingPatternDetails pattern,
        InventoryCrafting inventory, Operation<Boolean> original) {
        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        XTProfileDispatchContext.begin(cpu, medium);
        try {
            boolean pushed = original.call(medium, pattern, inventory);
            if (pushed) {
                XTProfileRouteTracker.INSTANCE.recordDispatch(cpu, medium, pattern, XTProfileDispatchContext.target());
            }
            return pushed;
        } finally {
            XTProfileDispatchContext.end();
        }
    }

    @Inject(method = "recordReturnedOutputs", at = @At("HEAD"))
    private void xtprofile$returnedOutput(IAEStack<?> returnedStack, CallbackInfo ci) {
        XTProfileRouteTracker.INSTANCE.recordReturnedOutput((CraftingCPUCluster) (Object) this, returnedStack);
    }
}
