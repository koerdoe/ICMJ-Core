package dev.mcmod.zhenghuocore.validation;

import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.engine.TileEngineBase_BC8;
import dev.mcmod.ic2grid.Ic2Grid;
import dev.mcmod.ic2grid.block.TileCreativeGenerator;
import dev.mcmod.ic2grid.energy.ElectricalNodeStats;
import dev.mcmod.zhenghuocore.CoreConfig;
import dev.mcmod.zhenghuocore.VoltageConfig;
import dev.mcmod.zhenghuocore.ZhenghuoCore;
import dev.mcmod.zhenghuocore.block.TileStarlightGenerator;
import dev.mcmod.zhenghuocore.compat.TileCompatibility;
import dev.mcmod.zhenghuocore.compat.ManaReceiverHooks;
import dev.mcmod.zhenghuocore.compat.RfGridNode;
import hellfirepvp.astralsorcery.common.block.network.BlockCollectorCrystalBase.CollectorCrystalType;
import hellfirepvp.astralsorcery.common.item.crystal.CrystalProperties;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.Constellations;
import hellfirepvp.astralsorcery.common.starlight.WorldNetworkHandler;
import hellfirepvp.astralsorcery.common.tile.network.TileCollectorCrystal;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.event.*;
import ic2.api.energy.tile.*;
import ic2.api.item.IC2Items;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mcjty.theoneprobe.TheOneProbe;
import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.apiimpl.elements.ElementProgress;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.energy.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import vazkii.botania.api.mana.IManaReceiver;
import vazkii.botania.common.block.ModBlocks;

/** Development-only real Forge client/integrated server checks. Never packaged in the mod. */
@Mod(modid = "zhenghuocore_validation", name = "Zhenghuo runtime checks", version = "1", clientSideOnly = true,
    dependencies = "required-after:zhenghuocore")
