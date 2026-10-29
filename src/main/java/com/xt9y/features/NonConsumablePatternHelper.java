package com.xt9y.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.xt9y.features.api.INonConsumablePatternDetails;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.util.GTUtility;

public final class NonConsumablePatternHelper {

    public static final String NBT_KEY = "xt9yNcInputs";

    private NonConsumablePatternHelper() {}

    public static int readMask(ItemStack patternStack) {
        if (patternStack == null || patternStack.getTagCompound() == null) return 0;
        int mask = 0;
        for (int slot : patternStack.getTagCompound()
            .getIntArray(NBT_KEY)) {
            if (slot >= 0 && slot < Integer.SIZE - 1) {
                mask |= 1 << slot;
            }
        }
        return mask;
    }

    public static void writeMask(ItemStack patternStack, int mask) {
        if (patternStack == null) return;
        NBTTagCompound tag = patternStack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            patternStack.setTagCompound(tag);
        }

        if (mask == 0) {
            tag.removeTag(NBT_KEY);
            return;
        }

        int count = Integer.bitCount(mask);
        int[] slots = new int[count];
        int cursor = 0;
        for (int slot = 0; slot < Integer.SIZE - 1; slot++) {
            if ((mask & (1 << slot)) != 0) slots[cursor++] = slot;
        }
        tag.setIntArray(NBT_KEY, slots);
    }

    public static ICraftingPatternDetails wrap(ICraftingPatternDetails details, ItemStack patternStack) {
        if (!XT9YConfig.cribNonConsumable) return details;
        int mask = readMask(patternStack);
        if (mask == 0 || details.isCraftable()) return details;

        IAEStack<?>[] inputs = details.getAEInputs();
        int validMask = 0;
        for (int slot = 0; slot < inputs.length && slot < Integer.SIZE - 1; slot++) {
            if ((mask & (1 << slot)) != 0 && inputs[slot] instanceof IAEItemStack) {
                validMask |= 1 << slot;
            }
        }
        if (validMask == 0) return details;
        return new NonConsumablePatternDetails(details, validMask);
    }

    private static IAEItemStack[] condense(IAEItemStack[] stacks) {
        List<IAEItemStack> result = new ArrayList<>();
        for (IAEItemStack stack : stacks) {
            if (stack == null) continue;
            IAEItemStack match = null;
            for (IAEItemStack existing : result) {
                if (GTUtility.areStacksEqual(existing.getItemStack(), stack.getItemStack())) {
                    match = existing;
                    break;
                }
            }
            if (match == null) {
                result.add(stack.copy());
            } else {
                match.setStackSize(match.getStackSize() + stack.getStackSize());
            }
        }
        return result.toArray(new IAEItemStack[0]);
    }

    private static IAEStack<?>[] condenseAE(IAEStack<?>[] stacks) {
        List<IAEStack<?>> result = new ArrayList<>();
        for (IAEStack<?> stack : stacks) {
            if (stack == null) continue;
            IAEStack<?> match = null;
            for (IAEStack<?> existing : result) {
                if (existing.isSameType(stack)) {
                    match = existing;
                    break;
                }
            }
            if (match == null) {
                result.add(stack.copy());
            } else {
                mergeStacks(match, stack);
            }
        }
        return result.toArray(new IAEStack<?>[0]);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void mergeStacks(IAEStack target, IAEStack source) {
        target.add(source);
    }

    public static final class NonConsumablePatternDetails
        implements ICraftingPatternDetails, INonConsumablePatternDetails {

        private final ICraftingPatternDetails delegate;
        private final int nonConsumableMask;
        private final IAEStack<?>[] originalInputs;
        private final IAEStack<?>[] aeInputs;
        private final IAEItemStack[] itemInputs;
        private final IAEItemStack[] nonConsumableInputs;

        private NonConsumablePatternDetails(ICraftingPatternDetails delegate, int nonConsumableMask) {
            this.delegate = delegate;
            this.nonConsumableMask = nonConsumableMask;
            this.originalInputs = copy(delegate.getAEInputs());
            this.aeInputs = copy(this.originalInputs);
            this.itemInputs = new IAEItemStack[this.aeInputs.length];

            List<IAEItemStack> nc = new ArrayList<>();
            for (int slot = 0; slot < this.aeInputs.length; slot++) {
                IAEStack<?> stack = this.aeInputs[slot];
                if ((nonConsumableMask & (1 << slot)) != 0 && stack instanceof IAEItemStack item) {
                    nc.add(item.copy());
                    this.aeInputs[slot] = null;
                } else if (stack instanceof IAEItemStack item) {
                    this.itemInputs[slot] = item;
                }
            }
            this.nonConsumableInputs = condense(nc.toArray(new IAEItemStack[0]));
        }

        private static IAEStack<?>[] copy(IAEStack<?>[] src) {
            IAEStack<?>[] copy = new IAEStack<?>[src.length];
            for (int i = 0; i < src.length; i++) {
                copy[i] = src[i] == null ? null : src[i].copy();
            }
            return copy;
        }

        @Override
        public IAEItemStack[] xt9y$getNonConsumableInputs() {
            IAEItemStack[] copy = new IAEItemStack[nonConsumableInputs.length];
            for (int i = 0; i < copy.length; i++) copy[i] = nonConsumableInputs[i].copy();
            return copy;
        }

        @Override
        public ICraftingPatternDetails xt9y$getDelegate() {
            return delegate;
        }

        @Override
        public ItemStack getPattern() {
            return delegate.getPattern();
        }

        @Override
        public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
            return slotIndex >= 0 && slotIndex < aeInputs.length
                && (nonConsumableMask & (1 << slotIndex)) == 0
                && delegate.isValidItemForSlot(slotIndex, itemStack, world);
        }

        @Override
        public boolean isCraftable() {
            return delegate.isCraftable();
        }

        @Override
        public IAEItemStack[] getInputs() {
            IAEItemStack[] copy = new IAEItemStack[itemInputs.length];
            for (int i = 0; i < copy.length; i++) copy[i] = itemInputs[i] == null ? null : itemInputs[i].copy();
            return copy;
        }

        @Override
        public IAEItemStack[] getCondensedInputs() {
            return condense(getInputs());
        }

        @Override
        public IAEItemStack[] getCondensedOutputs() {
            return delegate.getCondensedOutputs();
        }

        @Override
        public IAEItemStack[] getOutputs() {
            return delegate.getOutputs();
        }

        @Override
        public boolean canSubstitute() {
            return delegate.canSubstitute();
        }

        @Override
        public boolean canBeSubstitute() {
            return delegate.canBeSubstitute();
        }

        @Override
        public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
            return delegate.getOutput(craftingInv, world);
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

        @Override
        public void setPriority(int priority) {
            delegate.setPriority(priority);
        }

        @Override
        public IAEStack<?>[] getAEInputs() {
            return copy(aeInputs);
        }

        @Override
        public IAEStack<?>[] getCondensedAEInputs() {
            return condenseAE(getAEInputs());
        }

        @Override
        public IAEStack<?>[] getAEOutputs() {
            return delegate.getAEOutputs();
        }

        @Override
        public IAEStack<?>[] getCondensedAEOutputs() {
            return delegate.getCondensedAEOutputs();
        }

        @Override
        public boolean isInputOnly() {
            return delegate.isInputOnly();
        }

        @Override
        public java.util.UUID getInputOnlyUuid() {
            return delegate.getInputOnlyUuid();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NonConsumablePatternDetails that)) return false;
            return nonConsumableMask == that.nonConsumableMask && delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delegate, nonConsumableMask);
        }
    }
}
