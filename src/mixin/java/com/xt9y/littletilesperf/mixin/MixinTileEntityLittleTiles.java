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
import com.creativemd.littletiles.common.utils.LittleTileBlock;
import com.creativemd.littletiles.common.utils.LittleTileBlockColored;
import com.creativemd.littletiles.common.utils.LittleTilesCubeObject;
import com.xt9y.littletilesperf.CacheKey;
import com.xt9y.littletilesperf.ClientLodManager;
import com.xt9y.littletilesperf.PerfSettings;
import com.xt9y.littletilesperf.PerfStats;
import com.xt9y.littletilesperf.RenderSnapshot;
import com.xt9y.littletilesperf.RenderCubeSnapshot;

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

    @Unique
    private volatile RenderCubeSnapshot ltperf$cubeSnapshot;

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

    @Inject(method = "invalidate()V", at = @At("RETURN"), remap = false)
    private void ltperf$removeLodTracking(CallbackInfo ci) {
        ClientLodManager.INSTANCE.untrack((TileEntityLittleTiles) (Object) this);
    }

    @Unique
    private void ltperf$invalidate() {
        ltperf$revision++;
        ltperf$snapshot = null;
        ltperf$cubeSnapshot = null;
        PerfStats.invalidated();
    }

    // Return previously reduced static descriptors when the tile entity has
    // not changed. Only fully opaque basic boxes are retained, never cut
    // geometry with per-tile generation tracking.
    @Inject(method = "getRenderingCubes()Ljava/util/ArrayList;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void ltperf$cachedChunkGeometry(
            CallbackInfoReturnable<ArrayList<LittleTilesCubeObject>> cir) {
        if (!PerfSettings.mergeOpaqueMicrotiles || !PerfSettings.cacheOpaqueRenderCubes
            || !customRenderingTiles.isEmpty()) return;
        RenderCubeSnapshot cache = ltperf$cubeSnapshot;
        if (cache == null || cache.revision != ltperf$revision
            || cache.x != xCoord || cache.y != yCoord || cache.z != zCoord) return;
        boolean distant = PerfSettings.distantLod
            && ClientLodManager.INSTANCE.wantsDistantMesh((TileEntityLittleTiles) (Object) this);
        if (cache.distant != distant) return;
        PerfStats.meshCacheHit();
        cir.setReturnValue(cache.copy());
    }

    // Called by the existing LittleTiles ISBRH on Angelica's chunk-builder
    // threads. Reduced descriptors are passed to the same renderer, so its
    // VBO, render passes, shaders, lighting and chunk uploads stay unchanged.
    @Inject(method = "getRenderingCubes()Ljava/util/ArrayList;",
        at = @At("RETURN"), cancellable = true, remap = false)
    private void ltperf$mergeChunkGeometry(
            CallbackInfoReturnable<ArrayList<LittleTilesCubeObject>> cir) {
        if (!PerfSettings.mergeOpaqueMicrotiles || !customRenderingTiles.isEmpty()) return;
        ArrayList<LittleTilesCubeObject> source = cir.getReturnValue();
        if (source != null && source.size() >= 16) {
            // A LittleTileTileEntity (or third-party LittleTile subtype) can
            // have special lighting or rendering despite looking like a box.
            // Reject mixed special-content tile entities before voxelizing.
            List<LittleTile> tiles = getTiles();
            synchronized (tiles) {
                for (LittleTile tile : tiles) {
                    Class<?> type = tile.getClass();
                    if (type != LittleTileBlock.class && type != LittleTileBlockColored.class
                        || tile.isStructureBlock) return;
                }
            }
            boolean distant = PerfSettings.distantLod
                && ClientLodManager.INSTANCE.wantsDistantMesh((TileEntityLittleTiles) (Object) this);
            long before = ltperf$revision;
            ArrayList<LittleTilesCubeObject> reduced = MergedCubeRenderer.merge(source, distant);
            if (reduced != source && PerfSettings.cacheOpaqueRenderCubes && before == ltperf$revision
                && ltperf$allOpaqueStandard(reduced)) {
                ltperf$cubeSnapshot = new RenderCubeSnapshot(
                    before, xCoord, yCoord, zCoord, distant, reduced);
            }
            cir.setReturnValue(reduced);
        }
    }

    @Unique
    private boolean ltperf$allOpaqueStandard(List<LittleTilesCubeObject> cubes) {
        for (LittleTilesCubeObject cube : cubes) {
            if (cube.block == null || !cube.block.isOpaqueCube()
                || cube.block.getRenderType() != 0 || cube.meta < 0
                || cube.cutoutInfo != null) return false;
        }
        return true;
    }

    @Inject(method = "getRenderBoundingBox()Lnet/minecraft/util/AxisAlignedBB;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void ltperf$getBounds(CallbackInfoReturnable<AxisAlignedBB> cir) {
        RenderSnapshot snapshot = ltperf$getSnapshot();
        if (snapshot != null) cir.setReturnValue(ltperf$boundsWithDynamicTiles(snapshot));
    }

    @Inject(method = "getMaxRenderDistanceSquared()D",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void ltperf$getDistance(CallbackInfoReturnable<Double> cir) {
        RenderSnapshot snapshot = ltperf$getSnapshot();
        if (snapshot != null) cir.setReturnValue(ltperf$distanceWithDynamicTiles(snapshot));
    }

    @Unique
    private RenderSnapshot ltperf$getSnapshot() {
        if (!PerfSettings.cacheStaticRenderBounds || customRenderingTiles == null) return null;

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
            // Custom-rendering tiles can have animated geometry. Only retain the
            // static subset; dynamic bounds are unioned on every query below.
            if (tile.needCustomRendering()) continue;
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


    @Unique
    private AxisAlignedBB ltperf$boundsWithDynamicTiles(RenderSnapshot staticSnapshot) {
        if (customRenderingTiles.isEmpty()) return staticSnapshot.bounds;
        AxisAlignedBB staticBounds = staticSnapshot.bounds;
        double minX = staticBounds.minX, minY = staticBounds.minY, minZ = staticBounds.minZ;
        double maxX = staticBounds.maxX, maxY = staticBounds.maxY, maxZ = staticBounds.maxZ;
        for (LittleTile tile : customRenderingTiles) {
            AxisAlignedBB box = tile.getRenderBoundingBox();
            if (box == null) continue;
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }
        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Unique
    private double ltperf$distanceWithDynamicTiles(RenderSnapshot staticSnapshot) {
        double maxDistance = staticSnapshot.distanceSquared;
        for (LittleTile tile : customRenderingTiles) {
            maxDistance = Math.max(maxDistance, tile.getMaxRenderDistanceSquared());
        }
        return maxDistance;
    }

    @Inject(method = "updateCustomRenderer()V", at = @At("RETURN"), remap = false)
    private void ltperf$customRendererChanged(CallbackInfo ci) {
        ltperf$invalidate();
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

}
