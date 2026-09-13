package com.xt9y.features.xtprofile;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;

final class XTProfileData {

    static final class CpuRecord {

        long id;
        String name;
        String output;
        String outputKey;
        long firstSeenNs;
        long dispatches;
        long usedBytes;
        int coProcessors;
        long ae2TickNs;
        long ae2TickSamples;
        final Set<String> machineIds = new HashSet<>();
        final Set<String> itemKeys = new HashSet<>();
    }

    static final class MachineRecord {

        String id;
        String name;
        String type;
        String location;
        WeakReference<TileEntity> tile;
        long dispatches;
        long busyNs;
        long tickCostNs;
        long lastSampleNs;
        long currentCpuId;
        String currentItemKey;
        boolean active;
        boolean timingStartedByProfile;
        XTProfileStats.Timing timing = XTProfileStats.summarize(null);
        final Set<Long> activeCpuIds = new HashSet<>();
        final Map<Long, Long> dispatchesByCpu = new LinkedHashMap<>();
        final Map<Long, Long> busyNsByCpu = new LinkedHashMap<>();
        final Map<Long, Long> tickCostNsByCpu = new LinkedHashMap<>();
        final Map<String, Long> dispatchesByItem = new LinkedHashMap<>();
        final Map<String, Long> busyNsByItem = new LinkedHashMap<>();
        final Map<String, Long> tickCostNsByItem = new LinkedHashMap<>();
    }

    static final class ItemRecord {

        String key;
        String name;
        String itemId;
        String texture;
        int damage;
        long outputAmount;
        long dispatches;
        long processingNs;
        long tickCostNs;
        long firstSeenNs;
        long lastSeenNs;
        final Set<String> machineIds = new HashSet<>();
        final Set<Long> cpuIds = new HashSet<>();
        final Map<String, Long> inputs = new LinkedHashMap<>();
        final Map<Long, Long> firstSeenNsByCpu = new LinkedHashMap<>();
        final Map<Long, Long> lastSeenNsByCpu = new LinkedHashMap<>();
        final Map<Long, Long> dispatchesByCpu = new LinkedHashMap<>();
        final Map<Long, Long> processingNsByCpu = new LinkedHashMap<>();
        final Map<Long, Long> tickCostNsByCpu = new LinkedHashMap<>();
    }

    static final class RouteRecord {

        long cpuId;
        String cpu;
        String machineId;
        String machine;
        String mediumId;
        String medium;
        String mediumType;
        String mediumLocation;
        String itemKey;
        String pattern;
        long dispatches;
        long lastDispatchNs;
    }

    static final class TickSampleRecord {

        long atMillis;
        double mspt;
        double tps;
        double machineMspt;
        double ae2Mspt;
        double overheadMspt;
        String topMachineId;
        String itemKey;
    }

    static final class HistoryRecord {

        final long id;
        final String cpu;
        final String output;
        final String status;
        final long observedNs;
        final long dispatches;
        final int machineCount;

        HistoryRecord(long id, String cpu, String output, String status, long observedNs, long dispatches,
            int machineCount) {
            this.id = id;
            this.cpu = cpu;
            this.output = output;
            this.status = status;
            this.observedNs = observedNs;
            this.dispatches = dispatches;
            this.machineCount = machineCount;
        }
    }

    static final class Snapshot {

        boolean running;
        long startedAtMillis;
        double sessionMillis;
        int activeCrafts;
        int machineCount;
        long totalDispatches;
        double totalBusyMillis;
        double mspt;
        double tps;
        double machineMspt;
        double ae2Mspt;
        double overheadMspt;
        List<CpuView> cpus;
        List<MachineView> machines;
        List<ItemView> items;
        List<RouteView> routes;
        List<TickView> ticks;
        List<HistoryView> history;
    }

    static final class CpuView {

        long id;
        String name;
        String output;
        String outputKey;
        double observedMillis;
        long dispatches;
        int machineCount;
        List<String> machines;
        long usedBytes;
        int coProcessors;
        long averageAe2TickNs;
        double tickSharePercent;
    }

    static final class AttributionView {

        long cpuId;
        String cpu;
        long dispatches;
        double busyMillis;
        double tickCostMillis;
        double observedMillis;
    }

    static final class MachineView {

        String id;
        String name;
        String type;
        String location;
        boolean active;
        long dispatches;
        double busyMillis;
        double tickCostMillis;
        long averageTickNs;
        int worstTickNs;
        int timingSamples;
        double tickSharePercent;
        double mspt;
        String topItemKey;
        String topItem;
        List<String> activeCrafts;
        List<AttributionView> cpuUsage;
    }

    static final class ItemAmountView {

        String key;
        String name;
        long amount;
    }

    static final class ItemView {

        String key;
        String name;
        String itemId;
        String texture;
        int damage;
        long outputAmount;
        long dispatches;
        double observedMillis;
        double processingMillis;
        double waitingMillis;
        double tickCostMillis;
        List<String> machineIds;
        List<String> machines;
        List<Long> cpuIds;
        List<ItemAmountView> inputs;
        List<AttributionView> cpuUsage;
    }

    static final class RouteView {

        long cpuId;
        String cpu;
        String machineId;
        String machine;
        String mediumId;
        String medium;
        String mediumType;
        String mediumLocation;
        String itemKey;
        String pattern;
        long dispatches;
        boolean active;
        double lastDispatchAgoMillis;
    }

    static final class TickView {

        long atMillis;
        double mspt;
        double tps;
        double machineMspt;
        double ae2Mspt;
        double overheadMspt;
        String topMachineId;
        String itemKey;
    }

    static final class HistoryView {

        long id;
        String cpu;
        String output;
        String status;
        double observedMillis;
        long dispatches;
        int machineCount;
    }

    private XTProfileData() {}
}
