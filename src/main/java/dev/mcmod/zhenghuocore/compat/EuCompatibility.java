package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.ic2grid.Ic2Grid;
import dev.mcmod.ic2grid.energy.PowerUnits;
import dev.mcmod.zhenghuocore.CoreConfig;
import dev.mcmod.zhenghuocore.VoltageConfig;
import dev.mcmod.zhenghuocore.block.TileStarlightGenerator;
import dev.mcmod.zhenghuocore.energy.RfEuOutput;
import ic2.api.energy.EnergyNet;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Loader;

/** FE view of the existing EU endpoint. All transactions and source budgets belong to the grid. */
final class EuCompatibility implements IEnergyStorage {
    private final TileCompatibility owner;
    private final EnumFacing side;
    EuCompatibility(TileCompatibility owner, EnumFacing side) { this.owner = owner; this.side = side; }
    static boolean isEndpoint(TileCompatibility owner) {
        if (owner.tile == null || owner.tile.getWorld() == null || EnergyNet.instance == null
                || EnergyNet.instance.getTile(owner.tile.getWorld(), owner.tile.getPos()) instanceof RfGridNode) return false;
        return Ic2Grid.GRID.canReceiveEnergy(owner.tile.getWorld(), owner.tile.getPos(), null)
                || Ic2Grid.GRID.canExtractEnergy(owner.tile.getWorld(), owner.tile.getPos(), null);
    }
    @Override public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!owner.live() || maxReceive <= 0 || !canReceive()) return 0;
        // FE push does not identify its source block. Use defaults here; physical grid sources
        // have a real owner and apply that block's whitelist in RfGridNode.
        RfEuOutput offer = RfEuOutput.fromAvailableRf(maxReceive, CoreConfig.rfPerEu,
                (long) PowerUnits.voltageForTier(VoltageConfig.defaultTier()), VoltageConfig.defaultCurrent());
        if (offer.power == 0) return 0;
        double accepted = Ic2Grid.GRID.receiveEnergy(owner.tile.getWorld(), owner.tile.getPos(), side,
                offer.power, offer.voltage, offer.current, 1, 1.0 / CoreConfig.rfPerEu, simulate);
        int result = exactRf(accepted, maxReceive);
        if (!simulate && result > 0) owner.tile.markDirty();
        return result;
    }
    @Override public int extractEnergy(int maxExtract, boolean simulate) {
        if (!owner.live() || maxExtract <= 0 || !canExtract()) return 0;
        double extracted = Ic2Grid.GRID.extractEnergy(owner.tile.getWorld(), owner.tile.getPos(), side,
                maxExtract / (double) CoreConfig.rfPerEu, PowerUnits.voltageForTier(VoltageConfig.defaultTier()),
                1.0 / CoreConfig.rfPerEu, simulate);
        int result = exactRf(extracted, maxExtract);
        if (!simulate && result > 0) owner.tile.markDirty();
        return result;
    }
    static int exactRf(double eu, int maximum) {
        double rf = eu * CoreConfig.rfPerEu;
        double integral = Math.rint(rf);
        // Division/multiplication at large FE values can differ by one representable double.
        double tolerance = Math.max(1.0e-7, 2 * Math.ulp(rf));
        if (!Double.isFinite(rf) || rf < 0 || integral > maximum || Math.abs(rf - integral) > tolerance)
            throw new IllegalStateException("EU grid returned a non-FE transaction: " + eu + " EU");
        return (int) integral;
    }
    private int displayRf(double eu) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0, eu * CoreConfig.rfPerEu)); }
    @Override public int getEnergyStored() {
        if (Loader.isModLoaded("astralsorcery") && owner.tile instanceof TileStarlightGenerator)
            return displayRf(((TileStarlightGenerator) owner.tile).getStoredEu());
        return owner.tile.getWorld() == null ? 0 : displayRf(Ic2Grid.GRID.getStoredEu(owner.tile.getWorld(), owner.tile.getPos()));
    }
    @Override public int getMaxEnergyStored() {
        if (Loader.isModLoaded("astralsorcery") && owner.tile instanceof TileStarlightGenerator)
            return displayRf(((TileStarlightGenerator) owner.tile).getCapacityEu());
        return owner.tile.getWorld() == null ? 0 : displayRf(Ic2Grid.GRID.getCapacityEu(owner.tile.getWorld(), owner.tile.getPos()));
    }
    @Override public boolean canExtract() {
        return owner.tile.getWorld() != null && Ic2Grid.GRID.canExtractEnergy(owner.tile.getWorld(), owner.tile.getPos(), side);
    }
    @Override public boolean canReceive() {
        return owner.tile.getWorld() != null && Ic2Grid.GRID.canReceiveEnergy(owner.tile.getWorld(), owner.tile.getPos(), side);
    }
}
