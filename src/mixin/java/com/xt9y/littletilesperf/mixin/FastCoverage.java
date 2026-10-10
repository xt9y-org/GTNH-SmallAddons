package com.xt9y.littletilesperf.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import com.creativemd.littletiles.client.render.FaceClipper;
import com.creativemd.littletiles.common.utils.LittleTilesCubeObject;

/**
 * Exact, grid-indexed implementation of LittleTiles' local face-covering pass.
 *
 * Original logic loops over every (target, occluder) pair: O(n^2).
 * Only an occluder flush to a target face can cover it. Six 17-plane
 * buckets preserve input ordering while reducing the candidate set.
 * Neighboring block coverage remains in upstream LittleTiles.
 */
final class FastCoverage {

    private static final int GRID = 16;
    private static final int SIDES = 6;
    private static final int MIN_CUBES = 64;

    private FastCoverage() {}

    static FaceClipper[] local(List<LittleTilesCubeObject> cubes) {
        if (cubes.size() < MIN_CUBES) return null;

        // Non-standard coordinates must use upstream's unrestricted integer math.
        for (LittleTilesCubeObject c : cubes) {
            if (!valid(c)) return null;
        }

        @SuppressWarnings("unchecked")
        List<LittleTilesCubeObject>[][] bySide = (List<LittleTilesCubeObject>[][]) new List[SIDES][GRID + 1];
        for (LittleTilesCubeObject c : cubes) {
            if (c.cutoutInfo != null || !eligible(c)) continue;
            add(bySide, ForgeDirection.DOWN.ordinal(), c.gridMaxY, c);
            add(bySide, ForgeDirection.UP.ordinal(), c.gridMinY, c);
            add(bySide, ForgeDirection.NORTH.ordinal(), c.gridMaxZ, c);
            add(bySide, ForgeDirection.SOUTH.ordinal(), c.gridMinZ, c);
            add(bySide, ForgeDirection.WEST.ordinal(), c.gridMaxX, c);
            add(bySide, ForgeDirection.EAST.ordinal(), c.gridMinX, c);
        }

        FaceClipper[] result = new FaceClipper[cubes.size()];
        for (int i = 0; i < cubes.size(); ++i) {
            LittleTilesCubeObject target = cubes.get(i);
            if (!eligible(target)) continue;
            FaceClipper clipper = new FaceClipper(target);
            result[i] = clipper;

            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                int plane = facingCoordinate(target, side);
                List<LittleTilesCubeObject> possible = bySide[side.ordinal()][plane];
                if (possible == null) continue;
                for (LittleTilesCubeObject occluder : possible) {
                    if (occluder == target
                        || occluder.block != target.block
                        || occluder.meta != target.meta
                        || occluder.color != target.color) continue;
                    cover(clipper, target, occluder, side);
                }
            }
        }
        return result;
    }

    private static boolean valid(LittleTilesCubeObject c) {
        return c.gridMinX >= 0 && c.gridMinY >= 0 && c.gridMinZ >= 0
            && c.gridMaxX <= GRID && c.gridMaxY <= GRID && c.gridMaxZ <= GRID
            && c.gridMinX <= c.gridMaxX && c.gridMinY <= c.gridMaxY
            && c.gridMinZ <= c.gridMaxZ;
    }

    private static boolean eligible(LittleTilesCubeObject c) {
        return c.block != null && c.meta != -1 && !c.block.isOpaqueCube();
    }

    private static void add(List<LittleTilesCubeObject>[][] index, int side, int plane,
                            LittleTilesCubeObject cube) {
        List<LittleTilesCubeObject> bucket = index[side][plane];
        if (bucket == null) {
            bucket = new ArrayList<LittleTilesCubeObject>();
            index[side][plane] = bucket;
        }
        bucket.add(cube);
    }

    private static int facingCoordinate(LittleTilesCubeObject c, ForgeDirection side) {
        switch (side) {
            case DOWN: return c.gridMinY;
            case UP: return c.gridMaxY;
            case NORTH: return c.gridMinZ;
            case SOUTH: return c.gridMaxZ;
            case WEST: return c.gridMinX;
            case EAST: return c.gridMaxX;
            default: throw new AssertionError(side);
        }
    }

    private static void cover(FaceClipper clipper, LittleTilesCubeObject a,
                              LittleTilesCubeObject b, ForgeDirection side) {
        int minA, maxA, minB, maxB, minC, maxC, minD, maxD;
        switch (side) {
            case DOWN:
            case UP:
                minA = a.gridMinX; maxA = a.gridMaxX;
                minB = b.gridMinX; maxB = b.gridMaxX;
                minC = a.gridMinZ; maxC = a.gridMaxZ;
                minD = b.gridMinZ; maxD = b.gridMaxZ;
                break;
            case NORTH:
            case SOUTH:
                minA = a.gridMinX; maxA = a.gridMaxX;
                minB = b.gridMinX; maxB = b.gridMaxX;
                minC = a.gridMinY; maxC = a.gridMaxY;
                minD = b.gridMinY; maxD = b.gridMaxY;
                break;
            case WEST:
            case EAST:
                minA = a.gridMinZ; maxA = a.gridMaxZ;
                minB = b.gridMinZ; maxB = b.gridMaxZ;
                minC = a.gridMinY; maxC = a.gridMaxY;
                minD = b.gridMinY; maxD = b.gridMaxY;
                break;
            default: throw new AssertionError(side);
        }
        int firstMin = Math.max(minA, minB);
        int firstMax = Math.min(maxA, maxB);
        int secondMin = Math.max(minC, minD);
        int secondMax = Math.min(maxC, maxD);
        if (firstMin < firstMax && secondMin < secondMax)
            clipper.cover(side, firstMin, firstMax, secondMin, secondMax);
    }
}
