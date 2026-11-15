package com.xt9y.features;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.serialization.IByteBufAdapter;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.utils.item.LimitingItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.google.common.io.ByteArrayDataInput;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.covers.CoverContext;
import gregtech.api.covers.CoverPlacer;
import gregtech.api.covers.CoverRegistry;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.CoverUIBuildContext;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.CoverGuiData;
import gregtech.api.util.GTByteBuffer;
import gregtech.api.util.GTUtility;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.tileentities.machines.basic.MTEPump;
import gregtech.common.tileentities.machines.multi.MTEOilDrillBase;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import gregtech.common.covers.Cover;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;
import gregtech.common.gui.mui1.cover.CoverUIFactory;
import gtneioreplugin.plugin.block.BlockDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;
import io.netty.buffer.ByteBuf;
import tectech.TecTech;

/**
 * Gives the attached machine a virtual Eye of Harmony ore dimension.
 * The planet item in the cover is a ghost item; it is never consumed.
 */
public final class PlanetaryCover extends Cover {

    public static final Item ITEM = new Item().setUnlocalizedName("xt9yfeatures.planetary_cover")
        .setTextureName("minecraft:ender_eye")
        .setCreativeTab(CreativeTabs.tabMisc);

    private final ItemStackHandler planet = new LimitingItemStackHandler(1, 1);
    private ItemStackHandler selectedOres = new ItemStackHandler(1);
    private boolean oreBlacklist;
    private boolean fluidBlacklist;
    private String fluidName = "";

    public PlanetaryCover(CoverContext context) {
        super(context, null);
    }

    public static void registerItem() {
        GameRegistry.registerItem(ITEM, "planetary_cover");
        GameRegistry.addRecipe(
            new ItemStack(ITEM),
            "IRI",
            "RDR",
            "IRI",
            'I',
            Blocks.iron_block,
            'R',
            Items.redstone,
            'D',
            Blocks.diamond_block);
    }

