package com.xt9y.features.xtprofile;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.core.localization.GuiColors;
import appeng.core.localization.PlayerMessages;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class XTProfileClientOverlay implements INEIGuiHandler {

    private static final ResourceLocation CPU_TEXTURE = new ResourceLocation(
        "appliedenergistics2",
        "textures/guis/craftingcpu.png");
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
    private static final int LIST_BG = 0xFFC6C6C6;
    private static final int LIST_BORDER = 0xFF373737;
    private static final int LIST_INNER_BORDER = 0xFFFFFFFF;
    private static final int DIM_TEXT = 0xFF606060;

    private static boolean initialized;

    private int scroll;
    private boolean sessionScope;
    private long lastCpuId = Long.MIN_VALUE;
    private int panelLeft;
    private int panelTop;
    private int hoveredRow = -1;
    private int selectedEntry = -1;

    public static synchronized void init() {
        if (initialized) return;
        XTProfileClientOverlay overlay = new XTProfileClientOverlay();
        MinecraftForge.EVENT_BUS.register(overlay);
        API.registerNEIGuiHandler(overlay);
        initialized = true;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        XTProfileClientState.clear();
        scroll = 0;
        sessionScope = false;
        lastCpuId = Long.MIN_VALUE;
        hoveredRow = -1;
        selectedEntry = -1;
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
            drawCentered("Waiting for route data...", panelTop + 83, GuiColors.GuiTextColorGray.getColor());
            return;
        }

        if (view.cpuId != lastCpuId && !sessionScope) {
            lastCpuId = view.cpuId;
            scroll = 0;
            selectedEntry = -1;
        }

        clampScroll(view.entries.size());
        hoveredRow = rowAt(event.mouseX, event.mouseY, view.entries.size());
        if (selectedEntry >= view.entries.size()) selectedEntry = -1;
        drawRows(view);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiCraftingCPU)) return;

        Minecraft mc = Minecraft.getMinecraft();
        position(event.gui);

        int mouseX = scaledMouseX(Mouse.getEventX(), event.gui, mc);
        int mouseY = scaledMouseY(Mouse.getEventY(), event.gui, mc);
        if (!inside(mouseX, mouseY, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT)) return;

        XTProfilePanelMessage message = XTProfileClientState.current();
        XTProfilePanelData.View view = currentView(message);
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            if (view != null) {
                scroll += wheel < 0 ? 1 : -1;
                clampScroll(view.entries.size());
            }
            event.setCanceled(true);
            return;
        }

        if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;

        int cpuToggleLeft = panelLeft + PANEL_WIDTH - CPU_TOGGLE_WIDTH - ALL_TOGGLE_WIDTH - 13;
        int allToggleLeft = panelLeft + PANEL_WIDTH - ALL_TOGGLE_WIDTH - 7;
        if (inside(mouseX, mouseY, cpuToggleLeft, panelTop + 4, CPU_TOGGLE_WIDTH, 13)) {
            sessionScope = false;
            scroll = 0;
            selectedEntry = -1;
            event.setCanceled(true);
            return;
        }
        if (inside(mouseX, mouseY, allToggleLeft, panelTop + 4, ALL_TOGGLE_WIDTH, 13)) {
            sessionScope = true;
            scroll = 0;
            selectedEntry = -1;
            event.setCanceled(true);
            return;
        }

        int row = rowAt(mouseX, mouseY, view == null ? 0 : view.entries.size());
        if (row >= 0 && view != null) {
            int entryIndex = scroll + row;
            XTProfilePanelData.Entry entry = view.entries.get(entryIndex);
            selectedEntry = entryIndex;
            if (GuiScreen.isShiftKeyDown() && entry.hasLocation) highlight(entry, mc);
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        if (!(gui instanceof GuiCraftingCPU)) return false;
        position(gui);
        return overlaps(x, y, w, h, panelLeft - 2, panelTop - 2, PANEL_WIDTH + 4, PANEL_HEIGHT + 4);
    }

    private void drawBackground(GuiScreen gui) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(CPU_TEXTURE);
        gui.drawTexturedModalRect(panelLeft, panelTop, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        Gui.drawRect(panelLeft + 6, panelTop + 25, panelLeft + PANEL_WIDTH - 7, panelTop + 173, LIST_BORDER);
        Gui.drawRect(panelLeft + 7, panelTop + 26, panelLeft + PANEL_WIDTH - 8, panelTop + 172, LIST_INNER_BORDER);
        Gui.drawRect(panelLeft + 8, panelTop + 27, panelLeft + PANEL_WIDTH - 9, panelTop + 171, LIST_BG);
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
        mc.fontRenderer.drawString(subtitle, panelLeft + 8, panelTop + 19, DIM_TEXT);
    }

    private void drawRows(XTProfilePanelData.View view) {
        Minecraft mc = Minecraft.getMinecraft();
        List<XTProfilePanelData.Entry> entries = view.entries;
        int end = Math.min(entries.size(), scroll + VISIBLE_ROWS);
        int textColor = GuiColors.GuiTextColorGray.getColor();

        for (int index = scroll; index < end; index++) {
            int visible = index - scroll;
            int y = panelTop + ROW_TOP + visible * ROW_HEIGHT;
            XTProfilePanelData.Entry entry = entries.get(index);
            boolean hovered = visible == hoveredRow;
            boolean selected = index == selectedEntry;

            if (selected) {
                Gui.drawRect(panelLeft + 8, y - 2, panelLeft + PANEL_WIDTH - 9, y + 12, 0x55808080);
            } else if (hovered) {
                Gui.drawRect(
                    panelLeft + 8,
                    y - 2,
                    panelLeft + PANEL_WIDTH - 9,
                    y + 12,
                    GuiColors.CraftingDiagnosticTerminalRowHover.getColor());
            }

            if (visible > 0) {
                Gui.drawRect(
                    panelLeft + 9,
                    y - 3,
                    panelLeft + PANEL_WIDTH - 10,
                    y - 2,
                    GuiColors.CraftingDiagnosticTerminalLine.getColor());
            }

            String name = fit(entry.name, 102, mc);
            mc.fontRenderer.drawString(name, panelLeft + 10, y, textColor);

            String count = compact(entry.dispatches);
            int countX = panelLeft + 126 - mc.fontRenderer.getStringWidth(count);
            mc.fontRenderer.drawString(count, countX, y, textColor);

            String share = Math.round(entry.sharePercent) + "%";
            int shareX = panelLeft + PANEL_WIDTH - 11 - mc.fontRenderer.getStringWidth(share);
            mc.fontRenderer.drawString(share, shareX, y, DIM_TEXT);
        }

        if (entries.isEmpty()) {
            drawCentered(
                sessionScope ? "No session routes yet" : "No routes for this CPU yet",
                panelTop + 83,
                textColor);
        }

        XTProfilePanelData.Entry infoEntry = null;
        if (selectedEntry >= 0 && selectedEntry < entries.size()) {
            infoEntry = entries.get(selectedEntry);
        } else if (hoveredRow >= 0 && scroll + hoveredRow < entries.size()) {
            infoEntry = entries.get(scroll + hoveredRow);
        }

        if (infoEntry != null) {
            int infoY = panelTop + 174;
            String info = infoEntry.hasLocation ? "Shift-click: highlight" : "No world position";
            mc.fontRenderer.drawString(info, panelLeft + 8, infoY, DIM_TEXT);

            if (infoEntry.machine != null && !infoEntry.machine.isEmpty()) {
                String machine = fit(infoEntry.machine, 92, mc);
                int x = panelLeft + PANEL_WIDTH - 8 - mc.fontRenderer.getStringWidth(machine);
                mc.fontRenderer.drawString(machine, x, infoY, DIM_TEXT);
            }
        } else {
            mc.fontRenderer.drawString("Click row · Shift-click highlights", panelLeft + 8, panelTop + 174, DIM_TEXT);
        }
    }

    private static void drawToggle(Minecraft mc, int x, int y, int width, String label, boolean selected) {
        Gui.drawRect(x, y, x + width, y + 13, LIST_BORDER);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 12, 0xFFFFFFFF);
        Gui.drawRect(x + 2, y + 2, x + width - 2, y + 11, selected ? 0xFF8B8B8B : LIST_BG);
        int color = selected ? 0xFFFFFFFF : GuiColors.GuiTextColorGray.getColor();
        int textX = x + (width - mc.fontRenderer.getStringWidth(label)) / 2;
        mc.fontRenderer.drawString(label, textX, y + 3, color);
    }

    private static void highlight(XTProfilePanelData.Entry entry, Minecraft mc) {
        NamedDimensionalCoord coord = new NamedDimensionalCoord(
            entry.x,
            entry.y,
            entry.z,
            entry.dimension,
            entry.name == null ? "" : entry.name);
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

    private static int scaledMouseX(int rawX, GuiScreen gui, Minecraft mc) {
        return rawX * gui.width / mc.displayWidth;
    }

    private static int scaledMouseY(int rawY, GuiScreen gui, Minecraft mc) {
        return gui.height - rawY * gui.height / mc.displayHeight - 1;
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static boolean overlaps(int x, int y, int width, int height, int otherX, int otherY, int otherWidth,
        int otherHeight) {
        return x < otherX + otherWidth && x + width > otherX && y < otherY + otherHeight && y + height > otherY;
    }

    private static String fit(String value, int width, Minecraft mc) {
        String safe = value == null || value.isEmpty() ? "Unnamed crafting medium" : value;
        if (mc.fontRenderer.getStringWidth(safe) <= width) return safe;
        return mc.fontRenderer.trimStringToWidth(safe, Math.max(0, width - mc.fontRenderer.getStringWidth("...")))
            + "...";
    }

    private static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        if (value < 1_000_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }

    private void drawCentered(String text, int y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        int x = panelLeft + (PANEL_WIDTH - mc.fontRenderer.getStringWidth(text)) / 2;
        mc.fontRenderer.drawString(text, x, y, color);
    }

    private XTProfileClientOverlay() {}
}
