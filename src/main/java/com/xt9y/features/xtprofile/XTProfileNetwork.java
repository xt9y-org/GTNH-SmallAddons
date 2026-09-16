package com.xt9y.features.xtprofile;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class XTProfileNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("xtprofile");
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        CHANNEL.registerMessage(XTProfilePanelMessage.Handler.class, XTProfilePanelMessage.class, 0, Side.CLIENT);
        initialized = true;
    }

    static void send(EntityPlayerMP player, XTProfilePanelMessage message) {
        CHANNEL.sendTo(message, player);
    }

    private XTProfileNetwork() {}
}
