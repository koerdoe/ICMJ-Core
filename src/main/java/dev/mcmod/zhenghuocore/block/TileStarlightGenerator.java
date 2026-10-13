package dev.mcmod.zhenghuocore.block;

import dev.mcmod.ic2grid.energy.ElectricalSource;
import dev.mcmod.ic2grid.energy.PowerUnits;
import dev.mcmod.zhenghuocore.CoreConfig;
import dev.mcmod.zhenghuocore.energy.EnergyBuffer;
import dev.mcmod.zhenghuocore.energy.TransferBudget;
import hellfirepvp.astralsorcery.common.constellation.IWeakConstellation;
import hellfirepvp.astralsorcery.common.constellation.distribution.ConstellationSkyHandler;
import hellfirepvp.astralsorcery.common.util.SkyCollectionHelper;
import hellfirepvp.astralsorcery.common.starlight.transmission.IPrismTransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.ITransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.base.SimpleTransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.registry.TransmissionClassRegistry;
import hellfirepvp.astralsorcery.common.tile.base.TileReceiverBase;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyAcceptor;
import java.util.Locale;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

public final class TileStarlightGenerator extends TileReceiverBase implements ElectricalSource, MachineControl {
    private final EnergyBuffer buffer = new EnergyBuffer(CoreConfig.capacityRf);
    private final TransferBudget output = new TransferBudget();
    private boolean registered;
    public double getStoredEu() { return buffer.stored() / (double) scale(); }
    public double getCapacityEu() { return buffer.capacity() / (double) scale(); }
    // Keep the historical NBT counting unit fixed: changing FE/EU must not rescale stored EU.
    private long scale() { return 4 * EnergyBuffer.MICRO; }
    private boolean live() { return world != null && !world.isRemote && !isInvalid(); }
    @Override public void update() {
        super.update(); // Astral Sorcery owns transmission network placement and persistence.
        if (live() && !registered) { MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this)); registered = true; }
        if (live()) receiveStarlight(getAmbientStarlight());
    }
    public double getAmbientStarlight() {
        if (!live() || CoreConfig.ambientStarlightPerTick <= 0 || !world.provider.hasSkyLight() || !world.canSeeSky(pos.up())) return 0;
        ConstellationSkyHandler sky = ConstellationSkyHandler.getInstance();
        if (sky.getWorldHandler(world) == null) return 0;
        double strength = Math.max(0, sky.getCurrentDaytimeDistribution(world));
        double local = Math.max(0, 1 + .3 * SkyCollectionHelper.getSkyNoiseDistribution(world, pos));
        return CoreConfig.ambientStarlightPerTick * strength * local;
    }
    public void receiveStarlight(double amount) {
        if (!live() || !Double.isFinite(amount) || amount <= 0) return;
        long wanted = EnergyBuffer.floorUnits(amount * CoreConfig.euPerStarlight, scale());
        if (buffer.insert(wanted, false) > 0) markDirty();
    }
    @Override public double getOutputVoltage() { return CoreConfig.starlightVoltage; }
    @Override public double getMaximumCurrent() { return CoreConfig.starlightCurrent; }
    @Override public boolean isVoltageFixed() { return false; }
    @Override public double getVoltageStep() { return 1; }
    @Override public int getSourceTier() { return PowerUnits.tierForVoltage(getOutputVoltage()); }
    @Override public double getOfferedEnergy() {
        if (!live()) return 0;
        double packet = getOutputVoltage() * getMaximumCurrent();
        long whole = EnergyBuffer.ceilUnits(packet, scale());
        // Offer actual available energy. The grid decides whether a complete configured or adjusted output fits.
        return Math.min(buffer.stored(), output.room(world.getTotalWorldTime(), whole, false)) / (double) scale();
    }
    @Override public void drawEnergy(double amount) {
        if (!live() || !Double.isFinite(amount) || amount <= 0) return;
        long drawn = buffer.extract(EnergyBuffer.ceilUnits(amount, scale()), false);
        output.spend(world.getTotalWorldTime(), drawn, false); markDirty();
    }
    @Override public boolean emitsEnergyTo(IEnergyAcceptor acceptor, EnumFacing side) { return true; }
    @Override public ITransmissionReceiver provideEndpoint(BlockPos position) { return new Receiver(position); }
    @Override public String getUnLocalizedDisplayName() { return "tile.zhenghuocore.starlight_generator.name"; }
    @Override public String status() {
        return String.format(Locale.ROOT, "%.3f EU | %.0f V × %d A", buffer.stored() / (double) scale(), getOutputVoltage(), CoreConfig.starlightCurrent);
    }
    @Override public void readCustomNBT(NBTTagCompound tag) { super.readCustomNBT(tag); buffer.restore(tag.getLong("MicroRf")); }
    @Override public void writeCustomNBT(NBTTagCompound tag) { super.writeCustomNBT(tag); tag.setLong("MicroRf", buffer.stored()); tag.removeTag("Disabled"); }
    private void unload() {
        if (registered && world != null && !world.isRemote) { MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this)); registered = false; }
    }
    @Override public void beforeBreak() { unload(); onBreak(); }
    @Override public void invalidate() { unload(); super.invalidate(); }
    @Override public void onChunkUnload() { unload(); super.onChunkUnload(); }
    public static final class Receiver extends SimpleTransmissionReceiver {
        public Receiver(BlockPos position) { super(position); }
        @Override public TransmissionClassRegistry.TransmissionProvider getProvider() { return PROVIDER; }
        @Override public void onStarlightReceive(World world, boolean loaded, IWeakConstellation constellation, double amount) {
            if (!loaded || world.isRemote || !world.isBlockLoaded(getLocationPos())) return;
            TileEntity tile = world.getTileEntity(getLocationPos());
            if (tile instanceof TileStarlightGenerator) ((TileStarlightGenerator) tile).receiveStarlight(amount);
        }
    }
    public static final TransmissionClassRegistry.TransmissionProvider PROVIDER = new TransmissionClassRegistry.TransmissionProvider() {
        @Override public IPrismTransmissionNode provideEmptyNode() { return new Receiver(null); }
        @Override public String getIdentifier() { return "zhenghuocore:starlight_generator"; }
    };
}
