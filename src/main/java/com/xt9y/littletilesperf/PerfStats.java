package com.xt9y.littletilesperf;

import java.util.concurrent.atomic.LongAdder;

public final class PerfStats {

    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder MISSES = new LongAdder();
    private static final LongAdder INVALIDATIONS = new LongAdder();
    private static final LongAdder DUPLICATE_RENDERS_SKIPPED = new LongAdder();
    private static final LongAdder SCANNED_TILES = new LongAdder();
    private static final LongAdder MERGED_INPUT = new LongAdder();
    private static final LongAdder MERGED_OUTPUT = new LongAdder();
    private static final LongAdder HIDDEN_OPAQUE_FACES = new LongAdder();

    private PerfStats() {}

    public static void hiddenOpaqueFaces(int count) {
        if (PerfSettings.collectStatistics && count > 0) HIDDEN_OPAQUE_FACES.add(count);
    }

    public static void merged(int before, int after) {
        if (PerfSettings.collectStatistics) {
            MERGED_INPUT.add(before);
            MERGED_OUTPUT.add(after);
        }
    }

    public static void hit() {
        if (PerfSettings.collectStatistics) HITS.increment();
    }

    public static void miss(int tiles) {
        if (PerfSettings.collectStatistics) {
            MISSES.increment();
            SCANNED_TILES.add(tiles);
        }
    }

    public static void invalidated() {
        if (PerfSettings.collectStatistics) INVALIDATIONS.increment();
    }

    public static void skippedRender() {
        if (PerfSettings.collectStatistics) DUPLICATE_RENDERS_SKIPPED.increment();
    }

    public static String summary() {
        return "hits=" + HITS.sum() + " misses=" + MISSES.sum()
            + " invalidations=" + INVALIDATIONS.sum()
            + " microtiles scanned on misses=" + SCANNED_TILES.sum()
            + " duplicate invalidations avoided=" + DUPLICATE_RENDERS_SKIPPED.sum()
            + " merged cubes " + MERGED_INPUT.sum() + " -> " + MERGED_OUTPUT.sum()
            + " buried opaque faces skipped=" + HIDDEN_OPAQUE_FACES.sum()
            + " statistics=" + (PerfSettings.collectStatistics ? "on" : "off");
    }

    public static void reset() {
        HITS.reset();
        MISSES.reset();
        INVALIDATIONS.reset();
        DUPLICATE_RENDERS_SKIPPED.reset();
        SCANNED_TILES.reset();
        MERGED_INPUT.reset();
        MERGED_OUTPUT.reset();
        HIDDEN_OPAQUE_FACES.reset();
    }
}
