package dev.mcmod.zhenghuocore.validation;

import dev.mcmod.zhenghuocore.CoreConfig;
import dev.mcmod.zhenghuocore.block.TileStarlightGenerator;
import ic2.api.energy.tile.IEnergySink;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Real Flux network fixture. Invoke each check on the server after at least 20 natural ticks. */
public final class FluxValidation {
    private TileEntity plug, point, battery;
    private TileStarlightGenerator source;
    private Object network;
    private Class<?> networkApi, connectorApi, tileClass;
    private double suppliedEu, blockedEu;

    public void setup(WorldServer world, BlockPos plugPos) throws Exception {
        networkApi = Class.forName("sonar.fluxnetworks.api.network.IFluxNetwork");
        connectorApi = Class.forName("sonar.fluxnetworks.api.tiles.IFluxConnector");
        tileClass = Class.forName("sonar.fluxnetworks.common.tileentity.TileFluxCore");
        Class<?> dataClass = Class.forName("sonar.fluxnetworks.common.data.FluxNetworkData");
        Object data = dataClass.getMethod("get").invoke(null);
        Map<?, ?> networks = (Map<?, ?>) dataClass.getField("networks").get(data);
        Field nextId = dataClass.getField("uniqueID");
        int id = Math.max(1, nextId.getInt(data));
        while (networks.containsKey(id)) id++;
        nextId.setInt(data, id + 1);
        Class<?> security = Class.forName("sonar.fluxnetworks.api.network.SecurityType");
        Class<?> energy = Class.forName("sonar.fluxnetworks.api.utils.EnergyType");
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000004301");
        network = Class.forName("sonar.fluxnetworks.common.connection.FluxNetworkServer")
                .getConstructor(int.class, String.class, security, int.class, UUID.class, energy, String.class)
                .newInstance(id, "Zhenghuo Flux validation", security.getField("PUBLIC").get(null),
                        0x3399ff, owner, energy.getField("RF").get(null), "");
        dataClass.getMethod("addNetwork", networkApi).invoke(data, network);

        source = (TileStarlightGenerator) place(world, plugPos.west(), "zhenghuocore:starlight_generator");
        world.setBlockState(plugPos.west().up(), Blocks.STONE.getDefaultState());
        plug = place(world, plugPos, "fluxnetworks:fluxplug");
        point = place(world, plugPos.east(4), "fluxnetworks:fluxpoint");
        battery = place(world, plugPos.east(5), "cyclicmagic:battery");
        for (TileEntity tile : new TileEntity[]{plug, point}) {
            tileClass.getField("networkID").setInt(tile, id);
            tileClass.getField("playerUUID").set(tile, owner);
        }
        require(rf(battery).getEnergyStored() == 0, "Fresh Cyclic receiver starts empty");
        // Native tile ticks register connections; Flux's own server tick runs its network.
    }

    public void startTransfer() throws Exception {
        require((boolean) connectorApi.getMethod("isChunkLoaded").invoke(plug), "Real Flux Plug is loaded");
        require(((IEnergySink) plug).getDemandedEnergy() > 0, "Connected Flux requests real FE room");
        suppliedEu = 4096;
        source.receiveStarlight(suppliedEu / CoreConfig.euPerStarlight);
        require(source.getStoredEu() == suppliedEu, "Roofed source has only the test energy");
    }

    public void checkTransferAndSetZeroLimit() throws Exception {
        long receivedRf = rf(battery).getEnergyStored();
        long fluxRf = rf(plug).getEnergyStored() + (long) rf(point).getEnergyStored();
        double total = source.getStoredEu() + (receivedRf + fluxRf) / (double) CoreConfig.rfPerEu;
        require(receivedRf > 0, "Actual EU source powers real Flux Plug/Point/Cyclic receiver");
        require(Math.abs(total - suppliedEu) < 0.000001, "EU-to-Flux transfer conserves source, native Flux buffers and receiver");
        tileClass.getField("disableLimit").setBoolean(plug, false);
        tileClass.getField("limit").setLong(plug, 0);
        source.receiveStarlight(4096 / CoreConfig.euPerStarlight);
        blockedEu = source.getStoredEu();
    }

    public void checkBlocked(String condition) {
        require(((IEnergySink) plug).getDemandedEnergy() == 0, condition + ": no EU demand");
        require(source.getStoredEu() == blockedEu, condition + ": source retains its energy");
        require(((IEnergySink) plug).injectEnergy(EnumFacing.WEST, 32, 1) == 32,
                condition + ": direct EU input is fully rejected");
    }

    public void disconnect() throws Exception {
        networkApi.getMethod("queueConnectionRemoval", connectorApi, boolean.class).invoke(network, plug, false);
        connectorApi.getMethod("disconnect", networkApi).invoke(plug, network);
        tileClass.getField("limit").setLong(plug, Integer.MAX_VALUE);
        blockedEu = source.getStoredEu();
    }

    public void reconnectWithFullReceiver() throws Exception {
        IEnergyStorage receiver = rf(battery);
        for (int i = 0; i < 100000 && receiver.receiveEnergy(Integer.MAX_VALUE, true) > 0; i++)
            require(receiver.receiveEnergy(Integer.MAX_VALUE, false) > 0, "Fill real receiver through native FE");
        require(receiver.receiveEnergy(Integer.MAX_VALUE, true) == 0, "Receiver actually refuses more FE");
        networkApi.getMethod("queueConnectionAddition", connectorApi).invoke(network, plug);
        blockedEu = source.getStoredEu();
    }

    private static TileEntity place(WorldServer world, BlockPos pos, String name) {
        Block block = Block.REGISTRY.getObject(new ResourceLocation(name));
        require(block != null && block != Blocks.AIR, "Actual block exists: " + name);
        world.setBlockToAir(pos);
        world.setBlockState(pos, block.getDefaultState());
        TileEntity tile = world.getTileEntity(pos);
        require(tile != null, "Actual tile exists: " + name);
        return tile;
    }

    private static IEnergyStorage rf(TileEntity tile) {
        IEnergyStorage energy = tile.getCapability(CapabilityEnergy.ENERGY, null);
        if (energy == null) energy = tile.getCapability(CapabilityEnergy.ENERGY, EnumFacing.WEST);
        require(energy != null, "Real FE view exists");
        return energy;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Flux validation: " + message);
    }
}
