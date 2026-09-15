package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class XTProfilePanelAggregatorTest {

    @Test
    void cpuScopeUsesOnlyDispatchesFromTheSelectedCpuAndSortsDescending() {
        XTProfileData.MediumRecord first = medium("first", "Assembly Hub", 100, 1, 5, 2, 95);
        XTProfileData.MediumRecord second = medium("second", "PCB CRIB", 20, 1, 15, 2, 5);
        XTProfileData.MediumRecord third = medium("third", "Other CPU", 10, 2, 10);

        XTProfilePanelData.View view = XTProfilePanelAggregator.build(Arrays.asList(first, second, third), 1);

        assertEquals(20, view.totalDispatches);
        assertEquals(2, view.entries.size());
        assertEquals("PCB CRIB", view.entries.get(0).name);
        assertEquals(15, view.entries.get(0).dispatches);
        assertEquals(75.0, view.entries.get(0).sharePercent, 0.0001);
        assertEquals("Assembly Hub", view.entries.get(1).name);
        assertEquals(5, view.entries.get(1).dispatches);
        assertEquals(25.0, view.entries.get(1).sharePercent, 0.0001);
    }

    @Test
    void sessionScopeIncludesEveryRecordedCraft() {
        XTProfileData.MediumRecord first = medium("first", "Assembly Hub", 100, 1, 5, 2, 95);
        XTProfileData.MediumRecord second = medium("second", "PCB CRIB", 20, 1, 15, 2, 5);
        XTProfileData.MediumRecord third = medium("third", "Wafer Interface", 10, 2, 10);

        XTProfilePanelData.View view = XTProfilePanelAggregator.build(Arrays.asList(first, second, third), 0);

        assertEquals(130, view.totalDispatches);
        assertEquals(3, view.entries.size());
        assertEquals("Assembly Hub", view.entries.get(0).name);
        assertEquals(100, view.entries.get(0).dispatches);
        assertEquals("PCB CRIB", view.entries.get(1).name);
        assertEquals("Wafer Interface", view.entries.get(2).name);
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
}
