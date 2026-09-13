package com.xt9y.features.xtprofile;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class XTProfileManager {

    public static final XTProfileManager INSTANCE = new XTProfileManager();

    private static final int MAX_HISTORY = 100;
    private static final int MAX_ROUTES = 2_000;

    private final IdentityHashMap<CraftingCPUCluster, XTProfileData.CpuRecord> activeCpus = new IdentityHashMap<>();
    private final LinkedHashMap<String, XTProfileData.MachineRecord> machines = new LinkedHashMap<>();
    private final LinkedHashMap<String, XTProfileData.RouteRecord> routes = new LinkedHashMap<>();
    private final Deque<XTProfileData.HistoryRecord> history = new ArrayDeque<>();

    private volatile boolean running;
    private long startedAtNs;
    private long startedAtMillis;
    private long nextCpuId = 1;
    private long totalDispatches;
    private XTProfileHttpServer httpServer;

    public boolean isRunning() {
        return running;
    }

    public synchronized String start() throws IOException {
        if (running && httpServer != null) return httpServer.getUrl();

        releaseMachineTiming();
        clearState();
        XTProfileMachineResolver.clear();
        startedAtNs = System.nanoTime();
        startedAtMillis = System.currentTimeMillis();

        XTProfileHttpServer server = new XTProfileHttpServer(this);
        server.start();
        httpServer = server;
        running = true;
        return server.getUrl();
    }

    public synchronized void stop() {
        running = false;
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        releaseMachineTiming();
        activeCpus.clear();
        XTProfileMachineResolver.clear();
    }

    public synchronized void reset() {
        releaseMachineTiming();
        clearState();
        startedAtNs = System.nanoTime();
        startedAtMillis = System.currentTimeMillis();
        XTProfileMachineResolver.clear();
    }

    public synchronized String getUrl() {
        return httpServer == null ? null : httpServer.getUrl();
    }

    public void observeCpu(CraftingCPUCluster cpu) {
        if (!running || cpu == null || !cpu.isBusy()) return;
        synchronized (this) {
            if (running) ensureCpu(cpu, System.nanoTime());
        }
    }

    public void recordTarget(CraftingCPUCluster cpu, TileEntity rawTarget, ICraftingPatternDetails pattern) {
        if (!running || cpu == null || rawTarget == null) return;

        TileEntity target = XTProfileMachineResolver.resolve(rawTarget);
        long now = System.nanoTime();
        synchronized (this) {
            if (!running) return;
            recordDispatch(ensureCpu(cpu, now), ensureMachine(target, now), XTProfileLabels.pattern(pattern), now);
        }
    }

    public void recordUnresolvedMedium(CraftingCPUCluster cpu, ICraftingMedium medium,
        ICraftingPatternDetails pattern) {
        if (!running || cpu == null || medium == null) return;

        long now = System.nanoTime();
        synchronized (this) {
            if (!running) return;
            recordDispatch(ensureCpu(cpu, now), ensureMedium(medium, now), XTProfileLabels.pattern(pattern), now);
        }
    }

    public void finishCpu(CraftingCPUCluster cpu, String status) {
        if (cpu == null) return;
        synchronized (this) {
            XTProfileData.CpuRecord record = activeCpus.remove(cpu);
            if (record == null) return;

            long now = System.nanoTime();
            for (String machineId : record.machineIds) {
                XTProfileData.MachineRecord machine = machines.get(machineId);
                if (machine != null) machine.activeCpuIds.remove(record.id);
            }

            history.addFirst(
                new XTProfileData.HistoryRecord(
                    record.id,
                    record.name,
                    record.output,
                    status,
                    Math.max(0, now - record.firstSeenNs),
                    record.dispatches,
                    record.machineIds.size()));
            while (history.size() > MAX_HISTORY) history.removeLast();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !running) return;

        long now = System.nanoTime();
        synchronized (this) {
            if (!running) return;
            for (XTProfileData.MachineRecord machine : machines.values()) {
                TileEntity tile = machine.tile == null ? null : machine.tile.get();
                boolean active = false;
                if (tile instanceof IGregTechTileEntity && !machine.activeCpuIds.isEmpty() && !tile.isInvalid()) {
                    active = ((IGregTechTileEntity) tile).isActive();
                }
                machine.busyNs += XTProfileStats.busyDelta(machine.lastSampleNs, now, active);
                machine.lastSampleNs = now;
                machine.active = active;
            }
        }
    }

    synchronized XTProfileData.Snapshot snapshot() {
        return XTProfileSnapshotBuilder.build(
            running,
            startedAtNs,
            startedAtMillis,
            totalDispatches,
            activeCpus.values(),
            machines,
            routes.values(),
            history);
    }

    private XTProfileData.CpuRecord ensureCpu(CraftingCPUCluster cpu, long now) {
        XTProfileData.CpuRecord record = activeCpus.get(cpu);
        if (record == null) {
            record = new XTProfileData.CpuRecord();
            record.id = nextCpuId++;
            record.firstSeenNs = now;
            activeCpus.put(cpu, record);
        }

        String cpuName = cpu.getName();
        record.name = cpuName == null || cpuName.trim()
            .isEmpty() ? "CPU #" + record.id : cpuName;
        record.output = XTProfileLabels.stack(cpu.getFinalMultiOutput());
        record.usedBytes = cpu.getUsedStorage();
        record.coProcessors = cpu.getCoProcessors();
        return record;
    }

    private XTProfileData.MachineRecord ensureMachine(TileEntity tile, long now) {
        String id = XTProfileLabels.tileId(tile);
        XTProfileData.MachineRecord record = machines.get(id);
        if (record != null) {
            record.tile = new WeakReference<>(tile);
            return record;
        }

        record = new XTProfileData.MachineRecord();
        record.id = id;
        record.name = XTProfileLabels.machineName(tile);
        record.type = XTProfileLabels.machineType(tile);
        record.location = XTProfileLabels.machineLocation(tile);
        record.tile = new WeakReference<>(tile);
        record.lastSampleNs = now;
        machines.put(id, record);
        startMachineTiming(record, tile);
        return record;
    }

    private static void startMachineTiming(XTProfileData.MachineRecord record, TileEntity tile) {
        if (tile instanceof XTProfileTimingControl) {
            XTProfileTimingControl control = (XTProfileTimingControl) tile;
            if (!control.xtprofile$isTimingEnabled()) {
                control.xtprofile$setTimingEnabled(true);
                record.timingStartedByProfile = true;
            }
        } else if (tile instanceof IGregTechTileEntity) {
            ((IGregTechTileEntity) tile).startTimeStatistics();
        }
    }

    private XTProfileData.MachineRecord ensureMedium(ICraftingMedium medium, long now) {
        String id = "medium:" + medium.getClass()
            .getName() + "@" + Integer.toHexString(System.identityHashCode(medium));
        XTProfileData.MachineRecord record = machines.get(id);
        if (record != null) return record;

        record = new XTProfileData.MachineRecord();
        record.id = id;
        record.name = medium.getClass()
            .getSimpleName();
        record.type = "AE crafting medium";
        record.location = "unresolved target";
        record.lastSampleNs = now;
        machines.put(id, record);
        return record;
    }

    private void recordDispatch(XTProfileData.CpuRecord craft, XTProfileData.MachineRecord machine, String pattern,
        long now) {
        craft.dispatches++;
        craft.machineIds.add(machine.id);
        machine.dispatches++;
        machine.activeCpuIds.add(craft.id);
        totalDispatches++;

        String routeId = craft.id + "\n" + machine.id + "\n" + pattern;
        XTProfileData.RouteRecord route = routes.get(routeId);
        if (route == null) {
            route = new XTProfileData.RouteRecord();
            route.cpuId = craft.id;
            route.cpu = craft.output + " · " + craft.name;
            route.machineId = machine.id;
            route.machine = machine.name;
            route.pattern = pattern;
            routes.put(routeId, route);
            trimRoutes();
        }
        route.dispatches++;
        route.lastDispatchNs = now;
    }

    private void trimRoutes() {
        while (routes.size() > MAX_ROUTES) {
            String first = routes.keySet()
                .iterator()
                .next();
            routes.remove(first);
        }
    }

    private void releaseMachineTiming() {
        for (XTProfileData.MachineRecord record : machines.values()) {
            if (!record.timingStartedByProfile || record.tile == null) continue;
            TileEntity tile = record.tile.get();
            if (tile instanceof XTProfileTimingControl && !tile.isInvalid()) {
                ((XTProfileTimingControl) tile).xtprofile$setTimingEnabled(false);
            }
        }
    }

    private void clearState() {
        activeCpus.clear();
        machines.clear();
        routes.clear();
        history.clear();
        nextCpuId = 1;
        totalDispatches = 0;
    }

    private XTProfileManager() {}
}
