package com.xt9y.littletilesperf;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class PerfCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "ltperf";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ltperf [stats on|off|reset|status]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0) {
            String action = args[0];
            if ("stats".equalsIgnoreCase(action) && args.length > 1) action = args[1];

            if ("on".equalsIgnoreCase(action)) PerfSettings.collectStatistics = true;
            else if ("off".equalsIgnoreCase(action)) PerfSettings.collectStatistics = false;
            else if ("reset".equalsIgnoreCase(action)) {
                PerfStats.reset();
                FrameTimes.INSTANCE.reset();
            }
            else if (!"status".equalsIgnoreCase(action) && !"stats".equalsIgnoreCase(action)) {
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
                return;
            }
        }
        sender.addChatMessage(new ChatComponentText(
            "[LittleTilesPerf] spatial=" + PerfSettings.spatialFaceCulling
                + " boundsCache=" + PerfSettings.cacheStaticRenderBounds
                + " deduplicate=" + PerfSettings.skipDuplicateRenderInvalidation));
        sender.addChatMessage(new ChatComponentText("[LittleTilesPerf] " + PerfStats.summary()));
        sender.addChatMessage(new ChatComponentText("[LittleTilesPerf] " + FrameTimes.INSTANCE.report()));
    }
}
