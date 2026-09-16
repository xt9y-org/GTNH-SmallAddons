package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class XTProfileCompletionQueueTest {

    @Test
    void returnedOutputWaitsUntilEndOfTickDrain() {
        XTProfileCompletionQueue queue = new XTProfileCompletionQueue();
        XTProfilePendingOperations.Completion completion =
            new XTProfilePendingOperations.Completion("medium:assline", "machine:assline", 4L, 100L, 1_000L);

        queue.defer(7L, Collections.singletonList(completion));

        List<XTProfileCompletionQueue.Entry> drained = queue.drain();
        assertEquals(1, drained.size());
        assertEquals(7L, drained.get(0).cpuId);
        assertEquals(completion, drained.get(0).completion);
        assertTrue(queue.drain().isEmpty());
    }

    @Test
    void clearingCpuAlsoDropsDeferredCompletions() {
        XTProfileCompletionQueue queue = new XTProfileCompletionQueue();
        queue.defer(
            3L,
            Collections.singletonList(
                new XTProfilePendingOperations.Completion("medium:old", "machine:assline", 1L, 0L, 1L)));

        queue.clearCpu(3L);

        assertTrue(queue.drain().isEmpty());
    }
}
