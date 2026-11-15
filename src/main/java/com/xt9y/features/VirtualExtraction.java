package com.xt9y.features;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import bwcrossmod.galacticgreg.VoidMinerUtility.DropMap;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.WorldgenGTOreLayer;
import gregtech.common.WorldgenGTOreSmallPieces;
import gregtech.common.covers.Cover;
import gtneioreplugin.util.DimensionHelper;

/** Samples ore solely from the planet selected on the attached machine's cover. */
public final class VirtualExtraction {

    private static final Map<String, DropMap> EXTRA_DIMENSION_POOLS = new HashMap<>();

    public static boolean isVirtual(IGregTechTileEntity machine) {
        return dimension(machine) != null;
    }

    public static String dimension(IGregTechTileEntity machine) {
        if (machine == null || machine.getWorld() == null || machine.getWorld().isRemote) return null;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            Cover cover = machine.getCoverAtSide(side);
            if (cover instanceof PlanetaryCover) return ((PlanetaryCover) cover).getSelectedDimension();
        }
        return null;
    }

    /** Samples GTNH's weighted Void Miner ores or enabled ore layers of the selected Eye of Harmony dimension. */
    public static ItemStack nextOre(IGregTechTileEntity machine) {
        String dim = dimension(machine);
        if (dim == null) return null;

        DropMap map = poolFor(dim);
        DropMap extras = VoidMinerUtility.extraDropsByDimName.get(dim);
        map.isDistributionCached(extras);
        if (map.getOres() == null || map.getOres().length == 0) return null;

        return map.nextOre()
            .getItemStack();
    }

    /** A read-only choice of native/fallback ore pool, without loading any foreign dimension. */
    public static DropMap poolFor(String dimension) {
        if (dimension == null) return null;
        DropMap map = VoidMinerUtility.dropMapsByDimName.get(dimension);
        if (map == null || map.getInternalMap()
            .isEmpty()) map = fallbackPool(dimension);
        return map;
    }

    private static synchronized DropMap fallbackPool(String dimension) {
        DropMap cached = EXTRA_DIMENSION_POOLS.get(dimension);
        if (cached != null) return cached;

        DropMap map = new DropMap();
        List<StoneType> stoneTypes = DimensionHelper.getStoneTypes(dimension);
        for (WorldgenGTOreLayer layer : WorldgenGTOreLayer.sList) {
            if (!layer.mEnabled || !layer.getAllowedDimensions()
                .contains(dimension)) continue;
            if (stoneTypes.isEmpty()) {
                map.addDrop(layer.mPrimary, layer.mWeight);
                map.addDrop(layer.mSecondary, layer.mWeight);
                map.addDrop(layer.mSporadic, layer.mWeight / 8f);
                map.addDrop(layer.mBetween, layer.mWeight / 8f);
            } else {
                map.addDrop(layer.mPrimary, stoneTypes, layer.mWeight);
                map.addDrop(layer.mSecondary, stoneTypes, layer.mWeight);
                map.addDrop(layer.mSporadic, stoneTypes, layer.mWeight / 8f);
                map.addDrop(layer.mBetween, stoneTypes, layer.mWeight / 8f);
            }
        }
        for (WorldgenGTOreSmallPieces layer : WorldgenGTOreSmallPieces.sList) {
            if (!layer.mEnabled || !layer.getAllowedDimensions()
                .contains(dimension)) continue;
            if (stoneTypes.isEmpty()) {
                map.addDrop(layer.getMaterial(), layer.mAmount);
            } else {
                map.addDrop(layer.getMaterial(), stoneTypes, layer.mAmount);
            }
        }
        EXTRA_DIMENSION_POOLS.put(dimension, map);
        return map;
    }

    private VirtualExtraction() {}
}
