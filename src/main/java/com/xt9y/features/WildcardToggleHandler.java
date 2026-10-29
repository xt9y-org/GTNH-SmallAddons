package com.xt9y.features;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.xt9y.features.api.IWildcardToggleable;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.helpers.IInterfaceHost;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

public class WildcardToggleHandler {

    public static void init() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new WildcardToggleHandler());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if ((!XT9YConfig.cribWildcard && !XT9YConfig.ae2InterfaceWildcard)
            || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote
            || !event.entityPlayer.isSneaking()
            || !isSoftMallet(event.entityPlayer.getHeldItem())) {
            return;
        }

        TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
        IWildcardToggleable toggleable = null;

        if (tile instanceof BaseMetaTileEntity baseTile) {
            if (!XT9YConfig.cribWildcard) return;
            IMetaTileEntity meta = baseTile.getMetaTileEntity();
            if (meta instanceof IWildcardToggleable) {
                toggleable = (IWildcardToggleable) meta;
            }
        } else if (tile instanceof IInterfaceHost) {
            if (!XT9YConfig.ae2InterfaceWildcard) return;
            toggleable = (IWildcardToggleable) ((IInterfaceHost) tile).getInterfaceDuality();
        } else if (tile instanceof IPartHost && event.face >= 0 && event.face < 6) {
            if (!XT9YConfig.ae2InterfaceWildcard) return;
            IPart part = ((IPartHost) tile).getPart(ForgeDirection.getOrientation(event.face));
            if (part instanceof IInterfaceHost) {
                toggleable = (IWildcardToggleable) ((IInterfaceHost) part).getInterfaceDuality();
            }
        }

        if (toggleable == null) return;

        toggleWildcard(event.entityPlayer, toggleable);
        event.useItem = PlayerInteractEvent.Result.DENY;
        event.useBlock = PlayerInteractEvent.Result.DENY;
        event.setCanceled(true);
    }

    public static void toggleWildcard(EntityPlayer player, IWildcardToggleable toggleable) {
        boolean enabled = !toggleable.isWildcardEnabled();
        toggleable.setWildcardEnabled(enabled);
        player.addChatMessage(
            new ChatComponentTranslation(
                "ggfab.wildcard.toggle",
                enabled ? EnumChatFormatting.GREEN + "ON" + EnumChatFormatting.RESET
                    : EnumChatFormatting.RED + "OFF" + EnumChatFormatting.RESET));
    }

    private static boolean isSoftMallet(ItemStack stack) {
        return stack != null
            && gregtech.api.util.GTUtility.isStackInList(stack, gregtech.api.GregTechAPI.sSoftMalletList);
    }
}
