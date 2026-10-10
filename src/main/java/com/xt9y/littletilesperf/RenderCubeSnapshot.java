package com.xt9y.littletilesperf;

import java.util.ArrayList;
import java.util.List;

import com.creativemd.littletiles.common.utils.LittleTilesCubeObject;

/**
 * Immutable membership and revision for a static opaque LittleTiles render
 * snapshot. A fresh list wrapper is returned to callers because upstream
 * API promises an ArrayList, but the cube descriptors remain shared/read-only.
 */
public final class RenderCubeSnapshot {

    public final long revision;
    public final int x, y, z;
    public final boolean distant;
    private final ArrayList<LittleTilesCubeObject> cubes;

    public RenderCubeSnapshot(long revision, int x, int y, int z, boolean distant,
                              List<LittleTilesCubeObject> cubes) {
        this.revision = revision;
        this.x = x;
        this.y = y;
        this.z = z;
        this.distant = distant;
        this.cubes = new ArrayList<LittleTilesCubeObject>(cubes);
    }

    public ArrayList<LittleTilesCubeObject> copy() {
        return new ArrayList<LittleTilesCubeObject>(cubes);
    }
}
