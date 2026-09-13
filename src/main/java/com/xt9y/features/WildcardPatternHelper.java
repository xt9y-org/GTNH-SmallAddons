package com.xt9y.features;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.OrePrefixes.ParsedOreDictName;
import gregtech.api.util.GTOreDictUnificator;

public class WildcardPatternHelper {

    public static List<ICraftingPatternDetails> expandWildcard(ICraftingPatternDetails originalPattern,
        ItemStack patternStack, World world) {
        List<ICraftingPatternDetails> result = new ArrayList<>();

        IAEStack<?>[] inputs = originalPattern.getAEInputs();
        IAEStack<?>[] outputs = originalPattern.getAEOutputs();

        List<SlotOreInfo> inputOreInfo = new ArrayList<>();
        List<SlotOreInfo> outputOreInfo = new ArrayList<>();
        String commonMaterial = null;
        boolean hasVariableSlots = false;

        for (IAEStack<?> input : inputs) {
            if (input == null || !(input instanceof IAEItemStack itemStack)) {
                inputOreInfo.add(null);
                continue;
            }
            SlotOreInfo info = parseOreInfo(itemStack.getItemStack());
            inputOreInfo.add(info);
            if (info != null) {
                hasVariableSlots = true;
                if (commonMaterial == null) {
                    commonMaterial = info.materialString;
                } else if (!commonMaterial.equalsIgnoreCase(info.materialString)) {
                    return List.of(originalPattern);
                }
            }
        }

        for (IAEStack<?> output : outputs) {
            if (output == null || !(output instanceof IAEItemStack itemStack)) {
                outputOreInfo.add(null);
                continue;
            }
            SlotOreInfo info = parseOreInfo(itemStack.getItemStack());
            outputOreInfo.add(info);
            if (info != null) {
                hasVariableSlots = true;
                if (commonMaterial == null) {
                    commonMaterial = info.materialString;
                } else if (!commonMaterial.equalsIgnoreCase(info.materialString)) {
                    return List.of(originalPattern);
                }
            }
        }

        if (!hasVariableSlots) {
            return List.of(originalPattern);
        }

        for (Materials mat : Materials.getAll()) {
            if (mat == null || mat == Materials._NULL) continue;
            if (!mat.mUnifiable) continue;

            IAEStack<?>[] resolvedInputs = new IAEStack<?>[inputs.length];
            boolean valid = true;

            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] == null) {
                    resolvedInputs[i] = null;
                    continue;
                }
                SlotOreInfo info = inputOreInfo.get(i);
                if (info == null) {
                    resolvedInputs[i] = inputs[i];
                } else {
                    IAEItemStack resolved = resolveMaterialStack(info, mat, inputs[i].getStackSize());
                    if (resolved == null) {
                        valid = false;
                        break;
                    }
                    resolvedInputs[i] = resolved;
                }
            }
            if (!valid) continue;

            IAEStack<?>[] resolvedOutputs = new IAEStack<?>[outputs.length];
            for (int i = 0; i < outputs.length; i++) {
                if (outputs[i] == null) {
                    resolvedOutputs[i] = null;
                    continue;
                }
                SlotOreInfo info = outputOreInfo.get(i);
                if (info == null) {
                    resolvedOutputs[i] = outputs[i];
                } else {
                    IAEItemStack resolved = resolveMaterialStack(info, mat, outputs[i].getStackSize());
                    if (resolved == null) {
                        valid = false;
                        break;
                    }
                    resolvedOutputs[i] = resolved;
                }
            }
            if (!valid) continue;

            boolean sameItemMismatch = false;
            for (IAEStack<?> ri : resolvedInputs) {
                if (ri == null) continue;
                for (IAEStack<?> ro : resolvedOutputs) {
                    if (ro == null) continue;
                    if (ri instanceof IAEItemStack ii && ro instanceof IAEItemStack io) {
                        if (ii.getItemStack()
                            .isItemEqual(io.getItemStack())) {
                            sameItemMismatch = true;
                            break;
                        }
                    }
                }
                if (sameItemMismatch) break;
            }
            if (sameItemMismatch) continue;

            WildcardPatternDetails expandedDetail = new WildcardPatternDetails(
                patternStack,
                resolvedInputs,
                resolvedOutputs,
                originalPattern.isCraftable());
            result.add(expandedDetail);
        }

        return result;
    }

    private static IAEItemStack resolveMaterialStack(SlotOreInfo info, Materials material, long amount) {
        ItemStack canonical = GTOreDictUnificator.get(info.prefix, material, 1L);
        if (canonical == null) return null;

        IAEItemStack resolved = AEApi.instance()
            .storage()
            .createItemStack(canonical);
        return resolved == null ? null : resolved.setStackSize(amount);
    }

    public static SlotOreInfo parseOreInfo(ItemStack stack) {
        if (stack == null) return null;
        List<ParsedOreDictName> parsed = OrePrefixes.detectPrefix(stack);
        if (parsed.isEmpty()) return null;

        for (ParsedOreDictName p : parsed) {
            if (p == null || p.prefix == null || p.material == null) continue;
            if (!p.prefix.isMaterialBased()) continue;
            Materials mat = Materials.get(p.material);
            if (mat != null && mat != Materials._NULL && mat.mUnifiable) {
                return new SlotOreInfo(p.prefix, p.material);
            }
        }
        return null;
    }

    public static class SlotOreInfo {

        public final OrePrefixes prefix;
        public final String materialString;

        SlotOreInfo(OrePrefixes prefix, String materialString) {
            this.prefix = prefix;
            this.materialString = materialString;
        }
    }

    public static class WildcardPatternDetails implements ICraftingPatternDetails {

        private final ItemStack pattern;
        private final IAEStack<?>[] inputs;
        private final IAEStack<?>[] outputs;
        private final boolean craftable;

        public WildcardPatternDetails(ItemStack pattern, IAEStack<?>[] inputs, IAEStack<?>[] outputs,
            boolean craftable) {
            this.pattern = pattern;
            this.inputs = copyAEStackArray(inputs);
            this.outputs = copyAEStackArray(outputs);
            this.craftable = craftable;
        }

        private static IAEStack<?>[] copyAEStackArray(IAEStack<?>[] src) {
            IAEStack<?>[] dst = new IAEStack<?>[src.length];
            System.arraycopy(src, 0, dst, 0, src.length);
            return dst;
        }

        @Override
        public ItemStack getPattern() {
            return pattern.copy();
        }

        @Override
        public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
            if (slotIndex < 0 || slotIndex >= inputs.length) return false;
            IAEStack<?> aeStack = inputs[slotIndex];
            if (aeStack instanceof IAEItemStack aeItem) {
                return aeItem.isMeaningful() && aeItem.getItemStack()
                    .isItemEqual(itemStack);
            }
            return false;
        }

        @Override
        public boolean isCraftable() {
            return craftable;
        }

        @Override
        public IAEItemStack[] getInputs() {
            IAEItemStack[] result = new IAEItemStack[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] instanceof IAEItemStack ae) {
                    result[i] = ae;
                } else {
                    result[i] = null;
                }
            }
            return result;
        }

        @Override
        public IAEItemStack[] getCondensedInputs() {
            return condense(getInputs());
        }

        @Override
        public IAEItemStack[] getCondensedOutputs() {
            return condense(getOutputs());
        }

        private static IAEItemStack[] condense(IAEItemStack[] items) {
            LinkedHashMap<Integer, IAEItemStack> map = new LinkedHashMap<>();
            for (IAEItemStack stack : items) {
                if (stack == null) continue;
                ItemStack is = stack.getItemStack();
                int key = is.getItem()
                    .hashCode() * 31 + is.getItemDamage();
                IAEItemStack existing = map.get(key);
                if (existing != null) {
                    map.put(
                        key,
                        existing.copy()
                            .setStackSize(existing.getStackSize() + stack.getStackSize()));
                } else {
                    map.put(key, stack);
                }
            }
            return map.values()
                .toArray(new IAEItemStack[0]);
        }

        @Override
        public IAEItemStack[] getOutputs() {
            IAEItemStack[] result = new IAEItemStack[outputs.length];
            for (int i = 0; i < outputs.length; i++) {
                if (outputs[i] instanceof IAEItemStack ae) {
                    result[i] = ae;
                } else {
                    result[i] = null;
                }
            }
            return result;
        }

        @Override
        public boolean canSubstitute() {
            return false;
        }

        @Override
        public ItemStack getOutput(net.minecraft.inventory.InventoryCrafting craftingInv, World world) {
            if (outputs.length > 0 && outputs[0] instanceof IAEItemStack ae) {
                return ae.getItemStack();
            }
            return null;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public void setPriority(int priority) {}

        @Override
        public IAEStack<?>[] getAEInputs() {
            return copyAEStackArray(inputs);
        }

        @Override
        public IAEStack<?>[] getAEOutputs() {
            return copyAEStackArray(outputs);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WildcardPatternDetails that = (WildcardPatternDetails) o;
            return ItemStack.areItemStacksEqual(pattern, that.pattern) && inputsEquals(that);
        }

        private boolean inputsEquals(WildcardPatternDetails other) {
            if (inputs.length != other.inputs.length) return false;
            for (int i = 0; i < inputs.length; i++) {
                if (!stackEquals(inputs[i], other.inputs[i])) return false;
            }
            for (int i = 0; i < outputs.length; i++) {
                if (!stackEquals(outputs[i], other.outputs[i])) return false;
            }
            return true;
        }

        private static boolean stackEquals(IAEStack<?> a, IAEStack<?> b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if (a instanceof IAEItemStack aa && b instanceof IAEItemStack bb) {
                return aa.getItemStack()
                    .isItemEqual(bb.getItemStack()) && aa.getStackSize() == bb.getStackSize();
            }
            return a.equals(b);
        }

        @Override
        public int hashCode() {
            int h = Objects.hash(pattern.getItem(), pattern.getItemDamage());
            for (IAEStack<?> s : inputs) {
                if (s instanceof IAEItemStack is) {
                    h = h * 31 + is.hashCode();
                }
            }
            for (IAEStack<?> s : outputs) {
                if (s instanceof IAEItemStack is) {
                    h = h * 31 + is.hashCode();
                }
            }
            return h;
        }
    }
}
