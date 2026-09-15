package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

class XTProfilePanelAggregatorTest {

    @Test
    void panelEntriesExposeCraftTimeAndTpsUsage() {
        assertTrue(hasField(XTProfilePanelData.Entry.class, "craftTimeMillis"));
        assertTrue(hasField(XTProfilePanelData.Entry.class, "tpsUsagePercent"));
    }

    @Test
    void cpuScopeSortsByCraftTimeAndCalculatesTpsUsage() throws Exception {
        XTProfileData.MediumRecord first = medium("first", "Assembly Hub", 100, 1, 95);
        XTProfileData.MediumRecord second = medium("second", "PCB CRIB", 20, 1, 5);

        timing(first, 1, 2_000_000_000L, 40_000_000L, 40);
        timing(second, 1, 8_000_000_000L, 100_000_000L, 40);

        XTProfilePanelData.View view = XTProfilePanelAggregator.build(Arrays.asList(first, second), 1);

        assertEquals(2, view.entries.size());
        assertEquals("PCB CRIB", view.entries.get(0).name);
        assertEquals(8_000.0, doubleField(view.entries.get(0), "craftTimeMillis"), 0.0001);
        assertEquals(5.0, doubleField(view.entries.get(0), "tpsUsagePercent"), 0.0001);
        assertEquals("Assembly Hub", view.entries.get(1).name);
        assertEquals(2_000.0, doubleField(view.entries.get(1), "craftTimeMillis"), 0.0001);
        assertEquals(2.0, doubleField(view.entries.get(1), "tpsUsagePercent"), 0.0001);
    }

    @Test
    void sessionScopeAggregatesTimingAcrossEveryCraft() throws Exception {
        XTProfileData.MediumRecord first = medium("first", "Assembly Hub", 100, 1, 5, 2, 95);
        XTProfileData.MediumRecord second = medium("second", "PCB CRIB", 20, 1, 15, 2, 5);

        timing(first, 0, 12_000_000_000L, 300_000_000L, 100);
        timing(second, 0, 4_000_000_000L, 50_000_000L, 50);

        XTProfilePanelData.View view = XTProfilePanelAggregator.build(Arrays.asList(first, second), 0);

        assertEquals(2, view.entries.size());
        assertEquals("Assembly Hub", view.entries.get(0).name);
        assertEquals(12_000.0, doubleField(view.entries.get(0), "craftTimeMillis"), 0.0001);
        assertEquals(6.0, doubleField(view.entries.get(0), "tpsUsagePercent"), 0.0001);
        assertEquals("PCB CRIB", view.entries.get(1).name);
        assertEquals(4_000.0, doubleField(view.entries.get(1), "craftTimeMillis"), 0.0001);
        assertEquals(2.0, doubleField(view.entries.get(1), "tpsUsagePercent"), 0.0001);
    }

    @Test
    void entryPreservesHighlightLocationAndLatestRouteDetails() {
        XTProfileData.MediumRecord medium = medium("pcb", "PCB CRIB South", 4, 7, 4);
        medium.type = "MTEHatchCraftingInputME";
        medium.machine = "PCB Factory #2";
        medium.item = "Multifiberglass Elite Circuit Board";
        medium.hasLocation = true;
        medium.dimension = -28;
        medium.x = 120;
        medium.y = 74;
        medium.z = -44;

        XTProfilePanelData.Entry entry = XTProfilePanelAggregator.build(Arrays.asList(medium), 7).entries.get(0);

        assertEquals("pcb", entry.id);
        assertEquals("MTEHatchCraftingInputME", entry.type);
        assertEquals("PCB Factory #2", entry.machine);
        assertEquals("Multifiberglass Elite Circuit Board", entry.item);
        assertEquals(true, entry.hasLocation);
        assertEquals(-28, entry.dimension);
        assertEquals(120, entry.x);
        assertEquals(74, entry.y);
        assertEquals(-44, entry.z);
    }

    private static XTProfileData.MediumRecord medium(String id, String name, long total, long... cpuAndDispatches) {
        XTProfileData.MediumRecord record = new XTProfileData.MediumRecord();
        record.id = id;
        record.name = name;
        record.dispatches = total;
        for (int i = 0; i < cpuAndDispatches.length; i += 2) {
            record.dispatchesByCpu.put(cpuAndDispatches[i], cpuAndDispatches[i + 1]);
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    private static void timing(XTProfileData.MediumRecord record, long cpuId, long busyNs, long tickCostNs,
        long activeTicks) throws Exception {
        if (cpuId == 0) {
            setLong(record, "busyNs", busyNs);
            setLong(record, "tickCostNs", tickCostNs);
            setLong(record, "activeTicks", activeTicks);
            return;
        }
        ((Map<Long, Long>) field(record, "busyNsByCpu")).put(cpuId, busyNs);
        ((Map<Long, Long>) field(record, "tickCostNsByCpu")).put(cpuId, tickCostNs);
        ((Map<Long, Long>) field(record, "activeTicksByCpu")).put(cpuId, activeTicks);
    }

    private static boolean hasField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setLong(Object target, String name, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static double doubleField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }
}
