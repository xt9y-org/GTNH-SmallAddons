package com.xt9y.littletilesperf;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic exact cuboid mesher for LittleTiles' 16^3 voxel grid.
 *
 * Input zero = empty, positive integers = distinct rendering materials.
 * Every generated box covers exactly the original material voxels; unlike a
 * screen-space approximation this never expands a surface or loses detail.
 *
 * Runtime adapters are responsible for rejecting overlapping/unsupported
 * LittleTiles boxes and for preserving each material's block/meta/tint.
 */
public final class DenseGridMesher {

    public static final int EDGE = 16;
    public static final int VOLUME = EDGE * EDGE * EDGE;

    private DenseGridMesher() {}

    public static int index(int x, int y, int z) {
        return (y * EDGE + z) * EDGE + x;
    }

    public static final class Box {
        public final int material;
        public final int x0, y0, z0, x1, y1, z1;

        private Box(int material, int x0, int y0, int z0, int x1, int y1, int z1) {
            this.material = material;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }
    }

    /**
     * Opt-in distant LOD: seal sub-voxel cavities *inside* a block only.
     * Leaves two voxels of boundary unchanged and never replaces one solid
     * material with another. Mixed-material groups and groups intersecting
     * custom/transparent bounding boxes are kept exact.
     *
     * The caller MUST request a rebuild on transitions back to near LOD.
     */
    public static int[] simplifyDistantInterior(int[] source, boolean[] protectedCells) {
        if (source.length != VOLUME || protectedCells.length != VOLUME)
            throw new IllegalArgumentException("Expected 16^3 cells");
        int[] cells = source.clone();
        for (int y = 2; y < EDGE - 2; y += 2) {
            for (int z = 2; z < EDGE - 2; z += 2) {
                for (int x = 2; x < EDGE - 2; x += 2) {
                    int material = 0, filled = 0;
                    boolean mixed = false, blocked = false;
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dz = 0; dz < 2; dz++) {
                            for (int dx = 0; dx < 2; dx++) {
                                int at = index(x + dx, y + dy, z + dz);
                                if (protectedCells[at]) blocked = true;
                                int value = source[at];
                                if (value != 0) {
                                    if (material != 0 && material != value) mixed = true;
                                    material = value;
                                    filled++;
                                }
                            }
                        }
                    }
                    if (blocked || mixed || material == 0 || filled < 7 || filled == 8) continue;
                    for (int dy = 0; dy < 2; dy++)
                        for (int dz = 0; dz < 2; dz++)
                            for (int dx = 0; dx < 2; dx++)
                                cells[index(x + dx, y + dy, z + dz)] = material;
                }
            }
        }
        return cells;
    }

    /**
     * At most 4096 cells, so no large objects and no quadratic tile comparisons.
     * Expands contiguous material runs along X, Z then Y. Clearing consumed
     * cells guarantees non-overlapping, full coverage of every occupied voxel.
     * Does not mutate the supplied occupancy buffer.
     */
    public static List<Box> mesh(int[] occupancy) {
        if (occupancy.length != VOLUME) throw new IllegalArgumentException("Expected 16^3 cells");

        int[] cells = occupancy.clone();
        List<Box> out = new ArrayList<Box>();
        for (int y = 0; y < EDGE; y++) {
            for (int z = 0; z < EDGE; z++) {
                for (int x = 0; x < EDGE; x++) {
                    int material = cells[index(x, y, z)];
                    if (material <= 0) continue;

                    int endX = x + 1;
                    while (endX < EDGE && cells[index(endX, y, z)] == material) endX++;

                    int endZ = z + 1;
                    outerZ:
                    while (endZ < EDGE) {
                        for (int cx = x; cx < endX; cx++) {
                            if (cells[index(cx, y, endZ)] != material) break outerZ;
                        }
                        endZ++;
                    }

                    int endY = y + 1;
                    outerY:
                    while (endY < EDGE) {
                        for (int cz = z; cz < endZ; cz++) {
                            for (int cx = x; cx < endX; cx++) {
                                if (cells[index(cx, endY, cz)] != material) break outerY;
                            }
                        }
                        endY++;
                    }

                    for (int cy = y; cy < endY; cy++) {
                        for (int cz = z; cz < endZ; cz++) {
                            for (int cx = x; cx < endX; cx++) {
                                cells[index(cx, cy, cz)] = 0;
                            }
                        }
                    }
                    out.add(new Box(material, x, y, z, endX, endY, endZ));
                }
            }
        }
        return out;
    }
}
