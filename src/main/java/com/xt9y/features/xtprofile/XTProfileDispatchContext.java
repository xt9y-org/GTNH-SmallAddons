package com.xt9y.features.xtprofile;

import net.minecraft.tileentity.TileEntity;

public final class XTProfileDispatchContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public static void begin() {
        CURRENT.set(new Context());
    }

    public static void recordTarget(TileEntity target) {
        Context context = CURRENT.get();
        if (context != null) context.target = target;
    }

    public static TileEntity target() {
        Context context = CURRENT.get();
        return context == null ? null : context.target;
    }

    public static void end() {
        CURRENT.remove();
    }

    private XTProfileDispatchContext() {}

    private static final class Context {

        TileEntity target;
    }
}
