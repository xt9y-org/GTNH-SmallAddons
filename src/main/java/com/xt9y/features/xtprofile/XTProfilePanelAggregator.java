package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class XTProfilePanelAggregator {

    static XTProfilePanelData.View build(Collection<XTProfileData.MediumRecord> media, long cpuId) {
        XTProfilePanelData.View view = new XTProfilePanelData.View();
        view.cpuId = cpuId;

        List<PendingEntry> selected = new ArrayList<>();
        for (XTProfileData.MediumRecord medium : media) {
            long dispatches = cpuId <= 0 ? medium.dispatches : medium.dispatchesByCpu.getOrDefault(cpuId, 0L);
            if (dispatches <= 0) continue;
            view.totalDispatches += dispatches;
            selected.add(new PendingEntry(medium, dispatches));
        }

        selected.sort(
            Comparator.comparingLong((PendingEntry entry) -> entry.dispatches)
                .reversed()
                .thenComparing(entry -> safe(entry.medium.name), String.CASE_INSENSITIVE_ORDER));

        for (PendingEntry pending : selected) {
            XTProfileData.MediumRecord medium = pending.medium;
            XTProfilePanelData.Entry entry = new XTProfilePanelData.Entry();
            entry.id = medium.id;
            entry.name = medium.name;
            entry.type = medium.type;
            entry.machine = medium.machine;
            entry.item = medium.item;
            entry.dispatches = pending.dispatches;
            entry.sharePercent = view.totalDispatches == 0 ? 0.0 : pending.dispatches * 100.0 / view.totalDispatches;
            entry.hasLocation = medium.hasLocation;
            entry.dimension = medium.dimension;
            entry.x = medium.x;
            entry.y = medium.y;
            entry.z = medium.z;
            view.entries.add(entry);
        }
        return view;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class PendingEntry {

        final XTProfileData.MediumRecord medium;
        final long dispatches;

        PendingEntry(XTProfileData.MediumRecord medium, long dispatches) {
            this.medium = medium;
            this.dispatches = dispatches;
        }
    }

    private XTProfilePanelAggregator() {}
}
