package com.xt9y.littletilesperf;

import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = LittleTilesPerf.MOD_ID,
    name = "LittleTiles Performance",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:littletiles")
public final class LittleTilesPerf {

    public static final String MOD_ID = "littletilesperf";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PerfSettings.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            ClientCommandHandler.instance.registerCommand(new PerfCommand());
        }
    }
}
