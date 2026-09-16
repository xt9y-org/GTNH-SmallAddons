package com.xt9y.features.xtprofile;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final long GT_TICK_NS = 50_000_000L;

    private final WeakHashMap<CraftingCPUCluster, Long> cpuIds = new WeakHashMap<>();
    private final WeakHashMap<CraftingCPUCluster, String> cpuCraftIds = new WeakHashMap<>();
    private final Map<String, XTProfileData.MediumRecord> media = new LinkedHashMap<>();
    private final Map<String, MachineClock> machineClocks = new LinkedHashMap<>();
    private final XTProfilePendingOperations pendingOperations = new XTProfilePendingOperations();
    private final XTProfileIntervalUnion sessionCoverage = new XTProfileIntervalUnion();
    private final XTProfileIntervalUnion cpuCoverage = new XTProfileIntervalUnion();

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

            MachineClock clock = bindMachine(record, medium, exactTarget);
            if (clock != null && pattern != null && !pattern.isCraftable()) {
                Map<String, Long> expected = expectedOutputs(pattern);
                if (!expected.isEmpty()) {
                    pendingOperations
                        .add(cpuId, mediumId, clock.id, now, clock.activeTicks, clock.tickCostNs, expected);
                }
            }
        }
    }

    public void recordReturnedOutput(CraftingCPUCluster cpu, IAEStack<?> returnedStack) {
        if (cpu == null || returnedStack == null || returnedStack.getStackSize() <= 0) return;

        synchronized (this) {
            if (!active) return;
            Long cpuId = cpuIds.get(cpu);
            if (cpuId == null) return;

            String outputKey = XTProfileLabels.stackKey(returnedStack);
            if (outputKey == null || outputKey.isEmpty()) return;

            long now = System.nanoTime();
            List<XTProfilePendingOperations.Completion> completed = pendingOperations
                .accept(cpuId, outputKey, returnedStack.getStackSize(), now);
            for (XTProfilePendingOperations.Completion completion : completed) {
                XTProfileData.MediumRecord medium = media.get(completion.mediumId);
                MachineClock clock = machineClocks.get(completion.machineId);
                if (medium == null || clock == null) continue;

                advanceMachineClock(clock, false);
                long endTicks = clock.activeTicks;
                long tickCostNs = Math.max(0, clock.tickCostNs - completion.startedTickCostNs);
                accountCompletedMachineTicks(
                    medium,
                    cpuId,
                    completion.mediumId,
                    completion.machineId,
                    completion.startedActiveTicks,
                    endTicks,
                    tickCostNs,
                    sessionCoverage,
                    cpuCoverage);
            }
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
        if (event.phase == TickEvent.Phase.START) {
            synchronized (this) {
                if (!active) return;
                ticks++;
                for (MachineClock clock : machineClocks.values()) beginMachineTick(clock);
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) return;

        boolean sync;
        synchronized (this) {
            if (!active) return;
            for (MachineClock clock : machineClocks.values()) advanceMachineClock(clock, true);
            sync = ticks % PANEL_REFRESH_TICKS == 0;
        }
        if (sync) XTProfilePanelSync.syncOpenCraftingCpuScreens(this);
    }

    private MachineClock bindMachine(XTProfileData.MediumRecord record, ICraftingMedium medium,
        TileEntity exactTarget) {
        TileEntity machine = resolveMachine(exactTarget);
        if (!(machine instanceof IGregTechTileEntity) || machine.isInvalid()) machine = findMachine(medium);
        if (!(machine instanceof IGregTechTileEntity) || machine.isInvalid()) return null;

        record.machine = XTProfileLabels.machineName(machine);
        IGregTechTileEntity gregTech = (IGregTechTileEntity) machine;
        gregTech.startTimeStatistics();

        String machineId = XTProfileLabels.tileId(machine);
        MachineClock clock = machineClocks.get(machineId);
        if (clock == null) {
            clock = new MachineClock(machineId);
            machineClocks.put(machineId, clock);
            clock.tile = new WeakReference<>(machine);
            beginMachineTick(clock);
        } else {
            clock.tile = new WeakReference<>(machine);
            if (clock.serverTick != ticks) beginMachineTick(clock);
            else advanceMachineClock(clock, false);
        }
        return clock;
    }

    private void beginMachineTick(MachineClock clock) {
        TileEntity tile = clock.tile == null ? null : clock.tile.get();
        clock.serverTick = ticks;
        clock.accountedThisTick = false;
        if (!(tile instanceof IGregTechTileEntity) || tile.isInvalid()) {
            clock.activeAtStart = false;
            clock.progressAtStart = Integer.MIN_VALUE;
            clock.recipesDoneAtStart = Long.MIN_VALUE;
            return;
        }

        IGregTechTileEntity machine = (IGregTechTileEntity) tile;
        clock.activeAtStart = machine.isActive();
        clock.progressAtStart = progress(machine);
        clock.recipesDoneAtStart = recipesDone(machine);
    }

    private void advanceMachineClock(MachineClock clock, boolean endOfServerTick) {
        if (clock == null || clock.accountedThisTick || clock.serverTick != ticks) return;
        TileEntity tile = clock.tile == null ? null : clock.tile.get();
        if (!(tile instanceof IGregTechTileEntity) || tile.isInvalid()) return;

        IGregTechTileEntity machine = (IGregTechTileEntity) tile;
        boolean currentActive = machine.isActive();
        int currentProgress = progress(machine);
        long currentRecipesDone = recipesDone(machine);

        boolean progressChanged = clock.progressAtStart != Integer.MIN_VALUE && currentProgress != Integer.MIN_VALUE
            && currentProgress != clock.progressAtStart;
        boolean recipeCompleted = completedRecipe(clock.recipesDoneAtStart, currentRecipesDone);
        boolean activeStarted = !clock.activeAtStart && currentActive;
        boolean ran = progressChanged || recipeCompleted || activeStarted;

        if (endOfServerTick && !ran) {
            if (currentActive) ran = true;
            else if (clock.progressAtStart == Integer.MIN_VALUE && clock.activeAtStart) ran = true;
        }
        if (!ran) return;

        XTProfileStats.Timing timing = XTProfileStats.summarize(machine.getTimeStatistics());
        clock.activeTicks++;
        clock.tickCostNs += Math.max(0, timing.averageNs);
        clock.accountedThisTick = true;
    }

    private static Map<String, Long> expectedOutputs(ICraftingPatternDetails pattern) {
        Map<String, Long> expected = new LinkedHashMap<>();
        IAEStack<?>[] outputs = pattern.getCondensedAEOutputs();
        if (outputs == null) return expected;
        for (IAEStack<?> output : outputs) {
            if (output == null) continue;
            addExpectedOutput(expected, XTProfileLabels.stackKey(output), output.getStackSize());
        }
        return expected;
    }

    static void addExpectedOutput(Map<String, Long> expected, String key, long amount) {
        if (expected == null || key == null || key.isEmpty() || amount <= 0) return;
        Long previous = expected.get(key);
        expected.put(key, (previous == null ? 0L : previous) + amount);
    }

    static void accountCompletedMachineTicks(XTProfileData.MediumRecord medium, long cpuId, String mediumId,
        String machineId, long startTick, long endTick, long operationTickCostNs, XTProfileIntervalUnion allCoverage,
        XTProfileIntervalUnion perCpuCoverage) {
        if (medium == null || mediumId == null || machineId == null || endTick <= startTick) return;

        long spanTicks = endTick - startTick;
        String routeKey = mediumId + "\n" + machineId;
        long addedTicks = allCoverage.add(routeKey, startTick, endTick);
        if (addedTicks > 0) {
            medium.busyNs += addedTicks * GT_TICK_NS;
            medium.activeTicks += addedTicks;
            medium.tickCostNs += proportionalCost(operationTickCostNs, addedTicks, spanTicks);
        }

        if (cpuId == 0) return;
        String cpuRouteKey = cpuId + "\n" + routeKey;
        long addedCpuTicks = perCpuCoverage.add(cpuRouteKey, startTick, endTick);
        if (addedCpuTicks > 0) {
            add(medium.busyNsByCpu, cpuId, addedCpuTicks * GT_TICK_NS);
            add(medium.activeTicksByCpu, cpuId, addedCpuTicks);
            add(medium.tickCostNsByCpu, cpuId, proportionalCost(operationTickCostNs, addedCpuTicks, spanTicks));
        }
    }

    private static long proportionalCost(long totalCostNs, long coveredTicks, long spanTicks) {
        if (totalCostNs <= 0 || coveredTicks <= 0 || spanTicks <= 0) return 0;
        if (coveredTicks >= spanTicks) return totalCostNs;
        return Math.max(0, Math.round(totalCostNs * (coveredTicks / (double) spanTicks)));
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

    private static int progress(IGregTechTileEntity machine) {
        IMetaTileEntity metaTile = machine.getMetaTileEntity();
        if (metaTile instanceof MTEMultiBlockBase) return ((MTEMultiBlockBase) metaTile).getProgresstime();
        return Integer.MIN_VALUE;
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
            if (machineRunning((IGregTechTileEntity) resolved)) return resolved;
            if (fallback == null) fallback = resolved;
        }
        return fallback;
    }

    private long cpuId(CraftingCPUCluster cpu) {
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
            resetCpuMetrics(media.values(), existing);
            pendingOperations.clearCpu(existing);
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
        machineClocks.clear();
        pendingOperations.clear();
        sessionCoverage.clear();
        cpuCoverage.clear();
        XTProfileMachineResolver.clear();
        nextCpuId = 1;
        ticks = 0;
    }

    private static <K> void add(Map<K, Long> map, K key, long amount) {
        Long previous = map.get(key);
        map.put(key, (previous == null ? 0L : previous) + amount);
    }

    private XTProfileRouteTracker() {}

    private static final class MachineClock {

        final String id;
        WeakReference<TileEntity> tile;
        long activeTicks;
        long tickCostNs;
        long serverTick = Long.MIN_VALUE;
        boolean accountedThisTick;
        boolean activeAtStart;
        int progressAtStart = Integer.MIN_VALUE;
        long recipesDoneAtStart = Long.MIN_VALUE;

        MachineClock(String id) {
            this.id = id;
        }
    }
}
