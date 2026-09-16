package com.xt9y.features.xtprofile;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public final class XTProfileDispatchContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public static void begin(CraftingCPUCluster cpu, ICraftingMedium medium) {
        CURRENT.set(new Context(cpu, medium));
    }

    public static void recordTarget(TileEntity target, ICraftingPatternDetails pattern) {
        Context context = CURRENT.get();
        if (context == null) return;

        context.recordedTarget = true;
        context.target = target;
        XTProfileManager.INSTANCE.recordTarget(context.cpu, context.medium, target, pattern);
    }

    public static boolean hasRecordedTarget() {
        Context context = CURRENT.get();
        return context != null && context.recordedTarget;
    }

    static TileEntity target() {
        Context context = CURRENT.get();
        return context == null ? null : context.target;
    }

    public static void end() {
        CURRENT.remove();
    }

    private XTProfileDispatchContext() {}

    private static final class Context {

        final CraftingCPUCluster cpu;
        final ICraftingMedium medium;
        boolean recordedTarget;
        TileEntity target;

        Context(CraftingCPUCluster cpu, ICraftingMedium medium) {
            this.cpu = cpu;
            this.medium = medium;
        }
    }
}
