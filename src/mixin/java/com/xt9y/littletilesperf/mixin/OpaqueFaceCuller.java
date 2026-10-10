package com.xt9y.littletilesperf.mixin;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import com.creativemd.creativecore.client.rendering.FacePiece;
import com.creativemd.creativecore.client.rendering.IFaceClipper;
import com.creativemd.littletiles.common.utils.LittleTilesCubeObject;
import com.xt9y.littletilesperf.DenseGridMesher;
import com.xt9y.littletilesperf.PerfStats;

/**
 * Removes fully buried faces of ordinary opaque micro-cuboids even when their
 * texture materials differ. This works at face-emission time inside Angelica's
 * existing VBO meshing, so invisible faces never reach GPU vertex buffers.
 *
 * Conservative: no partial clipping, no chunk-boundary faces, no special
 * render types. Does not touch transparent or irregular/cutout geometry.
 */
final class OpaqueFaceCuller {

    private static final int EDGE = DenseGridMesher.EDGE;

    private OpaqueFaceCuller() {}

    static void apply(List<LittleTilesCubeObject> cubes, IFaceClipper[] coverage) {
        if (cubes.size() < 2) return;
        int eligible = 0;
        for (LittleTilesCubeObject c : cubes) if (solid(c)) eligible++;
        if (eligible < 2) return;

        boolean[] occupied = new boolean[DenseGridMesher.VOLUME];
        boolean[] reserved = new boolean[DenseGridMesher.VOLUME];

        // A nonstandard tile can overlap the bounding box of an opaque cube.
        // To avoid touching cutouts and geometry overlays, skip the whole
        // optimization when bounding-volume overlap is detected.
        for (LittleTilesCubeObject c : cubes) {
            if (!solid(c) && c != null) {
                int x0 = Math.max(0, c.gridMinX), x1 = Math.min(EDGE, c.gridMaxX);
                int y0 = Math.max(0, c.gridMinY), y1 = Math.min(EDGE, c.gridMaxY);
                int z0 = Math.max(0, c.gridMinZ), z1 = Math.min(EDGE, c.gridMaxZ);
                for (int y = y0; y < y1; y++)
                    for (int z = z0; z < z1; z++)
                        for (int x = x0; x < x1; x++)
                            reserved[DenseGridMesher.index(x, y, z)] = true;
            }
        }
        for (LittleTilesCubeObject c : cubes) {
            if (!solid(c)) continue;
            for (int y = c.gridMinY; y < c.gridMaxY; y++) {
                for (int z = c.gridMinZ; z < c.gridMaxZ; z++) {
                    for (int x = c.gridMinX; x < c.gridMaxX; x++) {
                        int i = DenseGridMesher.index(x, y, z);
                        if (reserved[i] || occupied[i]) return;
                        occupied[i] = true;
                    }
                }
            }
        }

        int removed = 0;
        for (int i = 0; i < cubes.size(); i++) {
            LittleTilesCubeObject cube = cubes.get(i);
            if (!solid(cube) || coverage[i] != null) continue;
            int mask = 0;
            for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
                if (buried(cube, face, occupied)) mask |= 1 << face.ordinal();
            }
            if (mask != 0) {
                coverage[i] = new HiddenFaces(mask);
                removed += Integer.bitCount(mask);
            }
        }
        PerfStats.hiddenOpaqueFaces(removed);
    }

    static boolean buried(LittleTilesCubeObject c, ForgeDirection side, boolean[] solid) {
        int dx = side.offsetX, dy = side.offsetY, dz = side.offsetZ;
        for (int y = c.gridMinY; y < c.gridMaxY; y++) {
            for (int z = c.gridMinZ; z < c.gridMaxZ; z++) {
                for (int x = c.gridMinX; x < c.gridMaxX; x++) {
                    // Only consider voxels directly touching this outer side.
                    if (dx < 0 && x != c.gridMinX || dx > 0 && x != c.gridMaxX - 1
                        || dy < 0 && y != c.gridMinY || dy > 0 && y != c.gridMaxY - 1
                        || dz < 0 && z != c.gridMinZ || dz > 0 && z != c.gridMaxZ - 1)
                        continue;
                    int nx = x + dx, ny = y + dy, nz = z + dz;
                    if (nx < 0 || nx >= EDGE || ny < 0 || ny >= EDGE
                        || nz < 0 || nz >= EDGE || !solid[DenseGridMesher.index(nx, ny, nz)])
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean solid(LittleTilesCubeObject c) {
        return c != null && c.block != null && c.meta >= 0 && c.cutoutInfo == null
            && c.block.isOpaqueCube() && c.block.getRenderType() == 0
            && c.gridMinX >= 0 && c.gridMinY >= 0 && c.gridMinZ >= 0
            && c.gridMaxX <= EDGE && c.gridMaxY <= EDGE && c.gridMaxZ <= EDGE
            && c.gridMinX < c.gridMaxX && c.gridMinY < c.gridMaxY
            && c.gridMinZ < c.gridMaxZ;
    }

    private static final class HiddenFaces implements IFaceClipper {

        private final int mask;

        private HiddenFaces(int mask) {
            this.mask = mask;
        }

        @Override
        public List<FacePiece> getFacePieces(ForgeDirection side) {
            return (mask & (1 << side.ordinal())) != 0 ? Collections.<FacePiece>emptyList() : null;
        }
    }
}
