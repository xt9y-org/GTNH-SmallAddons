package com.xt9y.features.mixin.mixins.late;

import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.XT9YConfig;

import gregtech.api.objects.GTUOFluid;
import gregtech.common.UndergroundOil;

/** Return the seed-determined pristine underground-fluid yield without writing GT chunk depletion data. */
@Mixin(value = UndergroundOil.class, remap = false)
public abstract class MixinUndergroundOilNonDepleting {

    @Inject(
        method = "undergroundOil(Lnet/minecraft/world/World;IIF)Lnet/minecraftforge/fluids/FluidStack;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void xt9y$pristineFluid(World world, int chunkX, int chunkZ, float coefficient,
        CallbackInfoReturnable<FluidStack> cir) {
        if (!XT9YConfig.voidPumps || world == null || world.isRemote) return;

        Pair<GTUOFluid, Integer> deposit = UndergroundOil.getPristineAmount(world, chunkX, chunkZ);
        if (deposit == null || deposit.getLeft() == null || deposit.getLeft().getFluid() == null) {
            cir.setReturnValue(null);
            return;
        }

        double speed = coefficient < 0 ? -(double) coefficient : (double) coefficient;
        int amount = (int) Math.min(Integer.MAX_VALUE,
            Math.floor(deposit.getRight() * speed / UndergroundOil.DIVIDER));
        cir.setReturnValue(amount > 0 ? new FluidStack(deposit.getLeft().getFluid(), amount) : null);
    }
}
