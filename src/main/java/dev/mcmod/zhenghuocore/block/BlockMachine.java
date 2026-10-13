package dev.mcmod.zhenghuocore.block;

import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public final class BlockMachine extends Block {
    private final Supplier<? extends TileEntity> factory;
    public BlockMachine(String name, Supplier<? extends TileEntity> factory) {
        super(Material.IRON);
        this.factory = factory;
        setRegistryName("zhenghuocore", name); setTranslationKey("zhenghuocore." + name);
        setHardness(3); setResistance(10); setHarvestLevel("pickaxe", 1); setCreativeTab(CreativeTabs.REDSTONE);
        setLightLevel(0.65F);
    }
    // Existing saves may contain the former facing metadata; every value is the same machine.
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState(); }
    @Override public int getMetaFromState(IBlockState state) { return 0; }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return factory.get(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        // Leave wands and linking tools to the owning mod.
        if (!player.getHeldItem(hand).isEmpty()) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof MachineControl)) return false;
        if (!world.isRemote) {
            MachineControl control = (MachineControl) tile;
            player.sendStatusMessage(new TextComponentString(control.status()), false);
        }
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof MachineControl) ((MachineControl) tile).beforeBreak();
        super.breakBlock(world, pos, state);
    }
}
