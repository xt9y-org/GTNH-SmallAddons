package com.xt9y.features.xtprofile;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class XTProfileManager {

    public static final XTProfileManager INSTANCE = new XTProfileManager();

    private static final int MAX_HISTORY = 100;
    private static final int MAX_ROUTES = 2_000;
    private static final int MAX_TICK_SAMPLES = 120;
    private static final int RECENT_TICKS = 100;
    private static final int TIMING_REFRESH_TICKS = 20;
    private static final int PANEL_REFRESH_TICKS = 10;

    private final IdentityHashMap<CraftingCPUCluster, XTProfileData.CpuRecord> activeCpus = new IdentityHashMap<>();
    private final LinkedHashMap<String, XTProfileData.MachineRecord> machines = new LinkedHashMap<>();
    private final LinkedHashMap<String, XTProfileData.MediumRecord> media = new LinkedHashMap<>();
    private final LinkedHashMap<String, XTProfileData.ItemRecord> items = new LinkedHashMap<>();
    private final LinkedHashMap<String, XTProfileData.RouteRecord> routes = new LinkedHashMap<>();
    private final Deque<XTProfileData.TickSampleRecord> tickSamples = new ArrayDeque<>();
    private final Deque<XTProfileData.HistoryRecord> history = new ArrayDeque<>();
    private final long[] recentTickNs = new long[RECENT_TICKS];

    private volatile boolean running;
    private long startedAtNs;
    private long startedAtMillis;
    private long nextCpuId = 1;
    private long totalDispatches;
    private long serverTickStartNs;
    private long ae2TickNsThisTick;
    private long profileWorkNsThisTick;
    private long tickSequence;
    private int recentTickCount;
    private int recentTickCursor;
    private double currentMspt;
    private double currentTps = 20.0;
    private double currentMachineMspt;
    private double currentAe2Mspt;
    private double currentOverheadMspt;
    private XTProfileHttpServer httpServer;

    public boolean isRunning() {
        return running;
    }

    public synchronized void startSession() {
        if (running) return;
        releaseMachineTiming();
        clearState();
        XTProfileMachineResolver.clear();
        startedAtNs = System.nanoTime();
        startedAtMillis = System.currentTimeMillis();
        running = true;
    }

    public synchronized String start() throws IOException {
        if (running && httpServer != null) return httpServer.getUrl();

        if (!running) startSession();
        if (httpServer != null) return httpServer.getUrl();

        XTProfileHttpServer server = new XTProfileHttpServer(this);
        server.start();
        httpServer = server;
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
        long workStart = System.nanoTime();
        synchronized (this) {
            if (running) ensureCpu(cpu, workStart);
            profileWorkNsThisTick += Math.max(0, System.nanoTime() - workStart);
        }
    }

    public void recordCpuTick(CraftingCPUCluster cpu, long elapsedNs) {
        if (!running || cpu == null || elapsedNs <= 0) return;
        long workStart = System.nanoTime();
        synchronized (this) {
            if (!running || !cpu.isBusy()) return;
            XTProfileData.CpuRecord record = ensureCpu(cpu, workStart);
            record.ae2TickNs += elapsedNs;
            record.ae2TickSamples++;
            ae2TickNsThisTick += elapsedNs;
            profileWorkNsThisTick += Math.max(0, System.nanoTime() - workStart);
        }
    }

    public void recordTarget(CraftingCPUCluster cpu, ICraftingMedium medium, TileEntity rawTarget,
        ICraftingPatternDetails pattern) {
        if (!running || cpu == null || rawTarget == null) return;

        long workStart = System.nanoTime();
        TileEntity target = XTProfileMachineResolver.resolve(rawTarget);
        synchronized (this) {
            if (!running) return;
            recordDispatch(ensureCpu(cpu, workStart), ensureMachine(target, workStart), medium, pattern, workStart);
            profileWorkNsThisTick += Math.max(0, System.nanoTime() - workStart);
        }
    }

    public void recordUnresolvedMedium(CraftingCPUCluster cpu, ICraftingMedium medium,
        ICraftingPatternDetails pattern) {
        if (!running || cpu == null || medium == null) return;

        long workStart = System.nanoTime();
        synchronized (this) {
            if (!running) return;
            recordDispatch(ensureCpu(cpu, workStart), ensureMedium(medium, workStart), medium, pattern, workStart);
            profileWorkNsThisTick += Math.max(0, System.nanoTime() - workStart);
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
                if (machine == null) continue;
                machine.activeCpuIds.remove(record.id);
                if (machine.currentCpuId == record.id) machine.currentCpuId = 0;
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
        if (!running) return;

        if (event.phase == TickEvent.Phase.START) {
            synchronized (this) {
                if (!running) return;
                serverTickStartNs = System.nanoTime();
                ae2TickNsThisTick = 0;
                profileWorkNsThisTick = 0;
                tickSequence++;
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) return;

        boolean updatePanel;
        long handlerStart = System.nanoTime();
        synchronized (this) {
            if (!running) return;
            long now = handlerStart;
            long machineTickNs = 0;
            long topMachineNs = 0;
            String topMachineId = null;
            String topItemKey = null;
            boolean refreshTiming = tickSequence % TIMING_REFRESH_TICKS == 0;

            for (XTProfileData.MachineRecord machine : machines.values()) {
                TileEntity tile = machine.tile == null ? null : machine.tile.get();
                if (refreshTiming) refreshTiming(machine, tile);

                boolean active = false;
                if (tile instanceof IGregTechTileEntity && machine.currentCpuId != 0 && !tile.isInvalid()) {
                    active = ((IGregTechTileEntity) tile).isActive();
                }

                long busyDelta = XTProfileStats.busyDelta(machine.lastSampleNs, now, active);
                machine.lastSampleNs = now;
                machine.active = active;
                if (!active || busyDelta <= 0) continue;

                long tickCost = Math.max(0, machine.timing.averageNs);
                machine.busyNs += busyDelta;
                machine.tickCostNs += tickCost;
                machineTickNs += tickCost;

                if (machine.currentCpuId != 0) {
                    add(machine.busyNsByCpu, machine.currentCpuId, busyDelta);
                    add(machine.tickCostNsByCpu, machine.currentCpuId, tickCost);
                }
                if (machine.currentItemKey != null) {
                    add(machine.busyNsByItem, machine.currentItemKey, busyDelta);
                    add(machine.tickCostNsByItem, machine.currentItemKey, tickCost);
                    XTProfileData.ItemRecord item = items.get(machine.currentItemKey);
                    if (item != null) {
                        item.processingNs += busyDelta;
                        item.tickCostNs += tickCost;
                        item.lastSeenNs = now;
                        if (machine.currentCpuId != 0) {
                            add(item.processingNsByCpu, machine.currentCpuId, busyDelta);
                            add(item.tickCostNsByCpu, machine.currentCpuId, tickCost);
                            item.lastSeenNsByCpu.put(machine.currentCpuId, now);
                        }
                    }
                }

                if (tickCost > topMachineNs) {
                    topMachineNs = tickCost;
                    topMachineId = machine.id;
                    topItemKey = machine.currentItemKey;
                }
            }

            long tickNs = serverTickStartNs <= 0 ? 0 : Math.max(0, now - serverTickStartNs);
            recentTickNs[recentTickCursor] = tickNs;
            recentTickCursor = (recentTickCursor + 1) % recentTickNs.length;
            if (recentTickCount < recentTickNs.length) recentTickCount++;

            long averageTickNs = XTProfileStats.averageNs(recentTickNs, recentTickCount);
            currentMspt = averageTickNs / 1_000_000.0;
            currentTps = XTProfileStats.tpsFromMspt(currentMspt);
            currentMachineMspt = machineTickNs / 1_000_000.0;
            currentAe2Mspt = ae2TickNsThisTick / 1_000_000.0;
            currentOverheadMspt = (profileWorkNsThisTick + Math.max(0, System.nanoTime() - handlerStart)) / 1_000_000.0;

            XTProfileData.TickSampleRecord sample = new XTProfileData.TickSampleRecord();
            sample.atMillis = System.currentTimeMillis();
            sample.mspt = currentMspt;
            sample.tps = currentTps;
            sample.machineMspt = currentMachineMspt;
            sample.ae2Mspt = currentAe2Mspt;
            sample.overheadMspt = currentOverheadMspt;
            sample.topMachineId = topMachineId;
            sample.itemKey = topItemKey;
            tickSamples.addLast(sample);
            while (tickSamples.size() > MAX_TICK_SAMPLES) tickSamples.removeFirst();
            updatePanel = tickSequence % PANEL_REFRESH_TICKS == 0;
        }

        if (updatePanel) XTProfilePanelSync.syncOpenCraftingCpuScreens(this);
    }

    synchronized XTProfileData.Snapshot snapshot() {
        return XTProfileSnapshotBuilder.build(
            running,
            startedAtNs,
            startedAtMillis,
            totalDispatches,
            currentMspt,
            currentTps,
            currentMachineMspt,
            currentAe2Mspt,
            currentOverheadMspt,
            activeCpus.values(),
            machines,
            items,
            routes.values(),
            tickSamples,
            history);
    }

    synchronized XTProfilePanelMessage panelMessage(CraftingCPUCluster cpu) {
        long now = System.nanoTime();
        XTProfileData.CpuRecord record = activeCpus.get(cpu);
        if (record == null && cpu != null && cpu.isBusy()) record = ensureCpu(cpu, now);

        long cpuId = record == null ? 0 : record.id;
        String cpuName = record == null ? safeCpuName(cpu) : record.name;
        return new XTProfilePanelMessage(
            cpuName,
            XTProfilePanelAggregator.build(media.values(), cpuId),
            XTProfilePanelAggregator.build(media.values(), 0));
    }

    private static String safeCpuName(CraftingCPUCluster cpu) {
        if (cpu == null) return "Crafting CPU";
        String name = cpu.getName();
        return name == null || name.trim()
            .isEmpty() ? "Crafting CPU" : name;
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
        IAEStack<?> output = cpu.getFinalMultiOutput();
        record.output = XTProfileLabels.stack(output);
        record.outputKey = XTProfileLabels.stackKey(output);
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
        String id = XTProfileLabels.mediumId(medium);
        XTProfileData.MachineRecord record = machines.get(id);
        if (record != null) return record;

        record = new XTProfileData.MachineRecord();
        record.id = id;
        record.name = XTProfileLabels.mediumName(medium);
        record.type = XTProfileLabels.mediumType(medium);
        record.location = XTProfileLabels.mediumLocation(medium);
        TileEntity tile = XTProfileLabels.mediumTile(medium);
        record.tile = tile == null ? null : new WeakReference<>(tile);
        record.lastSampleNs = now;
        machines.put(id, record);
        if (tile != null) startMachineTiming(record, tile);
        return record;
    }

    private XTProfileData.ItemRecord ensureItem(XTProfileLabels.StackInfo info, long now) {
        XTProfileData.ItemRecord record = items.get(info.key);
        if (record == null) {
            record = new XTProfileData.ItemRecord();
            record.key = info.key;
            record.firstSeenNs = now;
            items.put(info.key, record);
        }
        record.name = info.name;
        record.itemId = info.itemId;
        record.damage = info.damage;
        record.texture = info.texture;
        record.lastSeenNs = Math.max(record.lastSeenNs, now);
        return record;
    }

    private void recordDispatch(XTProfileData.CpuRecord craft, XTProfileData.MachineRecord machine,
        ICraftingMedium medium, ICraftingPatternDetails pattern, long now) {
        craft.dispatches++;
        craft.machineIds.add(machine.id);
        machine.dispatches++;
        machine.activeCpuIds.add(craft.id);
        machine.currentCpuId = craft.id;
        add(machine.dispatchesByCpu, craft.id, 1);
        totalDispatches++;

        XTProfileData.ItemRecord outputRecord = recordPattern(craft, machine, pattern, now);
        String itemKey = outputRecord == null ? null : outputRecord.key;
        machine.currentItemKey = itemKey;
        recordMedium(craft, machine, medium, outputRecord, now);

        String mediumId = medium == null ? "medium:unknown" : XTProfileLabels.mediumId(medium);
        String routeId = craft.id + "\n" + machine.id + "\n" + mediumId + "\n" + XTProfileLabels.pattern(pattern);
        XTProfileData.RouteRecord route = routes.get(routeId);
        if (route == null) {
            route = new XTProfileData.RouteRecord();
            route.cpuId = craft.id;
            route.cpu = craft.output + " · " + craft.name;
            route.machineId = machine.id;
            route.machine = machine.name;
            route.mediumId = mediumId;
            route.medium = medium == null ? "unknown crafting medium" : XTProfileLabels.mediumName(medium);
            route.mediumType = medium == null ? "unknown" : XTProfileLabels.mediumType(medium);
            route.mediumLocation = medium == null ? "unresolved" : XTProfileLabels.mediumLocation(medium);
            route.itemKey = itemKey;
            route.pattern = XTProfileLabels.pattern(pattern);
            routes.put(routeId, route);
            trimRoutes();
        }
        route.machine = machine.name;
        route.itemKey = itemKey;
        route.dispatches++;
        route.lastDispatchNs = now;
    }

    private void recordMedium(XTProfileData.CpuRecord craft, XTProfileData.MachineRecord machine,
        ICraftingMedium medium, XTProfileData.ItemRecord item, long now) {
        if (medium == null) return;

        String id = XTProfileLabels.mediumId(medium);
        XTProfileData.MediumRecord record = media.get(id);
        if (record == null) {
            record = new XTProfileData.MediumRecord();
            record.id = id;
            media.put(id, record);
        }

        record.name = XTProfileLabels.mediumName(medium);
        record.type = XTProfileLabels.mediumType(medium);
        record.machine = machine == null ? "" : machine.name;
        record.item = item == null ? "" : item.name;
        record.dispatches++;
        record.lastDispatchNs = now;
        add(record.dispatchesByCpu, craft.id, 1);

        TileEntity tile = XTProfileLabels.mediumTile(medium);
        if (tile != null && tile.getWorldObj() != null) {
            record.hasLocation = true;
            record.dimension = tile.getWorldObj().provider.dimensionId;
            record.x = tile.xCoord;
            record.y = tile.yCoord;
            record.z = tile.zCoord;
        }
    }

    private XTProfileData.ItemRecord recordPattern(XTProfileData.CpuRecord craft, XTProfileData.MachineRecord machine,
        ICraftingPatternDetails pattern, long now) {
        XTProfileLabels.StackInfo output = XTProfileLabels.primaryOutput(pattern);
        if (output == null) return null;

        XTProfileData.ItemRecord record = ensureItem(output, now);
        record.outputAmount = output.amount;
        record.dispatches++;
        record.machineIds.add(machine.id);
        record.cpuIds.add(craft.id);
        record.firstSeenNsByCpu.putIfAbsent(craft.id, now);
        record.lastSeenNsByCpu.put(craft.id, now);
        add(record.dispatchesByCpu, craft.id, 1);
        add(machine.dispatchesByItem, record.key, 1);
        craft.itemKeys.add(record.key);

        record.inputs.clear();
        if (pattern != null) {
            IAEStack<?>[] inputs = pattern.getCondensedAEInputs();
            if (inputs != null) {
                for (IAEStack<?> input : inputs) {
                    XTProfileLabels.StackInfo info = XTProfileLabels.describe(input);
                    if (info == null) continue;
                    ensureItem(info, now);
                    record.inputs.put(info.key, info.amount);
                }
            }
        }
        return record;
    }

    private void trimRoutes() {
        while (routes.size() > MAX_ROUTES) {
            String first = routes.keySet()
                .iterator()
                .next();
            routes.remove(first);
        }
    }

    private static void refreshTiming(XTProfileData.MachineRecord record, TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity) || tile.isInvalid()) {
            record.timing = XTProfileStats.summarize(null);
            return;
        }
        record.timing = XTProfileStats.summarize(((IGregTechTileEntity) tile).getTimeStatistics());
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
        media.clear();
        items.clear();
        routes.clear();
        tickSamples.clear();
        history.clear();
        nextCpuId = 1;
        totalDispatches = 0;
        serverTickStartNs = 0;
        ae2TickNsThisTick = 0;
        profileWorkNsThisTick = 0;
        tickSequence = 0;
        recentTickCount = 0;
        recentTickCursor = 0;
        for (int i = 0; i < recentTickNs.length; i++) recentTickNs[i] = 0;
        currentMspt = 0;
        currentTps = 20.0;
        currentMachineMspt = 0;
        currentAe2Mspt = 0;
        currentOverheadMspt = 0;
    }

    private static <K> void add(Map<K, Long> map, K key, long amount) {
        Long previous = map.get(key);
        map.put(key, (previous == null ? 0L : previous) + amount);
    }

    private XTProfileManager() {}
}
