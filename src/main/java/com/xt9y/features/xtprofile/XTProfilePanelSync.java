package com.xt9y.features.xtprofile;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import appeng.container.implementations.ContainerCraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;

final class XTProfilePanelSync {

    static void syncOpenCraftingCpuScreens(XTProfileRouteTracker tracker) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;

        for (Object playerObject : server.getConfigurationManager().playerEntityList) {
            if (!(playerObject instanceof EntityPlayerMP player)) continue;
            if (!(player.openContainer instanceof ContainerCraftingCPU container)) continue;

            CraftingCPUCluster cpu = container.getCpu();
            if (cpu == null) continue;
            XTProfileNetwork.send(player, tracker.panelMessage(cpu));
        }
    }

    static void syncOpenCraftingCpuScreens(XTProfileManager manager) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;

        for (Object playerObject : server.getConfigurationManager().playerEntityList) {
            if (!(playerObject instanceof EntityPlayerMP player)) continue;
            if (!(player.openContainer instanceof ContainerCraftingCPU container)) continue;

            CraftingCPUCluster cpu = container.getCpu();
            if (cpu == null) continue;
            XTProfileNetwork.send(player, manager.panelMessage(cpu));
        }
    }

    private XTProfilePanelSync() {}
}
