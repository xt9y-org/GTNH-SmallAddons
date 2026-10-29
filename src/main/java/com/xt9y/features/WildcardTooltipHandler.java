package com.xt9y.features;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import appeng.block.misc.BlockInterface;
import appeng.items.parts.ItemMultiPart;
import appeng.items.parts.PartType;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class WildcardTooltipHandler {

    public static void init() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new WildcardTooltipHandler());
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (!XT9YConfig.ae2InterfaceWildcard) return;
        ItemStack stack = event.itemStack;
        if (stack == null || !isAe2Interface(stack)) return;
        event.toolTip
            .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("xt9yfeatures.tooltip.wildcard.mallet"));
    }

    private static boolean isAe2Interface(ItemStack stack) {
        if (stack.getItem() instanceof ItemBlock itemBlock) {
            return itemBlock.field_150939_a instanceof BlockInterface;
        }
        if (stack.getItem() instanceof ItemMultiPart itemMultiPart) {
            return itemMultiPart.getTypeByStack(stack) == PartType.Interface;
        }
        return false;
    }
}
