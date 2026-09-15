package com.xt9y.features.xtprofile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.core.localization.GuiColors;
import appeng.core.localization.PlayerMessages;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class XTProfileClientOverlay implements INEIGuiHandler {

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
    private static final int CPU_BUTTON_ID = -4100;
    private static final int ALL_BUTTON_ID = -4101;
    private static final int ROW_BUTTON_BASE_ID = -4110;
    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int PANEL_BORDER = 0xFF373737;
    private static final int LIST_BG = 0xFFC6C6C6;
    private static final int LIST_BORDER = 0xFF373737;
    private static final int LIST_INNER_BORDER = 0xFFFFFFFF;
    private static final int DIM_TEXT = 0xFF606060;
    private static final int TIME_RIGHT = 128;
    private static final int TPS_RIGHT = 156;
    private static final int SCROLLBAR_LEFT = 162;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int SCROLLBAR_TOP = ROW_TOP - 2;
    private static final int SCROLLBAR_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;

    private static boolean initialized;

    private int scroll;
    private boolean sessionScope;
    private long lastCpuId = Long.MIN_VALUE;
    private int panelLeft;
    private int panelTop;
    private int hoveredRow = -1;
    private int selectedEntry = -1;
    private boolean scrollbarDragging;
    private boolean leftMouseDown;

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
        scrollbarDragging = false;
        leftMouseDown = false;
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiCraftingCPU)) return;

        position(event.gui);
        int cpuLeft = panelLeft + PANEL_WIDTH - CPU_TOGGLE_WIDTH - ALL_TOGGLE_WIDTH - 13;
        int allLeft = panelLeft + PANEL_WIDTH - ALL_TOGGLE_WIDTH - 7;
        event.buttonList.add(new OverlayHitButton(CPU_BUTTON_ID, cpuLeft, panelTop + 4, CPU_TOGGLE_WIDTH, 13));
        event.buttonList.add(new OverlayHitButton(ALL_BUTTON_ID, allLeft, panelTop + 4, ALL_TOGGLE_WIDTH, 13));

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            event.buttonList.add(
                new OverlayHitButton(
                    ROW_BUTTON_BASE_ID - row,
                    panelLeft + 8,
                    panelTop + ROW_TOP - 2 + row * ROW_HEIGHT,
                    SCROLLBAR_LEFT - 10,
                    ROW_HEIGHT));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiCraftingCPU)) return;

        if (event.button.id == CPU_BUTTON_ID) {
            sessionScope = false;
            scroll = 0;
            selectedEntry = -1;
            event.setCanceled(true);
            return;
        }

        if (event.button.id == ALL_BUTTON_ID) {
            sessionScope = true;
            scroll = 0;
            selectedEntry = -1;
            event.setCanceled(true);
            return;
        }

        int row = ROW_BUTTON_BASE_ID - event.button.id;
        if (row < 0 || row >= VISIBLE_ROWS) return;

        XTProfilePanelData.View view = currentView(XTProfileClientState.current());
        if (view == null) {
            event.setCanceled(true);
            return;
        }

        int entryIndex = scroll + row;
        if (entryIndex >= 0 && entryIndex < view.entries.size()) {
            selectedEntry = entryIndex;
            XTProfilePanelData.Entry entry = view.entries.get(entryIndex);
            if (GuiScreen.isShiftKeyDown() && entry.hasLocation) highlight(entry, Minecraft.getMinecraft());
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiCraftingCPU)) return;

        XTProfilePanelMessage message = XTProfileClientState.current();
        position(event.gui);
        beginPanelRender();
        try {
            drawBackground();
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

            handleScrollInput(event.mouseX, event.mouseY, view.entries.size());
            clampScroll(view.entries.size());
            hoveredRow = rowAt(event.mouseX, event.mouseY, view.entries.size());
            if (selectedEntry >= view.entries.size()) selectedEntry = -1;
            drawRows(view);
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return null;
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        if (!(gui instanceof GuiCraftingCPU)) return false;
        position(gui);
        return overlaps(x, y, w, h, panelLeft - 2, panelTop - 2, PANEL_WIDTH + 4, PANEL_HEIGHT + 4);
    }

    private void handleScrollInput(int mouseX, int mouseY, int entryCount) {
        boolean overPanel = inside(mouseX, mouseY, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        if (overPanel) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) scroll = XTProfileScroll.wheel(scroll, entryCount, VISIBLE_ROWS, wheel);
        }

        boolean mouseDown = Mouse.isButtonDown(0);
        if (entryCount > VISIBLE_ROWS) {
            int trackLeft = panelLeft + SCROLLBAR_LEFT;
            int trackTop = panelTop + SCROLLBAR_TOP;

            if (mouseDown && !leftMouseDown
                && inside(mouseX, mouseY, trackLeft, trackTop, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT)) {
                int thumbTop = XTProfileScroll.thumbTop(trackTop, SCROLLBAR_HEIGHT, entryCount, VISIBLE_ROWS, scroll);
                int thumbHeight = XTProfileScroll.thumbHeight(SCROLLBAR_HEIGHT, entryCount, VISIBLE_ROWS);
                if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
                    scrollbarDragging = true;
                } else {
                    scroll += mouseY < thumbTop ? -VISIBLE_ROWS : VISIBLE_ROWS;
                    clampScroll(entryCount);
                }
            }

            if (scrollbarDragging && mouseDown) {
                scroll = XTProfileScroll.scrollForThumb(mouseY, trackTop, SCROLLBAR_HEIGHT, entryCount, VISIBLE_ROWS);
            }
        } else {
            scrollbarDragging = false;
        }

        if (!mouseDown) scrollbarDragging = false;
        leftMouseDown = mouseDown;
    }

    private static void beginPanelRender() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawBackground() {
        int right = panelLeft + PANEL_WIDTH;
        int bottom = panelTop + PANEL_HEIGHT;

        Gui.drawRect(panelLeft, panelTop, right, bottom, PANEL_BORDER);
        Gui.drawRect(panelLeft + 1, panelTop + 1, right - 1, bottom - 1, PANEL_HIGHLIGHT);
        Gui.drawRect(panelLeft + 2, panelTop + 2, right - 2, bottom - 2, PANEL_BG);

        Gui.drawRect(right - 3, panelTop + 2, right - 2, bottom - 2, PANEL_SHADOW);
        Gui.drawRect(panelLeft + 2, bottom - 3, right - 2, bottom - 2, PANEL_SHADOW);

        Gui.drawRect(panelLeft + 6, panelTop + 25, right - 7, panelTop + 173, LIST_BORDER);
        Gui.drawRect(panelLeft + 7, panelTop + 26, right - 8, panelTop + 172, LIST_INNER_BORDER);
        Gui.drawRect(panelLeft + 8, panelTop + 27, right - 9, panelTop + 171, LIST_BG);
    }

    private void drawHeader(XTProfilePanelMessage message) {
        Minecraft mc = Minecraft.getMinecraft();
        int text = GuiColors.GuiTextColorGray.getColor();
        mc.fontRenderer.drawString("Crafting Routes", panelLeft + 8, panelTop + 7, text);

        int cpuLeft = panelLeft + PANEL_WIDTH - CPU_TOGGLE_WIDTH - ALL_TOGGLE_WIDTH - 13;
        int allLeft = panelLeft + PANEL_WIDTH - ALL_TOGGLE_WIDTH - 7;
        drawToggle(mc, cpuLeft, panelTop + 4, CPU_TOGGLE_WIDTH, "CPU", !sessionScope);
        drawToggle(mc, allLeft, panelTop + 4, ALL_TOGGLE_WIDTH, "All", sessionScope);

        String scope = message == null ? "Routes" : sessionScope ? "Session" : message.cpuName;
        mc.fontRenderer.drawString(fit(scope, 82, mc), panelLeft + 8, panelTop + 19, DIM_TEXT);
        drawRight(mc, "Time", panelLeft + TIME_RIGHT, panelTop + 19, DIM_TEXT);
        drawRight(mc, "TPS", panelLeft + TPS_RIGHT, panelTop + 19, DIM_TEXT);
    }

    private void drawRows(XTProfilePanelData.View view) {
        Minecraft mc = Minecraft.getMinecraft();
        List<XTProfilePanelData.Entry> entries = view.entries;
        int end = Math.min(entries.size(), scroll + VISIBLE_ROWS);
        int textColor = GuiColors.GuiTextColorGray.getColor();
        int rowRight = panelLeft + SCROLLBAR_LEFT - 2;

        for (int index = scroll; index < end; index++) {
            int visible = index - scroll;
            int y = panelTop + ROW_TOP + visible * ROW_HEIGHT;
            XTProfilePanelData.Entry entry = entries.get(index);
            boolean hovered = visible == hoveredRow;
            boolean selected = index == selectedEntry;

            if (selected) {
                Gui.drawRect(panelLeft + 8, y - 2, rowRight, y + 12, 0x55808080);
            } else if (hovered) {
                Gui.drawRect(
                    panelLeft + 8,
                    y - 2,
                    rowRight,
                    y + 12,
                    GuiColors.CraftingDiagnosticTerminalRowHover.getColor());
            }

            if (visible > 0) {
                Gui.drawRect(
                    panelLeft + 9,
                    y - 3,
                    rowRight - 1,
                    y - 2,
                    GuiColors.CraftingDiagnosticTerminalLine.getColor());
            }

            String name = fit(entry.name, 82, mc);
            mc.fontRenderer.drawString(name, panelLeft + 10, y, textColor);
            drawRight(mc, craftTime(entry.craftTimeMillis), panelLeft + TIME_RIGHT, y, textColor);
            drawRight(mc, tpsUsage(entry.tpsUsagePercent), panelLeft + TPS_RIGHT, y, DIM_TEXT);
        }

        drawScrollbar(entries.size());

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
            mc.fontRenderer
                .drawString("Scroll for more · Shift-click highlights", panelLeft + 8, panelTop + 174, DIM_TEXT);
        }
    }

    private void drawScrollbar(int entryCount) {
        if (entryCount <= VISIBLE_ROWS) return;

        int left = panelLeft + SCROLLBAR_LEFT;
        int top = panelTop + SCROLLBAR_TOP;
        int right = left + SCROLLBAR_WIDTH;
        int bottom = top + SCROLLBAR_HEIGHT;
        int thumbTop = XTProfileScroll.thumbTop(top, SCROLLBAR_HEIGHT, entryCount, VISIBLE_ROWS, scroll);
        int thumbHeight = XTProfileScroll.thumbHeight(SCROLLBAR_HEIGHT, entryCount, VISIBLE_ROWS);

        Gui.drawRect(left, top, right, bottom, PANEL_SHADOW);
        Gui.drawRect(left + 1, top, right - 1, bottom, 0xFF8B8B8B);
        Gui.drawRect(left, thumbTop, right, thumbTop + thumbHeight, PANEL_BORDER);
        Gui.drawRect(left + 1, thumbTop + 1, right - 1, thumbTop + thumbHeight - 1, PANEL_HIGHLIGHT);
        Gui.drawRect(left + 2, thumbTop + 2, right - 1, thumbTop + thumbHeight - 1, PANEL_BG);
    }

    private static void drawToggle(Minecraft mc, int x, int y, int width, String label, boolean selected) {
        Gui.drawRect(x, y, x + width, y + 13, LIST_BORDER);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 12, 0xFFFFFFFF);
        Gui.drawRect(x + 2, y + 2, x + width - 2, y + 11, selected ? 0xFF8B8B8B : LIST_BG);
        int color = selected ? 0xFFFFFFFF : GuiColors.GuiTextColorGray.getColor();
        int textX = x + (width - mc.fontRenderer.getStringWidth(label)) / 2;
        mc.fontRenderer.drawString(label, textX, y + 3, color);
    }

    private static void drawRight(Minecraft mc, String text, int right, int y, int color) {
        mc.fontRenderer.drawString(text, right - mc.fontRenderer.getStringWidth(text), y, color);
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
        if (!inside(mouseX, mouseY, left, top, SCROLLBAR_LEFT - 10, height)) return -1;
        int row = (mouseY - top) / ROW_HEIGHT;
        return scroll + row < entryCount ? row : -1;
    }

    private void clampScroll(int entryCount) {
        scroll = XTProfileScroll.clamp(scroll, entryCount, VISIBLE_ROWS);
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

    private static String craftTime(double millis) {
        if (!(millis > 0.0)) return "0s";
        if (millis < 1_000.0) return Math.round(millis) + "ms";

        double seconds = millis / 1_000.0;
        if (seconds < 10.0) return String.format(Locale.ROOT, "%.1fs", seconds);
        if (seconds < 60.0) return Math.round(seconds) + "s";

        long rounded = Math.round(seconds);
        if (rounded < 3_600) return String.format(Locale.ROOT, "%d:%02d", rounded / 60, rounded % 60);
        return String.format(Locale.ROOT, "%d:%02d", rounded / 3_600, (rounded % 3_600) / 60);
    }

    private static String tpsUsage(double percent) {
        if (!(percent > 0.0)) return "0%";
        if (percent < 0.05) return "<0.1%";
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    private void drawCentered(String text, int y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        int x = panelLeft + (PANEL_WIDTH - mc.fontRenderer.getStringWidth(text)) / 2;
        mc.fontRenderer.drawString(text, x, y, color);
    }

    private static final class OverlayHitButton extends GuiButton {

        private OverlayHitButton(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {}
    }

    private XTProfileClientOverlay() {}
}
