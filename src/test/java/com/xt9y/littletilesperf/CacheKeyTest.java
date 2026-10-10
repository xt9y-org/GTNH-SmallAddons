package com.xt9y.littletilesperf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CacheKeyTest {

    @Test
    void unchangedTileEntityReusesSnapshot() {
        assertTrue(CacheKey.matches(100L, 4096, 2, 64, 4, 100L, 4096, 2, 64, 4));
    }

    @Test
    void anyEditInvalidatesSnapshotEvenWithSameTileCount() {
        assertFalse(CacheKey.matches(100L, 4096, 2, 64, 4, 101L, 4096, 2, 64, 4));
    }

    @Test
    void additionAndRemovalInvalidateSnapshot() {
        assertFalse(CacheKey.matches(100L, 4096, 2, 64, 4, 100L, 4095, 2, 64, 4));
    }

    @Test
    void movedTileEntityCannotReuseOldBounds() {
        assertFalse(CacheKey.matches(100L, 4096, 2, 64, 4, 100L, 4096, 2, 65, 4));
    }
}