    public static void registerCover() {
        CoverRegistry.registerCover(
            new ItemStack(ITEM),
            Textures.BlockIcons.MACHINE_CASINGS[1][0],
            PlanetaryCover::new,
            CoverPlacer.builder()
                .onlyPlaceIf((side, item, tile) -> {
                    if (!(tile instanceof IGregTechTileEntity)) return false;
                    for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                        if (tile.getCoverAtSide(direction) instanceof PlanetaryCover) return false;
                    }
                    return true;
                })
                .build());
    }

    public ItemStackHandler getPlanetSlot() {
        return planet;
    }

    public boolean isFluidMachine() {
        if (!(getTile() instanceof IGregTechTileEntity)) return false;
        Object machine = ((IGregTechTileEntity) getTile()).getMetaTileEntity();
        return machine instanceof MTEPump || machine instanceof MTEOilDrillBase;
    }

    public ItemStackHandler getSelectedOres() {
        return selectedOres;
    }

    public void setSelectedOres(ItemStackHandler input) {
        if (input != null && input.getSlots() <= 512) {
            selectedOres = input;
            changed();
        }
    }

    public boolean isOreBlacklist() {
        return oreBlacklist;
    }

    public void setOreBlacklist(boolean value) {
        oreBlacklist = value;
        changed();
    }

    public boolean isFluidBlacklist() {
        return fluidBlacklist;
    }

    public void setFluidBlacklist(boolean value) {
        fluidBlacklist = value;
        changed();
    }

    public String getFluidName() {
        return fluidName;
    }

    public void setFluidName(String name) {
        if (name == null || name.isEmpty()) {
            fluidName = "";
        } else if (VirtualExtraction.fluidsFor(getSelectedDimension())
            .containsKey(name)) {
            fluidName = name;
        } else {
            return;
        }
        changed();
    }

    public List<String> getAvailableFluids() {
        List<String> fluids = new ArrayList<>(VirtualExtraction.fluidsFor(getSelectedDimension())
            .keySet());
        Collections.sort(fluids);
        return fluids;
    }

    public ItemStack[] getAvailableOres() {
        String dimension = getSelectedDimension();
        if (dimension == null) return new ItemStack[0];
        VoidMinerUtility.DropMap map = VirtualExtraction.poolFor(dimension);
        if (map == null) return new ItemStack[0];
        map.isDistributionCached(VoidMinerUtility.extraDropsByDimName.get(dimension));
        if (map.getOres() == null) return new ItemStack[0];
        return Arrays.stream(map.getOres())
            .map(GTUtility.ItemId::getItemStack)
            .sorted(Comparator.comparingInt(ItemStack::getItemDamage))
            .toArray(ItemStack[]::new);
    }

    private int indexOfOre(ItemStack ore) {
        if (ore == null) return -1;
        for (int i = 0; i < selectedOres.getSlots(); i++) {
            ItemStack item = selectedOres.getStackInSlot(i);
            if (item != null && GTUtility.areStacksEqual(item, ore)) return i;
        }
        return -1;
    }

    public boolean isOreSelected(ItemStack ore) {
        return indexOfOre(ore) >= 0;
    }

    public boolean allowsOre(ItemStack ore) {
        boolean empty = true;
        for (int i = 0; i < selectedOres.getSlots(); i++) {
            if (selectedOres.getStackInSlot(i) != null) {
                empty = false;
                break;
            }
        }
        return empty || (isOreSelected(ore) != oreBlacklist);
    }

    public void ensureOreSlots(int count) {
        if (count > selectedOres.getSlots() && count <= 512) selectedOres.setSize(count);
    }

    public void selectOre(ItemStack ore, boolean enabled) {
        ItemStack[] available = getAvailableOres();
        ensureOreSlots(available.length);
        for (int i = 0; i < available.length; i++) {
            if (!GTUtility.areStacksEqual(available[i], ore)) continue;
            ItemStack copy = null;
            if (enabled) {
                copy = ore.copy();
                copy.stackSize = 1;
            }
            selectedOres.setStackInSlot(i, copy);
            changed();
            return;
        }
    }

    public void selectAllOres(boolean enabled) {
        ItemStack[] available = getAvailableOres();
        selectedOres = new ItemStackHandler(Math.max(1, enabled ? available.length : 1));
        if (enabled) {
            for (int i = 0; i < available.length; i++) {
                selectedOres.setStackInSlot(i, available[i]);
            }
        }
        changed();
    }

    private void changed() {
        if (getTile() != null) {
            getTile().markDirty();
            getTile().issueCoverUpdate(coverSide);
        }
    }

    private static String dimensionOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null || TecTech.eyeOfHarmonyRecipeStorage == null) return null;
        Block block = Block.getBlockFromItem(stack.getItem());
        if (!(block instanceof BlockDimensionDisplay)) return null;
        if (TecTech.eyeOfHarmonyRecipeStorage.recipeLookUp(stack) == null) return null;
        String abbreviation = ((BlockDimensionDisplay) block).getDimension();
        try {
            return DimensionHelper.getFullName(abbreviation);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    /** Null means this cover is empty or does not contain an actual EoH planet recipe. */
    public String getSelectedDimension() {
        return XT9YConfig.planetaryCover ? dimensionOf(planet.getStackInSlot(0)) : null;
    }

    @Override
    public String getDescription() {
        String dimension = getSelectedDimension();
        return dimension == null ? "Insert an Eye of Harmony planet to select a dimension"
            : "Virtual dimension: " + dimension;
    }

    @Override
    @NotNull
    public List<String> getAdditionalTooltip() {
        return Collections.singletonList(getDescription());
    }

    @Override
    public boolean onCoverRightClick(EntityPlayer player, float hitX, float hitY, float hitZ) {
        if (player.worldObj.isRemote) return true;
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && dimensionOf(held) == null) {
            player.addChatMessage(new ChatComponentText("Hold an Eye of Harmony planet item, or use the cover GUI."));
            return true;
        }
        ItemStack selection = held == null ? null : held.copy();
        if (selection != null) selection.stackSize = 1;
        planet.setStackInSlot(0, selection);
        selectedOres = new ItemStackHandler(1);
        fluidName = "";
        changed();
        player.addChatMessage(new ChatComponentText(getDescription()));
        return true;
    }

    @Override
    public boolean isDataNeededOnClient() {
        return true;
    }

    @Override
    protected void readDataFromNbt(NBTBase nbt) {
        if (!(nbt instanceof NBTTagCompound)) return;
        NBTTagCompound data = (NBTTagCompound) nbt;
        planet.deserializeNBT(data.getCompoundTag("planet"));
        selectedOres.deserializeNBT(data.getCompoundTag("selectedOres"));
        oreBlacklist = data.getBoolean("oreBlacklist");
        fluidBlacklist = data.getBoolean("fluidBlacklist");
        fluidName = data.getString("fluidName");
    }

    @Override
    protected @NotNull NBTBase saveDataToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("planet", planet.serializeNBT());
        tag.setTag("selectedOres", selectedOres.serializeNBT());
        tag.setBoolean("oreBlacklist", oreBlacklist);
        tag.setBoolean("fluidBlacklist", fluidBlacklist);
        tag.setString("fluidName", fluidName);
        return tag;
    }

    @Override
    protected void readDataFromPacket(ByteArrayDataInput data) {
        readDataFromNbt(GTByteBuffer.readCompoundTagFromGreggyByteBuf(data));
    }

    @Override
    protected void writeDataToByteBuf(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, (NBTTagCompound) saveDataToNbt());
    }

    @Override
    public boolean hasCoverGUI() {
        return true;
    }

    @Override
    protected @NotNull CoverBaseGui<?> getCoverGui() {
        return new PlanetGui(this);
    }

    @Override
    public ModularWindow createWindow(CoverUIBuildContext context) {
        return new LegacyPlanetGui(context).createWindow();
    }

    /** MUI2 cover interface, with the same individual ore-toggle controls as GTNH's Void Miner. */
    private static final class PlanetGui extends CoverBaseGui<PlanetaryCover> {

        private String search = "";

        private PlanetGui(PlanetaryCover cover) {
            super(cover);
        }

        @Override
        protected String getGuiId() {
            return "cover.xt9y_planet";
        }

        @Override
        public void addUIWidgets(PanelSyncManager syncManager, Flow column, CoverGuiData data) {
            IPanelHandler orePanel = syncManager.syncedPanel("planet_ores", true, this::orePopup);
            IPanelHandler fluidPanel = syncManager.syncedPanel("planet_fluids", true, this::fluidPopup);

            column.child(IKey.str("Eye of Harmony planet:").asWidget())
                .child(new PhantomItemSlot().slot(new ModularSlot(cover.getPlanetSlot(), 0))
                    .marginTop(5))
                .child(IKey.dynamic(cover::getDescription).asWidget())
                .child(new ButtonWidget<>().size(125, 18)
                    .overlay(IKey.str(cover.isFluidMachine() ? "Select fluid" : "Ore whitelist / blacklist"))
                    .onMousePressed(button -> {
                        IPanelHandler panel = cover.isFluidMachine() ? fluidPanel : orePanel;
                        if (panel.isPanelOpen()) panel.closePanel();
                        else panel.openPanel();
                        return true;
                    })
                    .setEnabledIf($ -> cover.getSelectedDimension() != null));
        }

        private ModularPanel orePopup(PanelSyncManager manager, IPanelHandler handler) {
            ItemStack[] ores = cover.getAvailableOres();
            GenericSyncValue<ItemStackHandler, ?> sync = new GenericSyncValue<>(
                ItemStackHandler.class,
                cover::getSelectedOres,
                cover::setSelectedOres,
                new OreSelectionAdapter()).allowC2S();
            manager.syncValue("ore_selection", sync);
            BooleanSyncValue mode = new BooleanSyncValue(cover::isOreBlacklist, cover::setOreBlacklist).allowC2S();
            manager.syncValue("ore_blacklist", mode);

            return new ModularPanel("xt9y:planet:ores").child(ButtonWidget.panelCloseButton())
                .child(Flow.column()
                    .child(IKey.str("Planet ores - whitelist / blacklist").asWidget())
                    .child(Flow.row()
                        .child(new ListWidget<>().child(Flow.row()
                            .children(ores.length, i -> {
                                ItemStack ore = ores[i];
                                return new ToggleButton().value(
                                    new BoolValue.Dynamic(() -> cover.isOreSelected(ore), enabled -> {
                                        cover.selectOre(ore, enabled);
                                        sync.setValue(cover.getSelectedOres());
                                    }))
                                    .overlay(new ItemDrawable(ore).asIcon().size(16))
                                    .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                                    .background(false, GTGuiTextures.BUTTON_STANDARD)
                                    .tooltip(t -> t.addFromItem(ore))
                                    .setEnabledIf($ -> search.isEmpty() || ore.getDisplayName()
                                        .toLowerCase().contains(search.toLowerCase()));
                            })
                            .wrap().width(180).coverChildrenHeight().collapseDisabledChild())
                            .coverChildrenWidth().height(18 * Math.min(8, (ores.length + 9) / 10)))
                        .child(Flow.column()
                            .child(new ToggleButton().value(mode)
                                .tooltip(false, t -> t.add("Whitelist"))
                                .tooltip(true, t -> t.add("Blacklist"))
                                .overlay(false, GTGuiTextures.OVERLAY_BUTTON_WHITELIST.asIcon().size(16))
                                .overlay(true, GTGuiTextures.OVERLAY_BUTTON_BLACKLIST.asIcon().size(16)))
                            .child(new ButtonWidget<>().overlay(GTGuiTextures.OVERLAY_BUTTON_CHECKMARK.asIcon())
                                .tooltip(t -> t.add("Select all"))
                                .onMousePressed(button -> {
                                    cover.selectAllOres(true);
                                    sync.setValue(cover.getSelectedOres());
                                    return true;
                                }))
                            .child(new ButtonWidget<>().overlay(GTGuiTextures.OVERLAY_BUTTON_CROSS.asIcon())
                                .tooltip(t -> t.add("Clear selection - all ores allowed"))
                                .onMousePressed(button -> {
                                    cover.selectAllOres(false);
                                    sync.setValue(cover.getSelectedOres());
                                    return true;
                                }))
                            .childPadding(3).coverChildrenWidth())
                        .childPadding(4).coverChildren())
                    .child(new TextFieldWidget().value(
                        new StringSyncValue(() -> search, value -> search = value).allowC2S())
                        .hintText("Search ores")
                        .autoUpdateOnChange(true)
                        .width(140))
                    .childPadding(4).margin(7).coverChildren())
                .coverChildren();
        }

        private ModularPanel fluidPopup(PanelSyncManager manager, IPanelHandler handler) {
            List<String> names = cover.getAvailableFluids();
            StringSyncValue selected = new StringSyncValue(cover::getFluidName, cover::setFluidName).allowC2S();
            manager.syncValue("fluid_selection", selected);
            BooleanSyncValue mode = new BooleanSyncValue(cover::isFluidBlacklist, cover::setFluidBlacklist).allowC2S();
            manager.syncValue("fluid_blacklist", mode);

            FluidStackTank selectionTank = new FluidStackTank(
                () -> {
                    Fluid fluid = FluidRegistry.getFluid(cover.getFluidName());
                    return fluid == null ? null : new FluidStack(fluid, 1);
                },
                stack -> {
                    cover.setFluidName(stack == null ? "" : stack.getFluid().getName());
                    selected.setValue(cover.getFluidName());
                },
                1);

            return new ModularPanel("xt9y:planet:fluids").child(ButtonWidget.panelCloseButton())
                .child(Flow.column()
                    .child(IKey.str("Planet fluid - one selection").asWidget())
                    .child(Flow.row()
                        .child(new FluidSlot().syncHandler(
                            new FluidSlotSyncHandler(selectionTank).phantom(true).controlsAmount(false)))
                        .child(new ToggleButton().value(mode)
                            .tooltip(false, t -> t.add("Whitelist: pump selected fluid"))
                            .tooltip(true, t -> t.add("Blacklist: pump any available fluid except selected"))
                            .overlay(false, GTGuiTextures.OVERLAY_BUTTON_WHITELIST.asIcon().size(16))
                            .overlay(true, GTGuiTextures.OVERLAY_BUTTON_BLACKLIST.asIcon().size(16)))
                        .childPadding(6).coverChildren())
                    .child(new ListWidget<>().child(Flow.column()
                        .children(names.size(), i -> {
                            String name = names.get(i);
                            Fluid fluid = FluidRegistry.getFluid(name);
                            if (fluid == null) return IKey.str("Missing fluid: " + name).asWidget();
                            String label = new FluidStack(fluid, 1).getLocalizedName();
                            return new ButtonWidget<>().size(155, 16)
                                .overlay(IKey.str(label))
                                .onMousePressed(button -> {
                                    cover.setFluidName(name);
                                    selected.setValue(cover.getFluidName());
                                    return true;
                                });
                        })
                        .childPadding(2).coverChildren())
                        .height(Math.min(130, 18 * names.size())).width(155))
                    .child(new ButtonWidget<>().size(155, 16)
                        .overlay(IKey.str("Clear fluid selection"))
                        .onMousePressed(button -> {
                            cover.setFluidName("");
                            selected.setValue("");
                            return true;
                        }))
                    .childPadding(5).margin(7).coverChildren())
                .coverChildren();
        }
    }

    private static final class OreSelectionAdapter implements IByteBufAdapter<ItemStackHandler> {

        @Override
        public ItemStackHandler deserialize(PacketBuffer buffer) throws IOException {
            ItemStackHandler handler = new ItemStackHandler();
            handler.deserializeNBT(buffer.readNBTTagCompoundFromBuffer());
            return handler;
        }

        @Override
        public void serialize(PacketBuffer buffer, ItemStackHandler value) throws IOException {
            buffer.writeNBTTagCompoundToBuffer(value.serializeNBT());
        }

        @Override
        public boolean areEqual(@NotNull ItemStackHandler a, @NotNull ItemStackHandler b) {
            if (a.getSlots() != b.getSlots()) return false;
            for (int i = 0; i < a.getSlots(); i++) {
                if (!ItemStack.areItemStacksEqual(a.getStackInSlot(i), b.getStackInSlot(i))) return false;
            }
            return true;
        }
    }

    private static final class LegacyPlanetGui extends CoverUIFactory<PlanetaryCover> {

        private LegacyPlanetGui(CoverUIBuildContext context) {
            super(context);
        }

        @Override
        protected PlanetaryCover adaptCover(Cover cover) {
            return cover instanceof PlanetaryCover ? (PlanetaryCover) cover : null;
        }

        @Override
        protected void addUIWidgets(ModularWindow.Builder builder) {
            PlanetaryCover current = getCover();
            if (current == null) return;
            builder.widget(new TextWidget("Eye of Harmony planet (ghost item):").setPos(10, 25))
                .widget(
                    SlotWidget.phantom(current.getPlanetSlot(), 0)
                        .setPos(10, 45));
        }
    }
}