public final class RuntimeValidationMod {
    private static final BlockPos SOURCE = new BlockPos(0,80,0), RF_SOURCE = new BlockPos(10,80,0), SMALL = new BlockPos(20,80,0);
    private static final BlockPos POOL = new BlockPos(30,80,0), MJ = new BlockPos(40,80,0), ENGINE = new BlockPos(50,80,0);
    private static final BlockPos STAR = new BlockPos(60,80,0), CRYSTAL = new BlockPos(60,80,4), WELL = new BlockPos(70,80,0);
    private static final BlockPos LOSS = new BlockPos(80,80,0), OVER = new BlockPos(100,80,0), SPREADER = new BlockPos(34,80,0);
    private static final BlockPos HIGH_RF = new BlockPos(120,80,0);
    private static final int HIGH_RF_INITIAL = 1000000000;
    private static final BlockPos INFINITE_BATTERY = new BlockPos(176,80,0);
    private static final BlockPos REMOTE_SPREADER = new BlockPos(0,80,20), BURST_LOAD = new BlockPos(7,80,20);
    private static final BlockPos SPARK_LOAD = new BlockPos(20,80,20), SPARK_POOL = new BlockPos(28,80,20);
    private static final BlockPos NATIVE_SPREADER = new BlockPos(60,80,20), NATIVE_POOL = new BlockPos(67,80,20);
    private static final BlockPos AMBIENT_STAR = new BlockPos(176,80,20), COVERED_STAR = new BlockPos(184,80,20);
    private static final BlockPos AUTO_RF = new BlockPos(176,80,40), SMALL_AUTO_RF = new BlockPos(184,80,40), LOW_BUFFER_STAR = new BlockPos(192,80,40);
    private static final BlockPos CAPPED_STAR = new BlockPos(196,80,40), CHARGED_STAR = new BlockPos(200,80,40);
    private static final BlockPos PARTIAL_LOAD = new BlockPos(87,80,20), FULL_LOAD = new BlockPos(87,80,28), AE_CONTROLLER = new BlockPos(100,80,20);
    private static final BlockPos CLOSED_FACE_LOAD = new BlockPos(87,80,36);
    private static final BlockPos MANA_REFUSAL = new BlockPos(60,80,60);
    private int refusalPoolMana;
    private final double[] boxBeforeMana = new double[4];
    private final int[] boxPortMasks = new int[4];
    private final FluxValidation fluxValidation = new FluxValidation();
    private int fluxStage;
    private long fluxTick;
    private int discoveryStage;
    private long discoveryTick;
    private static final BlockPos IDLE_PROBE = new BlockPos(120,80,60), PORT_PROBE = new BlockPos(140,80,60);
    private static final BlockPos RELOAD_PROBE = new BlockPos(512,80,512);
    private static final BlockPos ENGINE_PROBE = new BlockPos(160,80,60);
    private EnumFacing firstEngineFace;
    private long previousWorldEnd = Long.MIN_VALUE;
    private int unchangedBoundaryPairs;
    private TileEntity previousPort, previousReload;
    private IEnergyTile previousProxy;
    // Available EU/t, integer voltage, current, actual whole EU output.
    private static final int[][] RF_OUTPUT_CASES = {
        {1,1,1,1}, {31,31,1,31}, {32,32,1,32},
        {8191,8191,1,8191}, {8192,8192,1,8192}, {8193,8192,1,8192},
        {8194,8192,1,8192}, {32768,8192,1,8192}, {40000,8192,1,8192}
    };
    private volatile Throwable failure;
    private volatile int phase;
    private boolean started, busy, ready, equalityWindowStarted;
    private static final Block RF_BLOCK = new FixtureBlock("rf", RfTile::new);
    private static final Block EU_BLOCK = new FixtureBlock("eu", EuTile::new);
    private static final Block MJ_BLOCK = new FixtureBlock("mj", MjTile::new);
    private static final Block IDLE_BLOCK = new FixtureBlock("idle", IdleTile::new);
    private int ticks;
    private double nativeBefore;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerTileEntity(RfTile.class, new ResourceLocation("zhenghuocore_validation", "rf"));
        GameRegistry.registerTileEntity(EuTile.class, new ResourceLocation("zhenghuocore_validation", "eu"));
        GameRegistry.registerTileEntity(MjTile.class, new ResourceLocation("zhenghuocore_validation", "mj"));
        GameRegistry.registerTileEntity(IdleTile.class, new ResourceLocation("zhenghuocore_validation", "idle"));
        if (Boolean.getBoolean("zhenghuocore.validation") || Boolean.getBoolean("zhenghuocore.audit")) MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void blocks(RegistryEvent.Register<Block> event) { event.getRegistry().registerAll(RF_BLOCK, EU_BLOCK, MJ_BLOCK, IDLE_BLOCK); }
    @Mod.EventHandler public void loaded(FMLPostInitializationEvent event) {
        ready = Boolean.getBoolean("zhenghuocore.validation") || Boolean.getBoolean("zhenghuocore.audit");
    }
    private static void require(boolean value, String description) { if (!value) throw new AssertionError(description); }
    private static void pass(String text) { System.out.println("ZHENGHUO_VALIDATION " + text + " PASSED"); }
    private static void requireTileThread(TileEntity tile) {
        if (tile.getWorld() instanceof WorldServer)
            require(((WorldServer)tile.getWorld()).isCallingFromMinecraftThread(),"Energy endpoint accessed outside the server thread: "+Thread.currentThread().getName());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void worldBoundary(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || event.world.provider.getDimension()!=0 || phase<1 || phase>=4) return;
        long time=event.world.getTotalWorldTime();
        if (event.phase==TickEvent.Phase.END) previousWorldEnd=time;
        else if (previousWorldEnd!=Long.MIN_VALUE) {
            if (time!=previousWorldEnd) failure=new AssertionError("World counter changed between END and next START: "+previousWorldEnd+" -> "+time);
            else unchangedBoundaryPairs++;
        }
    }
    private WorldServer world() { return Minecraft.getMinecraft().getIntegratedServer().getWorld(0); }
    private EntityPlayerMP player() { return Minecraft.getMinecraft().getIntegratedServer().getPlayerList().getPlayers().get(0); }
    private void task(Runnable runnable) {
        busy = true;
        Minecraft.getMinecraft().getIntegratedServer().addScheduledTask(() -> {
            try { runnable.run(); } catch (Throwable error) { error.printStackTrace(); failure = error; }
            finally { busy = false; }
        });
    }
    private static <T extends TileEntity> T fixture(WorldServer world, BlockPos pos, T tile) {
        world.setBlockToAir(pos);
        world.setBlockState(pos, (tile instanceof EuTile ? EU_BLOCK : tile instanceof MjTile ? MJ_BLOCK : RF_BLOCK).getDefaultState());
        world.setTileEntity(pos, tile); return tile;
    }
    private TileCreativeGenerator source(BlockPos pos) {
        WorldServer w = world(); w.setBlockToAir(pos); w.setBlockState(pos, Ic2Grid.GENERATOR.getDefaultState());
        w.setBlockState(pos.down(), Blocks.REDSTONE_BLOCK.getDefaultState());
        TileCreativeGenerator tile = (TileCreativeGenerator) w.getTileEntity(pos); tile.setSettings(32,1); tile.refreshRedstone(); return tile;
    }
    private static TileEntity place(WorldServer world, BlockPos pos, ItemStack stack) {
        world.setBlockToAir(pos); world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        EntityPlayerMP player = FakePlayerFactory.getMinecraft(world); player.interactionManager.setGameType(GameType.CREATIVE);
        player.setHeldItem(EnumHand.MAIN_HAND, stack.copy());
        stack.onItemUse(player, world, pos.down(), EnumHand.MAIN_HAND, EnumFacing.UP, .5F, 1F, .5F);
        TileEntity tile = world.getTileEntity(pos); require(tile != null, "Native placement " + stack); return tile;
    }
    // Historical 0.1.2 artifact reproduction only. Default validation below tests fixed behavior.
    private static final String[] AUDIT_BOXES = {"batbox", "cesu", "mfe", "mfsu"};
    private static final BlockPos AUDIT_FLUX = new BlockPos(60,80,10);
    private static void reproduced(String text) { System.out.println("ZHENGHUO_AUDIT REPRODUCED (existing defect, not a fix) " + text); }
    private void auditSetup() {
        WorldServer w = world();
        for (int x = -1; x <= 4; x++) for (int z = -1; z <= 1; z++) w.getChunk(x,z);
        player().capabilities.isFlying = true; player().sendPlayerAbilities(); player().setPositionAndUpdate(20.5,83,-5);
        fixture(w, SOURCE, new RfTile(1000,1000,true,false));
        fixture(w, SMALL, new RfTile(1000,1000,true,false));
        fixture(w, MJ, new RfTile(1000,1000,true,false));
        w.setBlockToAir(POOL); w.setBlockState(POOL,ModBlocks.pool.getDefaultState());
        for (int i=0;i<AUDIT_BOXES.length;i++) place(w,new BlockPos(i*4,80,10),IC2Items.getItem("te",AUDIT_BOXES[i]));
        Block flux=Block.REGISTRY.getObject(new ResourceLocation("fluxnetworks","fluxplug"));
        require(flux!=null && flux!=Blocks.AIR,"Actual Flux Networks Plug available for audit");
        w.setBlockToAir(AUDIT_FLUX.west()); w.setBlockToAir(AUDIT_FLUX); w.setBlockState(AUDIT_FLUX,flux.getDefaultState());
        ticks=0; phase=1;
        System.out.println("ZHENGHUO_AUDIT SETUP existing 0.1.2 behavior; awaiting native first ticks");
    }
    private void auditChecks() {
        WorldServer w=world();
        require(CoreConfig.rfPerMana==10 && CoreConfig.rfPerMj==10 && CoreConfig.rfPerEu==4,
                "Audit expects shipped default conversion ratios");
        RfTile full=(RfTile)w.getTileEntity(SOURCE);
        TileCompatibility state=full.getCapability(TileCompatibility.CAPABILITY,null);
        IManaReceiver receiver=ManaReceiverHooks.receiver(full);
        require(full.receiveEnergy(2000,true)==0 && receiver!=null && !receiver.isFull(),
                "Reproduction: native RF full but Mana adapter advertises space");
        receiver.recieveMana(200);
        int pending=state.serializeNBT().getInteger("ManaRfPending");
        require(full.energy==1000 && pending==2000,
                "Reproduction: full RF accepts 200 Mana into an extra 2000 RF buffer");
        reproduced("full RF: native=1000/1000, added Mana=200, extra ManaRfPending="+pending);

        TileEntity pool=w.getTileEntity(POOL);
        IManaReceiver nativeMana=(IManaReceiver)pool;
        nativeMana.recieveMana(Integer.MAX_VALUE);
        int fullMana=nativeMana.getCurrentMana();
        require(fullMana>0 && nativeMana.isFull(),"Native pool capacity initialized and filled");
        IEnergyStorage poolRf=pool.getCapability(CapabilityEnergy.ENERGY,EnumFacing.UP);
        require(poolRf!=null,"Native pool has compatibility FE port");
        NBTTagCompound before=pool.writeToNBT(new NBTTagCompound());
        int quote=poolRf.receiveEnergy(100,true);
        require(before.equals(pool.writeToNBT(new NBTTagCompound())),"Audit simulation does not change pool NBT");
        int accepted=poolRf.receiveEnergy(100,false);
        long carry=pool.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getLong("NativeCarry");
        require(quote==9 && accepted==9 && carry==9000000 && nativeMana.getCurrentMana()==fullMana,
                "Reproduction: full native pool still accepts 9 RF into NativeCarry");
        reproduced("full native Mana pool: simulated="+quote+" RF, accepted="+accepted+" RF, NativeCarry="+carry);

        for(int i=0;i<AUDIT_BOXES.length;i++) {
            TileEntity box=w.getTileEntity(new BlockPos(i*4,80,10));
            require(box.getClass().getName().startsWith("ic2.core.block.wiring.TileEntityElectric"),"Actual IC storage tile "+AUDIT_BOXES[i]);
            require(EnergyNet.instance.getTile(w,box.getPos())!=null,"IC storage registered its native EU delegate");
            boolean recognized=ManaReceiverHooks.isReceiver(box);
            require(!recognized && ManaReceiverHooks.receiver(box)==null,
                    "Reproduction: actual IC storage is absent from spreader's Mana target resolver "+AUDIT_BOXES[i]);
            reproduced("IC "+AUDIT_BOXES[i]+": native EU delegate registered, Mana target recognized="+recognized);
        }

        RfTile mjFull=(RfTile)w.getTileEntity(MJ);
        IMjReceiver mj=mjFull.getCapability(MjAPI.CAP_RECEIVER,EnumFacing.UP);
        require(mj!=null && mjFull.receiveEnergy(1,true)==0,"Full RF exposes MJ compatibility input");
        long requested=mj.getPowerRequested();
        long returned=mj.receivePower(requested,false);
        long mjCarry=mjFull.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getLong("MjCarry");
        require(requested==99999 && returned==0 && mjCarry==999990 && mjFull.energy==1000,
                "Reproduction: full RF requests and accepts micro-MJ into MjCarry");
        reproduced("full RF MJ input: requested="+requested+" micro-MJ, returned="+returned+", MjCarry="+mjCarry);

        RfTile euFull=(RfTile)w.getTileEntity(SMALL);
        TileCompatibility euState=euFull.getCapability(TileCompatibility.CAPABILITY,null);
        RfGridNode node=new RfGridNode(euFull,euState);
        require(node.getDemandedEnergy()==0,"Full RF correctly reports no EU demand before explicit injection");
        double euReturned=node.injectEnergy(EnumFacing.UP,1,1);
        long euCarry=euState.serializeNBT().getLong("EuCarry");
        require(euFull.energy==1000 && euReturned<1 && euCarry==999999,
                "Reproduction: explicit EU injection into full RF still charges EuCarry");
        reproduced("full RF explicit EU injection: offered=1 EU, returned="+euReturned+", EuCarry="+euCarry
                +"; API-level rejection test, not a claim that grid schedules a zero-demand sink");
        TileEntity flux=w.getTileEntity(AUDIT_FLUX);
        require(flux instanceof IEnergySink,"Flux Plug exposes native EU sink");
        IEnergySink sink=(IEnergySink)flux;
        IEnergyStorage fluxRf=flux.getCapability(CapabilityEnergy.ENERGY,EnumFacing.WEST);
        double demand=sink.getDemandedEnergy(), refused=sink.injectEnergy(EnumFacing.WEST,32,1);
        int rfQuote=fluxRf==null ? -1 : fluxRf.receiveEnergy(128,true);
        require(demand==Integer.MAX_VALUE && refused==32 && rfQuote==0 && auditFluxBuffer(flux)==0,
                "Reproduction: unnetworked real Flux Plug advertises EU demand but refuses EU and FE intake");
        reproduced("unnetworked Flux Plug: demand="+demand+" EU, refused="+refused+" EU, FE quote="+rfQuote
                +" RF; not the complete connected-network screenshot scenario");
        fixture(w,AUDIT_FLUX.west(),new RfTile(100000,100000,false,true).limitExtraction(128));
        ticks=0; phase=2;
    }
    private long auditFluxBuffer(TileEntity flux) {
        IEnergyStorage port=flux.getCapability(CapabilityEnergy.ENERGY,null);
        require(port!=null,"Actual Flux FE capability reads its transfer buffer");
        return port.getEnergyStored();
    }
    private void auditFluxCheck() {
        WorldServer w=world(); TileEntity flux=w.getTileEntity(AUDIT_FLUX);
        int spent=100000-((RfTile)w.getTileEntity(AUDIT_FLUX.west())).energy;
        long buffer=auditFluxBuffer(flux);
        System.out.println("ZHENGHUO_AUDIT OBSERVED unnetworked Flux: source RF spent="+spent+", transfer buffer="+buffer);
        require(EnergyNet.instance.getTile(w,AUDIT_FLUX)==flux,"Flux retains native EU grid endpoint");
        require(spent>0 && buffer==0,"Reproduction: RF source debited despite unnetworked Flux rejecting intake");
        reproduced("unnetworked Flux grid transfer: source lost="+spent+" RF, target buffer="+buffer
                +"; isolated rejection-path reproduction, not the complete connected-network screenshot scenario");
        phase=4; System.out.println("ZHENGHUO_AUDIT COMPLETE current defects reproduced; no product fix tested");
    }
    private void setup() {
        WorldServer w = world(); w.setWorldTime(18000); w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        player().capabilities.isFlying = true; player().sendPlayerAbilities(); player().setPositionAndUpdate(60.5,83, -5);
        // Load every fixture chunk before installation.
        for (int x = -1; x <= 12; x++) for (int z = -1; z <= 3; z++) w.getChunk(x,z);
        TileEntity refusalSource=place(w,MANA_REFUSAL,IC2Items.getItem("te","mfe"));
        try { refusalSource.getClass().getMethod("setFacing",EnumFacing.class).invoke(refusalSource,EnumFacing.EAST); }
        catch(ReflectiveOperationException error) { throw new AssertionError("Orient real refusal-test MFE",error); }
        place(w,MANA_REFUSAL.east(),IC2Items.getItem("cable","type:glass,insulation:0"));
        w.setBlockToAir(MANA_REFUSAL.east(2)); w.setBlockState(MANA_REFUSAL.east(2),ModBlocks.pool.getDefaultState());
        source(SOURCE);
        EuTile equalEuLoad = new EuTile(); equalEuLoad.demandLimit=10000;
        fixture(w, SOURCE.west(), equalEuLoad); fixture(w, SOURCE.east(), new RfTile(40000,0,true,false));
        fixture(w, RF_SOURCE, new RfTile(100000,100000,false,true).limitExtraction(128)); place(w, RF_SOURCE.east(), IC2Items.getItem("te", "macerator"));
        source(SMALL); fixture(w, SMALL.east(), new RfTile(4,0,true,false));
        w.setBlockToAir(POOL); w.setBlockState(POOL, ModBlocks.pool.getDefaultState());
        w.setBlockToAir(SPREADER); w.setBlockState(SPREADER, ModBlocks.spreader.getDefaultState());
        fixture(w, MJ, new MjTile());
        w.setBlockToAir(ENGINE); w.setBlockState(ENGINE, BCCoreBlocks.engine.getDefaultState().withProperty(BCCoreBlocks.engine.getEngineProperty(), EnumEngineType.STONE));
        w.setBlockState(ENGINE.down(), Blocks.REDSTONE_BLOCK.getDefaultState());
        w.setBlockToAir(WELL); w.setBlockState(WELL, BCFactoryBlocks.miningWell.getDefaultState());
        w.setBlockToAir(STAR); w.setBlockState(STAR, ((Block) ZhenghuoCore.MACHINES.get(0)).getDefaultState()); fixture(w, STAR.east(), new EuTile());
        w.setBlockState(STAR.up(),Blocks.STONE.getDefaultState());
        place(w, STAR.west(), IC2Items.getItem("te", "macerator"));
        w.setBlockToAir(CRYSTAL); w.setBlockState(CRYSTAL, BlocksAS.collectorCrystal.getDefaultState());
        TileCollectorCrystal crystal = (TileCollectorCrystal) w.getTileEntity(CRYSTAL);
        crystal.onPlace(Constellations.discidia, null, CrystalProperties.getMaxRockProperties(), UUID.randomUUID(), CollectorCrystalType.ROCK_CRYSTAL);
        source(LOSS); place(w, LOSS.east(), IC2Items.getItem("cable", "type:copper,insulation:1")); fixture(w, LOSS.east(2), new RfTile(40000,0,true,false));
        source(OVER).setSettings(8193,1); fixture(w, OVER.east(), new RfTile(40000,0,true,false));
        for (int i = 0; i < RF_OUTPUT_CASES.length; i++) {
            BlockPos at = HIGH_RF.add(i * 4,0,0);
            int[] rule = RF_OUTPUT_CASES[i];
            fixture(w, at, new RfTile(HIGH_RF_INITIAL,HIGH_RF_INITIAL,false,true).limitExtraction(rule[0] * 4 + 3));
            EuTile load = new EuTile(); load.capacity=100000000; load.demandLimit=100000; load.tier=Integer.MAX_VALUE;
            fixture(w, at.east(), load);
        }
        Block battery = Block.REGISTRY.getObject(new ResourceLocation("cyclicmagic", "battery_infinite"));
        require(battery != null && battery != Blocks.AIR, "Real installed Cyclic infinite battery available");
        w.setBlockToAir(INFINITE_BATTERY); w.setBlockState(INFINITE_BATTERY, battery.getDefaultState());
        place(w, INFINITE_BATTERY.east(), IC2Items.getItem("te", "macerator"));
        setupRemoteMana(w);
        for (int i=0;i<AUDIT_BOXES.length;i++) {
            BlockPos box=storagePos(i);
            place(w,box,IC2Items.getItem("te",AUDIT_BOXES[i]));
            w.setBlockToAir(box.west(7)); w.setBlockState(box.west(7),ModBlocks.spreader.getDefaultState());
        }
        Block controller=Block.REGISTRY.getObject(new ResourceLocation("appliedenergistics2","controller"));
        require(controller!=null && controller!=Blocks.AIR,"Real installed AE2 controller available");
        w.setBlockToAir(AE_CONTROLLER); w.setBlockState(AE_CONTROLLER,controller.getDefaultState());
        for (BlockPos pos : new BlockPos[]{AMBIENT_STAR, COVERED_STAR}) {
            w.setBlockToAir(pos); w.setBlockState(pos, ((Block) ZhenghuoCore.MACHINES.get(0)).getDefaultState());
        }
        w.setBlockToAir(AMBIENT_STAR.up()); w.setBlockState(COVERED_STAR.up(),Blocks.STONE.getDefaultState());
        place(w,AMBIENT_STAR.east(),IC2Items.getItem("te","macerator"));
        fixture(w,AUTO_RF,new RfTile(10000000,10000000,false,true).limitExtraction(8194*4));
        EuTile automaticLoad = new EuTile(); automaticLoad.capacity=1000000000; fixture(w,AUTO_RF.east(),automaticLoad);
        fixture(w,SMALL_AUTO_RF,new RfTile(10000000,10000000,false,true).limitExtraction(8194*4));
        EuTile tiny = new EuTile(); tiny.demandLimit=1; fixture(w,SMALL_AUTO_RF.east(),tiny);
        w.setBlockToAir(LOW_BUFFER_STAR); w.setBlockState(LOW_BUFFER_STAR,((Block) ZhenghuoCore.MACHINES.get(0)).getDefaultState());
        w.setBlockState(LOW_BUFFER_STAR.up(),Blocks.STONE.getDefaultState());
        place(w,LOW_BUFFER_STAR.east(),IC2Items.getItem("te","macerator"));
        ((TileStarlightGenerator)w.getTileEntity(LOW_BUFFER_STAR)).receiveStarlight(5.0/CoreConfig.euPerStarlight);
        for (BlockPos pos : new BlockPos[]{CAPPED_STAR, CHARGED_STAR}) {
            w.setBlockToAir(pos); w.setBlockState(pos,((Block) ZhenghuoCore.MACHINES.get(0)).getDefaultState());
            w.setBlockState(pos.up(),Blocks.STONE.getDefaultState());
        }
        TileStarlightGenerator capped = (TileStarlightGenerator)w.getTileEntity(CAPPED_STAR);
        capped.receiveStarlight(1000); capped.receiveStarlight(1000);
        require(capped.getStoredEu() == 16000, "Unified starlight conversion retains 8 EU per unit with no 128 EU/t generation cap");
        TileEntity charged = w.getTileEntity(CHARGED_STAR);
        NBTTagCompound chargedTag = charged.writeToNBT(new NBTTagCompound());
        chargedTag.setLong("MicroRf",32768L * CoreConfig.rfPerEu * dev.mcmod.zhenghuocore.energy.EnergyBuffer.MICRO);
        charged.readFromNBT(chargedTag);
        EuTile highVoltageLoad = new EuTile(); highVoltageLoad.tier=Integer.MAX_VALUE; highVoltageLoad.capacity=1000000000; highVoltageLoad.demandLimit=100000;
        fixture(w,CHARGED_STAR.east(),highVoltageLoad);
        try { fluxValidation.setup(w,new BlockPos(110,80,20)); }
        catch(Exception error) { throw new AssertionError("Real Flux fixture setup",error); }
        fluxStage=0; fluxTick=w.getTotalWorldTime();
        ticks = 0; phase = 1; pass("fixture setup");
    }
    private void directChecks() {
        WorldServer w = world();
        require(CoreConfig.rfPerMj==40 && CoreConfig.rfPerEu==4 && CoreConfig.rfPerMana==10,
                "Fixed conversion: 1 MJ = 40 RF = 10 EU = 4 Mana");
        probeChecks(w);
        // A new native pool initializes its capacity on its first tick, not in its constructor.
        IManaReceiver sparkSource = (IManaReceiver) w.getTileEntity(SPARK_POOL);
        sparkSource.recieveMana(4000);
        require(sparkSource.getCurrentMana() == 4000, "Native spark pool was filled after initialization");
        RfTile adaptive = (RfTile) w.getTileEntity(AUTO_RF);
        EuTile adaptiveLoad = (EuTile) w.getTileEntity(AUTO_RF.east());
        ElectricalNodeStats adaptiveStats = Ic2Grid.GRID.getNodeStats(EnergyNet.instance.getTile(w,AUTO_RF));
        require(adaptiveStats.getActualVoltage() == 32 && adaptiveStats.getCurrent() == 1 && adaptiveStats.getEnergyOut() == 32,
                "EU grid lowers configured RF ceiling IV / 1 A to safe 32 V / 1 A");
        require(10000000 - adaptive.energy == adaptiveLoad.energy * CoreConfig.rfPerEu,
                "Only actually dispatched EU debits RF during automatic voltage matching");
        require(((EuTile)w.getTileEntity(SMALL_AUTO_RF.east())).energy > 0
                && 10000000-((RfTile)w.getTileEntity(SMALL_AUTO_RF)).energy == ((EuTile)w.getTileEntity(SMALL_AUTO_RF.east())).energy*4,
                "Default 1 A source supplies a 1 EU demand with exact RF debit");
        require(w.getTileEntity(LOW_BUFFER_STAR.east()).writeToNBT(new NBTTagCompound()).getCompoundTag("components")
                .getCompoundTag("energy").getDouble("storage") == 4, "Star with only 5 EU emits 1 V / 4 A without splitting current");
        require(((TileStarlightGenerator)w.getTileEntity(LOW_BUFFER_STAR)).getStoredEu() == 1,
                "Star retains the 1 EU remainder below its whole 4 A output");
        require(CoreConfig.starlightVoltage == 8192 && CoreConfig.starlightCurrent == 4 && CoreConfig.euPerStarlight == 8,
                "Star output ceiling is 8192 V / 4 A with unchanged generation efficiency");
        require(((EuTile)w.getTileEntity(CHARGED_STAR.east())).energy == 32768
                && ((EuTile)w.getTileEntity(CHARGED_STAR.east())).largestPacket == 32768,
                "Charged star actually dispatches 8192 V x 4 A, totaling 32768 EU; received="
                + ((EuTile)w.getTileEntity(CHARGED_STAR.east())).energy + ", largest packet="
                + ((EuTile)w.getTileEntity(CHARGED_STAR.east())).largestPacket
                + ", remaining=" + ((TileStarlightGenerator)w.getTileEntity(CHARGED_STAR)).getStoredEu());
        pass("grid-owned RF voltage matching, integer whole current, exact debit and low-buffer starlight output");
        EuTile eu = (EuTile) w.getTileEntity(SOURCE.west()); RfTile rf = (RfTile) w.getTileEntity(SOURCE.east());
        require(eu.energy > 0 && rf.energy > 0, "EU and RF share one source");
        require(Math.abs(eu.energy - rf.energy / 4.0) < 0.3, "Same dispatch priority: " + eu.energy + " EU vs " + rf.energy + " RF");
        require(Ic2Grid.GRID.getNodeStats((IEnergyTile) w.getTileEntity(SOURCE)).getEnergyOut() == 32, "Generator remains exactly 32 EU/t");
        require(((RfTile) w.getTileEntity(SMALL.east())).energy == 0, "Insufficient RF demand does not split a whole EU batch");
        NBTTagCompound consumer = w.getTileEntity(RF_SOURCE.east()).writeToNBT(new NBTTagCompound());
        require(consumer.getCompoundTag("components").getCompoundTag("energy").getDouble("storage") > 0, "RF source powers a native IC2 machine through new EU grid");
        require(((RfTile) w.getTileEntity(RF_SOURCE)).energy < 100000, "RF source was debited");
        double receivedAfterLoss = Ic2Grid.GRID.getNodeStats(EnergyNet.instance.getTile(w, LOSS.east(2))).getEnergyIn();
        require(receivedAfterLoss > 0 && receivedAfterLoss < 32, "RF receives real cable loss: " + receivedAfterLoss);
        require(w.getTileEntity(OVER.east()) == null, "RF endpoint follows actual EU input overvoltage rule");
        pass("unified EU/RF dispatch, equal treatment, whole batches and native IC2 receiver");
        pass("native copper cable loss and RF endpoint overvoltage");
        for (int i = 0; i < RF_OUTPUT_CASES.length; i++) {
            BlockPos at = HIGH_RF.add(i * 4,0,0); int[] rule = RF_OUTPUT_CASES[i];
            ElectricalNodeStats stats = Ic2Grid.GRID.getNodeStats(EnergyNet.instance.getTile(w, at));
            require(stats != null && stats.getEnergyOut() == rule[3], "RF whole output for " + rule[0]
                    + ": actual=" + (stats == null ? null : stats.getEnergyOut()) + ", RF=" + ((RfTile)w.getTileEntity(at)).energy);
            require(stats.getActualVoltage() == rule[1] && stats.getCurrent() == rule[2],
                    "RF integer U/I for " + rule[0] + ": " + stats.getActualVoltage() + " V / " + stats.getCurrent() + " A");
            RfTile supply = (RfTile) w.getTileEntity(at); EuTile load = (EuTile) w.getTileEntity(at.east());
            require((long) HIGH_RF_INITIAL - supply.energy == (long) load.energy * CoreConfig.rfPerEu, "RF debit exactly matches whole EU output");
            int beforeRf = supply.energy;
            supply.getCapability(CapabilityEnergy.ENERGY, EnumFacing.EAST).extractEnergy(Integer.MAX_VALUE,true);
            require(beforeRf == supply.energy, "RF output simulation does not spend rounding remainders");
            System.out.println("ZHENGHUO_VALIDATION RF " + rule[0] + " EU/t -> " + rule[1] + " V x " + rule[2] + " A; debit=" + (rule[3]*4) + " RF/t");
        }
        pass("RF configured IV / 1 A ceiling, low-power integer quotes and exact RF debit");

        TileEntity pool = w.getTileEntity(POOL); IEnergyStorage manaRf = pool.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
        require(manaRf != null, "Native mana pool exposes RF");
        NBTTagCompound before = pool.writeToNBT(new NBTTagCompound()); require(manaRf.receiveEnergy(10,true) == 10, "Mana RF input simulation");
        require(before.equals(pool.writeToNBT(new NBTTagCompound())), "Mana simulation has no NBT mutation");
        require(manaRf.receiveEnergy(9,false)==0 && ((IManaReceiver)pool).getCurrentMana()==0,"Sub-Mana RF remains at sender");
        require(manaRf.receiveEnergy(19,false)==10 && ((IManaReceiver)pool).getCurrentMana()==1,"Only complete Mana is accepted");
        require(manaRf.extractEnergy(9,false)==0 && ((IManaReceiver)pool).getCurrentMana()==1,"Sub-Mana extraction does not debit source");
        require(manaRf.extractEnergy(19,false)==10,"Whole Mana extracts exactly 10 RF");
        require(((IManaReceiver)pool).getCurrentMana() == 0, "Native Mana debited");
        require(manaRf.receiveEnergy(100000,false)==100000 && ((IManaReceiver)pool).getCurrentMana()==10000,
                "Native Mana capacity permits input above the retired 4096 RF budget");
        require(manaRf.extractEnergy(100000,false)==100000 && ((IManaReceiver)pool).getCurrentMana()==0,
                "Native Mana output has no compatibility throughput budget");
        IManaReceiver nativePool=(IManaReceiver)pool; nativePool.recieveMana(Integer.MAX_VALUE);
        require(nativePool.isFull() && manaRf.receiveEnergy(100,true)==0 && manaRf.receiveEnergy(100,false)==0,"Full native pool rejects RF, including old 9 RF remainder case");
        nativePool.recieveMana(-nativePool.getCurrentMana());
        require(pool.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getKeySet().isEmpty(),"Compatibility adds no stored energy NBT");
        TileEntity spreader = w.getTileEntity(SPREADER); IEnergyStorage spreaderRf = spreader.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
        require(spreaderRf != null && spreaderRf.receiveEnergy(10,false) == 10 && ((IManaReceiver)spreader).getCurrentMana() == 1, "Native spreader accepts RF");
        require(spreaderRf.extractEnergy(10,false) == 10 && ((IManaReceiver)spreader).getCurrentMana() == 0, "Native spreader outputs RF");
        pass("actual Botania pool whole-unit transfers, full rejection, simulation and no hidden balance");

        MjTile mj = (MjTile) w.getTileEntity(MJ); IEnergyStorage mjRf = mj.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
        require(mjRf != null && mjRf.receiveEnergy(40,false) == 40 && mj.energy == MjAPI.MJ, "40 RF -> 1 native MJ");
        require(mjRf.extractEnergy(40,true) == 40 && mj.energy == MjAPI.MJ, "MJ simulation");
        require(mjRf.extractEnergy(40,false) == 40 && mj.energy == 0, "1 native MJ -> 40 RF");
        IMjReceiver rfAsMj = rf.getCapability(MjAPI.CAP_RECEIVER, EnumFacing.UP);
        int rfBefore = rf.energy; require(rfAsMj != null && rfAsMj.receivePower(MjAPI.MJ,false) == 0 && rf.energy == rfBefore+40, "RF tile accepts native MJ push at fixed ratio");
        IMjReceiver poolMj=pool.getCapability(MjAPI.CAP_RECEIVER,EnumFacing.UP);
        IMjPassiveProvider poolMjSource=pool.getCapability(MjAPI.CAP_PASSIVE_PROVIDER,EnumFacing.UP);
        require(poolMj!=null && poolMjSource!=null,"Real Mana pool exposes MJ through its unified FE port");
        require(poolMj.receivePower(MjAPI.MJ,true)==0 && nativePool.getCurrentMana()==0,"MJ-to-Mana simulation is read-only");
        require(poolMj.receivePower(MjAPI.MJ,false)==0 && nativePool.getCurrentMana()==4,"1 MJ becomes 4 Mana through FE");
        require(poolMjSource.extractPower(MjAPI.MJ,MjAPI.MJ,true)==MjAPI.MJ && nativePool.getCurrentMana()==4,"Mana-to-MJ simulation is read-only");
        require(poolMjSource.extractPower(MjAPI.MJ,MjAPI.MJ,false)==MjAPI.MJ && nativePool.getCurrentMana()==0,"4 Mana returns 1 MJ through FE");
        TileEntity well = w.getTileEntity(WELL); IEnergyStorage wellRf = well.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
        require(wellRf != null && wellRf.receiveEnergy(100,false) > 0, "Actual BC mining well accepts RF directly");
        TileEngineBase_BC8 engine = (TileEngineBase_BC8) w.getTileEntity(ENGINE); engine.addPower(10*MjAPI.MJ); engine.isRedstonePowered = true;
        IEnergyStorage engineRf = engine.getCapability(CapabilityEnergy.ENERGY, engine.getCurrentFacing());
        require(engineRf != null, "Actual BC engine output face exposes RF");
        long engineBefore = engine.getEnergyStored(); int taken = engineRf.extractEnergy(10,false);
        require(taken > 0 && engine.getEnergyStored() < engineBefore, "Actual BC engine RF extraction debits MJ");
        pass("MJ both directions, native RF endpoint, actual BC mining well and engine");
        boundaryChecks(w);
        storageManaChecks(w);
        TileEntity refusalSource=w.getTileEntity(MANA_REFUSAL);
        IManaReceiver refusalPool=(IManaReceiver)w.getTileEntity(MANA_REFUSAL.east(2));
        refusalPool.recieveMana(Integer.MAX_VALUE-refusalPool.getCurrentMana());
        require(refusalPool.isFull() && refusalPool.getCurrentMana()>1000,"Refusal pool completed native capacity initialization");
        refusalPool.recieveMana(-1); refusalPoolMana=refusalPool.getCurrentMana();
        IEnergyTile refusalSink=EnergyNet.instance.getTile(w,MANA_REFUSAL.east(2));
        require(refusalSink instanceof RfGridNode && ((IEnergySink)refusalSink).getDemandedEnergy()==2.5,
                "One remaining native Mana is exactly 2.5 EU demand through real FE adapter");
        ((ic2.api.tile.IEnergyStorage)refusalSource).addEnergy(1000);
        require(storedEu(refusalSource)==1000,"Start physical full-refusal regression with 1000 real EU");
        IEnergyTile nativeSource=EnergyNet.instance.getTile(w,MANA_REFUSAL);
        IEnergyTile nativeWire=EnergyNet.instance.getTile(w,MANA_REFUSAL.east());
        require(nativeSource instanceof IEnergySource && ((IEnergySource)nativeSource).getOfferedEnergy()>0
                && nativeWire instanceof IEnergyConductor,"Real MFE has available output and native glass conductor is registered");
        require(((IEnergyEmitter)nativeSource).emitsEnergyTo((IEnergyAcceptor)nativeWire,EnumFacing.EAST)
                && ((IEnergyAcceptor)nativeWire).acceptsEnergyFrom((IEnergyEmitter)nativeSource,EnumFacing.WEST)
                && ((IEnergyEmitter)nativeWire).emitsEnergyTo((IEnergyAcceptor)refusalSink,EnumFacing.EAST)
                && ((IEnergyAcceptor)refusalSink).acceptsEnergyFrom((IEnergyEmitter)nativeWire,EnumFacing.WEST),
                "MFE, glass cable and Mana FE endpoint have valid physical-side adjacency");

        TileStarlightGenerator star = (TileStarlightGenerator) w.getTileEntity(STAR);
        TileStarlightGenerator cached = (TileStarlightGenerator) w.getTileEntity(CAPPED_STAR);
        int savedRatio=CoreConfig.rfPerEu, savedVoltage=CoreConfig.starlightVoltage, savedCurrent=CoreConfig.starlightCurrent;
        double savedEfficiency=CoreConfig.euPerStarlight;
        try {
            CoreConfig.rfPerEu=4; CoreConfig.euPerStarlight=8;
            double cachedEu=cached.getStoredEu(), capacityEu=cached.getCapacityEu();
            NBTTagCompound oldCache=((TileEntity)cached).writeToNBT(new NBTTagCompound());
            require(cachedEu>0,"Existing star fixture has energy to persist before ratio change");
            CoreConfig.rfPerEu=8;
            TileEntity loadedCache=TileEntity.create(w,oldCache);
            require(loadedCache instanceof TileStarlightGenerator,"Actual star tile restores existing NBT");
            // create() only calls the default no-op setWorldCreate(); placement normally supplies the world later.
            loadedCache.setWorld(w);
            TileStarlightGenerator restoredStar=(TileStarlightGenerator)loadedCache;
            require(restoredStar.getStoredEu()==cachedEu && restoredStar.getCapacityEu()==capacityEu
                    && cached.getStoredEu()==cachedEu && cached.getCapacityEu()==capacityEu,
                    "FE/EU ratio 4 to 8 preserves old persisted and loaded star EU energy and capacity");
            require(loadedCache.getWorld()==w && !w.isRemote && !loadedCache.isInvalid()
                    && restoredStar.getCapacityEu()-restoredStar.getStoredEu()>=24,
                    "Restored generation fixture must be live and have room: stored="+restoredStar.getStoredEu()
                    +", capacity="+restoredStar.getCapacityEu()+", world="+(loadedCache.getWorld()==w)+", invalid="+loadedCache.isInvalid());
            double beforeGeneration=restoredStar.getStoredEu(); restoredStar.receiveStarlight(1);
            double atEight=restoredStar.getStoredEu()-beforeGeneration;
            CoreConfig.euPerStarlight=16;
            beforeGeneration=restoredStar.getStoredEu(); restoredStar.receiveStarlight(1);
            double atSixteen=restoredStar.getStoredEu()-beforeGeneration;
            require(atEight==8 && atSixteen==16 && atSixteen==2*atEight,
                    "Real star receipt doubles generation for multiplier 8 to 16 with equal input: delta8="+atEight
                    +", delta16="+atSixteen+", stored="+restoredStar.getStoredEu()+", capacity="+restoredStar.getCapacityEu());
            CoreConfig.starlightVoltage=4096; CoreConfig.starlightCurrent=8;
            require(restoredStar.getOutputVoltage()==4096 && restoredStar.getMaximumCurrent()==8,
                    "Star source exposes configured 4096 V and whole 8 A output");
        } finally {
            CoreConfig.rfPerEu=savedRatio; CoreConfig.euPerStarlight=savedEfficiency;
            CoreConfig.starlightVoltage=savedVoltage; CoreConfig.starlightCurrent=savedCurrent;
        }
        pass("star persisted EU survives ratio change, actual generation multiplier and configured output getters");
        require(((TileStarlightGenerator) w.getTileEntity(AMBIENT_STAR)).getAmbientStarlight() > 0,
                "Open sky collects actual Astral Sorcery ambient starlight");
        require(((TileStarlightGenerator) w.getTileEntity(COVERED_STAR)).getAmbientStarlight() == 0,
                "Solid roof blocks ambient starlight collection");
        require(WorldNetworkHandler.getNetworkHandler(w).getTransmissionNode(STAR) != null, "Star generator registered in native AS transmission network");
        TileCollectorCrystal crystal = (TileCollectorCrystal) w.getTileEntity(CRYSTAL);
        require(crystal.tryLink(FakePlayerFactory.getMinecraft(w), STAR), "Native collector crystal links to generator");
        crystal.onLinkCreate(FakePlayerFactory.getMinecraft(w), STAR);
        require(crystal.getLinkedPositions().contains(STAR), "Native linking tool commit establishes transmission");
        nativeBefore = ((EuTile) w.getTileEntity(STAR.east())).energy;
        player().setPositionAndUpdate(STAR.getX() + .5, STAR.getY(), STAR.getZ() - 2.5);
        phase = 2;
    }
    private void finalChecks() {
        WorldServer w = world();
        ElectricalNodeStats refusedWire=Ic2Grid.GRID.getNodeStats(EnergyNet.instance.getTile(w,MANA_REFUSAL.east()));
        ElectricalNodeStats refusedSource=Ic2Grid.GRID.getNodeStats(EnergyNet.instance.getTile(w,MANA_REFUSAL));
        require(storedEu(w.getTileEntity(MANA_REFUSAL))==1000
                && ((IManaReceiver)w.getTileEntity(MANA_REFUSAL.east(2))).getCurrentMana()==refusalPoolMana,
                "Repeated sub-Mana physical deliveries are fully refused without draining the real MFE");
        require(refusedWire!=null && refusedSource!=null && refusedWire.getCurrent()==0
                && refusedWire.getEnergyIn()==0 && refusedWire.getEnergyOut()==0 && refusedSource.getEnergyOut()==0,
                "Fully refused native Mana transfer settles zero current, source debit and cable loss");
        pass("real MFE/glass/Mana pool full rejection pays no source or line loss");
        probeChecks(w);
        checkRemoteMana(w);
        require(((EuTile) w.getTileEntity(STAR.east())).energy > nativeBefore, "Actual collector transmission generates EU");
        require(w.getTileEntity(STAR.west()).writeToNBT(new NBTTagCompound()).getCompoundTag("components")
                .getCompoundTag("energy").getDouble("storage") > 0, "Crystal-fed star generator powers a real native IC2 macerator");
        TileStarlightGenerator star = (TileStarlightGenerator) w.getTileEntity(STAR);
        require(w.getTileEntity(AMBIENT_STAR.east()).writeToNBT(new NBTTagCompound()).getCompoundTag("components")
                .getCompoundTag("energy").getDouble("storage") > 0, "Ambient starlight powers native IC2 without a collector crystal");
        require(((TileStarlightGenerator) w.getTileEntity(COVERED_STAR)).getStoredEu() == 0, "Roofed unlinked star generator produces no energy");
        NBTTagCompound saved = ((TileEntity) star).writeToNBT(new NBTTagCompound());
        require(TileEntity.create(w, saved).writeToNBT(new NBTTagCompound()).getLong("MicroRf") == saved.getLong("MicroRf"), "Star energy NBT round trip");
        require(WorldNetworkHandler.getNetworkHandler(w).getTransmissionNode(STAR).getProvider().getIdentifier().equals("zhenghuocore:starlight_generator"), "Persistent AS receiver provider");
        TileEntity battery = w.getTileEntity(INFINITE_BATTERY);
        IEnergyStorage batteryRf = battery.getCapability(CapabilityEnergy.ENERGY, EnumFacing.EAST);
        IEnergyTile batteryNode = EnergyNet.instance.getTile(w, INFINITE_BATTERY);
        NBTTagCompound batteryLoad = w.getTileEntity(INFINITE_BATTERY.east()).writeToNBT(new NBTTagCompound());
        double batteryEu = batteryLoad.getCompoundTag("components").getCompoundTag("energy").getDouble("storage");
        System.out.println("ZHENGHUO_VALIDATION CYCLIC BATTERY: extractable RF=" + batteryRf.extractEnergy(Integer.MAX_VALUE,true)
                + ", EU node=" + batteryNode + ", macerator stored EU=" + batteryEu);
        if (!Boolean.getBoolean("zhenghuocore.manaOnly")) require(batteryEu > 0, "Real Cyclic infinite battery powers native IC2 macerator");
        else System.out.println("ZHENGHUO_MANA_TOP RF automatic adaptation is excluded: awaiting IC grid API; observed battery load=" + batteryEu);
        w.setBlockToAir(STAR);
        require(WorldNetworkHandler.getNetworkHandler(w).getTransmissionNode(STAR) == null, "Breaking star removes AS node");
        w.setBlockToAir(SOURCE.east()); phase = 3;
        pass("actual Astral crystal transmission, EU output, persistence and AS removal");
    }
    private void probeChecks(WorldServer w) {
        IProbeInfoProvider provider = TheOneProbe.theOneProbeImp.getProviders().stream()
                .filter(p -> "zhenghuocore:energy".equals(p.getID())).findFirst().orElse(null);
        require(provider != null, "TOP registered our provider through its real IMC callback");
        TileStarlightGenerator star = (TileStarlightGenerator) w.getTileEntity(STAR);
        NBTTagCompound before = ((TileEntity) star).writeToNBT(new NBTTagCompound());
        ProbeInfo info = new ProbeInfo();
        IProbeHitData hit = new IProbeHitData() {
            public BlockPos getPos() { return STAR; }
            public net.minecraft.util.math.Vec3d getHitVec() { return new net.minecraft.util.math.Vec3d(STAR); }
            public EnumFacing getSideHit() { return EnumFacing.NORTH; }
            public ItemStack getPickBlock() { return ItemStack.EMPTY; }
        };
        provider.addProbeInfo(ProbeMode.NORMAL, info, player(), w, w.getBlockState(STAR), hit);
        require(info.getElements().size() >= 3 && info.getElements().get(0) instanceof ElementProgress,
                "TOP includes an EU bar even when starlight storage is empty");
        ByteBuf encoded = Unpooled.buffer();
        try {
            info.getElements().get(0).toBytes(encoded);
            require(encoded.readLong() == (long) star.getStoredEu() && encoded.readLong() == (long) star.getCapacityEu(),
                    "TOP sends real EU storage and capacity to the client");
        } finally { encoded.release(); }
        require(before.equals(((TileEntity) star).writeToNBT(new NBTTagCompound())), "Inspecting TOP does not change energy or NBT");
        pass("TOP registration, EU bar and server-to-client energy payload at " + star.getStoredEu() + " EU");
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (!ready || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (failure != null) throw new AssertionError(failure);
            if (!started) {
                started = true; mc.gameSettings.pauseOnLostFocus = false; mc.gameSettings.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                boolean audit=Boolean.getBoolean("zhenghuocore.audit");
                mc.launchIntegratedServer(Boolean.getBoolean("zhenghuocore.voltageTest") ? "zhenghuocore-voltage-validation" : audit ? "zhenghuocore-audit" : "zhenghuocore-validation", audit ? "Zhenghuo defect audit" : "Zhenghuo compatibility validation", new WorldSettings(7001,GameType.CREATIVE,false,false,WorldType.FLAT).enableCommands()); return;
            }
            if (mc.world == null || mc.getIntegratedServer() == null || busy) return;
            if (++ticks > 1200) throw new AssertionError("Timeout phase " + phase);
            if (Boolean.getBoolean("zhenghuocore.voltageTest")) {
                if (phase==0) task(this::voltageSetup);
                else if (phase==1 && ticks>60) task(this::voltageChecks);
                else if (phase==2 && ticks>40) task(this::voltageOutputChecks);
                else if (phase==3) {
                    String line=net.minecraft.client.resources.I18n.format("zhenghuocore.tooltip.voltage_tier", "HV");
                    require(line.equals("电压等级:HV"), "Chinese voltage tooltip translation");
                    java.util.List<String> tooltip=new ItemStack(Blocks.REDSTONE_BLOCK).getTooltip(mc.player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
                    require(java.util.Collections.frequency(tooltip,line)==1,"Actual whitelisted block item tooltip shows one HV line");
                    require(!new ItemStack(Blocks.STONE).getTooltip(mc.player,net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL).contains(line),"Unlisted blocks have no whitelist tooltip");
                    System.out.println("ZHENGHUO_VOLTAGE ALL PASSED"); phase=4;
                } else if (phase==4) mc.shutdown();
                return;
            }
            if (Boolean.getBoolean("zhenghuocore.audit")) {
                if (phase==0) task(this::auditSetup);
                else if (phase==1 && ticks>40) task(this::auditChecks);
                else if (phase==2 && ticks>40) task(this::auditFluxCheck);
                else if (phase==4) mc.shutdown();
                return;
            }
            if(phase==2 && fluxStage<5 && world().getTotalWorldTime()-fluxTick>=20) { task(this::fluxStep); return; }
            if (phase == 0) task(this::setup);
            else if (phase == 1 && ticks > 10 && !equalityWindowStarted) task(() -> {
                require(EnergyNet.instance.getTile(world(),SOURCE.east()) instanceof RfGridNode
                        && EnergyNet.instance.getTile(world(),SOURCE.west()) instanceof IEnergySink,
                        "Both endpoints registered before comparing equal-demand dispatch");
                ((EuTile)world().getTileEntity(SOURCE.west())).energy=0;
                ((RfTile)world().getTileEntity(SOURCE.east())).energy=0;
                equalityWindowStarted=true;
            });
            else if (phase == 1 && ticks > 40) task(this::directChecks);
            else if (phase == 2 && ticks > 350 && fluxStage==5) {
                for (int i=0;i<AUDIT_BOXES.length;i++) {
                    TileEntity clientBox=mc.world.getTileEntity(storagePos(i));
                    require(clientBox!=null,"Real client IC storage is loaded");
                    int mask=0;
                    for (EnumFacing face:EnumFacing.values()) {
                        IEnergyStorage port=clientBox.getCapability(CapabilityEnergy.ENERGY,face);
                        if(port!=null && port.canReceive()) mask|=1<<face.getIndex();
                        if(port!=null && port.canExtract()) mask|=1<<(6+face.getIndex());
                    }
                    require(mask==boxPortMasks[i] && mask!=0,"Client FE port directions match server for "+AUDIT_BOXES[i]);
                }
                pass("real client FE capability directions for all four IC storage tiers");
                net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir, "zhenghuocore-validation.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                task(this::finalChecks);
            }
            else if (phase == 3 && discoveryStage < 9 && world().getTotalWorldTime()-discoveryTick > 25) task(this::discoveryStep);
            else if (phase == 3 && ticks > 305 && discoveryStage == 9) task(() -> {
                require(EnergyNet.instance.getTile(world(), SOURCE.east()) == null, "Broken RF endpoint removed from EU network");
                require(unchangedBoundaryPairs>=5,"Observed at least five actual END/next-START world-time pairs");
                pass("main-thread energy fixtures and END/next-START clock boundary, pairs="+unchangedBoundaryPairs);
                System.out.println(Boolean.getBoolean("zhenghuocore.manaOnly") ? "ZHENGHUO_MANA_TOP ALL PASSED" : "ZHENGHUO_VALIDATION ALL PASSED"); phase = 4;
            });
            else if (phase == 4) mc.shutdown();
            if (phase == 2 && mc.player != null) { mc.player.rotationYaw = 0; mc.player.rotationPitch = 24; }
        } catch (Throwable error) { error.printStackTrace(); System.out.println((Boolean.getBoolean("zhenghuocore.audit") ? "ZHENGHUO_AUDIT FAILED " : "ZHENGHUO_VALIDATION FAILED ") + error); mc.shutdown(); }
    }
    private void voltageSetup() {
        WorldServer w=world();
        require(VoltageConfig.defaultTier()==5,"New compatibility default is IV");
        require(VoltageConfig.override(new ResourceLocation("botania:pool"))==3,"Actual pool whitelist is HV");
        for (BlockPos at : new BlockPos[]{SOURCE,RF_SOURCE,SMALL,POOL,MJ,ENGINE,STAR}) {
            w.setBlockToAir(at); w.setBlockToAir(at.east());
        }
        source(SOURCE).setSettings(8192,1); fixture(w,SOURCE.east(),new RfTile(1000000,0,true,false));
        source(RF_SOURCE).setSettings(512,1); w.setBlockState(RF_SOURCE.east(),ModBlocks.pool.getDefaultState());
        source(SMALL).setSettings(1024,1); w.setBlockState(SMALL.east(),ModBlocks.pool.getDefaultState());
        source(POOL).setSettings(8193,1); fixture(w,POOL.east(),new RfTile(1000000,0,true,false));
        place(w,MJ,IC2Items.getItem("te","macerator"));
        fixture(w,ENGINE,new RfTile(10000000,10000000,false,true));
        EuTile rfLoad=new EuTile(); rfLoad.tier=5; rfLoad.capacity=1000000000; rfLoad.demandLimit=100000;
        fixture(w,ENGINE.east(),rfLoad);
        w.setBlockState(STAR,ModBlocks.pool.getDefaultState());
        EuTile manaLoad=new EuTile(); manaLoad.tier=5; manaLoad.capacity=1000000000; manaLoad.demandLimit=100000;
        fixture(w,STAR.east(),manaLoad);
        ticks=0; phase=1;
    }
    private void voltageChecks() {
        WorldServer w=world();
        IEnergyTile rf=EnergyNet.instance.getTile(w,SOURCE.east());
        require(rf instanceof RfGridNode && ((IEnergySink)rf).getSinkTier()==5,"Unlisted FE device advertises IV to actual grid");
        require(((RfTile)w.getTileEntity(SOURCE.east())).energy>0,"IV FE device actually accepts 8192 V");
        IEnergyTile pool=EnergyNet.instance.getTile(w,RF_SOURCE.east());
        require(pool instanceof RfGridNode && ((IEnergySink)pool).getSinkTier()==3,"Real Botania whitelist overrides IV with HV");
        require(((IManaReceiver)w.getTileEntity(RF_SOURCE.east())).getCurrentMana()>0,"HV pool actually accepts 512 V");
        require(w.getTileEntity(SMALL.east())==null,"HV pool is destroyed by 1024 V through native grid overvoltage");
        require(w.getTileEntity(POOL.east())==null,"Unlisted IV FE device is destroyed above 8192 V");
        IEnergyTile nativeMachine=EnergyNet.instance.getTile(w,MJ);
        require(nativeMachine instanceof IEnergySink && ((IEnergySink)nativeMachine).getSinkTier()==1,"Native IC2 macerator retains its LV tier");
        pass("default IV, whitelist HV, actual voltage acceptance/overvoltage and original IC2 LV");
        ((IManaReceiver)w.getTileEntity(STAR)).recieveMana(100000);
        require(VoltageConfig.current(w.getTileEntity(STAR))==2,"Real Mana pool current whitelist is 2 A");
        ticks=0; phase=2;
    }
    private void voltageOutputChecks() {
        WorldServer w=world();
        ElectricalNodeStats rf=Ic2Grid.GRID.getNodeStats(EnergyNet.instance.getTile(w,ENGINE));
        require(rf.getActualVoltage()==8192 && rf.getCurrent()==1 && rf.getEnergyOut()==8192,
                "Default RF source emits at most IV 8192 V / 1 A: "+rf.getActualVoltage()+" / "+rf.getCurrent()+" / "+rf.getEnergyOut());
        require(10000000-((RfTile)w.getTileEntity(ENGINE)).energy==((EuTile)w.getTileEntity(ENGINE.east())).energy*4,
                "RF source debit matches delivered EU; power beyond IV stays at the source");
        ElectricalNodeStats mana=Ic2Grid.GRID.getNodeStats(EnergyNet.instance.getTile(w,STAR));
        require(mana.getCurrent()==2 && mana.getActualVoltage()==510 && mana.getEnergyOut()==1020,
                "HV / 2 A real Mana source respects 512 V ceiling and whole Mana quantum: "+mana.getActualVoltage()+" / "+mana.getCurrent()+" / "+mana.getEnergyOut());
        int manaSpent=100000-((IManaReceiver)w.getTileEntity(STAR)).getCurrentMana();
        require(manaSpent*2.5==((EuTile)w.getTileEntity(STAR.east())).energy,"Configured Mana source debit is exact");
        pass("default IV / 1 A RF output, configured HV / 2 A real Mana output, ceilings and exact source debit");
        phase=3;
    }

    private void discoveryStep() {
        WorldServer w=world();
        if(discoveryStage==0) {
            w.setBlockToAir(IDLE_PROBE); w.setBlockState(IDLE_PROBE,IDLE_BLOCK.getDefaultState());
            fixture(w,IDLE_PROBE.east(),new EuTile());
            RfTile tile=fixture(w,PORT_PROBE,new RfTile()); tile.receiveSide=EnumFacing.WEST;
            fixture(w,PORT_PROBE.west(),new EuTile());
            w.setBlockToAir(ENGINE_PROBE);
            w.setBlockState(ENGINE_PROBE,BCCoreBlocks.engine.getDefaultState().withProperty(BCCoreBlocks.engine.getEngineProperty(),EnumEngineType.STONE));
            fixture(w,ENGINE_PROBE.east(),new EuTile()); fixture(w,ENGINE_PROBE.west(),new EuTile());
            w.getChunk(RELOAD_PROBE);
            Block battery=Block.REGISTRY.getObject(new ResourceLocation("cyclicmagic","battery_infinite"));
            require(battery!=null && battery!=Blocks.AIR,"Actual reload source exists");
            w.setBlockToAir(RELOAD_PROBE); w.setBlockState(RELOAD_PROBE,battery.getDefaultState());
            place(w,RELOAD_PROBE.east(),IC2Items.getItem("te","macerator"));
        } else if(discoveryStage==1) {
            IdleTile idle=(IdleTile)w.getTileEntity(IDLE_PROBE);
            require(idle.feQueries>0,"Non-energy tile was checked after native EU neighbor appeared"); idle.feQueries=0;
            IEnergyTile proxy=EnergyNet.instance.getTile(w,PORT_PROBE);
            require(proxy instanceof RfGridNode && ((IEnergySink)proxy).acceptsEnergyFrom(null,EnumFacing.WEST),"Fresh sided RF endpoint discovered");
            previousProxy=proxy;
            TileEngineBase_BC8 engine=(TileEngineBase_BC8)w.getTileEntity(ENGINE_PROBE);
            engine.attemptRotation(); firstEngineFace=engine.getCurrentFacing();
            require(firstEngineFace==EnumFacing.EAST || firstEngineFace==EnumFacing.WEST,"Real BC engine finds either available native EU/FE/MJ receiver");
        } else if(discoveryStage==2) {
            require(((IdleTile)w.getTileEntity(IDLE_PROBE)).feQueries==0,"Idle non-energy tile incurs zero repeated FE discovery queries");
            RfTile tile=(RfTile)w.getTileEntity(PORT_PROBE); tile.receiveSide=EnumFacing.EAST;
            IBlockState state=w.getBlockState(PORT_PROBE); w.notifyBlockUpdate(PORT_PROBE,state,state,3);
            IEnergyTile engineProxy=EnergyNet.instance.getTile(w,ENGINE_PROBE);
            require(engineProxy instanceof RfGridNode && ((IEnergyEmitter)engineProxy).emitsEnergyTo(null,firstEngineFace),"BC rotation updates the real EU proxy output face");
            require(((TileEngineBase_BC8)w.getTileEntity(ENGINE_PROBE)).attemptRotation()==EnumActionResult.SUCCESS,"Second native BC rotation succeeds");
        } else if(discoveryStage==3) {
            IEnergyTile proxy=EnergyNet.instance.getTile(w,PORT_PROBE);
            require(proxy==previousProxy && !((IEnergySink)proxy).acceptsEnergyFrom(null,EnumFacing.WEST)
                    && ((IEnergySink)proxy).acceptsEnergyFrom(null,EnumFacing.EAST),"Actual block update refreshes changed sides on the same proxy");
            EnumFacing nextFace=((TileEngineBase_BC8)w.getTileEntity(ENGINE_PROBE)).getCurrentFacing();
            IEnergyEmitter engineProxy=(IEnergyEmitter)EnergyNet.instance.getTile(w,ENGINE_PROBE);
            require(nextFace!=firstEngineFace && engineProxy.emitsEnergyTo(null,nextFace) && !engineProxy.emitsEnergyTo(null,firstEngineFace),
                    "Native BC neighbor notification refreshes new/old EU output faces without periodic scanning");
            previousPort=w.getTileEntity(PORT_PROBE); fixture(w,PORT_PROBE,new RfTile());
        } else if(discoveryStage==4) {
            IEnergyTile proxy=EnergyNet.instance.getTile(w,PORT_PROBE);
            require(proxy instanceof RfGridNode && proxy!=previousProxy,"Same-position replacement gets a fresh endpoint");
            previousProxy=proxy; previousPort.invalidate();
            require(EnergyNet.instance.getTile(w,PORT_PROBE)==proxy,"Late invalidation of old tile cannot unregister replacement");
            w.removeTileEntity(PORT_PROBE);
            require(EnergyNet.instance.getTile(w,PORT_PROBE)==null,"Direct tile removal unregisters immediately");
        } else if(discoveryStage==5) {
            require(EnergyNet.instance.getTile(w,PORT_PROBE)==null
                    && w.getChunk(PORT_PROBE).getTileEntity(PORT_PROBE,net.minecraft.world.chunk.Chunk.EnumCreateEntityType.CHECK)==null,
                    "Discovery does not recreate a removed tile just because its block remains");
            require(((IdleTile)w.getTileEntity(IDLE_PROBE)).feQueries==0,"Unrelated lifecycle changes do not rescan the idle tile");
            previousReload=w.getTileEntity(RELOAD_PROBE);
            require(EnergyNet.instance.getTile(w,RELOAD_PROBE) instanceof RfGridNode
                    && storedEu(w.getTileEntity(RELOAD_PROBE.east()))>0,"Real Cyclic source powers real IC before unload");
            net.minecraft.world.chunk.Chunk chunk=w.getChunk(RELOAD_PROBE);
            w.getChunkProvider().queueUnload(chunk); w.getChunkProvider().tick();
            require(w.getChunkProvider().getLoadedChunk(RELOAD_PROBE.getX()>>4,RELOAD_PROBE.getZ()>>4)==null,
                    "Actual server chunk provider unloaded the test chunk");
            require(EnergyNet.instance.getTile(w,RELOAD_PROBE)==null,"Chunk event clears real FE proxy");
        } else if(discoveryStage==6) {
            require(!w.isBlockLoaded(RELOAD_PROBE,false),"Idle discovery does not reload unloaded chunks");
            w.getChunk(RELOAD_PROBE);
            require(w.getTileEntity(RELOAD_PROBE)!=previousReload,"Reload restored a different real tile object");
        } else if(discoveryStage==7) {
            require(EnergyNet.instance.getTile(w,RELOAD_PROBE) instanceof RfGridNode
                    && storedEu(w.getTileEntity(RELOAD_PROBE.east()))>0,"Actual chunk reload re-registers real Cyclic and IC endpoints");
            previousReload.invalidate();
            require(EnergyNet.instance.getTile(w,RELOAD_PROBE) instanceof RfGridNode,"Old unloaded tile cannot remove new chunk endpoint");
            w.setBlockToAir(RELOAD_PROBE); w.setBlockToAir(RELOAD_PROBE.east());
            w.setBlockToAir(IDLE_PROBE); w.setBlockToAir(IDLE_PROBE.east());
            w.setBlockToAir(PORT_PROBE); w.setBlockToAir(PORT_PROBE.west());
            w.setBlockToAir(ENGINE_PROBE); w.setBlockToAir(ENGINE_PROBE.east()); w.setBlockToAir(ENGINE_PROBE.west());
        } else if(discoveryStage==8) {
            require(EnergyNet.instance.getTile(w,RELOAD_PROBE)==null,"Cleanup removed reloaded proxy");
            pass("event-driven discovery, zero idle probes, sided update, replacement and actual chunk unload/reload");
        }
        discoveryStage++; discoveryTick=w.getTotalWorldTime();
    }

    private void fluxStep() {
        try {
            if(fluxStage==0) fluxValidation.startTransfer();
            else if(fluxStage==1) fluxValidation.checkTransferAndSetZeroLimit();
            else if(fluxStage==2) { fluxValidation.checkBlocked("zero limit"); fluxValidation.disconnect(); }
            else if(fluxStage==3) { fluxValidation.checkBlocked("disconnected"); fluxValidation.reconnectWithFullReceiver(); }
            else if(fluxStage==4) { fluxValidation.checkBlocked("full receiver"); pass("real linked Flux delivery, zero limit, disconnection and full receiver rejection"); }
            fluxStage++; fluxTick=world().getTotalWorldTime();
        } catch(Exception error) { throw new AssertionError("Real Flux stage "+fluxStage,error); }
    }

    private void setupRemoteMana(WorldServer w) {
        for (vazkii.botania.common.entity.EntityManaBurst burst : w.getEntitiesWithinAABB(vazkii.botania.common.entity.EntityManaBurst.class,
                new net.minecraft.util.math.AxisAlignedBB(-2,78,16,165,84,45))) w.removeEntityDangerously(burst);
        for (vazkii.botania.common.entity.EntitySpark spark : w.getEntitiesWithinAABB(vazkii.botania.common.entity.EntitySpark.class,
                new net.minecraft.util.math.AxisAlignedBB(-2,78,16,42,84,25))) w.removeEntityDangerously(spark);
        Block battery = Block.REGISTRY.getObject(new ResourceLocation("cyclicmagic", "battery"));
        require(battery != null && battery != Blocks.AIR, "Real Cyclic RF receiver exists");
        for (BlockPos pos : new BlockPos[]{BURST_LOAD, SPARK_LOAD}) {
            w.setBlockToAir(pos); w.setBlockState(pos, battery.getDefaultState());
            IEnergyStorage rf = w.getTileEntity(pos).getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
            require(rf != null && rf.canReceive() && rf.getEnergyStored() == 0, "Fresh real RF battery accepts input");
        }
        w.setBlockToAir(REMOTE_SPREADER); w.setBlockState(REMOTE_SPREADER, ModBlocks.spreader.getDefaultState());
        vazkii.botania.common.block.tile.mana.TileSpreader spreader = (vazkii.botania.common.block.tile.mana.TileSpreader) w.getTileEntity(REMOTE_SPREADER);
        require(spreader.bindTo(player(), new ItemStack(vazkii.botania.common.item.ModItems.twigWand), BURST_LOAD, EnumFacing.WEST),
                "Native wand binding accepts an RF target seven blocks away");
        spreader.recieveMana(1000);
        w.setBlockToAir(SPARK_POOL); w.setBlockState(SPARK_POOL, ModBlocks.pool.getDefaultState());
        IManaReceiver pool = (IManaReceiver) w.getTileEntity(SPARK_POOL);
        require(ManaReceiverHooks.receiver(pool) == pool, "Native mana receivers retain object identity");
        w.setBlockToAir(NATIVE_SPREADER); w.setBlockState(NATIVE_SPREADER,ModBlocks.spreader.getDefaultState());
        w.setBlockToAir(NATIVE_POOL); w.setBlockState(NATIVE_POOL,ModBlocks.pool.getDefaultState());
        vazkii.botania.common.block.tile.mana.TileSpreader nativeSpreader = (vazkii.botania.common.block.tile.mana.TileSpreader) w.getTileEntity(NATIVE_SPREADER);
        require(nativeSpreader.bindTo(player(),new ItemStack(vazkii.botania.common.item.ModItems.twigWand),NATIVE_POOL,EnumFacing.WEST),
                "Native mana spreader binds to original mana pool");
        nativeSpreader.recieveMana(1000);
        require(w.getTileEntity(NATIVE_POOL).getCapability(CapabilityEnergy.ENERGY,EnumFacing.UP) != null,
                "Native receiver also has our RF capability during this regression");
        placeSpark(w, SPARK_POOL); placeSpark(w, SPARK_LOAD);
        require(ManaReceiverHooks.sparkAttachable(w.getTileEntity(SPARK_LOAD)).getAttachedSpark() != null,
                "Native spark item attaches to real RF device");

        for(BlockPos target:new BlockPos[]{PARTIAL_LOAD,FULL_LOAD,CLOSED_FACE_LOAD}) {
            RfTile receiverTile=new RfTile(target.equals(PARTIAL_LOAD)?95:1000,target.equals(FULL_LOAD)?1000:0,true,false);
            if(target.equals(CLOSED_FACE_LOAD)) receiverTile.receiveSide=EnumFacing.EAST;
            fixture(w,target,receiverTile);
            w.setBlockToAir(target.west(7)); w.setBlockState(target.west(7),ModBlocks.spreader.getDefaultState());
            vazkii.botania.common.block.tile.mana.TileSpreader sender=(vazkii.botania.common.block.tile.mana.TileSpreader)w.getTileEntity(target.west(7));
            require(sender.bindTo(player(),new ItemStack(vazkii.botania.common.item.ModItems.twigWand),target,EnumFacing.WEST),"Bind real spreader to boundary RF fixture");
            sender.recieveMana(1000);
        }
        // A native per-call intake below one Mana cannot accept a partial Mana or create a queue.
        BlockPos slowPos = new BlockPos(40,80,20);
        RfTile slow = new RfTile(10000,0,true,false); slow.maxReceive = 3; slow.receiveSide = EnumFacing.WEST;
        fixture(w, slowPos, slow);
        IManaReceiver blockedFace = ManaReceiverHooks.impactReceiver(slow,
                new net.minecraft.util.math.RayTraceResult(new net.minecraft.util.math.Vec3d(slowPos), EnumFacing.EAST, slowPos));
        require(!blockedFace.canRecieveManaFromBursts(), "RF output-only or closed face rejects incoming mana burst");
        IManaReceiver receiver = ManaReceiverHooks.receiver(slow);
        receiver.recieveMana(200);
        require(receiver.isFull() && slow.energy==0 && slow.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getKeySet().isEmpty(),
                "Three-RF native intake rejects complete Mana and adds no deferred storage");
        pass("remote mana setup, native wand/spark use and sided whole-Mana rejection");
    }
    private static BlockPos storagePos(int index) { return new BlockPos(100+index*16,80,40); }
    private static double storedEu(TileEntity tile) {
        return tile.writeToNBT(new NBTTagCompound()).getCompoundTag("components").getCompoundTag("energy").getDouble("storage");
    }
    private int flyingMana(WorldServer world,BlockPos source) {
        int mana=0;
        for(vazkii.botania.common.entity.EntityManaBurst burst:world.getEntitiesWithinAABB(vazkii.botania.common.entity.EntityManaBurst.class,
                new net.minecraft.util.math.AxisAlignedBB(source).grow(16)))
            if(!burst.isFake() && !((Entity)burst).isDead && source.equals(burst.getBurstSourceBlockPos())) mana+=burst.getMana();
        return mana;
    }
    private IEnergyStorage fePort(TileEntity tile,boolean input,int amount) {
        StringBuilder details=new StringBuilder();
        for(EnumFacing side:EnumFacing.values()) {
            IEnergyStorage port=tile.getCapability(CapabilityEnergy.ENERGY,side);
            boolean allowed=port!=null && (input ? port.canReceive() : port.canExtract());
            int quote=!allowed ? 0 : input ? port.receiveEnergy(amount,true) : port.extractEnergy(amount,true);
            details.append(side).append(':').append(allowed).append('/').append(quote).append(' ');
            if(allowed && quote>=amount) return port;
        }
        IEnergyTile nativeTile=EnergyNet.instance.getTile(tile.getWorld(),tile.getPos());
        throw new AssertionError("No native-compatible FE "+(input?"input":"output")+" for "+tile.getClass().getName()
                + "; stored="+storedEu(tile)+", sourceOffer="+(nativeTile instanceof IEnergySource ? ((IEnergySource)nativeTile).getOfferedEnergy() : -1)
                + ", ports="+details);
    }
    private void storageManaChecks(WorldServer w) {
        for(int i=0;i<AUDIT_BOXES.length;i++) {
            TileEntity box=w.getTileEntity(storagePos(i));
            require(box.getClass().getName().startsWith("ic2.core.block.wiring.TileEntityElectric"),"Actual IC "+AUDIT_BOXES[i]);
            require(EnergyNet.instance.getTile(w,box.getPos())!=null,"Native IC storage delegate registered");
            // IC2's fullEnergy source withholds output below its native packet threshold.
            // IC2's public setStored is a no-op; use its actual public addEnergy implementation.
            ((ic2.api.tile.IEnergyStorage)box).addEnergy(8192);
            require(storedEu(box)==8192,"Native storage seeded above its output threshold");
            IEnergyStorage input=fePort(box,true,40);
            NBTTagCompound before=box.writeToNBT(new NBTTagCompound());
            require(input.receiveEnergy(40,true)==40 && before.equals(box.writeToNBT(new NBTTagCompound())),"IC FE intake simulation does not mutate NBT");
            double start=storedEu(box);
            require(input.receiveEnergy(40,false)==40 && storedEu(box)==start+10,"40 RF becomes exactly 10 real EU in "+AUDIT_BOXES[i]);
            IEnergyStorage output=fePort(box,false,40);
            before=box.writeToNBT(new NBTTagCompound());
            require(output.extractEnergy(40,true)==40 && before.equals(box.writeToNBT(new NBTTagCompound())),"IC FE output simulation does not mutate NBT");
            require(output.extractEnergy(40,false)==40 && storedEu(box)==start,"Real IC EU returns 40 RF without hidden storage");
            require(ManaReceiverHooks.isReceiver(box),"IC storage is a remote Mana target through FE");
            require(box instanceof ic2.api.tile.IEnergyStorage,"Native IC storage API is available");
            ic2.api.tile.IEnergyStorage nativeStorage=(ic2.api.tile.IEnergyStorage)box;
            IManaReceiver mana=ManaReceiverHooks.receiver(box);
            for(int gap=5;gap<=7;gap++) {
                // Only native test storage is set. These are inputs, independent of the output allowance already spent this tick.
                nativeStorage.addEnergy(nativeStorage.getCapacity()-gap-nativeStorage.getStored());
                require(nativeStorage.getStored()==nativeStorage.getCapacity()-gap,"Native storage has exactly the tested gap");
                before=box.writeToNBT(new NBTTagCompound());
                int room=((vazkii.botania.api.mana.spark.ISparkAttachable)mana).getAvailableSpaceForMana();
                vazkii.botania.common.entity.EntityManaBurst offer=new vazkii.botania.common.entity.EntityManaBurst(w);
                offer.setMana(2); offer.setStartingMana(2);
                require(room>=2 && !mana.isFull() && ManaReceiverHooks.limitBurst(offer,mana)==offer && offer.getMana()==2,
                        "IC "+AUDIT_BOXES[i]+" with "+gap+" EU space quotes a real two-Mana offer without false full");
                require(before.equals(box.writeToNBT(new NBTTagCompound())),"Near-full Mana quotes do not mutate actual IC NBT");
                double energyBefore=storedEu(box);
                require(ManaReceiverHooks.receiveSpark(mana,2)==2 && storedEu(box)==energyBefore+5,
                        "Two Mana becomes five real EU with "+gap+" EU free in "+AUDIT_BOXES[i]);
                require(box.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getKeySet().isEmpty(),"Near-full IC intake creates no compatibility balance");
            }
            nativeStorage.addEnergy((int)start-nativeStorage.getStored());
            require(storedEu(box)==start,"Restore native IC test energy before remote burst test");
            vazkii.botania.common.block.tile.mana.TileSpreader sender=(vazkii.botania.common.block.tile.mana.TileSpreader)w.getTileEntity(box.getPos().west(7));
            require(sender.bindTo(player(),new ItemStack(vazkii.botania.common.item.ModItems.twigWand),box.getPos(),EnumFacing.WEST),"Wand binds real spreader to IC "+AUDIT_BOXES[i]);
            boxBeforeMana[i]=storedEu(box); sender.recieveMana(1000);
            int mask=0;
            for(EnumFacing face:EnumFacing.values()) {
                IEnergyStorage port=box.getCapability(CapabilityEnergy.ENERGY,face);
                if(port!=null && port.canReceive()) mask|=1<<face.getIndex();
                if(port!=null && port.canExtract()) mask|=1<<(6+face.getIndex());
            }
            boxPortMasks[i]=mask;
        }
        pass("real batbox/CESU/MFE/MFSU FE round trips, read-only simulation and remote Mana binding");
    }
    private void boundaryChecks(WorldServer w) {
        BlockPos pos=new BlockPos(48,80,20);
        RfTile full=fixture(w,pos,new RfTile(1000,1000,true,false));
        IManaReceiver mana=ManaReceiverHooks.receiver(full);
        require(mana!=null && mana.isFull() && ManaReceiverHooks.receiveSpark(mana,200)==0,"Full RF rejects Mana and reports zero actual spark spend");
        mana.recieveMana(200);
        IMjReceiver mj=full.getCapability(MjAPI.CAP_RECEIVER,EnumFacing.UP);
        require(mj!=null && mj.getPowerRequested()==0 && mj.receivePower(MjAPI.MJ,true)==MjAPI.MJ
                && mj.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Full RF rejects MJ in simulation and commit");
        RfGridNode node=new RfGridNode(full,full.getCapability(TileCompatibility.CAPABILITY,null));
        require(node.getDemandedEnergy()==0 && node.injectEnergy(EnumFacing.UP,1,1)==1,"Full RF returns all injected EU");
        require(full.energy==1000 && full.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getKeySet().isEmpty(),"Full RF gained no compatibility balance");
        full.energy=985;
        require(ManaReceiverHooks.receiveSpark(mana,200)==1 && full.energy==995 && mana.isFull(),"Partial RF space accepts one Mana, leaving five native RF space");
        full.energy=1000;
        vazkii.botania.common.entity.EntityManaBurst burst=new vazkii.botania.common.entity.EntityManaBurst(w);
        burst.setMana(160); burst.setStartingMana(160); burst.setMotion(.2,0,0);
        ManaReceiverHooks.receiveBurst(mana,160,burst);
        net.minecraft.util.math.RayTraceResult hit=new net.minecraft.util.math.RayTraceResult(new net.minecraft.util.math.Vec3d(pos),EnumFacing.WEST,pos);
        ManaReceiverHooks.finishBurstImpact(burst,hit);
        require(burst.getMana()==160 && !((Entity)burst).isDead && ((Entity)burst).motionX==.2 && full.energy==1000,"Refused in-flight Mana stays in the moving original burst");
        full.energy=985; ManaReceiverHooks.receiveBurst(mana,160,burst);
        require(burst.getMana()==159 && full.energy==995,"Partial impact debits only actual delivered Mana from original burst");

        TileEntity controller=w.getTileEntity(AE_CONTROLLER);
        IEnergyStorage ae=fePort(controller,true,1);
        require(ae.receiveEnergy(Integer.MAX_VALUE,false)>0 && ae.receiveEnergy(1,true)==0,"Real AE2 controller filled through native FE");
        int stored=ae.getEnergyStored(); IManaReceiver aeMana=ManaReceiverHooks.receiver(controller);
        require(aeMana!=null && aeMana.isFull() && ManaReceiverHooks.receiveSpark(aeMana,200)==0,"Real full AE2 controller refuses Mana");
        aeMana.recieveMana(200);
        require(ae.getEnergyStored()==stored && controller.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getKeySet().isEmpty(),"Full AE2 receives no extra queue or hidden energy");
        pass("full/partial RF, full real AE2, MJ/EU rejection and original burst conservation without buffers");
    }
    private void placeSpark(WorldServer w, BlockPos pos) {
        EntityPlayerMP actor = FakePlayerFactory.getMinecraft(w);
        ItemStack spark = new ItemStack(vazkii.botania.common.item.ModItems.spark);
        actor.setHeldItem(EnumHand.MAIN_HAND, spark);
        require(spark.onItemUse(actor,w,pos,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F) == EnumActionResult.SUCCESS,
                "Native spark placement succeeds at " + pos);
    }
    private void checkRemoteMana(WorldServer w) {
        int burstRf = w.getTileEntity(BURST_LOAD).getCapability(CapabilityEnergy.ENERGY,EnumFacing.UP).getEnergyStored();
        int sparkRf = w.getTileEntity(SPARK_LOAD).getCapability(CapabilityEnergy.ENERGY,EnumFacing.UP).getEnergyStored();
        int spreaderMana = ((IManaReceiver) w.getTileEntity(REMOTE_SPREADER)).getCurrentMana();
        int poolMana = ((IManaReceiver) w.getTileEntity(SPARK_POOL)).getCurrentMana();
        int nativeInput = ((IManaReceiver) w.getTileEntity(NATIVE_POOL)).getCurrentMana();
        int nativeRemaining = ((IManaReceiver) w.getTileEntity(NATIVE_SPREADER)).getCurrentMana();
        vazkii.botania.api.mana.spark.ISparkEntity receiverSpark = ManaReceiverHooks.sparkAttachable(w.getTileEntity(SPARK_LOAD)).getAttachedSpark();
        vazkii.botania.api.mana.spark.ISparkEntity senderSpark = ((vazkii.botania.api.mana.spark.ISparkAttachable) w.getTileEntity(SPARK_POOL)).getAttachedSpark();
        System.out.println("ZHENGHUO_REMOTE_DEBUG burstRF=" + burstRf + " sparkRF=" + sparkRf
                + " pool=" + poolMana + " sourceSpark=" + senderSpark + " receiverSpark=" + receiverSpark
                + " receiverClass=" + (receiverSpark == null ? null : receiverSpark.getAttachedTile())
                + " sourceTransfers=" + (senderSpark == null ? null : senderSpark.getTransfers())
                + " targetRoom=" + ManaReceiverHooks.sparkAttachable(w.getTileEntity(SPARK_LOAD)).getAvailableSpaceForMana());
        require(nativeInput > 0 && nativeInput + nativeRemaining == 1000,
                "Native mana inputs still work with RF adapters: pool=" + nativeInput + ", spreader=" + nativeRemaining);
        require(burstRf > 0, "Actual flying mana bursts charge real RF battery seven blocks away");
        require(sparkRf > 0, "Actual native spark network charges RF battery eight blocks away");
        require(burstRf + spreaderMana * CoreConfig.rfPerMana == 1000 * CoreConfig.rfPerMana,
                "Remote burst mana is conserved without hidden storage: RF=" + burstRf + ", source=" + spreaderMana);
        require(sparkRf + poolMana * CoreConfig.rfPerMana == 4000 * CoreConfig.rfPerMana,
                "Remote spark mana is conserved without hidden storage: RF=" + sparkRf + ", source=" + poolMana);
        RfTile slow = (RfTile) w.getTileEntity(new BlockPos(40,80,20));
        require(slow.energy==0 && slow.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getKeySet().isEmpty(),"Rejected Mana never appears in later ticks");
        require(((RfTile)w.getTileEntity(PARTIAL_LOAD)).energy==90 && ((IManaReceiver)w.getTileEntity(PARTIAL_LOAD.west(7))).getCurrentMana()==991,
                "Actual flying burst shrinks to 9 Mana for 95 RF space and leaves 991 Mana in source");
        require(((RfTile)w.getTileEntity(FULL_LOAD)).energy==1000 && ((IManaReceiver)w.getTileEntity(FULL_LOAD.west(7))).getCurrentMana()==1000,
                "Actual full-target spreader keeps all Mana and does not fire");
        require(((RfTile)w.getTileEntity(CLOSED_FACE_LOAD)).energy==0
                && ((IManaReceiver)w.getTileEntity(CLOSED_FACE_LOAD.west(7))).getCurrentMana()==1000,
                "Real spreader aimed at a closed FE face keeps its Mana even when another face accepts input");
        for(int i=0;i<AUDIT_BOXES.length;i++) {
            TileEntity box=w.getTileEntity(storagePos(i));
            int remaining=((IManaReceiver)w.getTileEntity(storagePos(i).west(7))).getCurrentMana();
            int flying=flyingMana(w,storagePos(i).west(7));
            double gained=storedEu(box)-boxBeforeMana[i];
            require(gained>0 && Math.abs(gained*CoreConfig.rfPerEu+(remaining+flying)*CoreConfig.rfPerMana-10000)<0.00001,
                    "Actual remote spreader powers IC "+AUDIT_BOXES[i]+" through FE with exact debit: EU="+gained+", source Mana="+remaining+", flying Mana="+flying);
            require(box.getCapability(TileCompatibility.CAPABILITY,null).serializeNBT().getKeySet().isEmpty(),"IC storage has no compatibility energy balance");
        }
        pass("real spreader/spark RF delivery, partial/full boundary, and all four real IC storage tiers via FE");
    }
    private static final class FixtureBlock extends Block {
        private final Supplier<? extends TileEntity> factory;
        FixtureBlock(String id, Supplier<? extends TileEntity> factory) { super(Material.IRON); this.factory=factory; setRegistryName("zhenghuocore_validation",id); }
        @Override public boolean hasTileEntity(IBlockState state) { return true; }
        @Override public TileEntity createTileEntity(World world,IBlockState state) { return factory.get(); }
    }
    public static final class RfTile extends TileEntity implements IEnergyStorage {
        public int energy, capacity = 40000, maxExtract = Integer.MAX_VALUE, maxReceive = Integer.MAX_VALUE;
        public EnumFacing receiveSide;
        public boolean receive = true, extract;
        public RfTile() {} public RfTile(int cap,int energy,boolean receive,boolean extract) { capacity=cap;this.energy=energy;this.receive=receive;this.extract=extract; }
        public RfTile limitExtraction(int max) { maxExtract=max; return this; }
        public int receiveEnergy(int n,boolean simulate) { requireTileThread(this);int v=receive?Math.max(0,Math.min(Math.min(n,maxReceive),capacity-energy)):0;if(!simulate)energy+=v;return v; }
        public int extractEnergy(int n,boolean simulate) { requireTileThread(this);int v=extract?Math.max(0,Math.min(Math.min(n,maxExtract),energy)):0;if(!simulate)energy-=v;return v; }
        public int getEnergyStored(){requireTileThread(this);return energy;} public int getMaxEnergyStored(){requireTileThread(this);return capacity;} public boolean canExtract(){requireTileThread(this);return extract;} public boolean canReceive(){requireTileThread(this);return receive;}
        @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){requireTileThread(this);return cap==CapabilityEnergy.ENERGY
                ? (receiveSide == null || receiveSide == side ? CapabilityEnergy.ENERGY.cast(this) : null) :super.getCapability(cap,side);}
    }
    public static final class IdleTile extends TileEntity {
        int feQueries;
        @Override public <T>T getCapability(Capability<T> capability,EnumFacing side) {
            if(capability==CapabilityEnergy.ENERGY) feQueries++;
            return super.getCapability(capability,side);
        }
    }
    public static final class EuTile extends TileEntity implements IEnergySink,ITickable {
        public double energy, capacity=10000, demandLimit=1024, largestPacket; public int tier=1; private boolean registered;
        public void update(){if(!world.isRemote&&!registered){MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));registered=true;}}
        public boolean acceptsEnergyFrom(IEnergyEmitter emitter,EnumFacing side){requireTileThread(this);return true;} public int getSinkTier(){requireTileThread(this);return tier;}
        public double getDemandedEnergy(){requireTileThread(this);return Math.min(demandLimit,capacity-energy);} public double injectEnergy(EnumFacing side,double amount,double tier){requireTileThread(this);energy+=amount;largestPacket=Math.max(largestPacket,amount);return 0;}
        private void unload(){if(registered&&!world.isRemote){MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));registered=false;}}
        @Override public void invalidate(){unload();super.invalidate();} @Override public void onChunkUnload(){unload();super.onChunkUnload();}
    }
    public static final class MjTile extends TileEntity {
        public long energy; private final Port port=new Port(); private final MjCapabilityHelper caps=new MjCapabilityHelper(port);
        private final class Port implements IMjReceiver,IMjPassiveProvider,IMjReadable {
            public boolean canConnect(IMjConnector c){return true;} public long getPowerRequested(){return 100*MjAPI.MJ-energy;}
            public long receivePower(long n,boolean sim){long a=Math.min(n,getPowerRequested());if(!sim)energy+=a;return n-a;}
            public long extractPower(long min,long max,boolean sim){long a=Math.min(max,energy);if(a<min)return 0;if(!sim)energy-=a;return a;}
            public long getStored(){return energy;}public long getCapacity(){return 100*MjAPI.MJ;}
        }
        @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){T nativeCap=caps.getCapability(cap,side);return nativeCap!=null?nativeCap:super.getCapability(cap,side);}
    }
}
