package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.ic2grid.energy.ElectricalSource;
import dev.mcmod.ic2grid.energy.PowerUnits;
import dev.mcmod.zhenghuocore.CoreConfig;
import dev.mcmod.zhenghuocore.VoltageConfig;
import dev.mcmod.zhenghuocore.energy.EnergyBuffer;
import dev.mcmod.zhenghuocore.energy.RfEuOutput;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.*;
import ic2.api.info.ILocatable;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** A real endpoint in the existing EU graph. The grid alone selects and debits all generators. */
public final class RfGridNode implements IEnergySink, ElectricalSource, ILocatable {
    private static final EnumFacing[] FACES = EnumFacing.values();
    final TileEntity owner;
    private final TileCompatibility state;
    private IEnergyStorage quotedSource;
    private int quotedRf;
    private RfEuOutput quotedOutput = RfEuOutput.NONE;
    private long quoteTick = Long.MIN_VALUE, drawnTick = Long.MIN_VALUE;
    private int sides;
    public RfGridNode(TileEntity owner, TileCompatibility state) { this(owner, state, sideMask(owner)); }
    RfGridNode(TileEntity owner, TileCompatibility state, int sides) { this.owner = owner; this.state = state; this.sides = sides; }
    @Override public World getWorldObj() { return owner.getWorld(); }
    @Override public BlockPos getPosition() { return owner.getPos(); }
    private boolean live() { return state.live() && getWorldObj().isBlockLoaded(getPosition()) && getWorldObj().getTileEntity(getPosition()) == owner; }
    private long now() { return getWorldObj().getTotalWorldTime(); }
    private IEnergyStorage energy(EnumFacing side) { return owner.getCapability(CapabilityEnergy.ENERGY, side); }
    static int sideMask(TileEntity owner) {
        int mask = 0;
        for (EnumFacing face : FACES) {
            IEnergyStorage energy = owner.getCapability(CapabilityEnergy.ENERGY, face);
            if (energy == null) continue;
            if (energy.canReceive()) mask |= 1 << face.getIndex();
            if (energy.canExtract()) mask |= 1 << (face.getIndex() + 6);
        }
        return mask;
    }
    boolean updateSides(int next) { boolean changed = next != sides; sides = next; return changed; }
    @Override public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
        return !(emitter instanceof RfGridNode) && side != null && (sides & (1 << side.getIndex())) != 0;
    }
    @Override public boolean emitsEnergyTo(IEnergyAcceptor acceptor, EnumFacing side) {
        return !(acceptor instanceof RfGridNode) && side != null && (sides & (1 << (side.getIndex() + 6))) != 0;
    }
    @Override public int getSinkTier() { return VoltageConfig.tier(owner); }
    @Override public int getSourceTier() { return PowerUnits.tierForVoltage(getOutputVoltage()); }
    @Override public double getOutputVoltage() { return currentOutput().voltage; }
    @Override public double getMaximumCurrent() { return currentOutput().current; }
    @Override public boolean isVoltageFixed() { return false; }
    @Override public double getVoltageStep() {
        if (!(quotedSource instanceof NativeRfAdapter)) return 1;
        int quantum = ((NativeRfAdapter) quotedSource).rfQuantum();
        long perVolt = (long) CoreConfig.rfPerEu * Math.max(1, currentOutput().current);
        long a = quantum, b = perVolt;
        while (b != 0) { long r = a % b; a = b; b = r; }
        return quantum / a;
    }
    private RfEuOutput currentOutput() {
        return live() && quoteTick == now() ? quotedOutput : RfEuOutput.NONE;
    }
    @Override public double getDemandedEnergy() {
        if (!live()) return 0;
        int maxRf = Integer.MAX_VALUE, room = 0;
        for (EnumFacing face : FACES) {
            IEnergyStorage rf = energy(face);
            if (rf != null && rf.canReceive()) room = Math.max(room, Math.max(0, Math.min(maxRf, rf.receiveEnergy(maxRf, true))));
        }
        // Max, not sum: sided FE views commonly refer to the same physical storage.
        return room / (double) CoreConfig.rfPerEu;
    }
    @Override public double injectEnergy(EnumFacing side, double amount, double tier) {
        if (!live() || side == null || !Double.isFinite(amount) || amount <= 0) return amount;
        IEnergyStorage rf = energy(side);
        if (rf == null || !rf.canReceive()) return amount;
        int rfOffer = (int) Math.min(Integer.MAX_VALUE, EnergyBuffer.floorUnits(amount, CoreConfig.rfPerEu));
        if (rfOffer == 0) return amount;
        int accepted = Math.max(0, Math.min(rfOffer, rf.receiveEnergy(rfOffer, false)));
        if (accepted > 0) owner.markDirty();
        return Math.max(0, amount - accepted / (double) CoreConfig.rfPerEu);
    }
    @Override public double getOfferedEnergy() {
        if (!live() || drawnTick == now()) return 0;
        quotedSource = null;
        quotedRf = 0;
        quotedOutput = RfEuOutput.NONE;
        quoteTick = now();
        long maxVoltage = (long) PowerUnits.voltageForTier(VoltageConfig.tier(owner));
        int current = VoltageConfig.current(owner);
        for (EnumFacing face : FACES) {
            BlockPos neighborPos = getPosition().offset(face);
            if (!getWorldObj().isBlockLoaded(neighborPos)) continue;
            IEnergyTile neighbor = EnergyNet.instance.getTile(getWorldObj(), neighborPos);
            if (!(neighbor instanceof IEnergyAcceptor) || neighbor instanceof RfGridNode) continue;
            IEnergyStorage rf = energy(face);
            if (rf == null || !rf.canExtract() || !((IEnergyAcceptor) neighbor).acceptsEnergyFrom(this, face.getOpposite())) continue;
            int available = Math.max(0, rf.extractEnergy(Integer.MAX_VALUE, true));
            RfEuOutput output = RfEuOutput.fromAvailableRf(available, CoreConfig.rfPerEu,
                    maxVoltage, current);
            // Different faces often expose the same storage. Quote one eligible source, never sum them.
            if (output.power <= quotedOutput.power) continue;
            quotedSource = rf;
            quotedOutput = output;
            quotedRf = (int) (output.power * CoreConfig.rfPerEu);
        }
        // The solver reads U and I immediately after this offer. All three describe the same quote.
        return quotedOutput.power;
    }
    @Override public void drawEnergy(double amount) {
        if (!live() || quotedSource == null || quoteTick != now() || drawnTick == now() || !Double.isFinite(amount) || amount <= 0) return;
        int cost = (int) Math.min(quotedRf, EnergyBuffer.ceilUnits(amount, CoreConfig.rfPerEu));
        int extracted = quotedSource.extractEnergy(cost, false);
        if (extracted != cost) throw new IllegalStateException("RF endpoint changed after EU dispatch simulation at " + getPosition());
        drawnTick = now(); owner.markDirty();
    }
}
