package com.xt9y.littletilesperf;

import java.util.Arrays;
import java.util.Locale;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Optional, allocation-free per-frame pacing sample (not a GPU timer query).
 * Stores start-to-start render intervals in a fixed-size ring.
 */
public final class FrameTimes {

    public static final FrameTimes INSTANCE = new FrameTimes();
    private static final int CAPACITY = 2048;
    private final long[] intervalsNs = new long[CAPACITY];
    private int count;
    private int next;
    private long previousFrameStartNs;

    private FrameTimes() {}

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!PerfSettings.collectStatistics) {
            previousFrameStartNs = 0;
            return;
        }
        long now = System.nanoTime();
        long prev = previousFrameStartNs;
        previousFrameStartNs = now;
        if (prev == 0) return;
        long delta = now - prev;
        if (delta <= 0 || delta > 1000000000L) return;
        intervalsNs[next] = delta;
        next = (next + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    /** Called from the client thread while no frame event is running. */
    public void reset() {
        count = 0;
        next = 0;
        previousFrameStartNs = 0;
    }

    public String report() {
        if (count == 0) return "no frames sampled; use /ltperf stats on";
        long[] sorted = Arrays.copyOf(intervalsNs, count);
        Arrays.sort(sorted);
        double p50 = percentileSorted(sorted, 0.50) / 1000000.0;
        double p95 = percentileSorted(sorted, 0.95) / 1000000.0;
        double p99 = percentileSorted(sorted, 0.99) / 1000000.0;
        double fps = p50 > 0 ? 1000.0 / p50 : 0;
        return String.format(Locale.ROOT,
            "%d frames; p50 %.2fms (%.1f FPS), p95 %.2fms, p99 %.2fms",
            count, p50, fps, p95, p99);
    }

    /** Nearest-rank percentile: stable, deterministic, no interpolation. */
    static long percentileSorted(long[] sorted, double percentile) {
        if (sorted.length == 0) throw new IllegalArgumentException("empty samples");
        int index = Math.max(0, Math.min(sorted.length - 1,
            (int) Math.ceil(percentile * sorted.length) - 1));
        return sorted[index];
    }
}
