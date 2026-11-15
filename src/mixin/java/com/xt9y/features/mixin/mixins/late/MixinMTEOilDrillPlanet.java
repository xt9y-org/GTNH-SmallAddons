package com.xt9y.features.mixin.mixins.late;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import gregtech.common.tileentities.machines.multi.MTEOilDrillBase;

/** Preserve subclass fluid output multipliers, including the Infinite Fluid Drilling Rig. */
@Mixin(value = MTEOilDrillBase.class, remap = false)
public interface MixinMTEOilDrillPlanet {

    @Invoker("adjustPumpedOil")
    FluidStack xt9y$adjustFluid(FluidStack fluid);
}
