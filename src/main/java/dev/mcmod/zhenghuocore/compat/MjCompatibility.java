package dev.mcmod.zhenghuocore.compat;

import buildcraft.api.mj.*;
import buildcraft.lib.engine.TileEngineBase_BC8;
import dev.mcmod.zhenghuocore.CoreConfig;
import dev.mcmod.zhenghuocore.energy.EnergyBuffer;
import dev.mcmod.zhenghuocore.energy.NativePort;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.IEnergyStorage;

final class MjCompatibility {
    static NativePort port(TileCompatibility owner, EnumFacing side) {
        IMjReceiver receiver = owner.nativeCapability(MjAPI.CAP_RECEIVER, side);
        IMjPassiveProvider provider = owner.nativeCapability(MjAPI.CAP_PASSIVE_PROVIDER, side);
        IMjReadable readable = owner.nativeCapability(MjAPI.CAP_READABLE, side);
        TileEntity tile = owner.tile;
        TileEngineBase_BC8 engine = tile instanceof TileEngineBase_BC8 && (side == null || ((TileEngineBase_BC8) tile).getCurrentFacing() == side)
                ? (TileEngineBase_BC8) tile : null;
        if (receiver == null && provider == null && engine == null) return null;
        return new NativePort() {
            @Override public boolean canReceive() { return receiver != null && receiver.canReceive(); }
            @Override public boolean canExtract() { return provider != null || engine != null; }
            @Override public long receive(long amount, boolean simulate) {
                if (amount <= 0 || !canReceive()) return 0;
                long offer = Math.min(amount, Math.max(0, receiver.getPowerRequested()));
                return offer - Math.max(0, Math.min(offer, receiver.receivePower(offer, simulate)));
            }
            @Override public long extract(long amount, boolean simulate) {
                if (amount <= 0) return 0;
                if (provider != null) return Math.max(0, Math.min(amount, provider.extractPower(0, amount, simulate)));
                if (engine != null && engine.isRedstonePowered) return Math.max(0, Math.min(amount, engine.extractPower(0, amount, !simulate)));
                return 0;
            }
            @Override public long stored() { return readable != null ? readable.getStored() : engine != null ? engine.getEnergyStored() : 0; }
            @Override public long capacity() { return readable != null ? readable.getCapacity() : engine != null ? engine.getMaxPower() : receiver != null ? receiver.getPowerRequested() : 0; }
        };
    }
    static boolean mjCapability(Capability<?> c) { return c == MjAPI.CAP_RECEIVER || c == MjAPI.CAP_PASSIVE_PROVIDER || c == MjAPI.CAP_CONNECTOR || c == MjAPI.CAP_READABLE; }
    static <T> T capability(TileCompatibility owner, Capability<T> cap, EnumFacing side) {
        IEnergyStorage rf = owner.fe(side);
        if (rf == null || !mjCapability(cap)) return null;
        RfPort port = new RfPort(owner, side);
        if (cap == MjAPI.CAP_RECEIVER && !rf.canReceive() || cap == MjAPI.CAP_PASSIVE_PROVIDER && !rf.canExtract()) return null;
        if (cap == MjAPI.CAP_RECEIVER) return MjAPI.CAP_RECEIVER.cast(port);
        if (cap == MjAPI.CAP_PASSIVE_PROVIDER) return MjAPI.CAP_PASSIVE_PROVIDER.cast(port);
        if (cap == MjAPI.CAP_READABLE) return MjAPI.CAP_READABLE.cast(port);
        return MjAPI.CAP_CONNECTOR.cast(port);
    }
    /** Transfer only batches representable by both integer APIs, with no conversion balance. */
    static long transfer(IEnergyStorage rf, long maximum, long minimum, int rfPerMj, boolean receiving, boolean simulate) {
        if (rf == null || maximum <= 0 || minimum < 0 || maximum < minimum
                || (receiving ? !rf.canReceive() : !rf.canExtract())) return 0;
        if (rfPerMj <= 0) throw new IllegalArgumentException("rfPerMj must be positive");
        long common = rfPerMj, other = EnergyBuffer.MICRO;
        while (other != 0) { long next = common % other; common = other; other = next; }
        long rfBatch = rfPerMj / common, mjBatch = EnergyBuffer.MICRO / common;
        if (rf instanceof NativeRfAdapter) {
            int quantum = ((NativeRfAdapter) rf).rfQuantum();
            common = rfBatch; other = quantum;
            while (other != 0) { long next = common % other; common = other; other = next; }
            long factor = quantum / common;
            rfBatch *= factor; mjBatch *= factor;
        }
        int quote = (int) (Math.min(maximum / mjBatch, Integer.MAX_VALUE / rfBatch) * rfBatch);
        while (quote > 0) {
            int accepted = receiving ? rf.receiveEnergy(quote, true) : rf.extractEnergy(quote, true);
            int exact = (int) (Math.max(0, Math.min(quote, accepted)) / rfBatch * rfBatch);
            // Another FE adapter may require its own batch (e.g. an integer Mana).
            if (exact == quote) break;
            quote = exact;
        }
        long possible = quote / rfBatch * mjBatch;
        if (possible < minimum || quote == 0) return 0;
        if (simulate) return possible;
        int actual = receiving ? rf.receiveEnergy(quote, false) : rf.extractEnergy(quote, false);
        if (actual < 0 || actual > quote || actual % rfBatch != 0)
            throw new IllegalStateException("FE interface broke its simulated MJ batch: " + actual + " / " + quote);
        return actual / rfBatch * mjBatch;
    }
    /** Native RF blocks become ordinary MJ endpoints too, allowing engines/pipes to push directly. */
    private static final class RfPort implements IMjReceiver, IMjPassiveProvider, IMjReadable {
        private final TileCompatibility owner; private final EnumFacing side;
        RfPort(TileCompatibility owner, EnumFacing side) { this.owner = owner; this.side = side; }
        private IEnergyStorage rf() { return owner.fe(side); }
        @Override public boolean canConnect(IMjConnector other) { return other != null; }
        @Override public boolean canReceive() { IEnergyStorage r = rf(); return r != null && r.canReceive(); }
        @Override public long getPowerRequested() {
            IEnergyStorage r = rf();
            if (!owner.live() || r == null || !r.canReceive()) return 0;
            return transfer(r, Long.MAX_VALUE, 0, CoreConfig.rfPerMj, true, true);
        }
        @Override public long receivePower(long amount, boolean simulate) {
            if (amount <= 0 || !owner.live()) return amount;
            long paid = transfer(rf(), amount, 0, CoreConfig.rfPerMj, true, simulate);
            if (!simulate && paid > 0) owner.tile.markDirty();
            return amount - paid;
        }
        @Override public long extractPower(long min, long max, boolean simulate) {
            IEnergyStorage r = rf();
            if (!owner.live() || r == null || !r.canExtract() || min < 0 || max < min) return 0;
            long actual = transfer(r, max, min, CoreConfig.rfPerMj, false, simulate);
            if (!simulate && actual > 0) owner.tile.markDirty();
            return actual;
        }
        @Override public long getStored() { IEnergyStorage r = rf(); return r == null ? 0 : (long) r.getEnergyStored() * EnergyBuffer.MICRO / CoreConfig.rfPerMj; }
        @Override public long getCapacity() { IEnergyStorage r = rf(); return r == null ? 0 : (long) r.getMaxEnergyStored() * EnergyBuffer.MICRO / CoreConfig.rfPerMj; }
    }
}
