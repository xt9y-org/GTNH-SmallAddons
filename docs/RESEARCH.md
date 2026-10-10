# LittleTiles performance research — 2026-10-10

## Source basis

- [GTNH LittleTiles fork](https://github.com/GTNewHorizons/LittleTiles) (1.7.10)
- [LittleTiles tile entity](https://github.com/GTNewHorizons/LittleTiles/blob/master/src/main/java/com/creativemd/littletiles/common/tileentity/TileEntityLittleTiles.java)
- [LittleTile geometry cache](https://github.com/GTNewHorizons/LittleTiles/blob/master/src/main/java/com/creativemd/littletiles/common/utils/LittleTileGeometryCache.java)
- [Angelica renderer and compatibility matrix](https://github.com/GTNewHorizons/Angelica)
- [GTNH modpack configuration](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/blob/master/config/angelica-modules.cfg)

## Verified costs in the source

| Area | Current behavior | Consequence |
| --- | --- | --- |
| Render bounds | Tile entity allocates a snapshot of its tile list, then scans every tile on `getRenderBoundingBox()` | O(T) CPU + allocation for each call |
| Render distance | Loops over all tiles on `getMaxRenderDistanceSquared()` | O(T) CPU per call |
| Chunk mesh collection | `getRenderingCubes()` snapshots membership/cut generations, then creates cube descriptors | O(T) per rebuild; necessary until geometry changes |
| Geometry cache | `LittleTileGeometryCache` already retains cut meshes/culling result with generation checks | Do not blindly introduce another copy |
| Edit invalidation | `updateTiles` calls `update` then `updateNeighbor`, both triggering `updateRender` client-side | Duplicate neighbor invalidation and render range marking |
| GPU | Angelica does buffered, threaded chunk building | Avoid competing global renderer, OptiFine/FastCraft, duplicate meshing |

Here T is the number of microtiles *inside one LittleTiles tile entity*. There are also
independent costs in draw-call count, **visible face count**, vertices, shader overdraw,
VRAM, chunk rebuilds, world RAM and server/network serialization.

## Completed in this branch

1. Cache render bounding box and max render distance together, with O(1) hot validity check.
2. Invalidate on edit, tile-list replacement, explicit render invalidation, and unload.
3. Cache static tiles in mixed structures while recomputing animated/custom tile bounds every query.
4. Eliminate the second `updateRender` only inside `updateTiles`; preserve normal neighbor notifications.
5. Provide runtime diagnostics and opt-out config.
6. Add cache-key and frame-percentile unit tests and Java 21 Gradle CI.\n7. Add optional fixed-memory client frame-time sampling for benchmark p50/p95/p99 (not GPU timestamps).

## Next high-impact experiments (NOT yet implemented)

1. **Capture ground truth first:** use Spark/async-profiler CPU sampling and Angelica chunk-meshing
   counters to distinguish idle FPS, frame spikes and rebuild latency.
2. **Greedy mesh union of fully coplanar, compatible *visible* faces** across multiple
   microtiles; preserve UV orientation, metadata, color tint, lightmap, collision,
   translucent sorting, and sloped/cutout meshes. Needs source-level integration in
   LittleTiles/Angelica, not a naive TE renderer overwrite.
3. **Visibility/LOD:** reduce distant microgeometry based on screen-space error and material,
   while retaining silhouettes. Optional distant billboards/impostors for large static bases.
4. **Chunk-mesh cache:** safely reuse meshes of repeated built structures with content-addressed
   signatures and reference-counted GPU resources, invalidating on lighting/material edits.
5. **Edit batching:** spatially coalesce neighboring render-region invalidations per tick
   without delaying necessary server state changes; stress test multiplayer sync.
6. **Memory limits:** compressed per-chunk descriptors and bounded LRU for geometry, no
   unbounded per-tile caches.
7. **Corner cases:** colored blocks, emissive blocks, cutouts, transparent surfaces, doors,
   tile entities with dynamic renderers, shaders, chunks at coordinate boundaries, rapid edits.

## Performance claims

No claim of "zero FPS loss for millions of tiles" is established here. Without reducing
visible geometry, any finite GPU eventually saturates. The aim is to make **unchanged**
static scenery incur cost proportional to *visible surface complexity*, not the original
number of interior microtiles. Quantitative speedup must be measured on a representative
save, not inferred from code complexity.
