package com.xt9y.littletilesperf;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Defaults are conservative: the renderer remains unchanged for animated/custom tiles.
 * Every optimization can be switched off without changing saved world data.
 */
public final class PerfSettings {

    public static volatile boolean cacheStaticRenderBounds = true;
    public static volatile boolean mergeOpaqueMicrotiles = true;
    public static volatile boolean cullBuriedOpaqueFaces = true;
    public static volatile boolean distantLod = false;
    public static volatile int distantLodDistanceBlocks = 112;
    public static volatile boolean spatialFaceCulling = true;
    public static volatile boolean skipDuplicateRenderInvalidation = true;
    public static volatile boolean collectStatistics = false;

    private PerfSettings() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        cfg.load();
        spatialFaceCulling = cfg.getBoolean(
            "spatialFaceCulling", "rendering", true,
            "Use grid-indexed face coverage for blocks with at least 64 LittleTiles.");
        distantLod = cfg.getBoolean(
            "distantLod", "rendering", false,
            "Experimental: simplify tiny enclosed voids in far-away static microtiles; requires chunk rebuilds.");
        distantLodDistanceBlocks = cfg.getInt(
            "distantLodDistanceBlocks", "rendering", 112, 48, 256,
            "Distance where experimental LOD begins; 16-block hysteresis avoids rebuild oscillation.");
        cullBuriedOpaqueFaces = cfg.getBoolean(
            "cullBuriedOpaqueFaces", "rendering", true,
            "Remove completely buried faces between ordinary opaque microtiles, including different materials.");
        mergeOpaqueMicrotiles = cfg.getBoolean(
            "mergeOpaqueMicrotiles", "rendering", true,
            "Exactly coalesce fully opaque standard cuboids with identical block/meta/color before Angelica meshing.");
        cacheStaticRenderBounds = cfg.getBoolean(
            "cacheStaticRenderBounds", "rendering", true,
            "Cache static LittleTiles render bounds and maximum render distance per tile entity.");
        skipDuplicateRenderInvalidation = cfg.getBoolean(
            "skipDuplicateRenderInvalidation", "rendering", true,
            "One tile edit already invalidates rendering in update(); do not repeat that in updateNeighbor().");
        collectStatistics = cfg.getBoolean(
            "collectStatistics", "diagnostics", false,
            "Count cache hits/misses. Leave false when benchmarking FPS (counters add overhead).");
        if (cfg.hasChanged()) cfg.save();
    }
}
