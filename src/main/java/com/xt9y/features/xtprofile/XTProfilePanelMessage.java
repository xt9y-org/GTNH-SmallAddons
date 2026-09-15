package com.xt9y.features.xtprofile;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class XTProfilePanelMessage implements IMessage {

    private static final int MAX_ENTRIES = 256;
    private static final int MAX_STRING_LENGTH = 160;

    String cpuName = "";
    XTProfilePanelData.View cpu = new XTProfilePanelData.View();
    XTProfilePanelData.View session = new XTProfilePanelData.View();

    public XTProfilePanelMessage() {}

    XTProfilePanelMessage(String cpuName, XTProfilePanelData.View cpu, XTProfilePanelData.View session) {
        this.cpuName = safe(cpuName);
        this.cpu = cpu == null ? new XTProfilePanelData.View() : cpu;
        this.session = session == null ? new XTProfilePanelData.View() : session;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cpuName = ByteBufUtils.readUTF8String(buf);
        cpu = readView(buf);
        session = readView(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, trim(cpuName));
        writeView(buf, cpu);
        writeView(buf, session);
    }

    private static void writeView(ByteBuf buf, XTProfilePanelData.View view) {
        buf.writeLong(view.cpuId);
        buf.writeLong(view.totalDispatches);
        int size = Math.min(MAX_ENTRIES, view.entries.size());
        buf.writeShort(size);
        for (int i = 0; i < size; i++) {
            XTProfilePanelData.Entry entry = view.entries.get(i);
            ByteBufUtils.writeUTF8String(buf, trim(entry.id));
            ByteBufUtils.writeUTF8String(buf, trim(entry.name));
            ByteBufUtils.writeUTF8String(buf, trim(entry.type));
            ByteBufUtils.writeUTF8String(buf, trim(entry.machine));
            ByteBufUtils.writeUTF8String(buf, trim(entry.item));
            buf.writeLong(entry.dispatches);
            buf.writeDouble(entry.sharePercent);
            buf.writeBoolean(entry.hasLocation);
            buf.writeInt(entry.dimension);
            buf.writeInt(entry.x);
            buf.writeInt(entry.y);
            buf.writeInt(entry.z);
        }
    }

    private static XTProfilePanelData.View readView(ByteBuf buf) {
        XTProfilePanelData.View view = new XTProfilePanelData.View();
        view.cpuId = buf.readLong();
        view.totalDispatches = buf.readLong();
        int size = buf.readUnsignedShort();
        for (int i = 0; i < size; i++) {
            XTProfilePanelData.Entry entry = new XTProfilePanelData.Entry();
            entry.id = ByteBufUtils.readUTF8String(buf);
            entry.name = ByteBufUtils.readUTF8String(buf);
            entry.type = ByteBufUtils.readUTF8String(buf);
            entry.machine = ByteBufUtils.readUTF8String(buf);
            entry.item = ByteBufUtils.readUTF8String(buf);
            entry.dispatches = buf.readLong();
            entry.sharePercent = buf.readDouble();
            entry.hasLocation = buf.readBoolean();
            entry.dimension = buf.readInt();
            entry.x = buf.readInt();
            entry.y = buf.readInt();
            entry.z = buf.readInt();
            view.entries.add(entry);
        }
        return view;
    }

    private static String trim(String value) {
        String safe = safe(value);
        return safe.length() <= MAX_STRING_LENGTH ? safe : safe.substring(0, MAX_STRING_LENGTH);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Handler implements IMessageHandler<XTProfilePanelMessage, IMessage> {

        @Override
        public IMessage onMessage(XTProfilePanelMessage message, MessageContext ctx) {
            XTProfileClientState.update(message);
            return null;
        }
    }
}
