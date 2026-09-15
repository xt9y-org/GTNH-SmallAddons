package com.xt9y.features.xtprofile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class XTProfileRouteTracker {

    public static final XTProfileRouteTracker INSTANCE = new XTProfileRouteTracker();

    private static final int PANEL_REFRESH_TICKS = 10;

    private final WeakHashMap<CraftingCPUCluster, Long> cpuIds = new WeakHashMap<>();
    private final Map<String, XTProfileData.MediumRecord> media = new LinkedHashMap<>();

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
            record.lastDispatchNs = System.nanoTime();
            add(record.dispatchesByCpu, cpuId, 1);
            updateLocation(record, medium);
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
            sync = ticks % PANEL_REFRESH_TICKS == 0;
        }
        if (sync) XTProfilePanelSync.syncOpenCraftingCpuScreens(this);
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
        nextCpuId = 1;
        ticks = 0;
    }

    private static <K> void add(Map<K, Long> map, K key, long amount) {
        Long previous = map.get(key);
        map.put(key, (previous == null ? 0L : previous) + amount);
    }

    private XTProfileRouteTracker() {}
}
