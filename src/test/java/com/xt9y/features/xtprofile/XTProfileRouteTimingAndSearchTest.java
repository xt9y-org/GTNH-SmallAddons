package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.util.Arrays;

import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

class XTProfileRouteTimingAndSearchTest {

    @Test
    void dispatchContextKeepsExactInterfaceTargetUntilDispatchCompletes() throws Exception {
        TileEntity target = new TileEntity();
        XTProfileDispatchContext.begin(null, null);
        try {
            XTProfileDispatchContext.recordTarget(target, null);
            Method method = XTProfileDispatchContext.class.getDeclaredMethod("target");
            method.setAccessible(true);
            assertSame(target, method.invoke(null));
        } finally {
            XTProfileDispatchContext.end();
        }
    }

    @Test
    void activeMachineDispatchDoesNotResetTimingBoundary() throws Exception {
        Method method = XTProfileRouteTracker.class
            .getDeclaredMethod("shouldResetTimingBoundary", boolean.class, long.class, long.class, boolean.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(null, true, 4L, 4L, true));
        assertTrue((Boolean) method.invoke(null, true, 4L, 4L, false));
        assertTrue((Boolean) method.invoke(null, false, 4L, 4L, true));
        assertTrue((Boolean) method.invoke(null, true, 4L, 5L, true));
    }

    @Test
    void runningTickAccountsOneTickMachineBeforeEndOfTickInactiveState() throws Exception {
        Method method;
        try {
            method = XTProfileRouteTracker.class.getDeclaredMethod(
                "accountRunningTick",
                XTProfileData.MediumRecord.class,
                long.class,
                long.class,
                long.class,
                long.class);
        } catch (NoSuchMethodException missing) {
            fail("missing running-tick accounting for one-tick multiblocks");
            return;
        }
        method.setAccessible(true);

        XTProfileData.MediumRecord medium = new XTProfileData.MediumRecord();
        long nextSample = (Long) method.invoke(null, medium, 42L, 1_000L, 6_000L, 1_500L);

        assertEquals(6_000L, nextSample);
        assertEquals(5_000L, medium.busyNs);
        assertEquals(1_500L, medium.tickCostNs);
        assertEquals(1L, medium.activeTicks);
        assertEquals(5_000L, medium.busyNsByCpu.get(42L));
        assertEquals(1_500L, medium.tickCostNsByCpu.get(42L));
        assertEquals(1L, medium.activeTicksByCpu.get(42L));
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
