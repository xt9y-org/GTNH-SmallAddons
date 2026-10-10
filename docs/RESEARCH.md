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
| Local translucent face culling | `LittleTilesFaceCuller.computeCoverage` loops each cullable cube over **all** cubes in the block | O(T²) worst case; expensive chunk rebuild spikes for dense translucent structures |
| Already-opaque tiles | Upstream `ignoreForCulling` skips fully opaque blocks | Avoid extra indexing for mostly opaque blocks |
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
8. Speed up local translucent face culling with six directional integer-grid slab indexes. Only boxes
   opposite a face's coordinate plane can cover it. The same candidate order per face is retained,
   as are texture/block metadata/color rules, neighbor-block handling and integer clipping.
   For <64 tiles, <8 cullable tiles, or out-of-grid geometry, fall back to upstream. Opt-out via
   `rendering.spatialFaceCulling=false`. This replaces the O(T²) candidate scan with O(T +
   candidate face pairs) in eligible large blocks, but has **not** yet been benchmarked inside Minecraft.


## Additional source-level observation

[LittleTilesFaceCuller.java](https://github.com/GTNewHorizons/LittleTiles/blob/master/src/main/java/com/creativemd/littletiles/client/render/LittleTilesFaceCuller.java)
contains a quadratic nested loop in `computeCoverage`. It skips opaque microtiles, so ordinary
stone-heavy builds may never hit it; glass and other translucent microtiles are better targets.
The adjacent-block coverage still runs unchanged. This patch does **not** reduce GPU vertices:
transparent surface merging and geometry LOD need separate renderer-integrated work.

A deterministic randomized Python equivalence check (5, 20, 80, 200 candidate sets, 30 iterations
each) matched the brute-force face candidate and ordering rules for randomized box placements,
material IDs, opaque exclusions, and cutout exclusions. This is a **model check only**, not a
compiled Minecraft integration test; visual correctness with custom blocks needs in-game validation.

## Renderer-facing geometry work in this branch

These features run during LittleTiles' `getRenderingCubes()` / `computeCoverage()`
calls, before its `SpecialBlockTilesRenderer.renderWorldBlock` submits geometry to
the same ISBRH pipeline used by Angelica. Angelica keeps ownership of its
multi-threaded chunk compilation, lightmaps, shader material passes, VBO
upload and frustum culling.

- `DenseGridMesher`: bounds-limited 16×16×16 occupancy rasterization and
  deterministic exact greedy cuboids. Matches `Block` identity, metadata and tint.
  Preserves unsupported meshes, partial and transparent shapes through fallback.
- `MergedCubeRenderer`: rejects overlapping cells and tiles with unknown render
  types; never alters real `LittleTile` objects. Only exact `LittleTileBlock`
  and `LittleTileBlockColored` types are considered, excluding structures.
- `OpaqueFaceCuller`: removes faces for which every neighboring 1/16 cell is
  occupied by a standard opaque block. Supports different material boundaries;
  leaves partially occluded and external faces unchanged. Uses a lightweight
  `IFaceClipper` rather than the expensive triangle-culling pipeline.
- `RenderCubeSnapshot`: versioned compact descriptor cache. Both the method
  return and cached snapshot use separate list wrappers to avoid accidental list
  mutations. Invalidated on edit, neighbor geometry update and chunk unload.
- `ClientLodManager`: optional distance-band tracking with limited mesh
  invalidations per tick; the 2×2×2 interior-only simplifier refuses to change
  differing materials, cutout collision volumes or block boundary voxels.

The render-cube cache is limited to 100% opaque standard geometry.
This is not a generic translucent/UV-aware surface atlas merger.

### Computational model

Let N be microtile cubes in one block, V ≤ 4,096 grid voxels, F the emitted
faces. The previous renderer processes O(N) cubes and up to O(6N) faces, plus
any special clipping costs. The exact greedy prepass costs O(N + V + material
lookup overhead) for disjoint boxes, and reduces emitted geometry to O(6M)
where M is the number of merged boxes. In large homogeneous constructions,
M may be dramatically smaller than N. Faces hidden between different opaque
material boxes are also removed by a bounded-voxel check.

Actual CPU time, VBO bytes, GPU utilization, chunk rebuild latency, memory
retention, and visual parity **must be measured** on the user's real save.
The source-level complexity does not establish any FPS or GPU speedup.

## Next high-impact experiments (NOT yet implemented)

1. **Capture ground truth first:** use Spark/async-profiler CPU sampling and Angelica chunk-meshing
   counters to distinguish idle FPS, frame spikes and rebuild latency.
2. **Cross-block and UV-aware face merging:** extend the within-block greedy
   box union to carefully stitch visible coplanar surfaces across block
   boundaries while preserving texture tile repetition, shader material
   overrides, AO and per-block lighting.
3. **Real visual LOD:** replace the experimental cavity-only mode with camera-
   coherent, screen-space-error meshes/impostors for complex exterior detail.
   Requires dual mesh storage and safe near/far transitions in Angelica.
4. **Cross-chunk reuse:** instanced GPU meshes for duplicated structures,
   content-addressed signatures and reference-counted buffer resources; the
   existing in-block descriptor cache is not a GPU VBO cache.
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
