package com.xt9y.features.mixin.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.NonConsumableNEITransfer;

import codechicken.nei.recipe.IRecipeHandler;

@Pseudo
@Mixin(targets = "com.github.vfyjxf.nee.nei.NEEPatternTerminalHandler", remap = false)
public abstract class MixinNEEPatternTerminalNonConsumable {

    @Unique
    private static final ThreadLocal<Boolean> xt9y$currentNc = new ThreadLocal<>();

    @Inject(method = "packProcessRecipe", at = @At("HEAD"))
    private void xt9y$beginTransfer(IRecipeHandler recipe, int recipeIndex, int multiplier,
        CallbackInfoReturnable<?> cir) {
        xt9y$currentNc.set(false);
    }

    @ModifyArg(
        method = "packProcessRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/vfyjxf/nee/utils/ItemUtils;isInBlackList(Lnet/minecraft/item/ItemStack;Ljava/lang/String;Ljava/lang/String;)Z"),
        index = 0)
    private ItemStack xt9y$stripMarkerBeforeFiltering(ItemStack stack) {
        xt9y$currentNc.set(NonConsumableNEITransfer.strip(stack));
        return stack;
    }

    @ModifyArg(
        method = "packProcessRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/vfyjxf/nee/utils/ItemUtils;writeItemStackToNBT(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/nbt/NBTTagCompound;",
            ordinal = 0),
        index = 0)
    private ItemStack xt9y$markSerializedInput(ItemStack stack) {
        boolean nc = Boolean.TRUE.equals(xt9y$currentNc.get());
        xt9y$currentNc.set(false);
        if (!nc || stack == null) return stack;

        ItemStack copy = stack.copy();
        NonConsumableNEITransfer.mark(copy);
        return copy;
    }

    @Inject(method = "packProcessRecipe", at = @At("RETURN"))
    private void xt9y$endTransfer(IRecipeHandler recipe, int recipeIndex, int multiplier,
        CallbackInfoReturnable<?> cir) {
        xt9y$currentNc.remove();
    }
}
