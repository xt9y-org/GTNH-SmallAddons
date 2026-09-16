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
        double mspt, double tps, double machineMspt, double ae2Mspt, double overheadMspt,
        Collection<XTProfileData.CpuRecord> cpus, Map<String, XTProfileData.MachineRecord> machines,
        Map<String, XTProfileData.ItemRecord> items, Collection<XTProfileData.RouteRecord> routes,
        Collection<XTProfileData.TickSampleRecord> ticks, Deque<XTProfileData.HistoryRecord> history) {
        long now = System.nanoTime();
        XTProfileData.Snapshot snapshot = new XTProfileData.Snapshot();
        snapshot.running = running;
        snapshot.startedAtMillis = startedAtMillis;
        snapshot.sessionMillis = startedAtNs == 0 ? 0 : millis(Math.max(0, now - startedAtNs));
        snapshot.totalDispatches = totalDispatches;
        snapshot.mspt = mspt;
        snapshot.tps = tps;
        snapshot.machineMspt = machineMspt;
        snapshot.ae2Mspt = ae2Mspt;
        snapshot.overheadMspt = overheadMspt;
        snapshot.cpus = cpuViews(cpus, machines, now);
        snapshot.activeCrafts = snapshot.cpus.size();
        snapshot.machines = machineViews(cpus, machines, items);
        snapshot.machineCount = snapshot.machines.size();
        snapshot.totalBusyMillis = totalBusyMillis(machines.values());
        snapshot.items = itemViews(cpus, items, machines, now);
        snapshot.routes = routeViews(cpus, routes, now);
        snapshot.ticks = tickViews(ticks);
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
            view.outputKey = record.outputKey;
            view.observedMillis = millis(Math.max(0, now - record.firstSeenNs));
            view.dispatches = record.dispatches;
            view.machineCount = record.machineIds.size();
            view.machines = machineNames(record.machineIds, machines);
            view.usedBytes = record.usedBytes;
            view.coProcessors = record.coProcessors;
            view.averageAe2TickNs = record.ae2TickSamples == 0 ? 0 : record.ae2TickNs / record.ae2TickSamples;
            view.tickSharePercent = view.averageAe2TickNs / IDEAL_TICK_NS * 100.0;
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingLong((XTProfileData.CpuView view) -> view.dispatches)
                .reversed());
        return views;
    }

    private static List<XTProfileData.MachineView> machineViews(Collection<XTProfileData.CpuRecord> cpus,
        Map<String, XTProfileData.MachineRecord> machines, Map<String, XTProfileData.ItemRecord> items) {
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
            view.tickCostMillis = millis(record.tickCostNs);
            view.averageTickNs = record.timing.averageNs;
            view.worstTickNs = record.timing.worstNs;
            view.timingSamples = record.timing.samples;
            view.tickSharePercent = record.timing.averageNs / IDEAL_TICK_NS * 100.0;
            view.mspt = record.timing.averageNs / 1_000_000.0;
            view.activeCrafts = cpuNames(record.activeCpuIds, cpus);
            view.cpuUsage = machineCpuUsage(record, cpus);
            view.topItemKey = largestKey(record.tickCostNsByItem, record.dispatchesByItem);
            XTProfileData.ItemRecord item = view.topItemKey == null ? null : items.get(view.topItemKey);
            view.topItem = item == null ? null : item.name;
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingDouble((XTProfileData.MachineView view) -> view.tickCostMillis)
                .thenComparingDouble(view -> view.busyMillis)
                .reversed());
        return views;
    }

    private static List<XTProfileData.AttributionView> machineCpuUsage(XTProfileData.MachineRecord record,
        Collection<XTProfileData.CpuRecord> cpus) {
        Set<Long> ids = new HashSet<>();
        ids.addAll(record.dispatchesByCpu.keySet());
        ids.addAll(record.busyNsByCpu.keySet());
        ids.addAll(record.tickCostNsByCpu.keySet());
        List<XTProfileData.AttributionView> views = new ArrayList<>();
        for (Long id : ids) {
            XTProfileData.AttributionView view = new XTProfileData.AttributionView();
            view.cpuId = id;
            view.cpu = cpuName(id, cpus);
            view.dispatches = value(record.dispatchesByCpu, id);
            view.busyMillis = millis(value(record.busyNsByCpu, id));
            view.tickCostMillis = millis(value(record.tickCostNsByCpu, id));
            view.observedMillis = view.busyMillis;
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingDouble((XTProfileData.AttributionView view) -> view.tickCostMillis)
                .thenComparingLong(view -> view.dispatches)
                .reversed());
        return views;
    }

    private static List<XTProfileData.ItemView> itemViews(Collection<XTProfileData.CpuRecord> cpus,
        Map<String, XTProfileData.ItemRecord> items, Map<String, XTProfileData.MachineRecord> machines, long now) {
        Set<Long> activeCpuIds = cpuIds(cpus);
        List<XTProfileData.ItemView> views = new ArrayList<>();
        for (XTProfileData.ItemRecord record : items.values()) {
            XTProfileData.ItemView view = new XTProfileData.ItemView();
            view.key = record.key;
            view.name = record.name;
            view.itemId = record.itemId;
            view.texture = record.texture;
            view.damage = record.damage;
            view.outputAmount = record.outputAmount;
            view.dispatches = record.dispatches;
            view.processingMillis = millis(record.processingNs);
            view.tickCostMillis = millis(record.tickCostNs);
            view.machineIds = sortedStrings(record.machineIds);
            view.machines = machineNames(record.machineIds, machines);
            view.cpuIds = sortedLongs(record.cpuIds);
            view.inputs = itemInputs(record.inputs, items);
            view.cpuUsage = itemCpuUsage(record, cpus, activeCpuIds, now);
            double observed = 0;
            for (XTProfileData.AttributionView usage : view.cpuUsage) observed += usage.observedMillis;
            view.observedMillis = observed;
            view.waitingMillis = Math.max(0, view.observedMillis - view.processingMillis);
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingDouble((XTProfileData.ItemView view) -> view.observedMillis)
                .thenComparingDouble(view -> view.tickCostMillis)
                .reversed());
        return views;
    }

    private static List<XTProfileData.AttributionView> itemCpuUsage(XTProfileData.ItemRecord record,
        Collection<XTProfileData.CpuRecord> cpus, Set<Long> activeCpuIds, long now) {
        Set<Long> ids = new HashSet<>();
        ids.addAll(record.cpuIds);
        ids.addAll(record.dispatchesByCpu.keySet());
        List<XTProfileData.AttributionView> views = new ArrayList<>();
        for (Long id : ids) {
            long first = value(record.firstSeenNsByCpu, id);
            long last = activeCpuIds.contains(id) ? now : value(record.lastSeenNsByCpu, id);
            XTProfileData.AttributionView view = new XTProfileData.AttributionView();
            view.cpuId = id;
            view.cpu = cpuName(id, cpus);
            view.dispatches = value(record.dispatchesByCpu, id);
            view.busyMillis = millis(value(record.processingNsByCpu, id));
            view.tickCostMillis = millis(value(record.tickCostNsByCpu, id));
            view.observedMillis = first == 0 ? 0 : millis(Math.max(0, last - first));
            views.add(view);
        }
        Collections.sort(
            views,
            Comparator.comparingDouble((XTProfileData.AttributionView view) -> view.observedMillis)
                .thenComparingDouble(view -> view.tickCostMillis)
                .reversed());
        return views;
    }

    private static List<XTProfileData.ItemAmountView> itemInputs(Map<String, Long> inputs,
        Map<String, XTProfileData.ItemRecord> items) {
        List<XTProfileData.ItemAmountView> views = new ArrayList<>();
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            XTProfileData.ItemAmountView view = new XTProfileData.ItemAmountView();
            view.key = entry.getKey();
            XTProfileData.ItemRecord input = items.get(entry.getKey());
            view.name = input == null ? entry.getKey() : input.name;
            view.amount = entry.getValue();
            views.add(view);
        }
        return views;
    }

    private static List<XTProfileData.RouteView> routeViews(Collection<XTProfileData.CpuRecord> cpus,
        Collection<XTProfileData.RouteRecord> records, long now) {
        Set<Long> activeCpuIds = cpuIds(cpus);
        List<XTProfileData.RouteView> views = new ArrayList<>();
        for (XTProfileData.RouteRecord record : records) {
            XTProfileData.RouteView view = new XTProfileData.RouteView();
            view.cpuId = record.cpuId;
            view.cpu = record.cpu;
            view.machineId = record.machineId;
            view.machine = record.machine;
            view.mediumId = record.mediumId;
            view.medium = record.medium;
            view.mediumType = record.mediumType;
            view.mediumLocation = record.mediumLocation;
            view.itemKey = record.itemKey;
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

    private static List<XTProfileData.TickView> tickViews(Collection<XTProfileData.TickSampleRecord> records) {
        List<XTProfileData.TickView> views = new ArrayList<>();
        for (XTProfileData.TickSampleRecord record : records) {
            XTProfileData.TickView view = new XTProfileData.TickView();
            view.atMillis = record.atMillis;
            view.mspt = record.mspt;
            view.tps = record.tps;
            view.machineMspt = record.machineMspt;
            view.ae2Mspt = record.ae2Mspt;
            view.overheadMspt = record.overheadMspt;
            view.topMachineId = record.topMachineId;
            view.itemKey = record.itemKey;
            views.add(view);
        }
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
        if (!(tile instanceof IGregTechTileEntity) || tile.isInvalid()) return;
        record.timing = XTProfileStats.summarize(((IGregTechTileEntity) tile).getTimeStatistics());
    }

    private static List<String> machineNames(Set<String> ids, Map<String, XTProfileData.MachineRecord> machines) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            XTProfileData.MachineRecord machine = machines.get(id);
            if (machine != null) names.add(machine.name);
        }
        Collections.sort(names);
        return names;
    }

    private static List<String> cpuNames(Set<Long> ids, Collection<XTProfileData.CpuRecord> cpus) {
        List<String> names = new ArrayList<>();
        for (Long id : ids) names.add(cpuName(id, cpus));
        Collections.sort(names);
        return names;
    }

    private static String cpuName(long id, Collection<XTProfileData.CpuRecord> cpus) {
        for (XTProfileData.CpuRecord cpu : cpus) {
            if (cpu.id == id) return cpu.output + " · " + cpu.name;
        }
        return "CPU #" + id;
    }

    private static Set<Long> cpuIds(Collection<XTProfileData.CpuRecord> cpus) {
        Set<Long> ids = new HashSet<>();
        for (XTProfileData.CpuRecord cpu : cpus) ids.add(cpu.id);
        return ids;
    }

    private static String largestKey(Map<String, Long> primary, Map<String, Long> fallback) {
        String key = largestKey(primary);
        return key == null ? largestKey(fallback) : key;
    }

    private static String largestKey(Map<String, Long> values) {
        String result = null;
        long best = Long.MIN_VALUE;
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            long value = entry.getValue() == null ? 0 : entry.getValue();
            if (result == null || value > best) {
                result = entry.getKey();
                best = value;
            }
        }
        return result;
    }

    private static List<String> sortedStrings(Set<String> values) {
        List<String> result = new ArrayList<>(values);
        Collections.sort(result);
        return result;
    }

    private static List<Long> sortedLongs(Set<Long> values) {
        List<Long> result = new ArrayList<>(values);
        Collections.sort(result);
        return result;
    }

    private static long value(Map<Long, Long> values, long key) {
        Long value = values.get(key);
        return value == null ? 0 : value;
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
