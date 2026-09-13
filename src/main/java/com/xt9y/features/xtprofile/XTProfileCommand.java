package com.xt9y.features.xtprofile;

import java.io.IOException;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.xt9y.features.XT9YFeatures;

public final class XTProfileCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "xtprofile";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/xtprofile [start|status|reset|stop]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String action = args.length == 0 ? "start" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "start":
                start(sender);
                break;
            case "status":
                status(sender);
                break;
            case "reset":
                reset(sender);
                break;
            case "stop":
                stop(sender);
                break;
            default:
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
        }
    }

    private static void start(ICommandSender sender) {
        try {
            String url = XTProfileManager.INSTANCE.start();
            boolean opened = XTProfileHttpServer.openBrowser(url);
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "XTProfile is running."));
            sendUrl(sender, url, opened ? "Dashboard opened: " : "Open dashboard: ");
        } catch (IOException e) {
            XT9YFeatures.LOGGER.error("Could not start XTProfile", e);
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "XTProfile could not start: " + e.getMessage()));
        }
    }

    private static void status(ICommandSender sender) {
        if (!XTProfileManager.INSTANCE.isRunning()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "XTProfile is stopped."));
            return;
        }
        sendUrl(sender, XTProfileManager.INSTANCE.getUrl(), "XTProfile: ");
    }

    private static void reset(ICommandSender sender) {
        if (!XTProfileManager.INSTANCE.isRunning()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "XTProfile is stopped."));
            return;
        }
        XTProfileManager.INSTANCE.reset();
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "XTProfile session reset."));
    }

    private static void stop(ICommandSender sender) {
        if (!XTProfileManager.INSTANCE.isRunning()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "XTProfile is already stopped."));
            return;
        }
        XTProfileManager.INSTANCE.stop();
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "XTProfile stopped."));
    }

    private static void sendUrl(ICommandSender sender, String url, String prefix) {
        ChatComponentText line = new ChatComponentText(prefix);
        ChatComponentText link = new ChatComponentText(EnumChatFormatting.AQUA + url);
        link.getChatStyle().setUnderlined(true);
        link.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        line.appendSibling(link);
        sender.addChatMessage(line);
    }
}
