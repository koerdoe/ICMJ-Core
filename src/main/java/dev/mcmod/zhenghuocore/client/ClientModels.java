package dev.mcmod.zhenghuocore.client;

import dev.mcmod.zhenghuocore.ZhenghuoCore;
import dev.mcmod.zhenghuocore.block.BlockMachine;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = ZhenghuoCore.ID, value = Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void register(ModelRegistryEvent event) {
        for (BlockMachine block : ZhenghuoCore.MACHINES) ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }
}
