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
or remove the jar if needed. No completed build or in-game performance validation has been reported yet. This branch targets the
GTNH LittleTiles 1.6.59 dependency; other versions require compatibility verification.
