package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.CoreConfig;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.energy.IEnergyStorage;
import vazkii.botania.api.mana.spark.ISparkAttachable;
import vazkii.botania.api.mana.spark.ISparkEntity;

/** Stable Botania view of an RF tile; never replaces or registers a world tile. */
public final class RfManaReceiver extends TileEntity implements ISparkAttachable {
    private static final EnumFacing[] ALL_FACES = {null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST};
    private final TileCompatibility state;
    private final EnumFacing[] faces;
    RfManaReceiver(TileCompatibility state, EnumFacing face) { this.state = state; this.faces = face == null ? ALL_FACES : new EnumFacing[]{face}; syncLocation(); }
    void syncLocation() { setWorld(state.tile.getWorld()); setPos(state.tile.getPos()); }
    private boolean live() { return state.live() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == state.tile; }
    private boolean accepts() {
        for (EnumFacing side : faces) { IEnergyStorage rf = state.fe(side); if (rf != null && rf.canReceive()) return true; }
        return false;
    }
    @Override public boolean canRecieveManaFromBursts() { return accepts(); }
    @Override public boolean isFull() { return getAvailableSpaceForMana() == 0; }
    @Override public int getAvailableSpaceForMana() {
        return transfer(Integer.MAX_VALUE,true,false);
    }
    @Override public int getCurrentMana() {
        return transfer(Integer.MAX_VALUE,true,true);
    }
    @Override public void recieveMana(int mana) {
        if (mana>0) receive(mana);
        else if (mana<0) {
            int requested=(int)Math.min(Integer.MAX_VALUE,-(long)mana);
            if (transfer(requested,true,true)!=requested) throw new IllegalArgumentException("Insufficient FE for mana withdrawal at "+pos);
            if (transfer(requested,false,true)!=requested) throw new IllegalStateException("FE changed during mana withdrawal at "+pos);
        }
    }
    /** Returns only whole mana accepted by the existing FE port; no adapter storage. */
    int receive(int mana) { return transfer(mana,false,false); }
    int simulateReceive(int mana) { return transfer(mana,true,false); }
    private int transfer(int mana,boolean simulate,boolean extract) {
        if (!live() || mana<=0) return 0;
        int offered=Math.min(mana,Integer.MAX_VALUE/CoreConfig.rfPerMana)*CoreConfig.rfPerMana;
        IEnergyStorage selected=null; int best=0;
        for (EnumFacing side:faces) {
            IEnergyStorage port=state.fe(side);
            if(port==null || (extract ? !port.canExtract() : !port.canReceive())) continue;
            int quote=offered;
            int capacity=port.getMaxEnergyStored();
            // Integer.MAX_VALUE can be a saturated view of a larger native store.
            if(!extract && capacity>0 && capacity<Integer.MAX_VALUE)
                quote=Math.min(quote,Math.max(0,capacity-port.getEnergyStored()));
            // Recheck the integer Mana boundary against the same native port (e.g. whole EU units).
            while(quote>0) {
                int accepted=Math.max(0,Math.min(quote,extract ? port.extractEnergy(quote,true) : port.receiveEnergy(quote,true)));
                int whole=accepted/CoreConfig.rfPerMana*CoreConfig.rfPerMana;
                if(whole==quote) break;
                quote=whole;
            }
            if(quote>best) { selected=port; best=quote; }
        }
        if(simulate || best==0) return best/CoreConfig.rfPerMana;
        int actual=Math.max(0,Math.min(best,extract ? selected.extractEnergy(best,false) : selected.receiveEnergy(best,false)));
        if(actual%CoreConfig.rfPerMana!=0) throw new IllegalStateException("FE port violated whole Mana transfer quote at "+pos);
        if(actual>0) state.tile.markDirty();
        return actual/CoreConfig.rfPerMana;
    }
    @Override public boolean canAttachSpark(ItemStack stack) { return accepts(); }
    @Override public void attachSpark(ISparkEntity spark) { }
    @Override public ISparkEntity getAttachedSpark() {
        if (world == null) return null;
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(pos.up()))) {
            if (entity instanceof ISparkEntity && !entity.isDead) return (ISparkEntity) entity;
        }
        return null;
    }
    @Override public boolean areIncomingTranfersDone() { return isFull(); }
}
