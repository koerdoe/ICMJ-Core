package dev.mcmod.zhenghuocore;

import dev.mcmod.zhenghuocore.block.*;
import dev.mcmod.zhenghuocore.compat.CompatibilityEvents;
import dev.mcmod.zhenghuocore.compat.TileCompatibility;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid = ZhenghuoCore.ID, name = "整活核心", version = "0.1.5", acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:ic2;required-after:ic2grid@[0.5.1,);after:buildcraftcore;after:botania;after:astralsorcery")
public final class ZhenghuoCore {
    public static final String ID = "zhenghuocore";
    public static final List<BlockMachine> MACHINES = new ArrayList<>();
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        CoreConfig.load(event.getSuggestedConfigurationFile());
        TileCompatibility.register();
        MinecraftForge.EVENT_BUS.register(CompatibilityEvents.INSTANCE);
        ic2.api.energy.EnergyNet.instance.registerEventReceiver(CompatibilityEvents.INSTANCE);
        if (Loader.isModLoaded("theoneprobe")) {
            FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe", "dev.mcmod.zhenghuocore.compat.ProbeCompatibility");
        }
        if (Loader.isModLoaded("astralsorcery")) {
            MACHINES.add(new BlockMachine("starlight_generator", TileStarlightGenerator::new));
            GameRegistry.registerTileEntity(TileStarlightGenerator.class, new ResourceLocation(ID, "starlight_generator"));
            // Its public registry accepts providers throughout preInit. Register exactly once, before world loading.
            hellfirepvp.astralsorcery.common.starlight.transmission.registry.TransmissionClassRegistry.register(TileStarlightGenerator.PROVIDER);
        }
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void blocks(RegistryEvent.Register<Block> event) { for (BlockMachine block : MACHINES) event.getRegistry().register(block); }
    @SubscribeEvent public void items(RegistryEvent.Register<Item> event) { for (BlockMachine block : MACHINES) event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName())); }
}
