package com.xt9y.features.mixin.mixins.late;

import java.util.Objects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.xt9y.features.VirtualExtraction;
import com.xt9y.features.PlanetaryCover;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;

/** Let a GTNH Void Miner multiblock work from the planet selected on its controller cover. */
@Mixin(value = MTEVoidMinerBase.class, remap = false)
public abstract class MixinMTEVoidMinerPlanet {

    @Shadow
    private ModDimensionDef dimensionDef;

    @Shadow
    private boolean canVoidMine;

    @Shadow
    private float totalWeight;

    @Shadow
    public VoidMinerUtility.DropMap dropMap;

    @Shadow
    public VoidMinerUtility.DropMap extraDropMap;

    @Shadow
    public ItemStackHandler selected;

    @Shadow
    public boolean blacklist;

    @Unique
    private String xt9y$previousDimension;

    @Shadow
    private void calculateDropMap() {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(method = "calculateDropMap", at = @At("RETURN"), remap = false)
    private void xt9y$overrideDropMap(CallbackInfo ci) {
        MTEVoidMinerBase<?> miner = (MTEVoidMinerBase<?>) (Object) this;
        IGregTechTileEntity base = miner.getBaseMetaTileEntity();
        String dimension = VirtualExtraction.dimension(base);
        if (dimension == null) return;

        VoidMinerUtility.DropMap map = VirtualExtraction.poolFor(dimension);
        VoidMinerUtility.DropMap extra = VoidMinerUtility.extraDropsByDimName
            .getOrDefault(dimension, new VoidMinerUtility.DropMap());
        if (map == null) return;

        map.isDistributionCached(extra);
        dropMap = map;
        extraDropMap = extra;
        totalWeight = map.getTotalWeight() + extra.getTotalWeight();
        canVoidMine = totalWeight > 0 && map.getOres() != null && map.getOres().length > 0;
        dimensionDef = DimensionDef.getDefByName(dimension);
    }

    @Inject(method = "checkProcessing", at = @At("HEAD"), remap = false)
    private void xt9y$updatePlanet(CallbackInfoReturnable<CheckRecipeResult> cir) {
        MTEVoidMinerBase<?> miner = (MTEVoidMinerBase<?>) (Object) this;
        IGregTechTileEntity base = miner.getBaseMetaTileEntity();
        String dimension = VirtualExtraction.dimension(base);
        if (!Objects.equals(dimension, xt9y$previousDimension)) {
            xt9y$previousDimension = dimension;
            calculateDropMap();
        }
        PlanetaryCover cover = VirtualExtraction.cover(base);
        if (cover != null && cover.getSelectedDimension() != null) {
            selected = cover.getSelectedOres();
            blacklist = cover.isOreBlacklist();
        } else if (dropMap != null && dropMap.getOres() != null
            && selected.getSlots() != dropMap.getOres().length) {
            selected.setSize(dropMap.getOres().length);
        }
    }

}
