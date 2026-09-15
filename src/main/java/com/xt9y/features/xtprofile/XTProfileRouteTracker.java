package com.xt9y.features.xtprofile;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class XTProfileRouteTracker {

    public static final XTProfileRouteTracker INSTANCE = new XTProfileRouteTracker();

    private static final int PANEL_REFRESH_TICKS = 10;
    private static final int TIMING_REFRESH_TICKS = 20;

    private final WeakHashMap<CraftingCPUCluster, Long> cpuIds = new WeakHashMap<>();
    private final Map<String, XTProfileData.MediumRecord> media = new LinkedHashMap<>();
    private final Map<String, TimingTarget> timingTargets = new LinkedHashMap<>();

    private boolean active;
    private long nextCpuId = 1;
    private long ticks;

    public synchronized void startSession() {
        clear();
        active = true;
    }

    public synchronized void stopSession() {
        active = false;
        clear();
    }

    public synchronized void resetSession() {
        clear();
    }

    public synchronized boolean isActive() {
        return active;
    }

    public void recordDispatch(CraftingCPUCluster cpu, ICraftingMedium medium, ICraftingPatternDetails pattern) {
        if (cpu == null || medium == null) return;

        synchronized (this) {
            if (!active) return;

            long now = System.nanoTime();
            long cpuId = cpuId(cpu);
            String mediumId = XTProfileLabels.mediumId(medium);
            XTProfileData.MediumRecord record = media.get(mediumId);
            if (record == null) {
                record = new XTProfileData.MediumRecord();
                record.id = mediumId;
                media.put(mediumId, record);
            }

            record.name = XTProfileLabels.mediumName(medium);
            record.type = XTProfileLabels.mediumType(medium);
            XTProfileLabels.StackInfo output = XTProfileLabels.primaryOutput(pattern);
            record.item = output == null ? "" : output.name;
            record.dispatches++;
            record.lastDispatchNs = now;
            add(record.dispatchesByCpu, cpuId, 1);
            updateLocation(record, medium);
            bindTimingTarget(record, medium, cpuId, now);
        }
    }

    synchronized XTProfilePanelMessage panelMessage(CraftingCPUCluster cpu) {
        long cpuId = cpu == null ? 0 : cpuId(cpu);
        String cpuName = cpuName(cpu, cpuId);
        return new XTProfilePanelMessage(
            cpuName,
            XTProfilePanelAggregator.build(media.values(), cpuId),
            XTProfilePanelAggregator.build(media.values(), 0));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean sync;
        synchronized (this) {
            if (!active) return;
            ticks++;
            sampleTimingTargets(System.nanoTime(), ticks % TIMING_REFRESH_TICKS == 0);
            sync = ticks % PANEL_REFRESH_TICKS == 0;
        }
        if (sync) XTProfilePanelSync.syncOpenCraftingCpuScreens(this);
    }

    private void bindTimingTarget(XTProfileData.MediumRecord record, ICraftingMedium medium, long cpuId, long now) {
        TileEntity machine = findMachine(medium);
        if (!(machine instanceof IGregTechTileEntity) || machine.isInvalid()) return;

        String mediumId = record.id;
        TimingTarget target = timingTargets.get(mediumId);
        TileEntity previous = target == null || target.tile == null ? null : target.tile.get();
        if (target == null) {
            target = new TimingTarget();
            timingTargets.put(mediumId, target);
        }

        target.tile = new WeakReference<>(machine);
        target.cpuId = cpuId;
        target.lastSampleNs = now;
        record.machine = XTProfileLabels.machineName(machine);

        if (previous != machine) {
            ((IGregTechTileEntity) machine).startTimeStatistics();
            target.timing = XTProfileStats.summarize(((IGregTechTileEntity) machine).getTimeStatistics());
        }
    }

    private void sampleTimingTargets(long now, boolean refreshTiming) {
        for (Map.Entry<String, TimingTarget> entry : timingTargets.entrySet()) {
            XTProfileData.MediumRecord medium = media.get(entry.getKey());
            TimingTarget target = entry.getValue();
            TileEntity tile = target.tile == null ? null : target.tile.get();
            if (medium == null || !(tile instanceof IGregTechTileEntity) || tile.isInvalid()) continue;

            IGregTechTileEntity machine = (IGregTechTileEntity) tile;
            if (refreshTiming) target.timing = XTProfileStats.summarize(machine.getTimeStatistics());

            boolean machineActive = machine.isActive();
            long busyDelta = XTProfileStats.busyDelta(target.lastSampleNs, now, machineActive);
            target.lastSampleNs = now;
            if (!machineActive || busyDelta <= 0) continue;

            long tickCostNs = Math.max(0, target.timing.averageNs);
            medium.busyNs += busyDelta;
            medium.tickCostNs += tickCostNs;
            medium.activeTicks++;
            if (target.cpuId != 0) {
                add(medium.busyNsByCpu, target.cpuId, busyDelta);
                add(medium.tickCostNsByCpu, target.cpuId, tickCostNs);
                add(medium.activeTicksByCpu, target.cpuId, 1);
            }
        }
    }

    private static TileEntity findMachine(ICraftingMedium medium) {
        TileEntity mediumTile = XTProfileLabels.mediumTile(medium);
        if (mediumTile == null || mediumTile.getWorldObj() == null) return null;

        TileEntity direct = XTProfileMachineResolver.resolve(mediumTile);
        if (direct instanceof IGregTechTileEntity) return direct;

        World world = mediumTile.getWorldObj();
        TileEntity fallback = null;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity adjacent = world.getTileEntity(
                mediumTile.xCoord + direction.offsetX,
                mediumTile.yCoord + direction.offsetY,
                mediumTile.zCoord + direction.offsetZ);
            if (!(adjacent instanceof IGregTechTileEntity)) continue;

            TileEntity resolved = XTProfileMachineResolver.resolve(adjacent);
            if (!(resolved instanceof IGregTechTileEntity) || resolved.isInvalid()) continue;
            if (((IGregTechTileEntity) resolved).isActive()) return resolved;
            if (fallback == null) fallback = resolved;
        }
        return fallback;
    }

    private long cpuId(CraftingCPUCluster cpu) {
        Long existing = cpuIds.get(cpu);
        if (existing != null) return existing;
        long created = nextCpuId++;
        cpuIds.put(cpu, created);
        return created;
    }

    private static String cpuName(CraftingCPUCluster cpu, long id) {
        if (cpu == null) return "Crafting CPU";
        String name = cpu.getName();
        return name == null || name.trim()
            .isEmpty() ? "CPU #" + id : name;
    }

    private static void updateLocation(XTProfileData.MediumRecord record, ICraftingMedium medium) {
        TileEntity tile = XTProfileLabels.mediumTile(medium);
        if (tile == null || tile.getWorldObj() == null) return;
        record.hasLocation = true;
        record.dimension = tile.getWorldObj().provider.dimensionId;
        record.x = tile.xCoord;
        record.y = tile.yCoord;
        record.z = tile.zCoord;
    }

    private void clear() {
        cpuIds.clear();
        media.clear();
        timingTargets.clear();
        XTProfileMachineResolver.clear();
        nextCpuId = 1;
        ticks = 0;
    }

    private static <K> void add(Map<K, Long> map, K key, long amount) {
        Long previous = map.get(key);
        map.put(key, (previous == null ? 0L : previous) + amount);
    }

    private XTProfileRouteTracker() {}

    private static final class TimingTarget {

        WeakReference<TileEntity> tile;
        long cpuId;
        long lastSampleNs;
        XTProfileStats.Timing timing = XTProfileStats.summarize(null);
    }
}
