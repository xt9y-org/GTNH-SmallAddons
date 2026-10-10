package com.xt9y.littletilesperf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.xt9y.littletilesperf.DenseGridMesher.Box;

class DenseGridMesherTest {

    @Test
    void solidFourThousandVoxelBuildBecomesOneCuboid() {
        int[] cells = new int[DenseGridMesher.VOLUME];
        java.util.Arrays.fill(cells, 4);
        List<Box> result = DenseGridMesher.mesh(cells);
        assertEquals(1, result.size());
        Box b = result.get(0);
        assertEquals(4, b.material);
        assertEquals(0, b.x0);
        assertEquals(0, b.y0);
        assertEquals(0, b.z0);
        assertEquals(16, b.x1);
        assertEquals(16, b.y1);
        assertEquals(16, b.z1);
    }

    @Test
    void disconnectedAndDifferentMaterialsRemainIndependent() {
        int[] cells = new int[DenseGridMesher.VOLUME];
        cells[DenseGridMesher.index(0, 0, 0)] = 1;
        cells[DenseGridMesher.index(15, 15, 15)] = 1;
        cells[DenseGridMesher.index(1, 0, 0)] = 2;
        List<Box> boxes = DenseGridMesher.mesh(cells);
        assertEquals(3, boxes.size());
        assertReconstructs(cells, boxes);
    }

    @Test
    void randomizedDenseMixedGeometryRemainsExactlyEquivalent() {
        Random rng = new Random(0x5eedL);
        for (int iteration = 0; iteration < 200; iteration++) {
            int[] cells = new int[DenseGridMesher.VOLUME];
            int density = iteration % 5;
            for (int i = 0; i < cells.length; i++) {
                int v = rng.nextInt(10);
                cells[i] = v <= density ? 1 + rng.nextInt(6) : 0;
            }
            int[] original = cells.clone();
            List<Box> boxes = DenseGridMesher.mesh(cells);
            assertArrayEquals(original, cells, "mesh must not mutate inputs");
            assertReconstructs(original, boxes);
        }
    }

    @Test
    void farLodFillsOnlySingleVoxelInteriorCavities() {
        int[] cells = new int[DenseGridMesher.VOLUME];
        boolean[] reserved = new boolean[cells.length];
        // A complete 2x2x2 material cluster except for one missing interior cell.
        for (int y = 4; y < 6; y++)
            for (int z = 4; z < 6; z++)
                for (int x = 4; x < 6; x++)
                    cells[DenseGridMesher.index(x, y, z)] = 3;
        cells[DenseGridMesher.index(4, 4, 4)] = 0;
        int[] output = DenseGridMesher.simplifyDistantInterior(cells, reserved);
        assertEquals(3, output[DenseGridMesher.index(4, 4, 4)]);
        assertEquals(0, cells[DenseGridMesher.index(4, 4, 4)]);
    }

    @Test
    void farLodPreservesBoundariesMixedMaterialsAndCutouts() {
        int[] cells = new int[DenseGridMesher.VOLUME];
        boolean[] reserved = new boolean[cells.length];
        for (int y = 0; y < 2; y++)
            for (int z = 0; z < 2; z++)
                for (int x = 0; x < 2; x++)
                    cells[DenseGridMesher.index(x, y, z)] = 2;
        cells[DenseGridMesher.index(0, 0, 0)] = 0; // block boundary must stay exact

        for (int y = 6; y < 8; y++)
            for (int z = 6; z < 8; z++)
                for (int x = 6; x < 8; x++)
                    cells[DenseGridMesher.index(x, y, z)] = 8;
        cells[DenseGridMesher.index(7, 7, 7)] = 9; // mixed colors must stay exact
        cells[DenseGridMesher.index(6, 6, 6)] = 0;

        int[] before = cells.clone();
        assertArrayEquals(before, DenseGridMesher.simplifyDistantInterior(cells, reserved));

        cells[DenseGridMesher.index(7, 7, 7)] = 8;
        reserved[DenseGridMesher.index(7, 7, 7)] = true;
        assertArrayEquals(cells, DenseGridMesher.simplifyDistantInterior(cells, reserved));
    }

    @Test
    void rejectsInvalidGrid() {
        assertThrows(IllegalArgumentException.class, () -> DenseGridMesher.mesh(new int[5]));
    }

    private static void assertReconstructs(int[] original, List<Box> result) {
        int[] reconstructed = new int[DenseGridMesher.VOLUME];
        for (Box b : result) {
            assertTrue(b.material > 0);
            assertTrue(b.x0 < b.x1 && b.y0 < b.y1 && b.z0 < b.z1);
            for (int y = b.y0; y < b.y1; y++) {
                for (int z = b.z0; z < b.z1; z++) {
                    for (int x = b.x0; x < b.x1; x++) {
                        int index = DenseGridMesher.index(x, y, z);
                        assertEquals(0, reconstructed[index], "overlap at cell " + index);
                        reconstructed[index] = b.material;
                    }
                }
            }
        }
        assertArrayEquals(original, reconstructed, "no changed voxels or material colors");
    }
}
