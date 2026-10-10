package com.xt9y.littletilesperf;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Defaults are conservative: the renderer remains unchanged for animated/custom tiles.
 * Every optimization can be switched off without changing saved world data.
 */
public final class PerfSettings {

    public static volatile boolean cacheStaticRenderBounds = true;
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
