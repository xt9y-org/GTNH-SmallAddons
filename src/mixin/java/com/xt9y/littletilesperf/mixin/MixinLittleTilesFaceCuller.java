package com.xt9y.littletilesperf.mixin;

import java.util.List;

import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.creativemd.creativecore.client.rendering.IFaceClipper;
import com.creativemd.littletiles.client.render.FaceClipper;
import com.creativemd.littletiles.client.render.LittleTilesFaceCuller;
import com.creativemd.littletiles.common.utils.LittleTilesCubeObject;
import com.xt9y.littletilesperf.PerfSettings;

/** Preserve upstream neighbor culling, replacing only the quadratic local search. */
@Mixin(value = LittleTilesFaceCuller.class, remap = false)
public abstract class MixinLittleTilesFaceCuller {

    @Shadow
    private static void coverNeighbours(IBlockAccess world, List<LittleTilesCubeObject> cubes,
                                        FaceClipper[] clippers, int x, int y, int z) {
        throw new AssertionError("Mixin shadow not applied");
    }

    @Inject(method = "computeCoverage", at = @At("RETURN"), cancellable = true, remap = false)
    private static void ltperf$cullOpaqueFaces(IBlockAccess world, List<LittleTilesCubeObject> cubes,
                                                int x, int y, int z,
                                                CallbackInfoReturnable<IFaceClipper[]> cir) {
        if (PerfSettings.cullBuriedOpaqueFaces) {
            IFaceClipper[] coverage = cir.getReturnValue();
            if (coverage != null) OpaqueFaceCuller.apply(cubes, coverage);
        }
    }

    @Inject(method = "computeCoverage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ltperf$spatialCoverage(IBlockAccess world, List<LittleTilesCubeObject> cubes,
                                               int x, int y, int z,
                                               CallbackInfoReturnable<IFaceClipper[]> cir) {
        if (!PerfSettings.spatialFaceCulling) return;

        // Small lists and any nonstandard coordinates stay on the exact original path.
        FaceClipper[] clippers = FastCoverage.local(cubes);
        if (clippers == null) return;

        coverNeighbours(world, cubes, clippers, x, y, z);
        if (PerfSettings.cullBuriedOpaqueFaces) OpaqueFaceCuller.apply(cubes, clippers);
        cir.setReturnValue(clippers);
    }
}
