package com.xt9y.features;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * A single chunk-local virtual ore source. Its tile NBT is authoritative; the
 * human-editable config records are merely a preload index, not world changes.
 */
public final class OreSourceBlock extends BlockContainer {

    public static final OreSourceBlock INSTANCE = new OreSourceBlock();
    private static final String CATEGORY = "oreSources";
    private static final Map<String, Entry> PRELOADED = new HashMap<>();

    private OreSourceBlock() {
        super(net.minecraft.block.material.Material.iron);
        setBlockName("xt9yfeatures.ore_source");
        setBlockTextureName("minecraft:lapis_block");
        setHardness(5.0F);
        setResistance(10.0F);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    public static void register() {
        // Register regardless of feature state: existing worlds must retain their block IDs.
        GameRegistry.registerBlock(INSTANCE, "ore_source");
        GameRegistry.registerTileEntity(SourceTile.class, XT9YFeatures.MODID + ".ore_source");
        GameRegistry.addRecipe(
            new ItemStack(INSTANCE),
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

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new SourceTile();
    }

    public static boolean isPersonalWorld(World world) {
        if (world == null || world.provider == null) return false;
        Class<?> type = world.provider.getClass();
        while (type != null) {
            if ("me.eigenraven.personalspace.world.PersonalWorldProvider".equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        if (!XT9YConfig.oreSourceBlock || !isPersonalWorld(world) || !super.canPlaceBlockAt(world, x, y, z)) {
            return false;
        }
        for (TileEntity tile : world.getChunkFromBlockCoords(x, z).chunkTileEntityMap.values()) {
            if (tile instanceof SourceTile && (tile.xCoord != x || tile.yCoord != y || tile.zCoord != z)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof SourceTile) {
            SourceTile tile = (SourceTile) world.getTileEntity(x, y, z);
            record(world, tile);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (!XT9YConfig.oreSourceBlock) {
            player.addChatMessage(new ChatComponentText("XT9Y ore source is disabled in config."));
            return true;
        }

        TileEntity entity = world.getTileEntity(x, y, z);
        if (!(entity instanceof SourceTile)) return false;

        SourceTile tile = (SourceTile) entity;
        List<String> dimensions = VirtualExtraction.availableDimensions();
        if (dimensions.isEmpty()) {
            player.addChatMessage(new ChatComponentText("GTNH ore dimension tables are not available."));
            return true;
        }

        int index = dimensions.indexOf(tile.dimensionName);
        int direction = player.isSneaking() ? -1 : 1;
        tile.dimensionName = dimensions.get(Math.floorMod(index + direction, dimensions.size()));
        tile.markDirty();
        world.markBlockForUpdate(x, y, z);
        record(world, tile);
        player.addChatMessage(
            new ChatComponentText(
                "Virtual ore chunk [" + (x >> 4)
                    + ", "
                    + (z >> 4)
                    + "]: "
                    + tile.dimensionName
                    + " (right-click next, sneak + right-click previous)"));
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block oldBlock, int oldMeta) {
        if (!world.isRemote) forget(world, x, z, x, y, z);
        super.breakBlock(world, x, y, z, oldBlock, oldMeta);
    }

    /** Never force-loads the other chunks/dimensions/worlds listed in the config. */
    public static synchronized String getSourceDimension(World world, int chunkX, int chunkZ) {
        if (!isPersonalWorld(world) || !XT9YConfig.oreSourceBlock) return null;
        String key = key(world, chunkX, chunkZ);
        Entry cached = PRELOADED.get(key);
        if (cached != null) {
            TileEntity entity = world.getTileEntity(cached.x, cached.y, cached.z);
            if (entity instanceof SourceTile && world.getBlock(cached.x, cached.y, cached.z) == INSTANCE) {
                SourceTile tile = (SourceTile) entity;
                // An explicitly edited, valid config mode applies only to an existing matching tile.
                if (!tile.dimensionName.equals(cached.mode) && VirtualExtraction.availableDimensions()
                    .contains(cached.mode)) {
                    tile.dimensionName = cached.mode;
                    tile.markDirty();
                    world.markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
                }
                return tile.dimensionName;
            }
        }

        // The tile NBT wins over a stale or manually edited config entry.
        for (TileEntity entity : world.getChunkFromChunkCoords(chunkX, chunkZ).chunkTileEntityMap.values()) {
            if (entity instanceof SourceTile
                && world.getBlock(entity.xCoord, entity.yCoord, entity.zCoord) == INSTANCE) {
                SourceTile source = (SourceTile) entity;
                if (cached == null || !cached.same(source)) record(world, source);
                return source.dimensionName;
            }
        }
        // Missing blocks and unloaded/deleted worlds are never recreated from configuration.
        return null;
    }

    public static synchronized void preload(Configuration configuration) {
        PRELOADED.clear();
        for (Map.Entry<String, Property> property : configuration.getCategory(CATEGORY)
            .getValues()
            .entrySet()) {
            Entry entry = Entry.parse(
                property.getValue()
                    .getString());
            if (entry != null) PRELOADED.put(property.getKey(), entry);
        }
    }

    private static String key(World world, int chunkX, int chunkZ) {
        // The world name and seed keep independent saves/dimensions isolated.
        String id = world.getWorldInfo()
            .getWorldName() + "|"
            + world.getSeed()
            + "|"
            + world.provider.dimensionId
            + "|"
            + chunkX
            + "|"
            + chunkZ;
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    private static synchronized void record(World world, SourceTile tile) {
        if (XT9YConfig.configuration == null) return;
        String key = key(world, tile.xCoord >> 4, tile.zCoord >> 4);
        Entry entry = new Entry(tile.xCoord, tile.yCoord, tile.zCoord, tile.dimensionName);
        PRELOADED.put(key, entry);
        XT9YConfig.configuration.get(CATEGORY, key, entry.toString())
            .set(entry.toString());
        XT9YConfig.configuration.save();
    }

    private static synchronized void forget(World world, int chunkX, int chunkZ, int x, int y, int z) {
        String key = key(world, chunkX >> 4, chunkZ >> 4);
        Entry entry = PRELOADED.get(key);
        if (entry == null || entry.x != x || entry.y != y || entry.z != z) return;
        PRELOADED.remove(key);
        if (XT9YConfig.configuration != null) {
            XT9YConfig.configuration.getCategory(CATEGORY)
                .remove(key);
            XT9YConfig.configuration.save();
        }
    }

    private static final class Entry {

        final int x;
        final int y;
        final int z;
        final String mode;

        Entry(int x, int y, int z, String mode) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.mode = mode;
        }

        boolean same(SourceTile tile) {
            return x == tile.xCoord && y == tile.yCoord && z == tile.zCoord && mode.equals(tile.dimensionName);
        }

        @Override
        public String toString() {
            return x + "|" + y + "|" + z + "|" + mode;
        }

        static Entry parse(String value) {
            if (value == null) return null;
            String[] parts = value.split("\\|", 4);
            if (parts.length != 4 || parts[3].isEmpty()) return null;
            try {
                return new Entry(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    parts[3]);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    public static final class SourceTile extends TileEntity {

        public String dimensionName = "Overworld";

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            super.readFromNBT(nbt);
            if (nbt.hasKey("sourceDimension", 8)) {
                String selected = nbt.getString("sourceDimension");
                if (!selected.isEmpty()) dimensionName = selected;
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {
            super.writeToNBT(nbt);
            nbt.setString("sourceDimension", dimensionName);
        }
    }
}
