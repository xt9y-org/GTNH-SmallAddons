package com.xt9y.features.mixin.mixins.late;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.NonConsumableCRIBRuntime;
import com.xt9y.features.XT9YConfig;
import com.xt9y.features.api.INonConsumablePatternDetails;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.Platform;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

@Mixin(value = MTEHatchCraftingInputME.class, remap = false)
public abstract class MixinCRIBNonConsumable {

    @Shadow(remap = false)
    public abstract AENetworkProxy getProxy();

    @Shadow(remap = false)
    private BaseActionSource requestSource;

    @Shadow(remap = false)
    private PatternSlot<MTEHatchCraftingInputME>[] internalInventory;

    @Shadow(remap = false)
    private Map<ICraftingPatternDetails, PatternSlot<MTEHatchCraftingInputME>> patternDetailsPatternSlotMap;

    /**
     * Items removed from ME and owned by one active CRIB pattern slot. They are deliberately not inserted into the
     * PatternSlot item inventory: that inventory is GregTech's consumable input storage.
     */
    @Unique
    private final IdentityHashMap<PatternSlot<MTEHatchCraftingInputME>, Map<GTUtility.ItemId, Integer>> xt9y$borrowedNc = new IdentityHashMap<>();

    @Unique
    private BaseActionSource xt9y$requestSource() {
        if (requestSource == null) {
            requestSource = new MachineSource((IActionHost) ((IMetaTileEntity) this).getBaseMetaTileEntity());
        }
        return requestSource;
    }

    @Unique
    private int xt9y$borrowedAmount(PatternSlot<MTEHatchCraftingInputME> slot, GTUtility.ItemId id) {
        Map<GTUtility.ItemId, Integer> borrowed = xt9y$borrowedNc.get(slot);
        return borrowed == null ? 0 : borrowed.getOrDefault(id, 0);
    }

    @Unique
    private void xt9y$recordBorrowed(PatternSlot<MTEHatchCraftingInputME> slot, ItemStack stack, int amount) {
        if (amount <= 0) return;
        xt9y$borrowedNc.computeIfAbsent(slot, ignored -> new HashMap<>())
            .merge(GTUtility.ItemId.create(stack), amount, Integer::sum);
        xt9y$syncRuntime(slot);
    }

    @Unique
    private void xt9y$syncRuntime(PatternSlot<MTEHatchCraftingInputME> slot) {
        Map<GTUtility.ItemId, Integer> borrowed = xt9y$borrowedNc.get(slot);
        if (borrowed == null || borrowed.isEmpty()) {
            NonConsumableCRIBRuntime.clear(slot);
            return;
        }

        ItemStack[] inputs = new ItemStack[borrowed.size()];
        int index = 0;
        for (Map.Entry<GTUtility.ItemId, Integer> entry : borrowed.entrySet()) {
            ItemStack stack = entry.getKey()
                .getItemStack();
            stack.stackSize = Math.max(1, entry.getValue());
            inputs[index++] = stack;
        }
        NonConsumableCRIBRuntime.set(slot, inputs);
    }

    @Unique
    private boolean xt9y$ensureNonConsumables(PatternSlot<MTEHatchCraftingInputME> slot,
        INonConsumablePatternDetails details) {
        IAEItemStack[] requirements = details.xt9y$getNonConsumableInputs();
        if (requirements.length == 0) return true;

        try {
            IMEMonitor<IAEItemStack> storage = getProxy().getStorage()
                .getItemInventory();
            BaseActionSource source = xt9y$requestSource();
            List<IAEItemStack> missing = new ArrayList<>();

            // First simulate every missing reservation so a partial request cannot steal some catalysts and then fail.
            for (IAEItemStack required : requirements) {
                ItemStack requiredStack = required.getItemStack();
                GTUtility.ItemId id = GTUtility.ItemId.create(requiredStack);
                long encodedAmount = Math.max(1L, required.getStackSize());
                long amount = encodedAmount - xt9y$borrowedAmount(slot, id);
                if (amount <= 0) continue;

                IAEItemStack request = required.copy()
                    .setStackSize(amount);
                IAEItemStack simulated = Platform
                    .poweredExtraction(getProxy().getEnergy(), storage, request, source, Actionable.SIMULATE);
                if (simulated == null || simulated.getStackSize() < amount) return false;
                missing.add(request);
            }

            List<IAEItemStack> extracted = new ArrayList<>();
            for (IAEItemStack request : missing) {
                IAEItemStack got = Platform.poweredExtraction(getProxy().getEnergy(), storage, request, source);
                if (got == null || got.getStackSize() < request.getStackSize()) {
                    if (got != null && got.getStackSize() > 0) extracted.add(got);
                    xt9y$rollbackExtraction(slot, storage, source, extracted);
                    return false;
                }
                extracted.add(got);
            }

            // Record ownership only. ProcessingLogic receives disposable synthetic copies through
            // NonConsumableCRIBRuntime; the real borrowed item never enters PatternSlot.itemInventory.
            for (IAEItemStack got : extracted) {
                xt9y$recordBorrowed(slot, got.getItemStack(), (int) got.getStackSize());
            }
            xt9y$syncRuntime(slot);
            return true;
        } catch (GridAccessException ignored) {
            return false;
        }
    }

