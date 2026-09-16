package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

class XTProfileMinimalSurfaceTest {

    @Test
    void dispatchContextOnlyNeedsTheResolvedTarget() {
        TileEntity target = new TileEntity();
        XTProfileDispatchContext.begin(null, null);
        try {
            XTProfileDispatchContext.recordTarget(target);
            assertSame(target, XTProfileDispatchContext.target());
        } finally {
            XTProfileDispatchContext.end();
        }
    }
}
