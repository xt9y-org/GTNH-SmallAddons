package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class XTProfileIntervalUnion {

    private final Map<String, List<Interval>> intervals = new LinkedHashMap<>();
    private final Map<String, Long> coveredNs = new LinkedHashMap<>();

    long add(String key, long startNs, long endNs) {
        if (key == null || key.isEmpty() || endNs <= startNs) return 0;

        List<Interval> current = intervals.get(key);
        if (current == null) current = new ArrayList<>();

        long mergedStart = startNs;
        long mergedEnd = endNs;
        boolean inserted = false;
        List<Interval> merged = new ArrayList<>(current.size() + 1);

        for (Interval interval : current) {
            if (interval.endNs < mergedStart) {
                merged.add(interval);
            } else if (mergedEnd < interval.startNs) {
                if (!inserted) {
                    merged.add(new Interval(mergedStart, mergedEnd));
                    inserted = true;
                }
                merged.add(interval);
            } else {
                mergedStart = Math.min(mergedStart, interval.startNs);
                mergedEnd = Math.max(mergedEnd, interval.endNs);
            }
        }

        if (!inserted) merged.add(new Interval(mergedStart, mergedEnd));

        long previous = coveredNs.getOrDefault(key, 0L);
        long covered = 0;
        for (Interval interval : merged) covered += interval.endNs - interval.startNs;

        intervals.put(key, merged);
        coveredNs.put(key, covered);
        return Math.max(0, covered - previous);
    }

    long covered(String key) {
        return coveredNs.getOrDefault(key, 0L);
    }

    void clear() {
        intervals.clear();
        coveredNs.clear();
    }

    private static final class Interval {

        final long startNs;
        final long endNs;

        Interval(long startNs, long endNs) {
            this.startNs = startNs;
            this.endNs = endNs;
        }
    }
}
