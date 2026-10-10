package com.xt9y.littletilesperf;

import net.minecraft.util.AxisAlignedBB;

/** Immutable render metadata for the static portion of a LittleTiles tile entity. */
public final class RenderSnapshot {

    public final long revision;
    public final int size, x, y, z;
    public final AxisAlignedBB bounds;
    public final double distanceSquared;

    public RenderSnapshot(long revision, int size, int x, int y, int z,
                          AxisAlignedBB bounds, double distanceSquared) {
        this.revision = revision;
        this.size = size;
        this.x = x;
        this.y = y;
        this.z = z;
        this.bounds = bounds;
        this.distanceSquared = distanceSquared;
    }
}
