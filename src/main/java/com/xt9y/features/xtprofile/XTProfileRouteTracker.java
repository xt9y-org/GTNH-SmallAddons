package com.xt9y.features.xtprofile;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class XTProfileRouteTracker {

    public static final XTProfileRouteTracker INSTANCE = new XTProfileRouteTracker();

    private static final int PANEL_REFRESH_TICKS = 10;
    private static final int TIMING_REFRESH_TICKS = 20;

    private final WeakHashMap<CraftingCPUCluster, Long> cpuIds = new WeakHashMap<>();
    private final WeakHashMap<CraftingCPUCluster, String> cpuCraftIds = new WeakHashMap<>();
    private final Map<String, XTProfileData.MediumRecord> media = new LinkedHashMap<>();
    private final Map<String, TimingTarget> timingTargets = new LinkedHashMap<>();
    private final XTProfilePendingOperations pendingOperations = new XTProfilePendingOperations();
    private final XTProfileIntervalUnion completedIntervals = new XTProfileIntervalUnion();
    private final Map<Long, XTProfileIntervalUnion> completedIntervalsByCpu = new LinkedHashMap<>();

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
        recordDispatch(cpu, medium, pattern, null);
    }

    public void recordDispatch(CraftingCPUCluster cpu, ICraftingMedium medium, ICraftingPatternDetails pattern,
        TileEntity exactTarget) {
        if (cpu == null || medium == null) return;

        synchronized (this) {
            if (!active) return;

            long now = System.nanoTime();
            long cpuId = cpuId(cpu, now);
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
            bindTimingTarget(record, medium, exactTarget, cpuId, now);

            if (pattern != null && !pattern.isCraftable()) {
                Map<String, Long> expected = new LinkedHashMap<>();
                IAEStack<?>[] outputs = pattern.getCondensedAEOutputs();
                if (outputs != null) {
                    for (IAEStack<?> expectedOutput : outputs) {
                        if (expectedOutput == null) continue;
                        addExpectedOutput(
                            expected,
                            XTProfileLabels.stackKey(expectedOutput),
                            expectedOutput.getStackSize());
                    }
                }
                if (!expected.isEmpty()) pendingOperations.add(cpuId, mediumId, now, expected);
            }
        }
    }

    public void recordReturnedOutput(CraftingCPUCluster cpu, IAEStack<?> returnedStack) {
        if (cpu == null || returnedStack == null || returnedStack.getStackSize() <= 0) return;

        synchronized (this) {
            if (!active) return;

            long now = System.nanoTime();
            long cpuId = cpuId(cpu, now);
            String outputKey = XTProfileLabels.stackKey(returnedStack);
            if (outputKey == null || outputKey.isEmpty()) return;

            XTProfileIntervalUnion cpuIntervals = completedIntervalsByCpu.get(cpuId);
            if (cpuIntervals == null) {
                cpuIntervals = new XTProfileIntervalUnion();
                completedIntervalsByCpu.put(cpuId, cpuIntervals);
            }

            for (XTProfilePendingOperations.Completion completion : pendingOperations
                .accept(cpuId, outputKey, returnedStack.getStackSize(), now)) {
                XTProfileData.MediumRecord medium = media.get(completion.mediumId);
                if (medium != null) {
                    accountCompletedInterval(
                        medium,
                        cpuId,
                        completion.mediumId,
                        now - completion.elapsedNs,
                        now,
                        completedIntervals,
                        cpuIntervals);
                }
            }
        }
    }

    synchronized XTProfilePanelMessage panelMessage(CraftingCPUCluster cpu) {
        long now = System.nanoTime();
        long cpuId = cpu == null ? 0 : cpuId(cpu, now);
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

    private void bindTimingTarget(XTProfileData.MediumRecord record, ICraftingMedium medium, TileEntity exactTarget,
        long cpuId, long now) {
        TileEntity machine = resolveMachine(exactTarget);
        if (!(machine instanceof IGregTechTileEntity) || machine.isInvalid()) machine = findMachine(medium);
        if (!(machine instanceof IGregTechTileEntity) || machine.isInvalid()) return;

        String mediumId = record.id;
        TimingTarget target = timingTargets.get(mediumId);
        TileEntity previous = target == null || target.tile == null ? null : target.tile.get();
        long previousCpuId = target == null ? 0 : target.cpuId;
        if (target == null) {
            target = new TimingTarget();
            timingTargets.put(mediumId, target);
        }

        IGregTechTileEntity gregTech = (IGregTechTileEntity) machine;
        boolean machineActive = machineRunning(gregTech);
        boolean sameMachine = previous == machine;
        boolean resetBoundary = shouldResetTimingBoundary(sameMachine, previousCpuId, cpuId, machineActive);
        if (resetBoundary) {
            target.lastSampleNs = now;
            target.lastRecipesDone = recipesDone(gregTech);
        }

        target.tile = new WeakReference<>(machine);
        target.cpuId = cpuId;
        record.machine = XTProfileLabels.machineName(machine);

        // GT can disable its own timing collection while an idle machine is sleeping. Re-enable it on every
        // crafting dispatch so the next machine tick is always measurable, even when this is the same controller.
        gregTech.startTimeStatistics();
        if (!sameMachine || resetBoundary) target.timing = XTProfileStats.summarize(gregTech.getTimeStatistics());
    }

    static boolean shouldResetTimingBoundary(boolean sameMachine, long previousCpuId, long cpuId,
        boolean machineActive) {
        return !sameMachine || previousCpuId != cpuId || !machineActive;
    }

    private void sampleTimingTargets(long now, boolean refreshTiming) {
        for (Map.Entry<String, TimingTarget> entry : timingTargets.entrySet()) {
            XTProfileData.MediumRecord medium = media.get(entry.getKey());
            TimingTarget target = entry.getValue();
            TileEntity tile = target.tile == null ? null : target.tile.get();
            if (medium == null || !(tile instanceof IGregTechTileEntity) || tile.isInvalid()) continue;

            IGregTechTileEntity machine = (IGregTechTileEntity) tile;
            long currentRecipesDone = recipesDone(machine);
            boolean completedRecipe = completedRecipe(target.lastRecipesDone, currentRecipesDone);
            if (currentRecipesDone != Long.MIN_VALUE) target.lastRecipesDone = currentRecipesDone;

            if (refreshTiming || completedRecipe) target.timing = XTProfileStats.summarize(machine.getTimeStatistics());

            boolean machineActive = machineRunning(machine);
            if (machineActive || completedRecipe) {
                target.lastSampleNs = accountRunningTick(
                    medium,
                    target.cpuId,
                    target.lastSampleNs,
                    now,
                    Math.max(0, target.timing.averageNs));
            } else {
                target.lastSampleNs = now;
            }
        }
    }

    static boolean machineRunning(boolean active, int maxProgressTime) {
        return active || maxProgressTime > 0;
    }

    private static boolean machineRunning(IGregTechTileEntity machine) {
        IMetaTileEntity metaTile = machine.getMetaTileEntity();
        int maxProgressTime = metaTile instanceof MTEMultiBlockBase
            ? ((MTEMultiBlockBase) metaTile).getMaxProgresstime()
            : 0;
        return machineRunning(machine.isActive(), maxProgressTime);
    }

    static boolean completedRecipe(long previousRecipesDone, long currentRecipesDone) {
        return previousRecipesDone != Long.MIN_VALUE && currentRecipesDone != Long.MIN_VALUE
            && currentRecipesDone > previousRecipesDone;
    }

    static long accountRunningTick(XTProfileData.MediumRecord medium, long cpuId, long previousNs, long nowNs,
        long tickCostNs) {
        long safeTickCostNs = Math.max(0, tickCostNs);
        medium.tickCostNs += safeTickCostNs;
        medium.activeTicks++;
        if (cpuId != 0) {
            add(medium.tickCostNsByCpu, cpuId, safeTickCostNs);
            add(medium.activeTicksByCpu, cpuId, 1);
        }
        return nowNs;
    }

    static void accountCompletedLatency(XTProfileData.MediumRecord medium, long cpuId, long elapsedNs) {
        long safeElapsedNs = Math.max(0, elapsedNs);
        medium.busyNs += safeElapsedNs;
        if (cpuId != 0) add(medium.busyNsByCpu, cpuId, safeElapsedNs);
    }

    static void accountCompletedInterval(XTProfileData.MediumRecord medium, long cpuId, String mediumId,
        long startedNs, long returnedNs, XTProfileIntervalUnion allIntervals, XTProfileIntervalUnion cpuIntervals) {
        medium.busyNs += allIntervals.add(mediumId, startedNs, returnedNs);
        if (cpuId != 0 && cpuIntervals != null) {
            add(medium.busyNsByCpu, cpuId, cpuIntervals.add(mediumId, startedNs, returnedNs));
        }
    }

    static void addExpectedOutput(Map<String, Long> expected, String key, long amount) {
        if (expected == null || key == null || key.isEmpty() || amount <= 0) return;
        Long previous = expected.get(key);
        expected.put(key, (previous == null ? 0L : previous) + amount);
    }

    private static long recipesDone(IGregTechTileEntity machine) {
        IMetaTileEntity metaTile = machine.getMetaTileEntity();
        if (metaTile instanceof MTEMultiBlockBase) return ((MTEMultiBlockBase) metaTile).recipesDone;
        return Long.MIN_VALUE;
    }

    private static TileEntity resolveMachine(TileEntity target) {
        if (target == null || target.isInvalid()) return null;
        TileEntity resolved = XTProfileMachineResolver.resolve(target);
        return resolved instanceof IGregTechTileEntity ? resolved : null;
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

    private long cpuId(CraftingCPUCluster cpu, long now) {
        Long existing = cpuIds.get(cpu);
        String currentCraftId = craftId(cpu);
        if (existing == null) {
            long created = nextCpuId++;
            cpuIds.put(cpu, created);
            cpuCraftIds.put(cpu, currentCraftId);
            return created;
        }

        String previousCraftId = cpuCraftIds.get(cpu);
        if (isNewCraft(previousCraftId, currentCraftId, cpu.isBusy())) {
            pendingOperations.clearCpu(existing);
            completedIntervalsByCpu.remove(existing);
            resetCpuMetrics(media.values(), existing);
            resetTimingBoundary(existing, now);
        }
        cpuCraftIds.put(cpu, currentCraftId);
        return existing;
    }

    private static String craftId(CraftingCPUCluster cpu) {
        ICraftingLink link = cpu.getLastCraftingLink();
        return link == null ? null : link.getCraftingID();
    }

    static boolean isNewCraft(String previousCraftId, String currentCraftId, boolean busy) {
        if (!busy || currentCraftId == null) return false;
        if (previousCraftId == null) return true;
        return !previousCraftId.equals(currentCraftId);
    }

    static void resetCpuMetrics(Iterable<XTProfileData.MediumRecord> media, long cpuId) {
        for (XTProfileData.MediumRecord medium : media) {
            medium.dispatchesByCpu.remove(cpuId);
            medium.busyNsByCpu.remove(cpuId);
            medium.tickCostNsByCpu.remove(cpuId);
            medium.activeTicksByCpu.remove(cpuId);
        }
    }

    private void resetTimingBoundary(long cpuId, long now) {
        for (TimingTarget target : timingTargets.values()) {
            if (target.cpuId == cpuId) target.lastSampleNs = now;
        }
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
        cpuCraftIds.clear();
        media.clear();
        timingTargets.clear();
        pendingOperations.clear();
        completedIntervals.clear();
        completedIntervalsByCpu.clear();
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
        long lastRecipesDone = Long.MIN_VALUE;
        XTProfileStats.Timing timing = XTProfileStats.summarize(null);
    }
}
