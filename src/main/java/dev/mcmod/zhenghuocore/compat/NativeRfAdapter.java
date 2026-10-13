package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.energy.*;
import java.util.function.Supplier;
import net.minecraftforge.energy.IEnergyStorage;

final class NativeRfAdapter implements IEnergyStorage {
    private final TileCompatibility owner;
    private final Supplier<NativePort> ports;
    private final long scale;
    NativeRfAdapter(TileCompatibility owner, Supplier<NativePort> ports, long scale) { this.owner = owner; this.ports = ports; this.scale = scale; }
    int rfQuantum() {
        long a = scale, b = EnergyBuffer.MICRO;
        while (b != 0) { long r = a % b; a = b; b = r; }
        return (int) (scale / a);
    }
    @Override public int receiveEnergy(int max, boolean simulate) {
        NativePort port = ports.get();
        if (!owner.live() || port == null || max <= 0) return 0;
        int accepted = owner.ledger.receive(max, scale, port, simulate);
        if (!simulate && accepted > 0) owner.tile.markDirty();
        return accepted;
    }
    @Override public int extractEnergy(int max, boolean simulate) {
        NativePort port = ports.get();
        if (!owner.live() || port == null || max <= 0) return 0;
        int extracted = owner.ledger.extract(max, scale, port, simulate);
        if (!simulate && extracted > 0) owner.tile.markDirty();
        return extracted;
    }
    @Override public int getEnergyStored() { NativePort p = ports.get(); return p == null ? 0 : owner.ledger.stored(scale, p); }
    @Override public int getMaxEnergyStored() { NativePort p = ports.get(); return p == null ? 0 : owner.ledger.capacity(scale, p); }
    @Override public boolean canExtract() { NativePort p = ports.get(); return p != null && p.canExtract(); }
    @Override public boolean canReceive() { NativePort p = ports.get(); return p != null && p.canReceive(); }
}
