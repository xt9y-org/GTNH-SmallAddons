package com.xt9y.features.xtprofile;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IIcon;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IInterfaceViewable;
import appeng.helpers.ICustomNameObject;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

final class XTProfileLabels {

    static final class StackInfo {

        final String key;
        final String name;
        final String itemId;
        final int damage;
        final String texture;
        final long amount;

        StackInfo(String key, String name, String itemId, int damage, String texture, long amount) {
            this.key = key;
            this.name = name;
            this.itemId = itemId;
            this.damage = damage;
            this.texture = texture;
            this.amount = amount;
        }
    }

    static String pattern(ICraftingPatternDetails pattern) {
        if (pattern == null) return "unknown pattern";
        IAEStack<?>[] outputs = pattern.getCondensedAEOutputs();
        if (outputs == null || outputs.length == 0) return "processing pattern";
        return stack(outputs[0]);
    }

    static StackInfo primaryOutput(ICraftingPatternDetails pattern) {
        if (pattern == null) return null;
        IAEStack<?>[] outputs = pattern.getCondensedAEOutputs();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return null;
        return describe(outputs[0]);
    }

    static StackInfo describe(IAEStack<?> stack) {
        if (stack == null) return null;
        if (stack instanceof IAEItemStack) {
            IAEItemStack ae = (IAEItemStack) stack;
            ItemStack nativeStack = ae.getItemStack();
            Item item = ae.getItem();
            int damage = ae.getItemDamage();
            String itemId = registryName(item);
            String name = nativeStack == null ? stackName(stack) : nativeStack.getDisplayName();
            String texture = iconName(nativeStack);
            String key = "item:" + itemId + ":" + damage + ":" + tagHash(nativeStack);
            return new StackInfo(key, name, itemId, damage, texture, stack.getStackSize());
        }

        String name = stackName(stack);
        String type = stack.getClass()
            .getName();
        IAEStack<?> normalized = stack.copy();
        normalized.setStackSize(1);
        String key = "stack:" + type
            + ":"
            + Integer.toHexString(
                normalized.toString()
                    .hashCode());
        return new StackInfo(key, name, type, 0, null, stack.getStackSize());
    }

    static String stackKey(IAEStack<?> stack) {
        StackInfo info = describe(stack);
        return info == null ? null : info.key;
    }

    static String stack(IAEStack<?> stack) {
        if (stack == null) return "unknown output";
        return stackName(stack) + " ×" + stack.getStackSize();
    }

    static String mediumId(ICraftingMedium medium) {
        TileEntity tile = mediumTile(medium);
        if (tile != null) return "medium:" + tileId(tile);
        return "medium:" + medium.getClass()
            .getName() + "@" + Integer.toHexString(System.identityHashCode(medium));
    }

    static String mediumName(ICraftingMedium medium) {
        if (medium == null) return "unknown crafting medium";
        IInterfaceViewable viewable = interfaceViewable(medium);
        if (viewable != null) {
            String name = safeName(viewable.getName());
            if (name != null) return name;
        }
        if (medium instanceof ICustomNameObject) {
            ICustomNameObject named = (ICustomNameObject) medium;
            try {
                if (named.hasCustomName()) {
                    String name = safeName(named.getCustomName());
                    if (name != null) return name;
                }
            } catch (Throwable ignored) {}
        }
        if (medium instanceof MetaTileEntity) {
            String name = safeName(((MetaTileEntity) medium).getLocalName());
            if (name != null) return name;
        }
        if (medium instanceof TileEntity) return machineName((TileEntity) medium);

        String reflected = reflectiveName(medium);
        return reflected == null ? medium.getClass()
            .getSimpleName() : reflected;
    }

    static String mediumType(ICraftingMedium medium) {
        return medium == null ? "unknown"
            : medium.getClass()
                .getSimpleName();
    }

    static String mediumLocation(ICraftingMedium medium) {
        TileEntity tile = mediumTile(medium);
        return tile == null ? "unresolved" : machineLocation(tile);
    }

