package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

class XTProfileRouteTimingAndSearchTest {

    @Test
    void dispatchContextKeepsExactInterfaceTargetUntilDispatchCompletes() throws Exception {
        TileEntity target = new TileEntity();
        XTProfileDispatchContext.begin(null, null);
        try {
            XTProfileDispatchContext.recordTarget(target);
            Method method = XTProfileDispatchContext.class.getDeclaredMethod("target");
            method.setAccessible(true);
            assertSame(target, method.invoke(null));
        } finally {
            XTProfileDispatchContext.end();
        }
    }

    @Test
    void multiblockProgressCountsAsRunningWhenControllerActiveFlagIsFalse() {
        assertTrue(XTProfileRouteTracker.machineRunning(false, 200));
        assertTrue(XTProfileRouteTracker.machineRunning(true, 0));
        assertFalse(XTProfileRouteTracker.machineRunning(false, 0));
    }

    @Test
    void completedRecipeDetectsMonotonicRecipeCounterAdvance() {
        assertTrue(XTProfileRouteTracker.completedRecipe(4L, 5L));
        assertFalse(XTProfileRouteTracker.completedRecipe(4L, 4L));
        assertFalse(XTProfileRouteTracker.completedRecipe(Long.MIN_VALUE, 5L));
    }

    @Test
    void expectedOutputsIgnoreInvalidEntriesAndSumDuplicates() {
        Map<String, Long> expected = new LinkedHashMap<>();

        XTProfileRouteTracker.addExpectedOutput(expected, "item:a", 2L);
        XTProfileRouteTracker.addExpectedOutput(expected, "item:a", 3L);
        XTProfileRouteTracker.addExpectedOutput(expected, "", 9L);
        XTProfileRouteTracker.addExpectedOutput(expected, null, 9L);
        XTProfileRouteTracker.addExpectedOutput(expected, "item:b", 0L);

        assertEquals(1, expected.size());
        assertEquals(5L, expected.get("item:a"));
    }

    @Test
    void resolverCanPickControllerFromDirectWatcherList() throws Exception {
        ControllerMarker expected = new ControllerMarkerImpl();
        Method method = XTProfileMachineResolver.class.getDeclaredMethod("firstInstance", Iterable.class, Class.class);
        method.setAccessible(true);

        Object resolved = method.invoke(null, Arrays.asList("not a controller", expected), ControllerMarker.class);
        assertSame(expected, resolved);
    }

    @Test
    void routeSearchMatchesInterfaceOrMachineName() throws Exception {
        Class<?> search = Class.forName("com.xt9y.features.xtprofile.XTProfileSearch");
        Method method = search.getDeclaredMethod("matches", String.class, String.class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null, "Assembly Line Interface", "Assembly Line", "interface"));
        assertTrue((Boolean) method.invoke(null, "CRIB #4", "PCB Factory", "pcb"));
        assertFalse((Boolean) method.invoke(null, "CRIB #4", "PCB Factory", "lathe"));
        assertTrue((Boolean) method.invoke(null, "CRIB #4", "PCB Factory", ""));
    }

    private interface ControllerMarker {
    }

    private static final class ControllerMarkerImpl implements ControllerMarker {
    }
}
