package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.block.TileStarlightGenerator;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.NodeStats;
import java.util.Locale;
import java.util.function.Function;
import mcjty.theoneprobe.api.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/** Loaded only through TOP's optional IMC callback; energy stays in the EU network. */
public final class ProbeCompatibility implements Function<ITheOneProbe, Void>, IProbeInfoProvider {
    @Override public Void apply(ITheOneProbe probe) { probe.registerProvider(this); return null; }
    @Override public String getID() { return "zhenghuocore:energy"; }
    @Override public void addProbeInfo(ProbeMode mode, IProbeInfo info, EntityPlayer player,
            World world, IBlockState block, IProbeHitData hit) {
        TileEntity tile = world.getTileEntity(hit.getPos());
        if (Loader.isModLoaded("astralsorcery") && tile instanceof TileStarlightGenerator) {
            TileStarlightGenerator generator = (TileStarlightGenerator) tile;
            info.progress((long) generator.getStoredEu(), (long) generator.getCapacityEu(),
                    info.defaultProgressStyle().suffix(" EU").numberFormat(NumberFormat.COMMAS)
                            .filledColor(0xffcc2222).alternateFilledColor(0xffaa1111));
            info.text(TextStyleClass.INFO + IProbeInfo.STARTLOC + "zhenghuocore.probe.max_output" + IProbeInfo.ENDLOC
                    + String.format(Locale.ROOT, " %.0f V × %.0f A", generator.getOutputVoltage(), generator.getMaximumCurrent()));
            NodeStats stats = EnergyNet.instance == null ? null : EnergyNet.instance.getNodeStats(generator);
            info.text(TextStyleClass.INFO + IProbeInfo.STARTLOC + "zhenghuocore.probe.actual_output" + IProbeInfo.ENDLOC
                    + String.format(Locale.ROOT, " %.2f EU/t", stats == null ? 0 : stats.getEnergyOut()));
        }
    }
}
