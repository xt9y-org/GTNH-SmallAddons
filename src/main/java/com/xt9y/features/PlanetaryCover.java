package com.xt9y.features;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.utils.item.LimitingItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
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
        return dimension == null ? "Insert an Eye of Harmony planet to select a dimension" : "Virtual dimension: " + dimension;
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
        if (getTile() != null) {
            getTile().markDirty();
            getTile().issueCoverUpdate(coverSide);
        }
        player.addChatMessage(new ChatComponentText(getDescription()));
        return true;
    }

    @Override
    public boolean isDataNeededOnClient() {
        return true;
    }

    @Override
    protected void readDataFromNbt(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            planet.deserializeNBT(((NBTTagCompound) nbt).getCompoundTag("planet"));
        }
    }

    @Override
    protected @NotNull NBTBase saveDataToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("planet", planet.serializeNBT());
        return tag;
    }

    @Override
    protected void readDataFromPacket(ByteArrayDataInput data) {
        planet.deserializeNBT(GTByteBuffer.readCompoundTagFromGreggyByteBuf(data));
    }

    @Override
    protected void writeDataToByteBuf(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, planet.serializeNBT());
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

    private static final class PlanetGui extends CoverBaseGui<PlanetaryCover> {

        private PlanetGui(PlanetaryCover cover) {
            super(cover);
        }

        @Override
        protected String getGuiId() {
            return "cover.xt9y_planet";
        }

        @Override
        public void addUIWidgets(PanelSyncManager syncManager, Flow column, CoverGuiData data) {
            column.child(IKey.str("Eye of Harmony planet (ghost item):").asWidget())
                .child(
                    new PhantomItemSlot().slot(new ModularSlot(cover.getPlanetSlot(), 0))
                        .marginTop(5));
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
