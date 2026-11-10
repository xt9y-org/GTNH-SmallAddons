package com.xt9y.features.mixin.mixins.late;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.xt9y.features.XT9YConfig;

import gregtech.common.tileentities.machines.basic.MTEPump;

/** GT's LV+ world-fluid pump keeps its fluid blocks when the option is enabled. */
@Mixin(value = MTEPump.class, remap = false)
public abstract class MixinMTEPumpNonDepleting {

    @Redirect(
        method = "consumeFluid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/fluids/IFluidBlock;drain(Lnet/minecraft/world/World;IIIZ)Lnet/minecraftforge/fluids/FluidStack;"),
        remap = false)
    private FluidStack xt9y$simulateFluidDrain(IFluidBlock block, World world, int x, int y, int z, boolean doDrain) {
        return block.drain(world, x, y, z, doDrain && !XT9YConfig.voidPumps);
    }

    @Redirect(
        method = "consumeFluid",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockToAir(III)Z"),
        remap = false)
    private boolean xt9y$preserveForgeFluid(World world, int x, int y, int z) {
        return XT9YConfig.voidPumps || world.setBlockToAir(x, y, z);
    }

    @Redirect(
        method = "consumeFluid",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z"),
        remap = false)
    private boolean xt9y$preserveFluidSource(World world, int x, int y, int z, Block block, int meta, int flags) {
        return XT9YConfig.voidPumps || world.setBlock(x, y, z, block, meta, flags);
    }
}
