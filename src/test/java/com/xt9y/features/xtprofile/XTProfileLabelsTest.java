package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;

class XTProfileLabelsTest {

    @Test
    void wrappedAe2InterfaceUsesHostName() {
        IInterfaceViewable host = new TestInterfaceHost();
        ICraftingMedium medium = new WrappedMedium(host);

        assertEquals("Assembly Line Interface", XTProfileLabels.mediumName(medium));
    }

    private static final class WrappedMedium implements ICraftingMedium {

        private final IInterfaceViewable host;

        private WrappedMedium(IInterfaceViewable host) {
            this.host = host;
        }

        public IInterfaceViewable getHost() {
            return host;
        }

        @Override
        public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class TestInterfaceHost implements IInterfaceViewable {

        @Override
        public DimensionalCoord getLocation() {
            return new DimensionalCoord(1, 2, 3, 0);
        }

        @Override
        public int rows() {
            return 1;
        }

        @Override
        public int rowSize() {
            return 1;
        }

        @Override
        public IInventory getPatterns() {
            return null;
        }

        @Override
        public String getName() {
            return "Assembly Line Interface";
        }

        @Override
        public TileEntity getTileEntity() {
            return null;
        }

        @Override
        public boolean shouldDisplay() {
            return true;
        }

        @Override
        public IGridNode getGridNode(ForgeDirection dir) {
            return null;
        }

        @Override
        public AECableType getCableConnectionType(ForgeDirection dir) {
            return AECableType.NONE;
        }

        @Override
        public void securityBreak() {}
    }
}