    @Unique
    private void xt9y$rollbackExtraction(PatternSlot<MTEHatchCraftingInputME> slot, IMEMonitor<IAEItemStack> storage,
        BaseActionSource source, List<IAEItemStack> extracted) throws GridAccessException {
        for (IAEItemStack stack : extracted) {
            IAEItemStack rest = Platform.poweredInsert(getProxy().getEnergy(), storage, stack, source);
            if (rest == null || rest.getStackSize() <= 0) continue;

            // If ME cannot take the rollback, retain ownership outside the CRIB and retry from onPostTick.
            xt9y$recordBorrowed(slot, rest.getItemStack(), (int) rest.getStackSize());
        }
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void xt9y$borrowNonConsumables(ICraftingPatternDetails patternDetails,
        net.minecraft.inventory.InventoryCrafting table, CallbackInfoReturnable<Boolean> cir) {
        if (!XT9YConfig.cribNonConsumable) return;
        if (!(patternDetails instanceof INonConsumablePatternDetails ncDetails)) return;

        PatternSlot<MTEHatchCraftingInputME> slot = patternDetailsPatternSlotMap.get(patternDetails);
        if (slot == null || !xt9y$ensureNonConsumables(slot, ncDetails)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private boolean xt9y$returnReservation(Map<GTUtility.ItemId, Integer> borrowed, IMEMonitor<IAEItemStack> storage,
        BaseActionSource source) throws GridAccessException {
        Iterator<Map.Entry<GTUtility.ItemId, Integer>> iterator = borrowed.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<GTUtility.ItemId, Integer> entry = iterator.next();
            ItemStack stack = entry.getKey()
                .getItemStack();
            stack.stackSize = entry.getValue();
            IAEItemStack aeStack = AEApi.instance()
                .storage()
                .createItemStack(stack);
            IAEItemStack rest = Platform.poweredInsert(getProxy().getEnergy(), storage, aeStack, source);
            int remaining = rest == null ? 0 : (int) rest.getStackSize();
            if (remaining <= 0) iterator.remove();
            else entry.setValue(remaining);
        }
        return borrowed.isEmpty();
    }

    @Inject(method = "onPostTick", at = @At("TAIL"))
    private void xt9y$returnUnusedNonConsumables(IGregTechTileEntity base, long timer, CallbackInfo ci) {
        if (!base.isServerSide() || xt9y$borrowedNc.isEmpty()) return;

        try {
            IMEMonitor<IAEItemStack> storage = getProxy().getStorage()
                .getItemInventory();
            BaseActionSource source = xt9y$requestSource();

            Iterator<Map.Entry<PatternSlot<MTEHatchCraftingInputME>, Map<GTUtility.ItemId, Integer>>> iterator = xt9y$borrowedNc
                .entrySet()
                .iterator();
            while (iterator.hasNext()) {
                Map.Entry<PatternSlot<MTEHatchCraftingInputME>, Map<GTUtility.ItemId, Integer>> entry = iterator.next();
                PatternSlot<MTEHatchCraftingInputME> slot = entry.getKey();

                // Current slots keep their reservation while any real consumable item/fluid is still waiting. A stale
                // slot (pattern removed/replaced) is refunded immediately.
                if (xt9y$isCurrentSlot(slot) && (!slot.isItemEmpty() || !slot.isFluidEmpty())) continue;

                if (xt9y$returnReservation(entry.getValue(), storage, source)) {
                    NonConsumableCRIBRuntime.clear(slot);
                    iterator.remove();
                } else {
                    xt9y$syncRuntime(slot);
                }
            }
        } catch (GridAccessException ignored) {}
    }

    @Inject(method = "refundAll", at = @At("TAIL"))
    private void xt9y$refundAllReservations(boolean shouldDrop, CallbackInfo ci) {
        if (xt9y$borrowedNc.isEmpty()) return;

        try {
            IMEMonitor<IAEItemStack> storage = getProxy().getStorage()
                .getItemInventory();
            BaseActionSource source = xt9y$requestSource();
            Iterator<Map.Entry<PatternSlot<MTEHatchCraftingInputME>, Map<GTUtility.ItemId, Integer>>> iterator = xt9y$borrowedNc
                .entrySet()
                .iterator();
            while (iterator.hasNext()) {
                Map.Entry<PatternSlot<MTEHatchCraftingInputME>, Map<GTUtility.ItemId, Integer>> entry = iterator.next();
                if (xt9y$returnReservation(entry.getValue(), storage, source)) {
                    NonConsumableCRIBRuntime.clear(entry.getKey());
                    iterator.remove();
                }
            }
        } catch (GridAccessException ignored) {}
    }

    @Unique
    private boolean xt9y$isCurrentSlot(PatternSlot<MTEHatchCraftingInputME> candidate) {
        for (PatternSlot<MTEHatchCraftingInputME> slot : internalInventory) {
            if (slot == candidate) return true;
        }
        return false;
    }

    /**
     * v1.0.5 stored the borrowed catalyst physically in PatternSlot.itemInventory. When upgrading an existing world,
     * remove the reserved amount from that consumable inventory and keep it represented only by the reservation map.
     */
    @Unique
    private void xt9y$detachLegacyPhysicalReservation(PatternSlot<MTEHatchCraftingInputME> slot,
        Map<GTUtility.ItemId, Integer> borrowed) {
        Map<GTUtility.ItemId, Integer> remaining = new HashMap<>(borrowed);
        for (ItemStack stack : slot.getItemInputs()) {
            if (stack == null || stack.stackSize <= 0) continue;
            GTUtility.ItemId id = GTUtility.ItemId.create(stack);
            int remove = Math.min(stack.stackSize, remaining.getOrDefault(id, 0));
            if (remove <= 0) continue;

            stack.stackSize -= remove;
            int left = remaining.get(id) - remove;
            if (left <= 0) remaining.remove(id);
            else remaining.put(id, left);
            if (remaining.isEmpty()) break;
        }
        slot.updateSlotItems();
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void xt9y$saveBorrowedNonConsumables(NBTTagCompound nbt, CallbackInfo ci) {
        NBTTagList slots = new NBTTagList();

        for (int index = 0; index < internalInventory.length; index++) {
            PatternSlot<MTEHatchCraftingInputME> slot = internalInventory[index];
            if (slot == null) continue;

            Map<GTUtility.ItemId, Integer> borrowed = xt9y$borrowedNc.get(slot);
            if (borrowed == null || borrowed.isEmpty()) continue;

            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("slot", index);
            NBTTagList items = new NBTTagList();

            for (Map.Entry<GTUtility.ItemId, Integer> entry : borrowed.entrySet()) {
                ItemStack stack = entry.getKey()
                    .getItemStack();
                stack.stackSize = entry.getValue();
                items.appendTag(GTUtility.saveItem(stack));
            }

            slotTag.setTag("items", items);
            slots.appendTag(slotTag);
        }

        if (slots.tagCount() > 0) nbt.setTag("xt9yBorrowedNc", slots);
        else nbt.removeTag("xt9yBorrowedNc");
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void xt9y$loadBorrowedNonConsumables(NBTTagCompound nbt, CallbackInfo ci) {
        for (PatternSlot<MTEHatchCraftingInputME> slot : xt9y$borrowedNc.keySet()) {
            NonConsumableCRIBRuntime.clear(slot);
        }
        xt9y$borrowedNc.clear();

        NBTTagList slots = nbt.getTagList("xt9yBorrowedNc", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int index = slotTag.getInteger("slot");
            if (index < 0 || index >= internalInventory.length) continue;

            PatternSlot<MTEHatchCraftingInputME> slot = internalInventory[index];
            if (slot == null) continue;

            NBTTagList items = slotTag.getTagList("items", Constants.NBT.TAG_COMPOUND);
            Map<GTUtility.ItemId, Integer> borrowed = new HashMap<>();
            for (int j = 0; j < items.tagCount(); j++) {
                ItemStack stack = GTUtility.loadItem(items.getCompoundTagAt(j));
                if (stack == null || stack.stackSize <= 0) continue;
                borrowed.merge(GTUtility.ItemId.create(stack), stack.stackSize, Integer::sum);
            }

            if (!borrowed.isEmpty()) {
                xt9y$borrowedNc.put(slot, borrowed);
                xt9y$detachLegacyPhysicalReservation(slot, borrowed);
                xt9y$syncRuntime(slot);
            }
        }
    }
}
