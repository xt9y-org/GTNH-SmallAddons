# LittleTiles Performance — GTNH 1.7.10

**Independent client-side optimization mod**, developed on the `little-tiles` branch.
No classes or features from XT9Y Features are included. The `master` branch is unchanged.

## Why this approach

LittleTiles on GTNH uses a tile entity holding many individual microtiles, but chunk geometry is
already baked/cached. Its current source still rescans **every microtile** when the renderer
requests a tile entity's bounding box and maximum render distance. For N static microtiles
inside a tile entity, those operations cost O(N) on every call; caching their result makes
the unchanged, static hot path O(1).

Also, in the verified GTNH code path, `updateTiles(boolean)` calls `update()` then
`updateNeighbor()`, and both invoke `updateRender()`. This mod omits the second
redundant render invalidation only during that specific update, leaving normal
neighbor notifications in place.

Additionally, dense translucent blocks previously ran an O(T²) local occluder search
in `LittleTilesFaceCuller.computeCoverage`. The new spatial index buckets potential
occluders by six face directions and the 1/16-block coordinate plane, preserving the
upstream clipping and neighbor-block pass. It is enabled only for at least 64 tiles
with at least 8 cullable tiles; smaller/opaque-heavy/unusual cases use upstream.

## Geometry reduction (new)

**Exact, enabled by default:**
- Ordinary opaque boxes of the same block ID, metadata and RGB tint are
  greedily re-packed on the 16³ cell grid into fewer rectangular volumes.
  Every material voxel is preserved; interiors shared by merged boxes emit no faces.
- Fully buried opaque faces between *different* block materials are omitted
  before the original Angelica chunk pipeline emits VBO vertices. Partial,
  unknown, overlapping, custom, and boundary cases retain original geometry.
- Reduced descriptors are cached per static tile entity and invalidated on
  edits and unload. Chunk rebuilds can reuse O(reduced boxes), rather than
  rebuilding O(original microtiles), read-only descriptors.
- These are **chunk mesh preprocessing changes** inside LittleTiles'
  standard ISBRH/Angelica path; no custom OpenGL renderer and no save conversion.

**Distance detail (experimental, off by default):**
- Beyond a configurable distance, opt-in 2×2×2 interior-only occupancy
  simplification seals one-voxel cavities surrounded by the same opaque
  material. Block-edge voxels, different materials, and cutout bounding
  volumes are preserved.
- A client-side camera-distance tracker requests at most two chunk rebuilds
  per tick as LOD bands change, with ±16-block hysteresis.
- This is deliberately a *very conservative* LOD, **not** a broad impostor
  system or full screen-space-error reducer. It may have little benefit for
  detailed exterior surfaces. It needs in-game visual and rebuild testing.

**These are CPU optimizations, not a replacement for Angelica or a new GPU renderer.**
Existing LittleTiles geometry and save files are untouched. Static builds benefit most.
Mixed static/animated tile entities cache only the static subset; dynamic parts are recomputed on each query.

## Build & install

Requires GTNH Minecraft 1.7.10, LittleTiles, and GTNH's usual Mixin stack.
Use Java 21 for building (Gradle 9.4), not for the bytecode target (Java 8).

```sh
./gradlew test build
```

Copy **only** the resulting mod jar from `build/libs` to the client `mods` directory.
Do not replace LittleTiles or Angelica. Keep the original jar as a rollback path.

## Configuration

`config/littletilesperf.cfg`:

- `rendering.mergeOpaqueMicrotiles=true` — exact greedy union of compatible opaque voxel boxes.
- `rendering.cullBuriedOpaqueFaces=true` — remove fully occluded opaque faces inside a LittleTiles block.
- `rendering.cacheOpaqueRenderCubes=true` — retain compact static render geometry between rebuilds.
- `rendering.distantLod=false` — experimental interior detail simplification only; opt in after backing up the world.
- `rendering.distantLodDistanceBlocks=112` — LOD threshold in blocks, with ±16-block hysteresis.
- `rendering.spatialFaceCulling=true` — index glass/translucent face-occluder candidates
  rather than compare every microtile with every other. Switch off if translucent faces
  appear incorrect in-game.
- `rendering.cacheStaticRenderBounds=true` — cache bounds and render range for unanimated tiles.
- `rendering.skipDuplicateRenderInvalidation=true` — eliminate duplicate invalidation
  during `updateTiles` (disable first if edited tiles visually fail to update).
- `diagnostics.collectStatistics=false` — leave off for production benchmarking.

Run `/ltperf` in the client. `/ltperf stats on` starts recording diagnostic counters and a 2,048-frame ring buffer of frame intervals (p50/p95/p99);
`/ltperf stats reset` resets them; `/ltperf stats off` stops counting.

## Benchmark protocol

1. Back up the world. Use exactly the same camera pose, render distance, pack version,
   shader settings, resolution, JVM, and world seed.
2. Record median FPS, p95/p99 frame times, memory and chunk-rebuild latency over
   a fixed camera route in the **unmodified GTNH client**.
3. Add this jar, warm up the same route, and record the same metrics.
   Compare with the exact merge/cull/cache flags on and off independently.
4. Repeat with each optimization disabled independently to isolate regressions, especially dense glass walls.
5. Test stationary multi-million-tile builds, heavy editing, chunk loading, colorful
   tiles, sloped/cutout tiles, and animated doors separately.

Frame intervals measure client pacing, **not GPU timestamps**. FPS and memory gains **have not yet been validated on your actual world**.
A bounded GPU, RAM and chunk-build budget makes strictly zero FPS cost for unlimited
visible geometry impossible. See `docs/RESEARCH.md` for next steps.

## Compatibility and rollback

Designed against the GTNH 1.7.10 LittleTiles tile-entity source with `updateTiles(boolean)`,
`updateNeighbor()`, `getRenderBoundingBox()` and `getMaxRenderDistanceSquared()`.
The Mixin is client-only. Other Mixin mods that redirect those *same*
`updateNeighbor/updateTiles` call sites may conflict; disable `skipDuplicateRenderInvalidation`
or remove the jar if needed. No successful CI build or in-game performance validation has been verified in this session. This branch targets the
GTNH LittleTiles 1.6.59 dependency; other versions require compatibility verification.

## Known limits

- Different-material exterior faces cannot be merged without preserving
  texture atlas UV repeat, tint, AO and lightmap semantics. No unsafe shader
  or translucent face merging is attempted.
- Only within-block 16³ geometry is merged; **cross-block/cross-chunk**
  greedy meshing and true GPU-instanced repeated structures are not implemented.
- Angelica already handles chunk buffering, frustum culling, and GPU uploads;
  this mod supplies fewer faces/descriptors to that existing pipeline.
- The 1.6.59 dependency is the GTNH pack's listed LittleTiles version, but
  compatibility still depends on the exact user's installed jar.
