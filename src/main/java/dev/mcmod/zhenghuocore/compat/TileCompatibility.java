package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.CoreConfig;
import dev.mcmod.zhenghuocore.energy.*;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Loader;

public final class TileCompatibility implements ICapabilitySerializable<NBTTagCompound> {
    @CapabilityInject(TileCompatibility.class) public static Capability<TileCompatibility> CAPABILITY;
    public final TileEntity tile;
    final ConversionLedger ledger = new ConversionLedger();
    private final IEnergyStorage[] adapters = new IEnergyStorage[7];
    final Object[] manaReceivers = new Object[7];
    private boolean querying;
    public TileCompatibility(TileEntity tile) { this.tile = tile; }
    public static void register() {
        CapabilityManager.INSTANCE.register(TileCompatibility.class, new Capability.IStorage<TileCompatibility>() {
            @Override public NBTBase writeNBT(Capability<TileCompatibility> capability, TileCompatibility instance, EnumFacing side) { return instance.serializeNBT(); }
            @Override public void readNBT(Capability<TileCompatibility> capability, TileCompatibility instance, EnumFacing side, NBTBase tag) {
                if (tag instanceof NBTTagCompound) instance.deserializeNBT((NBTTagCompound) tag);
            }
        }, () -> new TileCompatibility(null));
    }
    public boolean live() { return tile != null && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid(); }
    <T> T nativeCapability(Capability<T> capability, EnumFacing side) {
        if (tile == null || querying) return null;
        querying = true;
        try { return tile.getCapability(capability, side); } finally { querying = false; }
    }
    IEnergyStorage nativeRf(EnumFacing side) { return nativeCapability(CapabilityEnergy.ENERGY, side); }
    /** Every compatibility consumer uses this same FE endpoint, including adapted native energy. */
    public IEnergyStorage fe(EnumFacing side) {
        if (tile == null || querying) return null;
        IEnergyStorage original = nativeRf(side);
        if (original != null) return original;
        int index = side == null ? 6 : side.getIndex();
        if (EuCompatibility.isEndpoint(this)) {
            if (!(adapters[index] instanceof EuCompatibility)) adapters[index] = new EuCompatibility(this, side);
            return adapters[index];
        }
        if (port(side) == null) return null;
        if (adapters[index] == null) adapters[index] = new NativeRfAdapter(this, () -> port(side), scale());
        return adapters[index];
    }
    private NativePort port(EnumFacing side) {
        NativePort mana = Loader.isModLoaded("botania") ? ManaCompatibility.port(tile) : null;
        return mana != null ? mana : Loader.isModLoaded("buildcraftcore") ? MjCompatibility.port(this, side) : null;
    }
    private long scale() {
        return Loader.isModLoaded("botania") && ManaCompatibility.port(tile) != null ? CoreConfig.rfPerMana * EnergyBuffer.MICRO : CoreConfig.rfPerMj;
    }
    @Override public boolean hasCapability(Capability<?> capability, EnumFacing side) { return getCapability(capability, side) != null; }
    @Override public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CAPABILITY) return CAPABILITY.cast(this);
        if (querying || tile == null) return null;
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(fe(side));
        }
        if (Loader.isModLoaded("buildcraftcore") && MjCompatibility.mjCapability(capability)) return MjCompatibility.capability(this, capability, side);
        return null;
    }
    @Override public NBTTagCompound serializeNBT() {
        return new NBTTagCompound();
    }
    @Override public void deserializeNBT(NBTTagCompound tag) {
        // Retired 0.1.2 compatibility balances are never restored as additional device capacity.
    }
}
