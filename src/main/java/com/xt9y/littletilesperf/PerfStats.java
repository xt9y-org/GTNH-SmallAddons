package com.xt9y.littletilesperf;

import java.util.concurrent.atomic.LongAdder;

public final class PerfStats {

    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder MISSES = new LongAdder();
    private static final LongAdder INVALIDATIONS = new LongAdder();
    private static final LongAdder DUPLICATE_RENDERS_SKIPPED = new LongAdder();
    private static final LongAdder SCANNED_TILES = new LongAdder();

    private PerfStats() {}

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
            + " statistics=" + (PerfSettings.collectStatistics ? "on" : "off");
    }

    public static void reset() {
        HITS.reset();
        MISSES.reset();
        INVALIDATIONS.reset();
        DUPLICATE_RENDERS_SKIPPED.reset();
        SCANNED_TILES.reset();
    }
}
