package com.xt9y.littletilesperf.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.utils.LittleTile;
import com.xt9y.littletilesperf.CacheKey;
import com.xt9y.littletilesperf.PerfSettings;
import com.xt9y.littletilesperf.PerfStats;

/**
 * LittleTiles 1.7.10 integration. Cache only render metadata, never world tile contents
 * or meshes. The custom-rendering path is deliberately left untouched.
 */
@Mixin(value = TileEntityLittleTiles.class, remap = false)
public abstract class MixinTileEntityLittleTiles extends TileEntity {

    @Shadow
    public ArrayList<LittleTile> customRenderingTiles;

    @Shadow
    public abstract List<LittleTile> getTiles();

    @Unique
    private volatile long ltperf$revision;

    @Unique
    private volatile RenderSnapshot ltperf$snapshot;

    /** Never cache across edits, NBT reloads or neighbor-triggered render invalidations. */
    @Inject(method = "updateTiles(Z)V", at = @At("RETURN"), remap = false)
    private void ltperf$tilesUpdated(boolean cleanUp, CallbackInfo ci) {
        ltperf$invalidate();
    }

    @Inject(method = "setTiles(Ljava/util/List;)V", at = @At("RETURN"), remap = false)
    private void ltperf$tilesReplaced(List<LittleTile> tiles, CallbackInfo ci) {
        ltperf$invalidate();
    }

    @Inject(method = "updateRender()V", at = @At("HEAD"), remap = false)
    private void ltperf$renderInvalidated(CallbackInfo ci) {
        ltperf$invalidate();
    }

    @Inject(method = "invalidate()V", at = @At("HEAD"), remap = false)
    private void ltperf$chunkUnloaded(CallbackInfo ci) {
        ltperf$invalidate();
    }

    @Unique
    private void ltperf$invalidate() {
        ltperf$revision++;
        ltperf$snapshot = null;
        PerfStats.invalidated();
    }

    @Inject(method = "getRenderBoundingBox()Lnet/minecraft/util/AxisAlignedBB;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void ltperf$getBounds(CallbackInfoReturnable<AxisAlignedBB> cir) {
        RenderSnapshot snapshot = ltperf$getSnapshot();
        if (snapshot != null) cir.setReturnValue(snapshot.bounds);
    }

    @Inject(method = "getMaxRenderDistanceSquared()D",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void ltperf$getDistance(CallbackInfoReturnable<Double> cir) {
        RenderSnapshot snapshot = ltperf$getSnapshot();
        if (snapshot != null) cir.setReturnValue(snapshot.distanceSquared);
    }

    @Unique
    private RenderSnapshot ltperf$getSnapshot() {
        if (!PerfSettings.cacheStaticRenderBounds
            || customRenderingTiles == null || !customRenderingTiles.isEmpty()) return null;

        List<LittleTile> tiles = getTiles();
        long revision = ltperf$revision;
        RenderSnapshot current = ltperf$snapshot;

        if (current != null
            && CacheKey.matches(current.revision, current.size, current.x, current.y, current.z,
                revision, tiles.size(), xCoord, yCoord, zCoord)) {
            PerfStats.hit();
            return current;
        }

        // Read membership atomically with upstream's synchronized tile list.
        List<LittleTile> members;
        synchronized (tiles) {
            members = new ArrayList<LittleTile>(tiles);
        }

        double minX = xCoord, minY = yCoord, minZ = zCoord;
        double maxX = xCoord + 1, maxY = yCoord + 1, maxZ = zCoord + 1;
        double maxDistance = 0;
        for (LittleTile tile : members) {
            AxisAlignedBB bounds = tile.getRenderBoundingBox();
            if (bounds != null) {
                minX = Math.min(minX, bounds.minX);
                minY = Math.min(minY, bounds.minY);
                minZ = Math.min(minZ, bounds.minZ);
                maxX = Math.max(maxX, bounds.maxX);
                maxY = Math.max(maxY, bounds.maxY);
                maxZ = Math.max(maxZ, bounds.maxZ);
            }
            maxDistance = Math.max(maxDistance, tile.getMaxRenderDistanceSquared());
        }

        RenderSnapshot built = new RenderSnapshot(
            revision, members.size(), xCoord, yCoord, zCoord,
            AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ), maxDistance);
        if (!CacheKey.matches(revision, members.size(), xCoord, yCoord, zCoord,
            ltperf$revision, tiles.size(), xCoord, yCoord, zCoord)) return null;

        // Concurrent chunk meshing is possible. Duplicate cold calculations are safe.
        ltperf$snapshot = built;
        PerfStats.miss(members.size());
        return built;
    }

    // LittleTiles.updateTiles invokes update() followed by updateNeighbor().
    // Both paths invoke updateRender(), unnecessarily invalidating neighboring
    // geometry twice. Keep updateNeighbor() notification, omit ONLY its duplicate
    // render invalidation when called from updateTiles().
    @Unique
    private boolean ltperf$inTileUpdate;

    @Redirect(method = "updateTiles(Z)V",
        at = @At(value = "INVOKE",
            target = "Lcom/creativemd/littletiles/common/tileentity/TileEntityLittleTiles;updateNeighbor()V"),
        remap = false)
    private void ltperf$coalesceNeighbourRender(TileEntityLittleTiles self) {
        if (!PerfSettings.skipDuplicateRenderInvalidation) {
            self.updateNeighbor();
            return;
        }
        boolean prior = ltperf$inTileUpdate;
        ltperf$inTileUpdate = true;
        try {
            self.updateNeighbor();
        } finally {
            ltperf$inTileUpdate = prior;
        }
    }

    @Redirect(method = "updateNeighbor()V",
        at = @At(value = "INVOKE",
            target = "Lcom/creativemd/littletiles/common/tileentity/TileEntityLittleTiles;updateRender()V"),
        remap = false)
    private void ltperf$avoidRedundantRender(TileEntityLittleTiles self) {
        if (!ltperf$inTileUpdate || !PerfSettings.skipDuplicateRenderInvalidation) {
            self.updateRender();
        } else {
            PerfStats.skippedRender();
        }
    }

    @Unique
    private static final class RenderSnapshot {

        private final long revision;
        private final int size, x, y, z;
        private final AxisAlignedBB bounds;
        private final double distanceSquared;

        private RenderSnapshot(long revision, int size, int x, int y, int z,
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
}
