package dev.mcmod.zhenghuocore.client;

import dev.mcmod.zhenghuocore.VoltageConfig;
import dev.mcmod.zhenghuocore.ZhenghuoCore;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = ZhenghuoCore.ID, value = Side.CLIENT)
public final class VoltageTooltip {
    private VoltageTooltip() { }

    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) return;
        ResourceLocation id = ((ItemBlock) stack.getItem()).getBlock().getRegistryName();
        if (VoltageConfig.listed(id)) event.getToolTip().add(I18n.format("zhenghuocore.tooltip.voltage_tier", VoltageConfig.tierName(VoltageConfig.tier(id))));
    }
}
