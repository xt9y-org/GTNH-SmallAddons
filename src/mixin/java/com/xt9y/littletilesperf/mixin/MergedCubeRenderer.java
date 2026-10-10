package com.xt9y.littletilesperf.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;

import com.creativemd.littletiles.common.utils.LittleTilesCubeObject;
import com.xt9y.littletilesperf.DenseGridMesher;
import com.xt9y.littletilesperf.DenseGridMesher.Box;
import com.xt9y.littletilesperf.PerfStats;

/**
 * Convert only compatible solid micro-cuboids into an exact, usually much
 * smaller set of rectangular volumes *before* Angelica compiles the chunk.
 *
 * Never touches the authoritative tile list, collision, structures or NBT.
 * Irregular shapes, translucent/custom renderers and mixed/overlapping
 * geometry retain the original render descriptors.
 */
final class MergedCubeRenderer {

    private static final int MIN_CANDIDATES = 16;

    private MergedCubeRenderer() {}

    static ArrayList<LittleTilesCubeObject> merge(ArrayList<LittleTilesCubeObject> original) {
        if (original == null || original.size() < MIN_CANDIDATES) return original;

        int candidates = 0;
        boolean[] reserved = new boolean[DenseGridMesher.VOLUME];
        // Reserve occupied cell volumes for all non-mergeable shapes; this
        // conservatively rejects overlapping cutout geometry too.
        for (LittleTilesCubeObject c : original) {
            if (eligible(c)) {
                candidates++;
            } else if (c != null) {
                reserve(reserved, c);
            }
        }
        if (candidates < MIN_CANDIDATES) return original;

        int[] cells = new int[DenseGridMesher.VOLUME];
        Map<Material, Integer> materialIds = new HashMap<Material, Integer>();
        List<LittleTilesCubeObject> templates = new ArrayList<LittleTilesCubeObject>();
        for (LittleTilesCubeObject c : original) {
            if (!eligible(c)) continue;
            Material key = new Material(c.block, c.meta, c.color);
            Integer id = materialIds.get(key);
            if (id == null) {
                templates.add(c);
                id = templates.size();
                materialIds.put(key, id);
            }
            for (int y = c.gridMinY; y < c.gridMaxY; y++) {
                for (int z = c.gridMinZ; z < c.gridMaxZ; z++) {
                    for (int x = c.gridMinX; x < c.gridMaxX; x++) {
                        int offset = DenseGridMesher.index(x, y, z);
                        if (cells[offset] != 0 || reserved[offset]) {
                            // Overlapping boxes can be intentional for cutouts,
                            // overlays or custom renderers. Preserve them.
                            return original;
                        }
                        cells[offset] = id;
                    }
                }
            }
        }

        List<Box> merged = DenseGridMesher.mesh(cells);
        if (merged.size() >= candidates) return original;

        ArrayList<LittleTilesCubeObject> output =
            new ArrayList<LittleTilesCubeObject>(original.size() - candidates + merged.size());
        boolean inserted = false;
        for (LittleTilesCubeObject cube : original) {
            if (eligible(cube)) {
                if (!inserted) {
                    for (Box box : merged) {
                        LittleTilesCubeObject material = templates.get(box.material - 1);
                        LittleTilesCubeObject one = new LittleTilesCubeObject(
                            box.x0, box.y0, box.z0, box.x1, box.y1, box.z1);
                        one.block = material.block;
                        one.meta = material.meta;
                        one.color = material.color;
                        // Only fully opaque standard cubes are merged. The
                        // upstream culler doesn't need their geometryCache.
                        output.add(one);
                    }
                    inserted = true;
                }
            } else {
                output.add(cube);
            }
        }
        PerfStats.merged(candidates, merged.size());
        return output;
    }

    private static boolean eligible(LittleTilesCubeObject c) {
        // render type 0 = vanilla face-based block rendering. Other renderers
        // may depend on the original sub-boxes or tile-specific metadata.
        return c != null && c.block != null && c.meta >= 0 && c.cutoutInfo == null
            && c.block.isOpaqueCube() && c.block.getRenderType() == 0
            && c.gridMinX >= 0 && c.gridMinY >= 0 && c.gridMinZ >= 0
            && c.gridMaxX <= 16 && c.gridMaxY <= 16 && c.gridMaxZ <= 16
            && c.gridMinX < c.gridMaxX && c.gridMinY < c.gridMaxY
            && c.gridMinZ < c.gridMaxZ;
    }

    private static void reserve(boolean[] reserved, LittleTilesCubeObject c) {
        int x0 = Math.max(0, c.gridMinX), x1 = Math.min(16, c.gridMaxX);
        int y0 = Math.max(0, c.gridMinY), y1 = Math.min(16, c.gridMaxY);
        int z0 = Math.max(0, c.gridMinZ), z1 = Math.min(16, c.gridMaxZ);
        for (int y = y0; y < y1; y++) {
            for (int z = z0; z < z1; z++) {
                for (int x = x0; x < x1; x++) {
                    reserved[DenseGridMesher.index(x, y, z)] = true;
                }
            }
        }
    }

    private static final class Material {

        private final Block block;
        private final int meta, color;

        private Material(Block block, int meta, int color) {
            this.block = block;
            this.meta = meta;
            this.color = color;
        }

        @Override
        public int hashCode() {
            int h = System.identityHashCode(block);
            return 31 * (31 * h + meta) + color;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Material)) return false;
            Material rhs = (Material) other;
            return block == rhs.block && meta == rhs.meta && color == rhs.color;
        }
    }
}
