package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class XTProfilePendingOperationsTest {

    @Test
    void oneOutputCompletesAtReturnTime() {
        XTProfilePendingOperations pending = new XTProfilePendingOperations();
        pending.add(7L, "medium:a", 1_000L, outputs("item:x", 2L));

        assertTrue(
            pending.accept(7L, "item:x", 1L, 4_000L)
                .isEmpty());
        List<XTProfilePendingOperations.Completion> done = pending.accept(7L, "item:x", 1L, 6_000L);

        assertEquals(1, done.size());
        assertEquals("medium:a", done.get(0).mediumId);
        assertEquals(1_000L, done.get(0).startedNs);
        assertEquals(6_000L, done.get(0).completedNs);
        assertEquals(5_000L, done.get(0).elapsedNs);
    }

    @Test
    void multiOutputWaitsForEveryOutput() {
        XTProfilePendingOperations pending = new XTProfilePendingOperations();
        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("item:a", 1L);
        expected.put("item:b", 3L);
        pending.add(2L, "medium:assline", 10L, expected);

        assertTrue(
            pending.accept(2L, "item:a", 1L, 20L)
                .isEmpty());
        assertTrue(
            pending.accept(2L, "item:b", 2L, 30L)
                .isEmpty());
        List<XTProfilePendingOperations.Completion> done = pending.accept(2L, "item:b", 1L, 40L);

        assertEquals(1, done.size());
        assertEquals("medium:assline", done.get(0).mediumId);
        assertEquals(10L, done.get(0).startedNs);
        assertEquals(40L, done.get(0).completedNs);
        assertEquals(30L, done.get(0).elapsedNs);
    }

    @Test
    void identicalOperationsCompleteFifo() {
        XTProfilePendingOperations pending = new XTProfilePendingOperations();
        pending.add(3L, "medium:first", 100L, outputs("item:x", 1L));
        pending.add(3L, "medium:second", 200L, outputs("item:x", 1L));

        assertEquals(
            "medium:first",
            pending.accept(3L, "item:x", 1L, 500L)
                .get(0).mediumId);
        assertEquals(
            "medium:second",
            pending.accept(3L, "item:x", 1L, 700L)
                .get(0).mediumId);
    }

    @Test
    void aLargeReturnCanCompleteSeveralIdenticalOperationsInOrder() {
        XTProfilePendingOperations pending = new XTProfilePendingOperations();
        pending.add(3L, "medium:first", 100L, outputs("item:x", 2L));
        pending.add(3L, "medium:second", 200L, outputs("item:x", 2L));

        List<XTProfilePendingOperations.Completion> done = pending.accept(3L, "item:x", 4L, 700L);

        assertEquals(2, done.size());
        assertEquals("medium:first", done.get(0).mediumId);
        assertEquals("medium:second", done.get(1).mediumId);
    }

    @Test
    void clearCpuDropsOldCraftOperations() {
        XTProfilePendingOperations pending = new XTProfilePendingOperations();
        pending.add(4L, "medium:old", 100L, outputs("item:x", 1L));
        pending.clearCpu(4L);

        assertTrue(
            pending.accept(4L, "item:x", 1L, 200L)
                .isEmpty());
    }

    @Test
    void returnedOutputForAnotherCpuDoesNotConsumeOperation() {
        XTProfilePendingOperations pending = new XTProfilePendingOperations();
        pending.add(4L, "medium:a", 100L, outputs("item:x", 1L));

        assertTrue(
            pending.accept(5L, "item:x", 1L, 200L)
                .isEmpty());
        assertEquals(
            "medium:a",
            pending.accept(4L, "item:x", 1L, 300L)
                .get(0).mediumId);
    }

    @Test
    void completionCarriesMachineClockSnapshot() {
        XTProfilePendingOperations pending = new XTProfilePendingOperations();
        pending.add(9L, "medium:a", "machine:a", 100L, 12L, 345L, outputs("item:x", 1L));

        XTProfilePendingOperations.Completion done = pending.accept(9L, "item:x", 1L, 200L)
            .get(0);

        assertEquals("machine:a", done.machineId);
        assertEquals(12L, done.startedActiveTicks);
        assertEquals(345L, done.startedTickCostNs);
        assertEquals(100L, done.startedNs);
        assertEquals(200L, done.completedNs);
    }

    private static Map<String, Long> outputs(String key, long amount) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put(key, amount);
        return result;
    }
}
