package dev.mcmod.zhenghuocore.compat;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.IEnergyNetEventReceiver;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.energy.tile.IMetaDelegate;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Discovers only positions whose lifecycle or connection state actually changed. */
public final class CompatibilityEvents implements IWorldEventListener, IEnergyNetEventReceiver {
    public static final CompatibilityEvents INSTANCE = new CompatibilityEvents();
    private static final ResourceLocation KEY = new ResourceLocation("zhenghuocore", "energy");
    private static final EnumFacing[] FACES = EnumFacing.values();
    private static final class WorldState {
        final Map<BlockPos, RfGridNode> nodes = new HashMap<>();
        final Set<BlockPos> pending = new LinkedHashSet<>();
    }
    private final Map<World, WorldState> worlds = new IdentityHashMap<>();
    private CompatibilityEvents() { }

    @SubscribeEvent public void attach(AttachCapabilitiesEvent<TileEntity> event) {
        event.addCapability(KEY, new TileCompatibility(event.getObject()));
    }
    @SubscribeEvent public void loaded(WorldEvent.Load event) {
        if (!event.getWorld().isRemote) {
            worlds.computeIfAbsent(event.getWorld(), w -> new WorldState());
            event.getWorld().addEventListener(this);
        }
    }
    /** Called after the world's actual onLoad dispatch, including deferred additions and chunk reloads. */
    public static void tileLoaded(TileEntity tile) {
        World world = tile.getWorld();
        if (world != null && !world.isRemote) {
            INSTANCE.worlds.computeIfAbsent(world, w -> new WorldState());
            connectionsChanged(world, tile.getPos());
        }
    }
    /** Also available to dynamic ports which do not issue a normal block update. */
    public static void connectionsChanged(World world, BlockPos pos) {
        if (world.isRemote) return;
        // Some configuration packets notify blocks from their network handler; registry work stays on the server thread.
        if (world instanceof WorldServer && !((WorldServer)world).isCallingFromMinecraftThread()) {
            BlockPos changed = pos.toImmutable();
            ((WorldServer)world).addScheduledTask(() -> connectionsChanged(world, changed)); return;
        }
        WorldState state = INSTANCE.worlds.get(world);
        if (state != null) state.pending.add(pos.toImmutable());
    }
    public static void tileInvalidated(TileEntity tile) {
        World world = tile.getWorld();
        if (world == null || world.isRemote) return;
        BlockPos pos = tile.getPos();
        WorldState state = INSTANCE.worlds.get(world);
        if (state != null) remove(state, pos, tile);
        INSTANCE.changedAround(world, pos);
    }
    private void changedAround(World world, BlockPos pos) {
        if (world.isRemote) return;
        connectionsChanged(world, pos);
        for (EnumFacing face : FACES) connectionsChanged(world, pos.offset(face));
    }
    @Override public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        if (oldState == newState) connectionsChanged(world, pos);
        else changedAround(world, pos);
    }
    @SubscribeEvent public void neighborsChanged(BlockEvent.NeighborNotifyEvent event) {
        connectionsChanged(event.getWorld(), event.getPos());
        for (EnumFacing face : event.getNotifiedSides()) connectionsChanged(event.getWorld(), event.getPos().offset(face));
    }
    @Override public void onAdd(IEnergyTile tile) { nativeNodeChanged(tile); }
    @Override public void onRemove(IEnergyTile tile) { nativeNodeChanged(tile); }
    private void nativeNodeChanged(IEnergyTile tile) {
        if (tile instanceof RfGridNode) return;
        if (tile instanceof IMetaDelegate) {
            for (IEnergyTile port : ((IMetaDelegate)tile).getSubTiles())
                changedAround(EnergyNet.instance.getWorld(port), EnergyNet.instance.getPos(port));
        } else changedAround(EnergyNet.instance.getWorld(tile), EnergyNet.instance.getPos(tile));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void tick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || event.phase != TickEvent.Phase.START || EnergyNet.instance == null) return;
        WorldState state = worlds.get(event.world);
        if (state == null || state.pending.isEmpty()) return;
        // Grid notifications raised during refresh belong to the next batch; empty ticks allocate nothing.
        BlockPos[] changed = state.pending.toArray(new BlockPos[0]);
        state.pending.clear();
        for (BlockPos pos : changed) refresh(event.world, state, pos);
    }
    private void refresh(World world, WorldState state, BlockPos pos) {
        // CHECK never creates a missing tile or loads an unloaded chunk.
        TileEntity tile = world.isBlockLoaded(pos, false)
                ? world.getChunk(pos).getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) : null;
        RfGridNode node = state.nodes.get(pos);
        if (node != null && node.owner != tile) { remove(state, pos, null); node = null; }
        if (tile == null || tile.isInvalid()) { remove(state, pos, null); return; }
        String type = tile.getClass().getName();
        IEnergyTile existing = EnergyNet.instance.getTile(world, pos);
        if (tile instanceof IEnergyTile || type.startsWith("ic2.") || type.startsWith("dev.mcmod.ic2grid.")
                || existing != null && !(existing instanceof RfGridNode) || !hasEuNeighbor(world, pos)) {
            remove(state, pos, null); return;
        }
        TileCompatibility adapter = tile.getCapability(TileCompatibility.CAPABILITY, null);
        int sides = adapter == null ? 0 : RfGridNode.sideMask(tile);
        if (sides == 0) { remove(state, pos, null); return; }
        if (node == null) {
            node = new RfGridNode(tile, adapter, sides);
            state.nodes.put(pos, node); EnergyNet.instance.addTile(node);
        } else if (node.updateSides(sides)) {
            EnergyNet.instance.removeTile(node); EnergyNet.instance.addTile(node);
        }
    }
    private boolean hasEuNeighbor(World world, BlockPos pos) {
        for (EnumFacing face : FACES) {
            BlockPos neighbor = pos.offset(face);
            if (!world.isBlockLoaded(neighbor, false)) continue;
            IEnergyTile tile = EnergyNet.instance.getTile(world, neighbor);
            if (tile != null && !(tile instanceof RfGridNode)) return true;
        }
        return false;
    }
    private static void remove(WorldState state, BlockPos pos, TileEntity expected) {
        RfGridNode node = state.nodes.get(pos);
        if (node != null && (expected == null || node.owner == expected)) {
            state.nodes.remove(pos);
            if (EnergyNet.instance != null) EnergyNet.instance.removeTile(node);
        }
    }
    @SubscribeEvent public void chunkUnloaded(ChunkEvent.Unload event) {
        WorldState state = worlds.get(event.getWorld());
        if (state == null) return;
        for (Map.Entry<BlockPos, TileEntity> entry : event.getChunk().getTileEntityMap().entrySet()) {
            remove(state, entry.getKey(), entry.getValue()); state.pending.remove(entry.getKey());
        }
    }
    @SubscribeEvent public void unloaded(WorldEvent.Unload event) {
        World world = event.getWorld(); world.removeEventListener(this);
        WorldState state = worlds.remove(world);
        if (state != null && EnergyNet.instance != null) for (RfGridNode node : state.nodes.values()) EnergyNet.instance.removeTile(node);
    }

    @Override public void notifyLightSet(BlockPos pos) { }
    @Override public void markBlockRangeForRenderUpdate(int a, int b, int c, int d, int e, int f) { }
    @Override public void playSoundToAllNearExcept(EntityPlayer p, SoundEvent s, SoundCategory c, double x, double y, double z, float v, float pitch) { }
    @Override public void playRecord(SoundEvent sound, BlockPos pos) { }
    @Override public void spawnParticle(int id, boolean range, double x, double y, double z, double dx, double dy, double dz, int... args) { }
    @Override public void spawnParticle(int id, boolean range, boolean count, double x, double y, double z, double dx, double dy, double dz, int... args) { }
    @Override public void onEntityAdded(Entity entity) { }
    @Override public void onEntityRemoved(Entity entity) { }
    @Override public void broadcastSound(int id, BlockPos pos, int data) { }
    @Override public void playEvent(EntityPlayer player, int id, BlockPos pos, int data) { }
    @Override public void sendBlockBreakProgress(int id, BlockPos pos, int progress) { }
}
