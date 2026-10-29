package com.xt9y.features;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Feature switches are read once during Forge pre-initialization.
 * Gameplay switches should match between the server and clients.
 */
public final class XT9YConfig {

    public static boolean linkedInputHatch = true;
    public static boolean cribWildcard = true;
    public static boolean ae2InterfaceWildcard = true;
    public static boolean cribNonConsumable = true;
    public static boolean xtProfile = true;
    public static boolean betterLoadingScreenCompat = true;

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();

        linkedInputHatch = config.getBoolean(
            "linkedInputHatch",
            "features",
            true,
            "Register the Linked Input Hatch and its recipe. Leave enabled when the world contains linked hatches.");
        cribWildcard = config.getBoolean(
            "cribWildcard",
            "features",
            true,
            "Enable CRIB wildcard expansion, button and Soft Mallet toggle.");
        ae2InterfaceWildcard = config.getBoolean(
            "ae2InterfaceWildcard",
            "features",
            true,
            "Enable wildcard expansion and controls for AE2 Interfaces, including AE2FC dual interfaces.");
        cribNonConsumable = config.getBoolean(
            "cribNonConsumable",
            "features",
            true,
            "Enable non-consumable processing patterns, CRIB reservations and NEI recipe transfer integration.");
        xtProfile = config.getBoolean(
            "xtProfile",
            "features",
            true,
            "Enable XTProfile timing/route tracking and the AE2 Crafting CPU side panel.");
        betterLoadingScreenCompat = config.getBoolean(
            "betterLoadingScreenCompat",
            "features",
            true,
            "Prevent an extra Better Loading Screen resource refresh on its rendering thread.");

        if (config.hasChanged()) config.save();
    }

    private XT9YConfig() {}
}
