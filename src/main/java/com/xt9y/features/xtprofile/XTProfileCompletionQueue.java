package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.List;

final class XTProfileCompletionQueue {

    static final class Entry {

        final long cpuId;
        final XTProfilePendingOperations.Completion completion;

        Entry(long cpuId, XTProfilePendingOperations.Completion completion) {
            this.cpuId = cpuId;
            this.completion = completion;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    void defer(long cpuId, List<XTProfilePendingOperations.Completion> completions) {
        if (completions == null || completions.isEmpty()) return;
        for (XTProfilePendingOperations.Completion completion : completions) {
            if (completion != null) entries.add(new Entry(cpuId, completion));
        }
    }

    List<Entry> drain() {
        if (entries.isEmpty()) return new ArrayList<>();
        List<Entry> drained = new ArrayList<>(entries);
        entries.clear();
        return drained;
    }

    void clearCpu(long cpuId) {
        entries.removeIf(entry -> entry.cpuId == cpuId);
    }

    void clear() {
        entries.clear();
    }
}
