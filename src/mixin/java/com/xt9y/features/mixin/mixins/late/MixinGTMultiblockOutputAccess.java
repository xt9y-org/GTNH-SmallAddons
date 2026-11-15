package com.xt9y.features.mixin.mixins.late;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public interface MixinGTMultiblockOutputAccess {

    @Invoker("canOutputAll")
    boolean xt9y$canOutputAll(ItemStack[] items);

    @Invoker("canOutputAll")
    boolean xt9y$canOutputAllFluids(FluidStack[] fluids);
}
