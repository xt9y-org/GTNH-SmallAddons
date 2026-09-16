package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

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
    void routeSearchMatchesInterfaceOrMachineName() throws Exception {
        Class<?> search = Class.forName("com.xt9y.features.xtprofile.XTProfileSearch");
        Method method = search.getDeclaredMethod("matches", String.class, String.class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null, "Assembly Line Interface", "Assembly Line", "interface"));
        assertTrue((Boolean) method.invoke(null, "CRIB #4", "PCB Factory", "pcb"));
        assertFalse((Boolean) method.invoke(null, "CRIB #4", "PCB Factory", "lathe"));
        assertTrue((Boolean) method.invoke(null, "CRIB #4", "PCB Factory", ""));
    }
}
