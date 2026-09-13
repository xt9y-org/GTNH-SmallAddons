package com.xt9y.features.xtprofile;

final class XTProfileStats {

    private static final long MAX_SAMPLE_NS = 1_000_000_000L;

    static final class Timing {

        final int samples;
        final long averageNs;
        final int worstNs;

        Timing(int samples, long averageNs, int worstNs) {
            this.samples = samples;
            this.averageNs = averageNs;
            this.worstNs = worstNs;
        }
    }

    static Timing summarize(int[] values) {
        if (values == null || values.length == 0) return new Timing(0, 0, 0);

        long total = 0;
        int samples = 0;
        int worst = 0;
        for (int value : values) {
            if (value <= 0) continue;
            total += value;
            samples++;
            if (value > worst) worst = value;
        }
        return new Timing(samples, samples == 0 ? 0 : total / samples, worst);
    }

    static long busyDelta(long previousNs, long nowNs, boolean active) {
        if (!active || previousNs <= 0 || nowNs <= previousNs) return 0;
        return Math.min(nowNs - previousNs, MAX_SAMPLE_NS);
    }

    private XTProfileStats() {}
}
