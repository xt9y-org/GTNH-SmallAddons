package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class XTProfilePanelMessageTest {

    @Test
    void packetRoundTripKeepsCpuSessionAndHighlightData() {
        XTProfilePanelData.View cpu = new XTProfilePanelData.View();
        cpu.cpuId = 42;
        cpu.totalDispatches = 17;
        cpu.entries.add(entry("crib", "PCB CRIB South", 12, 70.588, true));

        XTProfilePanelData.View session = new XTProfilePanelData.View();
        session.totalDispatches = 99;
        session.entries.add(entry("interface", "Assembly Hub 02", 55, 55.555, false));

        XTProfilePanelMessage written = new XTProfilePanelMessage("Quantum CPU", cpu, session);
        ByteBuf buffer = Unpooled.buffer();
        written.toBytes(buffer);

        XTProfilePanelMessage read = new XTProfilePanelMessage();
        read.fromBytes(buffer);

        assertEquals("Quantum CPU", read.cpuName);
        assertEquals(42, read.cpu.cpuId);
        assertEquals(17, read.cpu.totalDispatches);
        assertEquals(99, read.session.totalDispatches);
        assertEquals("PCB CRIB South", read.cpu.entries.get(0).name);
        assertEquals(12, read.cpu.entries.get(0).dispatches);
        assertEquals(70.588, read.cpu.entries.get(0).sharePercent, 0.0001);
        assertTrue(read.cpu.entries.get(0).hasLocation);
        assertEquals(-28, read.cpu.entries.get(0).dimension);
        assertEquals(120, read.cpu.entries.get(0).x);
        assertEquals(74, read.cpu.entries.get(0).y);
        assertEquals(-44, read.cpu.entries.get(0).z);
        assertEquals("Assembly Hub 02", read.session.entries.get(0).name);
    }

    private static XTProfilePanelData.Entry entry(String id, String name, long dispatches, double share,
        boolean location) {
        XTProfilePanelData.Entry entry = new XTProfilePanelData.Entry();
        entry.id = id;
        entry.name = name;
        entry.type = "medium";
        entry.machine = "PCB Factory #2";
        entry.item = "Elite Circuit Board";
        entry.dispatches = dispatches;
        entry.sharePercent = share;
        entry.hasLocation = location;
        entry.dimension = -28;
        entry.x = 120;
        entry.y = 74;
        entry.z = -44;
        return entry;
    }
}
