package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

final class XTProfileSnapshotBuilder {

    private static final double IDEAL_TICK_NS = 50_000_000.0;

    static XTProfileData.Snapshot build(boolean running, long startedAtNs, long startedAtMillis, long totalDispatches,
        Collection<XTProfileData.CpuRecord> cpus, Map<String, XTProfileData.MachineRecord> machines,
        Collection<XTProfileData.RouteRecord> routes, Deque<XTProfileData.HistoryRecord> history) {
        long now = System.nanoTime();
        XTProfileData.Snapshot snapshot = new XTProfileData.Snapshot();
        snapshot.running = running;
        snapshot.startedAtMillis = startedAtMillis;
        snapshot.sessionMillis = startedAtNs == 0 ? 0 : millis(Math.max(0, now - startedAtNs));
        snapshot.totalDispatches = totalDispatches;
        snapshot.cpus = cpuViews(cpus, machines, now);
        snapshot.activeCrafts = snapshot.cpus.size();
        snapshot.machines = machineViews(cpus, machines);
        snapshot.machineCount = snapshot.machines.size();
        snapshot.totalBusyMillis = totalBusyMillis(machines.values());
        snapshot.routes = routeViews(cpus, routes, now);
        snapshot.history = historyViews(history);
        return snapshot;
    }

    private static List<XTProfileData.CpuView> cpuViews(Collection<XTProfileData.CpuRecord> records,
        Map<String, XTProfileData.MachineRecord> machines, long now) {
        List<XTProfileData.CpuView> views = new ArrayList<>();
        for (XTProfileData.CpuRecord record : records) {
            XTProfileData.CpuView view = new XTProfileData.CpuView();
            view.id = record.id;
            view.name = record.name;
            view.output = record.output;
            view.observedMillis = millis(Math.max(0, now - record.firstSeenNs));
            view.dispatches = record.dispatches;
            view.machineCount = record.machineIds.size();
            view.machines = machineNames(record.machineIds, machines);
            view.usedBytes = record.usedBytes;
            view.coProcessors = record.coProcessors;
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingLong((XTProfileData.CpuView view) -> view.dispatches)
                .reversed());
        return views;
    }

    private static List<XTProfileData.MachineView> machineViews(Collection<XTProfileData.CpuRecord> cpus,
        Map<String, XTProfileData.MachineRecord> machines) {
        List<XTProfileData.MachineView> views = new ArrayList<>();
        for (XTProfileData.MachineRecord record : machines.values()) {
            refreshTiming(record);
            XTProfileData.MachineView view = new XTProfileData.MachineView();
            view.id = record.id;
            view.name = record.name;
            view.type = record.type;
            view.location = record.location;
            view.active = record.active;
            view.dispatches = record.dispatches;
            view.busyMillis = millis(record.busyNs);
            view.averageTickNs = record.timing.averageNs;
            view.worstTickNs = record.timing.worstNs;
            view.timingSamples = record.timing.samples;
            view.tickSharePercent = record.timing.averageNs / IDEAL_TICK_NS * 100.0;
            view.activeCrafts = cpuNames(record.activeCpuIds, cpus);
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingDouble((XTProfileData.MachineView view) -> view.busyMillis)
                .reversed());
        return views;
    }

    private static List<XTProfileData.RouteView> routeViews(Collection<XTProfileData.CpuRecord> cpus,
        Collection<XTProfileData.RouteRecord> records, long now) {
        Set<Long> activeCpuIds = new HashSet<>();
        for (XTProfileData.CpuRecord cpu : cpus) activeCpuIds.add(cpu.id);

        List<XTProfileData.RouteView> views = new ArrayList<>();
        for (XTProfileData.RouteRecord record : records) {
            XTProfileData.RouteView view = new XTProfileData.RouteView();
            view.cpuId = record.cpuId;
            view.cpu = record.cpu;
            view.machineId = record.machineId;
            view.machine = record.machine;
            view.pattern = record.pattern;
            view.dispatches = record.dispatches;
            view.active = activeCpuIds.contains(record.cpuId);
            view.lastDispatchAgoMillis = millis(Math.max(0, now - record.lastDispatchNs));
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingLong((XTProfileData.RouteView view) -> view.dispatches)
                .reversed());
        return views;
    }

    private static List<XTProfileData.HistoryView> historyViews(Deque<XTProfileData.HistoryRecord> records) {
        List<XTProfileData.HistoryView> views = new ArrayList<>();
        for (XTProfileData.HistoryRecord record : records) {
            XTProfileData.HistoryView view = new XTProfileData.HistoryView();
            view.id = record.id;
            view.cpu = record.cpu;
            view.output = record.output;
            view.status = record.status;
            view.observedMillis = millis(record.observedNs);
            view.dispatches = record.dispatches;
            view.machineCount = record.machineCount;
            views.add(view);
        }
        return views;
    }

    private static void refreshTiming(XTProfileData.MachineRecord record) {
        TileEntity tile = record.tile == null ? null : record.tile.get();
        if (!(tile instanceof IGregTechTileEntity) || tile.isInvalid()) {
            record.timing = XTProfileStats.summarize(null);
            return;
        }
        record.timing = XTProfileStats.summarize(((IGregTechTileEntity) tile).getTimeStatistics());
    }

    private static List<String> machineNames(Set<String> ids, Map<String, XTProfileData.MachineRecord> machines) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            XTProfileData.MachineRecord machine = machines.get(id);
            if (machine != null) names.add(machine.name);
        }
        return names;
    }

    private static List<String> cpuNames(Set<Long> ids, Collection<XTProfileData.CpuRecord> cpus) {
        List<String> names = new ArrayList<>();
        for (XTProfileData.CpuRecord cpu : cpus) {
            if (ids.contains(cpu.id)) names.add(cpu.output + " · " + cpu.name);
        }
        return names;
    }

    private static double totalBusyMillis(Collection<XTProfileData.MachineRecord> machines) {
        long total = 0;
        for (XTProfileData.MachineRecord machine : machines) total += machine.busyNs;
        return millis(total);
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private XTProfileSnapshotBuilder() {}
}
