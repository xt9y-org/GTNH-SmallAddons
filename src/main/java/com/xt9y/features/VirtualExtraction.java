package com.xt9y.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import bwcrossmod.galacticgreg.VoidMinerUtility.DropMap;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;

/** Server-side ore selection; never changes terrain or consults another world. */
public final class VirtualExtraction {

    public static boolean isVirtual(World world, int blockX, int blockZ) {
        return dimension(world, blockX, blockZ) != null;
    }

    public static String dimension(World world, int blockX, int blockZ) {
        if (world == null || world.isRemote) return null;

        // PersonalSpace always requires an actual, loaded source block in THIS chunk.
        if (OreSourceBlock.isPersonalWorld(world)) {
            return XT9YConfig.oreSourceBlock ? OreSourceBlock.getSourceDimension(world, blockX >> 4, blockZ >> 4)
                : null;
        }

        if (!XT9YConfig.voidMiners) return null;
        ModDimensionDef def = DimensionDef.getEffectiveDefForChunk(world, blockX >> 4, blockZ >> 4);
        if (def != null) return def.getDimensionName();

        String name = DimensionDef.getDimensionName(world);
        return VoidMinerUtility.dropMapsByDimName.containsKey(name) ? name : null;
    }

    /** Uses GTNH's own enabled ore/planet tables, including small ores and additional void miner drops. */
    public static ItemStack nextOre(World world, int x, int z) {
        String dim = dimension(world, x, z);
        if (dim == null) return null;

        DropMap map = VoidMinerUtility.dropMapsByDimName.get(dim);
        DropMap extras = VoidMinerUtility.extraDropsByDimName.get(dim);
        if (map == null && extras == null) return null;
        if (map == null) map = new DropMap();
        map.isDistributionCached(extras);
        if (map.getOres() == null || map.getOres().length == 0) return null;

        return map.nextOre()
            .getItemStack();
    }

    public static List<String> availableDimensions() {
        Map<String, DropMap> maps = VoidMinerUtility.dropMapsByDimName;
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, DropMap> entry : maps.entrySet()) {
            if (!entry.getValue()
                .getInternalMap()
                .isEmpty()) names.add(entry.getKey());
        }
        for (Map.Entry<String, DropMap> entry : VoidMinerUtility.extraDropsByDimName.entrySet()) {
            if (!entry.getValue()
                .getInternalMap()
                .isEmpty() && !names.contains(entry.getKey())) {
                names.add(entry.getKey());
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        for (String preferred : new String[] { DimensionDef.DimNames.NETHER, DimensionDef.DimNames.DEEPDARK,
            DimensionDef.DimNames.OW }) {
            if (names.remove(preferred)) names.add(0, preferred);
        }
        return names;
    }

    private VirtualExtraction() {}
}
