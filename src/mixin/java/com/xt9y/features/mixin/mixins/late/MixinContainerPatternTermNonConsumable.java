package com.xt9y.features.mixin.mixins.late;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.NonConsumableNEITransfer;
import com.xt9y.features.NonConsumablePatternHelper;
import com.xt9y.features.api.INonConsumablePatternTerminal;

import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.sync.ActionHandler;
import appeng.container.sync.StreamCodecs;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.IntSyncHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class MixinContainerPatternTermNonConsumable implements INonConsumablePatternTerminal {

    @Shadow(remap = false)
    @Final
    private IAEStackInventory inputs;

    @Shadow(remap = false)
    @Final
    private SlotRestrictedInput patternSlotOUT;

    @Shadow(remap = false)
    public abstract boolean isCraftingMode();

    @Unique
    private IntSyncHandler xt9y$ncMaskSync;

    @Unique
    private ActionHandler<Integer> xt9y$toggleNcAction;

    @Unique
    private ItemStack xt9y$lastPatternSnapshot;

    @Unique
    private boolean xt9y$patternSnapshotInitialized;

    @Unique
    private int xt9y$encodingMask;

    @Inject(
        method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/api/storage/ITerminalHost;Z)V",
        at = @At("RETURN"))
    private void xt9y$initNcSync(InventoryPlayer inventoryPlayer, ITerminalHost terminal, boolean craftingModeSupport,
        CallbackInfo ci) {
        SyncRegistrar sync = ((AEBaseContainer) (Object) this).getSyncManager()
            .root()
            .child("xt9yNonConsumable");

        this.xt9y$ncMaskSync = sync.intS2C("mask");
        this.xt9y$toggleNcAction = sync.actionC2S("toggle", StreamCodecs.intValue())
            .onServerAction(this::xt9y$toggleOnServer);

        this.xt9y$loadMaskFromCurrentPattern();
    }

    @Unique
    private void xt9y$toggleOnServer(int slotIndex) {
        xt9y$refreshPatternState();
        if (isCraftingMode()) return;
        if (slotIndex < 0 || slotIndex >= inputs.getSizeInventory() || slotIndex >= Integer.SIZE - 1) return;

        IAEStack<?> stack = inputs.getAEStackInSlot(slotIndex);
        if (!(stack instanceof IAEItemStack)) return;

        int mask = xt9y$ncMaskSync.get() ^ (1 << slotIndex);
        xt9y$ncMaskSync.set(mask);
    }

    @Unique
    private void xt9y$loadMaskFromCurrentPattern() {
        ItemStack current = patternSlotOUT.getStack();
        int mask = isCraftingMode() ? 0 : NonConsumablePatternHelper.readMask(current);
        xt9y$ncMaskSync.setLocalValue(mask);
        xt9y$lastPatternSnapshot = current == null ? null : current.copy();
        xt9y$patternSnapshotInitialized = true;
    }

    @Unique
    private boolean xt9y$samePattern(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return ItemStack.areItemStacksEqual(a, b);
    }

    @Unique
    private void xt9y$refreshPatternState() {
        ItemStack current = patternSlotOUT.getStack();
        if (!xt9y$patternSnapshotInitialized) {
            xt9y$loadMaskFromCurrentPattern();
            return;
        }

        if (!xt9y$samePattern(current, xt9y$lastPatternSnapshot)) {
            int mask = isCraftingMode() ? 0 : NonConsumablePatternHelper.readMask(current);
            if (Platform.isServer()) xt9y$ncMaskSync.set(mask);
            else xt9y$ncMaskSync.setLocalValue(mask);
            xt9y$lastPatternSnapshot = current == null ? null : current.copy();
        }
    }

    @Unique
    private int xt9y$sanitizedMask() {
        int mask = xt9y$ncMaskSync.get();
        int sanitized = 0;
        int size = Math.min(inputs.getSizeInventory(), Integer.SIZE - 1);
        for (int slot = 0; slot < size; slot++) {
            if ((mask & (1 << slot)) == 0) continue;
            if (inputs.getAEStackInSlot(slot) instanceof IAEItemStack) {
                sanitized |= 1 << slot;
            }
        }
        return sanitized;
    }

    @Unique
    private void xt9y$importTransferredNonConsumables() {
        int mask = xt9y$ncMaskSync.get();
        boolean changed = false;
        int size = Math.min(inputs.getSizeInventory(), Integer.SIZE - 1);

        for (int slot = 0; slot < size; slot++) {
            IAEStack<?> stack = inputs.getAEStackInSlot(slot);
            if (!(stack instanceof IAEItemStack item)) continue;

            ItemStack itemStack = item.getItemStack();
            if (!NonConsumableNEITransfer.strip(itemStack)) continue;

            IAEItemStack clean = AEItemStack.create(itemStack);
            if (clean == null) continue;
            clean.setStackSize(item.getStackSize());
            inputs.putAEStackInSlot(slot, clean);
            mask |= 1 << slot;
            changed = true;
        }

        if (changed) {
            xt9y$ncMaskSync.set(mask);
            ((ContainerPatternTerm) (Object) this).inputsSync.markDirty();
        }
    }

    @Override
    public boolean xt9y$isNonConsumable(int slotIndex) {
        xt9y$refreshPatternState();
        return !isCraftingMode() && slotIndex >= 0
            && slotIndex < Integer.SIZE - 1
            && (xt9y$ncMaskSync.get() & (1 << slotIndex)) != 0;
    }

    @Override
    public void xt9y$requestToggleNonConsumable(int slotIndex) {
        xt9y$refreshPatternState();
        if (isCraftingMode() || xt9y$toggleNcAction == null) return;
        if (slotIndex < 0 || slotIndex >= inputs.getSizeInventory() || slotIndex >= Integer.SIZE - 1) return;
        if (!(inputs.getAEStackInSlot(slotIndex) instanceof IAEItemStack)) return;

        // Immediate client feedback. The server action sends the authoritative mask back afterwards.
        xt9y$ncMaskSync.setLocalValue(xt9y$ncMaskSync.get() ^ (1 << slotIndex));
        xt9y$toggleNcAction.send(slotIndex);
    }

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"))
    private void xt9y$refreshNcState(CallbackInfo ci) {
        xt9y$refreshPatternState();
        if (!Platform.isServer() || isCraftingMode()) return;
        xt9y$importTransferredNonConsumables();
        int sanitized = xt9y$sanitizedMask();
        if (sanitized != xt9y$ncMaskSync.get()) xt9y$ncMaskSync.set(sanitized);
    }

    @Inject(method = "encode", at = @At("HEAD"))
    private void xt9y$captureNcBeforeEncode(CallbackInfo ci) {
        if (isCraftingMode()) {
            xt9y$encodingMask = 0;
            return;
        }
        if (Platform.isServer()) xt9y$importTransferredNonConsumables();
        xt9y$encodingMask = xt9y$sanitizedMask();
    }

    @Inject(method = "encode", at = @At("TAIL"))
    private void xt9y$writeNcMetadata(CallbackInfo ci) {
        if (isCraftingMode()) return;
        ItemStack encoded = patternSlotOUT.getStack();
        if (encoded == null) return;

        xt9y$ncMaskSync.set(xt9y$encodingMask);
        int mask = xt9y$sanitizedMask();
        if (mask != xt9y$ncMaskSync.get()) xt9y$ncMaskSync.set(mask);
        NonConsumablePatternHelper.writeMask(encoded, mask);
        xt9y$lastPatternSnapshot = encoded.copy();
        xt9y$patternSnapshotInitialized = true;
        xt9y$encodingMask = 0;
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void xt9y$clearNcMetadata(CallbackInfo ci) {
        if (xt9y$ncMaskSync != null) xt9y$ncMaskSync.set(0);
    }

    @Inject(method = "setCraftingMode", at = @At("TAIL"))
    private void xt9y$clearNcWhenCrafting(boolean craftingMode, CallbackInfo ci) {
        if (craftingMode && xt9y$ncMaskSync != null) xt9y$ncMaskSync.set(0);
    }
}
