package com.xt9y.features.xtprofile;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.core.localization.GuiColors;
import appeng.core.localization.PlayerMessages;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class XTProfileClientOverlay {

    private static final ResourceLocation CPU_TEXTURE =
        new ResourceLocation("appliedenergistics2", "textures/guis/craftingcpu.png");
    private static final int CPU_WIDTH = 238;
    private static final int CPU_HEIGHT = 184;
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 184;
    private static final int PANEL_GAP = 4;
    private static final int ROW_TOP = 34;
    private static final int ROW_HEIGHT = 15;
    private static final int VISIBLE_ROWS = 9;
    private static final int CPU_TOGGLE_WIDTH = 30;
    private static final int ALL_TOGGLE_WIDTH = 24;

    private static boolean initialized;

    private int scroll;
    private boolean sessionScope;
    private long lastCpuId = Long.MIN_VALUE;
    private int panelLeft;
    private int panelTop;
    private int hoveredRow = -1;

    public static synchronized void init() {
        if (initialized) return;
        MinecraftForge.EVENT_BUS.register(new XTProfileClientOverlay());
        initialized = true;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        XTProfileClientState.clear();
        scroll = 0;
        sessionScope = false;
        lastCpuId = Long.MIN_VALUE;
        hoveredRow = -1;
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiCraftingCPU)) return;

        XTProfilePanelMessage message = XTProfileClientState.current();
        position(event.gui);
        drawBackground(event.gui);
        drawHeader(message);

        XTProfilePanelData.View view = currentView(message);
        if (view == null) {
            drawCentered(event.gui, "Waiting for route data...", panelTop + 83, GuiColors.GuiTextColorGray.getColor());
            return;
        }

        if (view.cpuId != lastCpuId && !sessionScope) {
            lastCpuId = view.cpuId;
            scroll = 0;
        }
        clampScroll(view.entries.size());
        hoveredRow = rowAt(event.mouseX, event.mouseY, view.entries.size());
        drawRows(event.gui, view);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiCraftingCPU)) return;

        position(mc.currentScreen);
        int mouseX = scaledMouseX(event.x, mc);
        int mouseY = scaledMouseY(event.y, mc);
        if (!inside(mouseX, mouseY, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT)) return;

        XTProfilePanelMessage message = XTProfileClientState.current();
        XTProfilePanelData.View view = currentView(message);

        if (event.dwheel != 0) {
            if (view != null) {
                scroll += event.dwheel < 0 ? 1 : -1;
                clampScroll(view.entries.size());
            }
            event.setCanceled(true);
            return;
        }

        if (event.button != 0 || !event.buttonstate) return;

        int cpuToggleLeft = panelLeft + PANEL_WIDTH - CPU_TOGGLE_WIDTH - ALL_TOGGLE_WIDTH - 13;
        int allToggleLeft = panelLeft + PANEL_WIDTH - ALL_TOGGLE_WIDTH - 7;
        if (inside(mouseX, mouseY, cpuToggleLeft, panelTop + 4, CPU_TOGGLE_WIDTH, 13)) {
            sessionScope = false;
            scroll = 0;
            event.setCanceled(true);
            return;
        }
        if (inside(mouseX, mouseY, allToggleLeft, panelTop + 4, ALL_TOGGLE_WIDTH, 13)) {
            sessionScope = true;
            scroll = 0;
            event.setCanceled(true);
            return;
        }

        int row = rowAt(mouseX, mouseY, view == null ? 0 : view.entries.size());
        if (row >= 0 && view != null) {
            XTProfilePanelData.Entry entry = view.entries.get(scroll + row);
            if (GuiScreen.isShiftKeyDown() && entry.hasLocation) highlight(entry, mc);
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);
    }

    private void drawBackground(GuiScreen gui) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(CPU_TEXTURE);
        gui.drawTexturedModalRect(panelLeft, panelTop, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        Gui.drawRect(panelLeft + 6, panelTop + 25, panelLeft + PANEL_WIDTH - 7, panelTop + 173, 0xB0181B1D);
        Gui.drawRect(panelLeft + 7, panelTop + 26, panelLeft + PANEL_WIDTH - 8, panelTop + 172, 0xD024282B);
    }

    private void drawHeader(XTProfilePanelMessage message) {
        Minecraft mc = Minecraft.getMinecraft();
        int text = GuiColors.GuiTextColorGray.getColor();
        mc.fontRenderer.drawString("Crafting Routes", panelLeft + 8, panelTop + 7, text);

        int cpuLeft = panelLeft + PANEL_WIDTH - CPU_TOGGLE_WIDTH - ALL_TOGGLE_WIDTH - 13;
        int allLeft = panelLeft + PANEL_WIDTH - ALL_TOGGLE_WIDTH - 7;
        drawToggle(mc, cpuLeft, panelTop + 4, CPU_TOGGLE_WIDTH, "CPU", !sessionScope);
        drawToggle(mc, allLeft, panelTop + 4, ALL_TOGGLE_WIDTH, "All", sessionScope);

        XTProfilePanelData.View view = currentView(message);
        String subtitle;
        if (message == null) {
            subtitle = "Live interface / CRIB usage";
        } else if (sessionScope) {
            subtitle = "Session · " + (view == null ? 0 : view.totalDispatches) + " pushes";
        } else {
            subtitle = fit(message.cpuName, 104, mc) + " · " + (view == null ? 0 : view.totalDispatches) + " pushes";
        }
        mc.fontRenderer.drawString(subtitle, panelLeft + 8, panelTop + 19, 0xFF707070);
    }

    private void drawRows(GuiScreen gui, XTProfilePanelData.View view) {
        Minecraft mc = Minecraft.getMinecraft();
        List<XTProfilePanelData.Entry> entries = view.entries;
        int end = Math.min(entries.size(), scroll + VISIBLE_ROWS);
        int textColor = GuiColors.GuiTextColorGray.getColor();

        for (int index = scroll; index < end; index++) {
            int visible = index - scroll;
            int y = panelTop + ROW_TOP + visible * ROW_HEIGHT;
            XTProfilePanelData.Entry entry = entries.get(index);
            boolean hovered = visible == hoveredRow;
            if (hovered) Gui.drawRect(panelLeft + 8, y - 2, panelLeft + PANEL_WIDTH - 9, y + 12, 0x66475A63);

            String name = fit(entry.name, 102, mc);
            mc.fontRenderer.drawString(name, panelLeft + 10, y, textColor);

            String count = compact(entry.dispatches);
            int countX = panelLeft + 126 - mc.fontRenderer.getStringWidth(count);
            mc.fontRenderer.drawString(count, countX, y, 0xFFD0D0D0);

            String share = Math.round(entry.sharePercent) + "%";
            int shareX = panelLeft + PANEL_WIDTH - 11 - mc.fontRenderer.getStringWidth(share);
            mc.fontRenderer.drawString(share, shareX, y, 0xFF8A8A8A);
        }

        if (entries.isEmpty()) {
            drawCentered(gui, sessionScope ? "No session routes yet" : "No routes for this CPU yet", panelTop + 83,
                textColor);
        }

        if (hoveredRow >= 0 && scroll + hoveredRow < entries.size()) {
            XTProfilePanelData.Entry entry = entries.get(scroll + hoveredRow);
            int infoY = panelTop + 174;
            String info = entry.hasLocation ? "Shift-click: highlight" : "No world position";
            mc.fontRenderer.drawString(info, panelLeft + 8, infoY, entry.hasLocation ? 0xFF5A7F89 : 0xFF777777);

            if (entry.machine != null && !entry.machine.isEmpty()) {
                String machine = fit(entry.machine, 92, mc);
                int x = panelLeft + PANEL_WIDTH - 8 - mc.fontRenderer.getStringWidth(machine);
                mc.fontRenderer.drawString(machine, x, infoY, 0xFF777777);
            }
        } else {
            mc.fontRenderer.drawString("Shift-click a row to highlight", panelLeft + 8, panelTop + 174, 0xFF777777);
        }
    }

    private static void drawToggle(Minecraft mc, int x, int y, int width, String label, boolean selected) {
        Gui.drawRect(x, y, x + width, y + 13, selected ? 0xFF59666B : 0xFF303638);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 12, selected ? 0xFF88999F : 0xFF4A5153);
        int color = selected ? 0xFFFFFFFF : 0xFFC0C0C0;
        int textX = x + (width - mc.fontRenderer.getStringWidth(label)) / 2;
        mc.fontRenderer.drawString(label, textX, y + 3, color);
    }

    private static void highlight(XTProfilePanelData.Entry entry, Minecraft mc) {
        NamedDimensionalCoord coord =
            new NamedDimensionalCoord(entry.x, entry.y, entry.z, entry.dimension, entry.name == null ? "" : entry.name);
        Map<NamedDimensionalCoord, String[]> messages = new HashMap<>();
        messages.put(
            coord,
            new String[] { PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                PlayerMessages.MachineInOtherDimNamed.getUnlocalized() });
        BlockPosHighlighter.highlightNamedBlocks(mc.thePlayer, messages, "Interface / CRIB");
        mc.thePlayer.closeScreen();
    }

    private XTProfilePanelData.View currentView(XTProfilePanelMessage message) {
        if (message == null) return null;
        return sessionScope ? message.session : message.cpu;
    }

    private void position(GuiScreen gui) {
        int cpuLeft = (gui.width - CPU_WIDTH) / 2;
        int cpuTop = (gui.height - CPU_HEIGHT) / 2;
        int right = cpuLeft + CPU_WIDTH + PANEL_GAP;
        panelLeft = right + PANEL_WIDTH <= gui.width ? right : Math.max(0, cpuLeft - PANEL_WIDTH - PANEL_GAP);
        panelTop = cpuTop;
    }

    private int rowAt(int mouseX, int mouseY, int entryCount) {
        int left = panelLeft + 8;
        int top = panelTop + ROW_TOP - 2;
        int height = VISIBLE_ROWS * ROW_HEIGHT;
        if (!inside(mouseX, mouseY, left, top, PANEL_WIDTH - 17, height)) return -1;
        int row = (mouseY - top) / ROW_HEIGHT;
        return scroll + row < entryCount ? row : -1;
    }

    private void clampScroll(int entryCount) {
        int max = Math.max(0, entryCount - VISIBLE_ROWS);
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    private static int scaledMouseX(int rawX, Minecraft mc) {
        return rawX * mc.currentScreen.width / mc.displayWidth;
    }

    private static int scaledMouseY(int rawY, Minecraft mc) {
        return mc.currentScreen.height - rawY * mc.currentScreen.height / mc.displayHeight - 1;
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static String fit(String value, int width, Minecraft mc) {
        String safe = value == null || value.isEmpty() ? "Unnamed crafting medium" : value;
        if (mc.fontRenderer.getStringWidth(safe) <= width) return safe;
        return mc.fontRenderer.trimStringToWidth(safe, Math.max(0, width - mc.fontRenderer.getStringWidth("..."))) + "...";
    }

    private static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        if (value < 1_000_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }

    private static void drawCentered(GuiScreen gui, String text, int y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        int x = panelLeftStatic(gui) + (PANEL_WIDTH - mc.fontRenderer.getStringWidth(text)) / 2;
        mc.fontRenderer.drawString(text, x, y, color);
    }

    private static int panelLeftStatic(GuiScreen gui) {
        int cpuLeft = (gui.width - CPU_WIDTH) / 2;
        int right = cpuLeft + CPU_WIDTH + PANEL_GAP;
        return right + PANEL_WIDTH <= gui.width ? right : Math.max(0, cpuLeft - PANEL_WIDTH - PANEL_GAP);
    }

    private XTProfileClientOverlay() {}
}
