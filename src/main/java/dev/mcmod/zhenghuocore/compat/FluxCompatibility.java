package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.CoreConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** The existing Flux EU sink delegates to its own sided FE input and network limits. */
public final class FluxCompatibility {
    private FluxCompatibility() { }

    private static boolean live(TileEntity tile) {
        return tile != null && tile.getWorld() != null && !tile.getWorld().isRemote && !tile.isInvalid();
    }

    public static double demandedEnergy(TileEntity tile) {
        if (!live(tile)) return 0;
        int available = 0;
        for (EnumFacing side : EnumFacing.values()) {
            IEnergyStorage rf = tile.getCapability(CapabilityEnergy.ENERGY, side);
            if (rf != null && rf.canReceive())
                available = Math.max(available, Math.max(0, rf.receiveEnergy(Integer.MAX_VALUE, true)));
        }
        // Each side refers to the same Flux transfer buffer; summing would invent demand.
        return available / (double) CoreConfig.rfPerEu;
    }

    public static double injectEnergy(TileEntity tile, EnumFacing side, double amount, double voltage) {
        if (!live(tile) || side == null || !Double.isFinite(amount) || amount <= 0) return amount;
        IEnergyStorage rf = tile.getCapability(CapabilityEnergy.ENERGY, side);
        if (rf == null || !rf.canReceive()) return amount;
        int offered = (int) Math.min(Integer.MAX_VALUE, Math.floor(amount * CoreConfig.rfPerEu));
        if (offered == 0) return amount;
        int accepted = Math.max(0, Math.min(offered, rf.receiveEnergy(offered, false)));
        return Math.max(0, amount - accepted / (double) CoreConfig.rfPerEu);
    }
}
