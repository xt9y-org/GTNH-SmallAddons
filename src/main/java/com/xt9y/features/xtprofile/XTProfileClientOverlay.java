package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.ScreenColor;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.widgets.GuiAeButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
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
    private static final int PANEL_GAP = 0;
    private static final int TOTAL_SLOTS = 8;
    private static final int VISIBLE_ROWS = 7;
    private static final int SLOT_HEIGHT = 23;
    private static final int PANEL_HEIGHT = 41 + (TOTAL_SLOTS - 2) * SLOT_HEIGHT + 31;

    private static final int SLOT_LEFT = 9;
    private static final int SCROLLBAR_LEFT = PANEL_WIDTH - 16;
    private static final int SLOT_WIDTH = SCROLLBAR_LEFT - SLOT_LEFT - 2;
    private static final int SEARCH_TOP = 19;
    private static final int DATA_TOP = SEARCH_TOP + SLOT_HEIGHT;

    private static final int CPU_BUTTON_ID = -4100;
    private static final int ALL_BUTTON_ID = -4101;
    private static final int ROW_BUTTON_BASE_ID = -4110;

    private static final int CPU_BUTTON_WIDTH = 55;
    private static final int ALL_BUTTON_WIDTH = 30;
    private static final int HEADER_BUTTON_HEIGHT = 15;
    private static final int HEADER_TIME_RIGHT = 126;
    private static final int HEADER_TPS_RIGHT = 157;
    private static final int ROW_TIME_RIGHT = 116;
    private static final int ROW_TPS_RIGHT = 145;

    private static boolean initialized;
    private static XTProfileClientOverlay instance;

    private final GuiScrollbar scrollbar = new GuiScrollbar();

    private int scroll;
    private boolean sessionScope;
    private long lastCpuId = Long.MIN_VALUE;
    private int panelLeft;
    private int panelTop;
    private int hoveredRow = -1;
    private int selectedEntry = -1;
    private boolean leftMouseDown;
    private MEGuiTextField searchField;
    private GuiAeButton cpuButton;
    private GuiAeButton allButton;

    public static synchronized void init() {
        if (initialized) return;
        XTProfileClientOverlay overlay = new XTProfileClientOverlay();
        instance = overlay;
        MinecraftForge.EVENT_BUS.register(overlay);
        API.registerNEIGuiHandler(overlay);
        initialized = true;
    }

    public static boolean handleSearchKey(char character, int key) {
        XTProfileClientOverlay overlay = instance;
        if (overlay == null || overlay.searchField == null
            || !overlay.searchField.isFocused()
            || !(Minecraft.getMinecraft().currentScreen instanceof GuiCraftingCPU)) return false;

        String oldText = overlay.searchField.getText();
        boolean handled = overlay.searchField.textboxKeyTyped(character, key);
        if (!oldText.equals(overlay.searchField.getText())) overlay.resetFilteredPosition();

        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) return true;
        return handled;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        XTProfileClientState.clear();
        if (searchField != null) searchField.setFocused(false);
        searchField = null;
        cpuButton = null;
        allButton = null;
        scroll = 0;
        scrollbar.setCurrentScroll(0);
        sessionScope = false;
        lastCpuId = Long.MIN_VALUE;
        hoveredRow = -1;
        selectedEntry = -1;
        leftMouseDown = false;
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiCraftingCPU)) return;

        position(event.gui);
        createWidgets();
        layoutWidgets();
        event.buttonList.add(cpuButton);
        event.buttonList.add(allButton);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            event.buttonList.add(
                new OverlayHitButton(
                    ROW_BUTTON_BASE_ID - row,
                    panelLeft + SLOT_LEFT,
                    panelTop + DATA_TOP + row * SLOT_HEIGHT,
                    SLOT_WIDTH,
                    SLOT_HEIGHT));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiCraftingCPU)) return;

        if (event.button.id == CPU_BUTTON_ID) {
            sessionScope = false;
            resetFilteredPosition();
            event.setCanceled(true);
            return;
        }

        if (event.button.id == ALL_BUTTON_ID) {
            sessionScope = true;
            resetFilteredPosition();
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

        List<XTProfilePanelData.Entry> entries = filteredEntries(view);
        int entryIndex = scroll + row;
        if (entryIndex >= 0 && entryIndex < entries.size()) {
            selectedEntry = entryIndex;
            XTProfilePanelData.Entry entry = entries.get(entryIndex);
            if (GuiScreen.isShiftKeyDown() && entry.hasLocation) highlight(entry, Minecraft.getMinecraft());
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiCraftingCPU) || !(event.gui instanceof AEBaseGui)) return;

        AEBaseGui gui = (AEBaseGui) event.gui;
        XTProfilePanelMessage message = XTProfileClientState.current();
        position(event.gui);
        createWidgets();
        layoutWidgets();
        beginPanelRender();
        try {
            drawNativeBackground(gui);
            drawHeader(message, event.mouseX, event.mouseY);
            drawSearch(gui);

            XTProfilePanelData.View view = currentView(message);
            if (view == null) {
                configureScrollbar(0);
                drawCentered("Waiting for route data...", panelTop + 110, GuiColors.CraftingStatusCPUName.getColor());
                return;
            }

            if (view.cpuId != lastCpuId && !sessionScope) {
                lastCpuId = view.cpuId;
                resetFilteredPosition();
            }

            List<XTProfilePanelData.Entry> entries = filteredEntries(view);
            configureScrollbar(entries.size());
            handlePointerInput(gui, event.mouseX, event.mouseY, entries.size());
            configureScrollbar(entries.size());
            hoveredRow = rowAt(event.mouseX, event.mouseY, entries.size());
            if (selectedEntry >= entries.size()) selectedEntry = -1;
            drawRows(gui, entries);
            scrollbar.draw(gui);
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

    private void createWidgets() {
        if (searchField == null) {
            searchField = new MEGuiTextField(SLOT_WIDTH - 6, 17, "Search Interface / CRIB / machine");
            searchField.setMaxStringLength(64);
            searchField.setUnfocusWithEnter(true);
        }
        if (cpuButton == null) {
            cpuButton = new GuiAeButton(
                CPU_BUTTON_ID,
                0,
                0,
                CPU_BUTTON_WIDTH,
                HEADER_BUTTON_HEIGHT,
                "CPU",
                "Current CPU");
        }
        if (allButton == null) {
            allButton = new GuiAeButton(
                ALL_BUTTON_ID,
                0,
                0,
                ALL_BUTTON_WIDTH,
                HEADER_BUTTON_HEIGHT,
                "All",
                "All profiled crafts");
        }
    }

    private void layoutWidgets() {
        searchField.x = panelLeft + SLOT_LEFT + 3;
        searchField.y = panelTop + SEARCH_TOP + 3;
        searchField.w = SLOT_WIDTH - 6;
        searchField.h = 17;

        cpuButton.xPosition = panelLeft + 9;
        cpuButton.yPosition = panelTop + 2;
        allButton.xPosition = panelLeft + 66;
        allButton.yPosition = panelTop + 2;
        cpuButton.enabled = sessionScope;
        allButton.enabled = !sessionScope;

        scrollbar.setLeft(panelLeft + SCROLLBAR_LEFT);
        scrollbar.setTop(panelTop + SEARCH_TOP);
        scrollbar.setWidth(12);
        scrollbar.setHeight(PANEL_HEIGHT - 27);
    }

    private void configureScrollbar(int entryCount) {
        scrollbar.setRange(0, Math.max(0, entryCount - VISIBLE_ROWS), 1);
        scrollbar.setCurrentScroll(scroll);
        scroll = scrollbar.getCurrentScroll();
        scrollbar.setVisible(true);
    }

    private void handlePointerInput(AEBaseGui gui, int mouseX, int mouseY, int entryCount) {
        boolean mouseDown = Mouse.isButtonDown(0);
        boolean justPressed = mouseDown && !leftMouseDown;

        if (justPressed) {
            String before = searchField.getText();
            searchField.mouseClicked(mouseX, mouseY, 0);
            if (!before.equals(searchField.getText())) resetFilteredPosition();
            scrollbar.click(gui, mouseX, mouseY);
        }

        if (mouseDown) scrollbar.clickMove(mouseY);

        boolean overRows = inside(
            mouseX,
            mouseY,
            panelLeft + SLOT_LEFT,
            panelTop + DATA_TOP,
            SLOT_WIDTH,
            VISIBLE_ROWS * SLOT_HEIGHT);
        if (overRows) {
            int wheel = Mouse.getDWheel();
            if (wheel != 0) scrollbar.wheel(wheel);
        }

        scroll = scrollbar.getCurrentScroll();
        scroll = XTProfileScroll.clamp(scroll, entryCount, VISIBLE_ROWS);
        scrollbar.setCurrentScroll(scroll);
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

    private void drawNativeBackground(AEBaseGui gui) {
        gui.bindTexture("guis/cpu_selector.png");
        ScreenColor.setGuiColor();

        drawThreeSlice(panelLeft, panelTop, PANEL_WIDTH, 41, 0, 0, 94, 41, 9, 18);
        int y = panelTop + 41;
        for (int row = 1; row < TOTAL_SLOTS - 1; row++) {
            drawThreeSlice(panelLeft, y, PANEL_WIDTH, SLOT_HEIGHT, 0, 41, 94, SLOT_HEIGHT, 9, 18);
            y += SLOT_HEIGHT;
        }
        drawThreeSlice(panelLeft, y, PANEL_WIDTH, 31, 0, 133, 94, 31, 9, 18);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawHeader(XTProfilePanelMessage message, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        String scope = message == null ? "CPU" : message.cpuName;
        cpuButton.displayString = sessionScope ? "CPU" : fit(scope, 46, mc);
        cpuButton.enabled = sessionScope;
        allButton.enabled = !sessionScope;
        cpuButton.drawButton(mc, mouseX, mouseY);
        allButton.drawButton(mc, mouseX, mouseY);

        int color = GuiColors.CraftingStatusCPUStorage.getColor();
        drawRight(mc, "Time", panelLeft + HEADER_TIME_RIGHT, panelTop + 6, color);
        drawRight(mc, "TPS", panelLeft + HEADER_TPS_RIGHT, panelTop + 6, color);
    }

    private void drawSearch(AEBaseGui gui) {
        drawSlot(gui, panelTop + SEARCH_TOP, false, false);
        searchField.drawTextBox();
        if (searchField.getText()
            .isEmpty() && !searchField.isFocused()) {
            drawScaledString(
                Minecraft.getMinecraft(),
                "Search Interface / CRIB / machine...",
                searchField.x + 3,
                searchField.y + 5,
                0.75F,
                GuiColors.CraftingStatusCPUStorage.getColor());
        }
    }

    private void drawRows(AEBaseGui gui, List<XTProfilePanelData.Entry> entries) {
        Minecraft mc = Minecraft.getMinecraft();
        int end = Math.min(entries.size(), scroll + VISIBLE_ROWS);

        for (int index = scroll; index < end; index++) {
            int visible = index - scroll;
            int y = panelTop + DATA_TOP + visible * SLOT_HEIGHT;
            XTProfilePanelData.Entry entry = entries.get(index);
            boolean hovered = visible == hoveredRow;
            boolean selected = index == selectedEntry;

            drawSlot(gui, y, hovered, selected);

            String name = fit(entry.name, 170, mc);
            drawScaledString(
                mc,
                name,
                panelLeft + SLOT_LEFT + 3,
                y + 3,
                0.8F,
                GuiColors.CraftingStatusCPUName.getColor());

            String machine = entry.machine == null || entry.machine.isEmpty() ? "" : fit(entry.machine, 115, mc);
            drawScaledString(
                mc,
                machine,
                panelLeft + SLOT_LEFT + 3,
                y + 12,
                0.65F,
                GuiColors.CraftingStatusCPUStorage.getColor());
            drawScaledRight(
                mc,
                craftTime(entry.craftTimeMillis),
                panelLeft + ROW_TIME_RIGHT,
                y + 12,
                0.7F,
                GuiColors.CraftingStatusCPUStorage.getColor());
            drawScaledRight(
                mc,
                tpsUsage(entry.tpsUsagePercent),
                panelLeft + ROW_TPS_RIGHT,
                y + 12,
                0.7F,
                GuiColors.CraftingStatusCPUStorage.getColor());
        }

        if (entries.isEmpty()) {
            String search = searchField == null ? "" : searchField.getText();
            String empty = search.isEmpty() ? (sessionScope ? "No session routes yet" : "No routes for this CPU yet")
                : "No matching routes";
            drawCentered(empty, panelTop + DATA_TOP + 5, GuiColors.CraftingStatusCPUName.getColor());
        }
    }

    private void drawSlot(AEBaseGui gui, int y, boolean hovered, boolean selected) {
        if (selected) {
            GL11.glColor4f(0.0F, 0.8352F, 1.0F, 1.0F);
        } else if (hovered) {
            GL11.glColor4f(0.65F, 0.9F, 1.0F, 1.0F);
        } else {
            ScreenColor.setGuiColor();
        }
        gui.bindTexture("guis/cpu_selector.png");
        drawThreeSlice(panelLeft + SLOT_LEFT, y, SLOT_WIDTH, SLOT_HEIGHT, 100, 0, 67, SLOT_HEIGHT, 3, 3);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawThreeSlice(int x, int y, int width, int height, int u, int v, int sourceWidth,
        int sourceHeight, int leftCap, int rightCap) {
        int centerSource = sourceWidth - leftCap - rightCap;
        int centerWidth = Math.max(0, width - leftCap - rightCap);
        drawTexturedStretch(x, y, leftCap, height, u, v, leftCap, sourceHeight);
        if (centerWidth > 0 && centerSource > 0) {
            drawTexturedStretch(x + leftCap, y, centerWidth, height, u + leftCap, v, centerSource, sourceHeight);
        }
        drawTexturedStretch(
            x + width - rightCap,
            y,
            rightCap,
            height,
            u + sourceWidth - rightCap,
            v,
            rightCap,
            sourceHeight);
    }

    private static void drawTexturedStretch(int x, int y, int width, int height, int u, int v, int uWidth,
        int vHeight) {
        if (width <= 0 || height <= 0 || uWidth <= 0 || vHeight <= 0) return;
        double scale = 1.0 / 256.0;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, u * scale, (v + vHeight) * scale);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, (u + uWidth) * scale, (v + vHeight) * scale);
        tessellator.addVertexWithUV(x + width, y, 0.0, (u + uWidth) * scale, v * scale);
        tessellator.addVertexWithUV(x, y, 0.0, u * scale, v * scale);
        tessellator.draw();
    }

    private static void drawScaledString(Minecraft mc, String text, int x, int y, float scale, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        mc.fontRenderer.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private static void drawScaledRight(Minecraft mc, String text, int right, int y, float scale, int color) {
        int width = Math.round(mc.fontRenderer.getStringWidth(text) * scale);
        drawScaledString(mc, text, right - width, y, scale, color);
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

    private List<XTProfilePanelData.Entry> filteredEntries(XTProfilePanelData.View view) {
        String search = searchField == null ? "" : searchField.getText();
        if (search.isEmpty()) return view.entries;
        List<XTProfilePanelData.Entry> filtered = new ArrayList<>();
        for (XTProfilePanelData.Entry entry : view.entries) {
            if (XTProfileSearch.matches(entry.name, entry.machine, search)) filtered.add(entry);
        }
        return filtered;
    }

    private void resetFilteredPosition() {
        scroll = 0;
        scrollbar.setCurrentScroll(0);
        hoveredRow = -1;
        selectedEntry = -1;
    }

    private void position(GuiScreen gui) {
        int cpuLeft = (gui.width - CPU_WIDTH) / 2;
        int cpuTop = (gui.height - CPU_HEIGHT) / 2;
        int right = cpuLeft + CPU_WIDTH + PANEL_GAP;
        panelLeft = right + PANEL_WIDTH <= gui.width ? right : Math.max(0, cpuLeft - PANEL_WIDTH - PANEL_GAP);
        panelTop = Math.max(0, Math.min(gui.height - PANEL_HEIGHT, cpuTop - (PANEL_HEIGHT - CPU_HEIGHT) / 2));
    }

    private int rowAt(int mouseX, int mouseY, int entryCount) {
        int left = panelLeft + SLOT_LEFT;
        int top = panelTop + DATA_TOP;
        int height = VISIBLE_ROWS * SLOT_HEIGHT;
        if (!inside(mouseX, mouseY, left, top, SLOT_WIDTH, height)) return -1;
        int row = (mouseY - top) / SLOT_HEIGHT;
        return scroll + row < entryCount ? row : -1;
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
