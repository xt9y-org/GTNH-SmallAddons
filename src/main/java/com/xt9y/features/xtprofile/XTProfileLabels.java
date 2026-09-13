package com.xt9y.features.xtprofile;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

final class XTProfileLabels {

    static String pattern(ICraftingPatternDetails pattern) {
        if (pattern == null) return "unknown pattern";
        IAEStack<?>[] outputs = pattern.getCondensedAEOutputs();
        if (outputs == null || outputs.length == 0) return "processing pattern";
        return stack(outputs[0]);
    }

    static String stack(IAEStack<?> stack) {
        if (stack == null) return "unknown output";
        String name;
        try {
            name = stack.getChatComponent().getUnformattedText();
        } catch (Throwable ignored) {
            name = stack.toString();
        }
        return name + " ×" + stack.getStackSize();
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
        return tile.getClass().getSimpleName();
    }

    static String machineType(TileEntity tile) {
        if (tile instanceof IGregTechTileEntity) {
            IMetaTileEntity meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
            if (meta != null) return meta.getClass().getSimpleName();
        }
        return tile.getClass().getSimpleName();
    }

    static String machineLocation(TileEntity tile) {
        if (tile == null || tile.getWorldObj() == null) return "unknown";
        return "DIM " + tile.getWorldObj().provider.dimensionId + " · " + tile.xCoord + ", " + tile.yCoord + ", "
            + tile.zCoord;
    }

    private XTProfileLabels() {}
}
