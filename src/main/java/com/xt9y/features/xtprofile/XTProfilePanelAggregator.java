package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class XTProfilePanelAggregator {

    private static final double TICK_BUDGET_NS = 50_000_000.0;

    static XTProfilePanelData.View build(Collection<XTProfileData.MediumRecord> media, long cpuId) {
        XTProfilePanelData.View view = new XTProfilePanelData.View();
        view.cpuId = cpuId;

        List<PendingEntry> selected = new ArrayList<>();
        for (XTProfileData.MediumRecord medium : media) {
            long dispatches = cpuId <= 0 ? medium.dispatches : medium.dispatchesByCpu.getOrDefault(cpuId, 0L);
            if (dispatches <= 0) continue;

            long busyNs = cpuId <= 0 ? medium.busyNs : medium.busyNsByCpu.getOrDefault(cpuId, 0L);
            long tickCostNs = cpuId <= 0 ? medium.tickCostNs : medium.tickCostNsByCpu.getOrDefault(cpuId, 0L);
            long activeTicks = cpuId <= 0 ? medium.activeTicks : medium.activeTicksByCpu.getOrDefault(cpuId, 0L);
            view.totalDispatches += dispatches;
            selected.add(new PendingEntry(medium, dispatches, busyNs, tickCostNs, activeTicks));
        }

        selected.sort(
            Comparator.comparingLong((PendingEntry entry) -> entry.busyNs)
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
            entry.craftTimeMillis = pending.busyNs / 1_000_000.0;
            double averageTickNs = pending.activeTicks <= 0 ? 0.0 : pending.tickCostNs / (double) pending.activeTicks;
            entry.tpsUsagePercent = averageTickNs * 100.0 / TICK_BUDGET_NS;
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
        final long busyNs;
        final long tickCostNs;
        final long activeTicks;

        PendingEntry(XTProfileData.MediumRecord medium, long dispatches, long busyNs, long tickCostNs,
            long activeTicks) {
            this.medium = medium;
            this.dispatches = dispatches;
            this.busyNs = busyNs;
            this.tickCostNs = tickCostNs;
            this.activeTicks = activeTicks;
        }
    }

    private XTProfilePanelAggregator() {}
}