    static TileEntity mediumTile(ICraftingMedium medium) {
        if (medium == null) return null;
        try {
            IInterfaceViewable viewable = interfaceViewable(medium);
            if (viewable != null) {
                TileEntity tile = viewable.getTileEntity();
                if (tile != null) return tile;
            }
            if (medium instanceof IMetaTileEntity) {
                IGregTechTileEntity base = ((IMetaTileEntity) medium).getBaseMetaTileEntity();
                if (base instanceof TileEntity) return (TileEntity) base;
            }
            if (medium instanceof TileEntity) return (TileEntity) medium;
        } catch (Throwable ignored) {}
        return null;
    }

    static String tileId(TileEntity tile) {
        if (tile == null) return "tile:null";
        int dimension = tile.getWorldObj() == null ? 0 : tile.getWorldObj().provider.dimensionId;
        return dimension + ":" + tile.xCoord + ":" + tile.yCoord + ":" + tile.zCoord;
    }

    static String machineName(TileEntity tile) {
        if (tile instanceof IGregTechTileEntity) {
            IMetaTileEntity meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
            if (meta instanceof MetaTileEntity) {
                String name = ((MetaTileEntity) meta).getLocalName();
                if (name != null && !name.isEmpty()) return name;
            }
        }

        try {
            Block block = tile.getBlockType();
            if (block != null) {
                String name = block.getLocalizedName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {}
        return tile.getClass()
            .getSimpleName();
    }

    static String machineType(TileEntity tile) {
        if (tile instanceof IGregTechTileEntity) {
            IMetaTileEntity meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
            if (meta != null) return meta.getClass()
                .getSimpleName();
        }
        return tile.getClass()
            .getSimpleName();
    }

    static String machineLocation(TileEntity tile) {
        if (tile == null || tile.getWorldObj() == null) return "unknown";
        return "DIM " + tile.getWorldObj().provider.dimensionId
            + " · "
            + tile.xCoord
            + ", "
            + tile.yCoord
            + ", "
            + tile.zCoord;
    }

    private static IInterfaceViewable interfaceViewable(ICraftingMedium medium) {
        if (medium instanceof IInterfaceViewable) return (IInterfaceViewable) medium;
        if (medium == null) return null;
        try {
            Method method = medium.getClass()
                .getMethod("getHost");
            if (method.getParameterTypes().length != 0) return null;
            method.setAccessible(true);
            Object host = method.invoke(medium);
            return host instanceof IInterfaceViewable ? (IInterfaceViewable) host : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stackName(IAEStack<?> stack) {
        try {
            return stack.getChatComponent()
                .getUnformattedText();
        } catch (Throwable ignored) {
            return stack.toString();
        }
    }

    private static String registryName(Item item) {
        if (item == null) return "unknown";
        try {
            Object name = Item.itemRegistry.getNameForObject(item);
            if (name != null) return String.valueOf(name);
        } catch (Throwable ignored) {}
        return item.getUnlocalizedName();
    }

    private static String iconName(ItemStack stack) {
        if (stack == null) return null;
        try {
            IIcon icon = stack.getIconIndex();
            if (icon != null) return safeName(icon.getIconName());
        } catch (Throwable ignored) {}
        return null;
    }

    private static int tagHash(ItemStack stack) {
        try {
            return stack != null && stack.hasTagCompound() ? stack.getTagCompound()
                .hashCode() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String reflectiveName(Object value) {
        String[] methods = { "getName", "getCustomName", "getLocalName", "getInventoryName" };
        for (String methodName : methods) {
            try {
                Method method = value.getClass()
                    .getMethod(methodName);
                if (method.getParameterTypes().length != 0) continue;
                Object result = method.invoke(value);
                if (result instanceof String) {
                    String name = safeName((String) result);
                    if (name != null) return name;
                } else if (result instanceof IChatComponent) {
                    String name = safeName(((IChatComponent) result).getUnformattedText());
                    if (name != null) return name;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String safeName(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private XTProfileLabels() {}
}
