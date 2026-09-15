package com.xt9y.features;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Temporary transport metadata used only while NotEnoughEnergistics transfers an NC input. */
public final class NonConsumableNEITransfer {

    static final String NBT_KEY = "xt9yNcTransfer";

    private NonConsumableNEITransfer() {}

    public static void mark(ItemStack stack) {
        if (stack == null) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        markTag(tag);
    }

    public static boolean strip(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !stripTag(tag)) return false;
        if (tag.hasNoTags()) stack.setTagCompound(null);
        return true;
    }

    static void markTag(NBTTagCompound tag) {
        if (tag != null) tag.setBoolean(NBT_KEY, true);
    }

    static boolean stripTag(NBTTagCompound tag) {
        if (tag == null || !tag.getBoolean(NBT_KEY)) return false;
        tag.removeTag(NBT_KEY);
        return true;
    }
}
