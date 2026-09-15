package com.xt9y.features;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class NonConsumableNEITransferTest {

    @Test
    void transferMarkerRoundTripPreservesOtherNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("keep", "value");

        NonConsumableNEITransfer.markTag(tag);

        assertTrue(tag.getBoolean(NonConsumableNEITransfer.NBT_KEY));
        assertTrue(NonConsumableNEITransfer.stripTag(tag));
        assertFalse(tag.hasKey(NonConsumableNEITransfer.NBT_KEY));
        assertTrue("value".equals(tag.getString("keep")));
    }

    @Test
    void unmarkedNbtIsNotConsumed() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("keep", 42);

        assertFalse(NonConsumableNEITransfer.stripTag(tag));
        assertTrue(tag.getInteger("keep") == 42);
    }
}
