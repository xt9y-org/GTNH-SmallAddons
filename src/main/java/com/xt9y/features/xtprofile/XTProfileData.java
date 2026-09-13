package com.xt9y.features.xtprofile;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;

final class XTProfileData {

    static final class CpuRecord {

        long id;
        String name;
        String output;
        long firstSeenNs;
        long dispatches;
        long usedBytes;
        int coProcessors;
        final Set<String> machineIds = new HashSet<>();
    }

    static final class MachineRecord {

        String id;
        String name;
        String type;
        String location;
        WeakReference<TileEntity> tile;
        long dispatches;
        long busyNs;
        long lastSampleNs;
        boolean active;
        boolean timingStartedByProfile;
        XTProfileStats.Timing timing = XTProfileStats.summarize(null);
        final Set<Long> activeCpuIds = new HashSet<>();
    }

    static final class RouteRecord {

        long cpuId;
        String cpu;
        String machineId;
        String machine;
        String pattern;
        long dispatches;
        long lastDispatchNs;
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
        List<CpuView> cpus;
        List<MachineView> machines;
        List<RouteView> routes;
        List<HistoryView> history;
    }

    static final class CpuView {

        long id;
        String name;
        String output;
        double observedMillis;
        long dispatches;
        int machineCount;
        List<String> machines;
        long usedBytes;
        int coProcessors;
    }

    static final class MachineView {

        String id;
        String name;
        String type;
        String location;
        boolean active;
        long dispatches;
        double busyMillis;
        long averageTickNs;
        int worstTickNs;
        int timingSamples;
        double tickSharePercent;
        List<String> activeCrafts;
    }

    static final class RouteView {

        long cpuId;
        String cpu;
        String machineId;
        String machine;
        String pattern;
        long dispatches;
        boolean active;
        double lastDispatchAgoMillis;
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
