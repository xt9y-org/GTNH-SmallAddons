package com.xt9y.features.xtprofile;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public final class XTProfileDispatchContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public static void begin(CraftingCPUCluster cpu) {
        CURRENT.set(new Context(cpu));
    }

    public static void recordTarget(TileEntity target, ICraftingPatternDetails pattern) {
        Context context = CURRENT.get();
        if (context == null) return;

        context.recordedTarget = true;
        XTProfileManager.INSTANCE.recordTarget(context.cpu, target, pattern);
    }

    public static boolean hasRecordedTarget() {
        Context context = CURRENT.get();
        return context != null && context.recordedTarget;
    }

    public static void end() {
        CURRENT.remove();
    }

    private XTProfileDispatchContext() {}

    private static final class Context {

        final CraftingCPUCluster cpu;
        boolean recordedTarget;

        Context(CraftingCPUCluster cpu) {
            this.cpu = cpu;
        }
    }
}
