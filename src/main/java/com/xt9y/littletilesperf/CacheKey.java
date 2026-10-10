package com.xt9y.littletilesperf;

/** Pure O(1) cache-validity check. No per-frame hashing or allocation. */
public final class CacheKey {

    private CacheKey() {}

    public static boolean matches(
        long snapshotGeneration, int snapshotSize, int snapshotX, int snapshotY, int snapshotZ,
        long currentGeneration, int currentSize, int currentX, int currentY, int currentZ) {
        return snapshotGeneration == currentGeneration
            && snapshotSize == currentSize
            && snapshotX == currentX
            && snapshotY == currentY
            && snapshotZ == currentZ;
    }
}
