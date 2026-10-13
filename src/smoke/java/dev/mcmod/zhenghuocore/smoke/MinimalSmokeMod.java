package dev.mcmod.zhenghuocore.smoke;

import dev.mcmod.ic2grid.Ic2Grid;
import dev.mcmod.ic2grid.block.TileCreativeGenerator;
import dev.mcmod.zhenghuocore.ZhenghuoCore;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.energy.*;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid="zhenghuocore_smoke",name="Minimal runtime checks",version="1",clientSideOnly=true,dependencies="required-after:zhenghuocore")
public final class MinimalSmokeMod {
    private boolean started, scheduled; private volatile int phase; private int ticks; private volatile Throwable failure;
    @Mod.EventHandler public void ready(FMLPostInitializationEvent e) { if(Boolean.getBoolean("zhenghuocore.smoke")) MinecraftForge.EVENT_BUS.register(this); }
    @SubscribeEvent public void attach(AttachCapabilitiesEvent<TileEntity> e) {
        if(e.getObject() instanceof TileEntityChest) e.addCapability(new ResourceLocation("zhenghuocore_smoke","rf"),new ICapabilityProvider(){
            final IEnergyStorage rf=new EnergyStorage(1024,1024,0);
            public boolean hasCapability(Capability<?> c,EnumFacing s){return c==CapabilityEnergy.ENERGY;}
            public <T>T getCapability(Capability<T> c,EnumFacing s){return c==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(rf):null;}
        });
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        try {
            if(failure!=null)throw new AssertionError(failure);
            if(!started){
                started=true;
                if(Loader.isModLoaded("botania")||Loader.isModLoaded("buildcraftcore")||Loader.isModLoaded("astralsorcery"))throw new AssertionError("Optional mod leaked into minimal runtime");
                if(!ZhenghuoCore.MACHINES.isEmpty())throw new AssertionError("Starlight block registered without AS");
                mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
                mc.launchIntegratedServer("zhenghuocore-minimal","Zhenghuo minimal",new WorldSettings(500,GameType.CREATIVE,false,false,WorldType.FLAT).enableCommands());return;
            }
            if(mc.world==null||mc.getIntegratedServer()==null)return;
            if(++ticks>600)throw new AssertionError("Minimal timeout");
            if(phase==0&&!scheduled){
                scheduled=true;mc.getIntegratedServer().addScheduledTask(()->{try{
                    WorldServer w=mc.getIntegratedServer().getWorld(0);BlockPos p=new BlockPos(0,80,0);
                    w.setBlockToAir(p);w.setBlockState(p,Ic2Grid.GENERATOR.getDefaultState());w.setBlockState(p.down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                    TileCreativeGenerator g=(TileCreativeGenerator)w.getTileEntity(p);g.setSettings(32,1);g.refreshRedstone();
                    w.setBlockToAir(p.east());w.setBlockState(p.east(),Blocks.CHEST.getDefaultState());phase=1;
                }catch(Throwable t){failure=t;}finally{scheduled=false;}});
            }else if(phase==1&&ticks>60&&!scheduled){
                scheduled=true;mc.getIntegratedServer().addScheduledTask(()->{try{
                    TileEntity t=mc.getIntegratedServer().getWorld(0).getTileEntity(new BlockPos(1,80,0));
                    if(t.getCapability(CapabilityEnergy.ENERGY,EnumFacing.WEST).getEnergyStored()<=0)throw new AssertionError("RF endpoint did not receive EU in minimal environment");
                    TileEntity eu=mc.getIntegratedServer().getWorld(0).getTileEntity(new BlockPos(0,80,0));
                    IEnergyStorage fe=eu.getCapability(CapabilityEnergy.ENERGY,EnumFacing.EAST);
                    if(fe==null||!fe.canExtract())throw new AssertionError("Native EU FE interface absent without optional mods");
                    fe.getEnergyStored();fe.getMaxEnergyStored();
                    if(fe.extractEnergy(128,true)!=128)throw new AssertionError("Native EU FE simulation failed without optional mods");
                    System.out.println("ZHENGHUO_MINIMAL ALL PASSED");phase=2;
                }catch(Throwable t){failure=t;}});
            }else if(phase==2)mc.shutdown();
        }catch(Throwable t){t.printStackTrace();System.out.println("ZHENGHUO_MINIMAL FAILED "+t);mc.shutdown();}
    }
}
