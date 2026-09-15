package com.xt9y.features.mixin.mixins.late;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.NonConsumableNEITransfer;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

@Pseudo
@Mixin(targets = "com.github.vfyjxf.nee.processor.GregTech5RecipeProcessor", remap = false)
public abstract class MixinGregTech5RecipeProcessorNonConsumable {

    @Unique
    private static final ThreadLocal<List<PositionedStack>> xt9y$sourceInputs = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<boolean[]> xt9y$sourceNc = new ThreadLocal<>();

    @Inject(method = "getRecipeInput", at = @At("HEAD"))
    private void xt9y$captureNonConsumables(IRecipeHandler recipe, int recipeIndex, String identifier,
        CallbackInfoReturnable<List<PositionedStack>> cir) {
        List<PositionedStack> inputs = recipe.getIngredientStacks(recipeIndex);
        if (inputs == null || inputs.isEmpty()) {
            xt9y$sourceInputs.remove();
            xt9y$sourceNc.remove();
            return;
        }

        boolean[] nc = new boolean[inputs.size()];
        boolean any = false;
        for (int i = 0; i < inputs.size(); i++) {
            PositionedStack stack = inputs.get(i);
            if (stack != null && stack.item != null && stack.item.stackSize == 0) {
                nc[i] = true;
                any = true;
            }
        }

        if (!any) {
            xt9y$sourceInputs.remove();
            xt9y$sourceNc.remove();
            return;
        }

        xt9y$sourceInputs.set(new ArrayList<>(inputs));
        xt9y$sourceNc.set(nc);
    }

    @Inject(method = "getRecipeInput", at = @At("RETURN"), cancellable = true)
    private void xt9y$restoreNonConsumables(IRecipeHandler recipe, int recipeIndex, String identifier,
        CallbackInfoReturnable<List<PositionedStack>> cir) {
        List<PositionedStack> source = xt9y$sourceInputs.get();
        boolean[] nc = xt9y$sourceNc.get();
        xt9y$sourceInputs.remove();
        xt9y$sourceNc.remove();
        if (source == null || nc == null) return;

        List<PositionedStack> current = cir.getReturnValue();
        if (current == null) current = new ArrayList<>();

        int ncCount = 0;
        for (boolean marked : nc) if (marked) ncCount++;

        List<PositionedStack> restored = new ArrayList<>(source.size());
        if (current.size() == source.size()) {
            restored.addAll(current);
            for (int i = 0; i < source.size(); i++) {
                if (!nc[i]) continue;
                restored.set(i, xt9y$taggedCopy(source.get(i)));
                if (source.get(i) != null && source.get(i).item != null) source.get(i).item.stackSize = 0;
            }
        } else if (current.size() == source.size() - ncCount) {
            int cursor = 0;
            for (int i = 0; i < source.size(); i++) {
                if (nc[i]) {
                    restored.add(xt9y$taggedCopy(source.get(i)));
                    if (source.get(i) != null && source.get(i).item != null) source.get(i).item.stackSize = 0;
                } else {
                    restored.add(current.get(cursor++));
                }
            }
        } else {
            restored.addAll(current);
            for (int i = 0; i < source.size(); i++) {
                if (!nc[i]) continue;
                restored.add(xt9y$taggedCopy(source.get(i)));
                if (source.get(i) != null && source.get(i).item != null) source.get(i).item.stackSize = 0;
            }
        }

        cir.setReturnValue(restored);
    }

    @Unique
    private static PositionedStack xt9y$taggedCopy(PositionedStack source) {
        PositionedStack copy = source.copy();
        for (ItemStack stack : copy.items) {
            if (stack == null) continue;
            if (stack.stackSize < 1) stack.stackSize = 1;
            NonConsumableNEITransfer.mark(stack);
        }
        if (copy.item != null) {
            if (copy.item.stackSize < 1) copy.item.stackSize = 1;
            NonConsumableNEITransfer.mark(copy.item);
        }
        return copy;
    }
}
