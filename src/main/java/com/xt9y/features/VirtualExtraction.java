package com.xt9y.features;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import bwcrossmod.galacticgreg.VoidMinerUtility.DropMap;
import gregtech.GTMod;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.util.GTUtility;
import gregtech.common.WorldgenGTOreLayer;
import gregtech.common.WorldgenGTOreSmallPieces;
import gregtech.common.covers.Cover;
import gtneioreplugin.util.DimensionHelper;

/** Samples ore solely from the planet selected on the attached machine's cover. */
public final class VirtualExtraction {

    private static final Map<String, DropMap> EXTRA_DIMENSION_POOLS = new HashMap<>();

    public static PlanetaryCover cover(IGregTechTileEntity machine) {
        if (machine == null || machine.getWorld() == null || machine.getWorld().isRemote) return null;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            Cover cover = machine.getCoverAtSide(side);
            if (cover instanceof PlanetaryCover) return (PlanetaryCover) cover;
        }
        return null;
    }

    public static boolean isVirtual(IGregTechTileEntity machine) {
        return dimension(machine) != null;
    }

    public static String dimension(IGregTechTileEntity machine) {
        PlanetaryCover selected = cover(machine);
        return selected == null ? null : selected.getSelectedDimension();
    }

    /** Weighted sampling restricted by the machine's ore whitelist / blacklist. */
    public static ItemStack nextOre(IGregTechTileEntity machine) {
        PlanetaryCover selected = cover(machine);
        if (selected == null) return null;
        String dimension = selected.getSelectedDimension();
        if (dimension == null) return null;
        DropMap map = poolFor(dimension);
        if (map == null) return null;
        map.isDistributionCached(VoidMinerUtility.extraDropsByDimName.get(dimension));

        double total = 0;
        for (Map.Entry<GTUtility.ItemId, Float> entry : map.getInternalMap()
            .entrySet()) {
            if (selected.allowsOre(
                entry.getKey()
                    .getItemStack()))
                total += Math.max(0, entry.getValue());
        }
        if (total <= 0) return null;
        double roll = ThreadLocalRandom.current()
            .nextDouble(total);
        for (Map.Entry<GTUtility.ItemId, Float> entry : map.getInternalMap()
            .entrySet()) {
            if (!selected.allowsOre(
                entry.getKey()
                    .getItemStack()))
                continue;
            roll -= Math.max(0, entry.getValue());
            if (roll < 0) return entry.getKey()
                .getItemStack();
        }
        return null;
    }

    /**
     * Planetary underground-fluid table from GregTech's server config, without loading a world.
     * The EoH item resolves to a GalacticGreg internal dimension name.
     */
    public static Map<String, GTUOFluid> fluidsFor(String dimension) {
        Map<String, GTUOFluid> result = new LinkedHashMap<>();
        if (dimension == null || GTMod.proxy == null || GTMod.proxy.mUndergroundOil == null) return result;
        String shortName = null;
        String displayName = null;
        for (DimensionHelper.Dimension entry : DimensionHelper.getAllDim()) {
            if (entry.internalName()
                .equals(dimension)) {
                shortName = entry.abbr();
                displayName = entry.trimmedName();
                break;
            }
        }
        if (shortName == null) return result;
        for (Map.Entry<String, GTUODimension> entry : GTMod.proxy.mUndergroundOil.getDimensionList()
            .entrySet()) {
            String key = entry.getKey();
            boolean match = key.equalsIgnoreCase(displayName) || ("Ow".equals(shortName) && "0".equals(key))
                || ("CB".equals(shortName) && "aCentauriBb".equals(key))
                || ("BC".equals(shortName) && "BarnardaC".equals(key))
                || ("BE".equals(shortName) && "BarnardaE".equals(key))
                || ("BF".equals(shortName) && "BarnardaF".equals(key))
                || ("TE".equals(shortName) && "TCetiE".equals(key));
            if (!match) continue;
            for (Map.Entry<String, GTUOFluid> fluid : entry.getValue()
                .getFluids()
                .entrySet()) {
                if (fluid.getValue()
                    .getFluid() != null && fluid.getValue().Chance > 0) {
                    result.put(fluid.getKey(), fluid.getValue());
                }
            }
        }
        return result;
    }

    /** At most one fluid per operation, in the selected planet's valid underground-fluid pool. */
    public static FluidStack nextFluid(PlanetaryCover selected, double multiplier) {
        if (selected == null) return null;
        Map<String, GTUOFluid> pool = fluidsFor(selected.getSelectedDimension());
        if (pool.isEmpty() || selected.getFluidName()
            .isEmpty()) return null;

        GTUOFluid choice = null;
        if (!selected.isFluidBlacklist()) {
            choice = pool.get(selected.getFluidName());
        } else {
            int weight = 0;
            for (Map.Entry<String, GTUOFluid> entry : pool.entrySet()) {
                if (!entry.getKey()
                    .equals(selected.getFluidName())) weight += Math.max(0, entry.getValue().Chance);
            }
            if (weight <= 0) return null;
            int roll = ThreadLocalRandom.current()
                .nextInt(weight);
            for (Map.Entry<String, GTUOFluid> entry : pool.entrySet()) {
                if (entry.getKey()
                    .equals(selected.getFluidName())) continue;
                roll -= Math.max(0, entry.getValue().Chance);
                if (roll < 0) {
                    choice = entry.getValue();
                    break;
                }
            }
        }
        if (choice == null || choice.getFluid() == null) return null;
        double baseAmount = Math.max(1.0, (choice.MinAmount + (double) choice.MaxAmount) / 2.0);
        int amount = (int) Math.max(1, Math.min(Integer.MAX_VALUE, baseAmount * Math.max(0.01, multiplier)));
        return new FluidStack(choice.getFluid(), amount);
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
