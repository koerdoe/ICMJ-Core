package dev.mcmod.zhenghuocore.compat;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IManaReceiver;
import vazkii.botania.api.mana.spark.*;
import vazkii.botania.common.entity.EntityManaBurst;
import vazkii.botania.common.entity.EntitySpark;

/** Called only by the four patched Botania consumers, leaving all native receivers unchanged. */
public final class ManaReceiverHooks {
    private ManaReceiverHooks() { }
    public static boolean isReceiver(Object value) { return receiver(value) != null; }
    public static IManaReceiver receiver(Object value) { return resolve(value, null); }
    public static IManaReceiver impactReceiver(Object value, RayTraceResult hit) { return resolve(value, hit == null ? null : hit.sideHit); }
    /** The spreader's native fake burst must cache the port on the face it actually hit. */
    public static TileEntity collisionView(TileEntity tile,EntityManaBurst burst,RayTraceResult hit) {
        if(!burst.isFake() || tile instanceof IManaReceiver) return tile;
        IManaReceiver receiver=impactReceiver(tile,hit);
        return receiver instanceof RfManaReceiver ? (RfManaReceiver)receiver : tile;
    }
    private static IManaReceiver resolve(Object value, EnumFacing side) {
        if (value instanceof IManaReceiver) return (IManaReceiver) value;
        if (!(value instanceof TileEntity)) return null;
        TileEntity tile = (TileEntity) value;
        TileCompatibility state = tile.getCapability(TileCompatibility.CAPABILITY, null);
        if (state == null) return null;
        boolean rf = state.fe(null)!=null;
        if (!rf) for (EnumFacing face : EnumFacing.values()) if (state.fe(face) != null) { rf = true; break; }
        if (!rf) return null;
        int index = side == null ? 6 : side.getIndex();
        if (state.manaReceivers[index] == null) state.manaReceivers[index] = new RfManaReceiver(state, side);
        RfManaReceiver receiver = (RfManaReceiver) state.manaReceivers[index];
        receiver.syncLocation();
        return receiver;
    }
    public static boolean isSparkAttachable(Object value) { return sparkAttachable(value) != null; }
    public static ISparkAttachable sparkAttachable(Object value) {
        if (value instanceof ISparkAttachable) return (ISparkAttachable) value;
        IManaReceiver receiver = resolve(value, null);
        return receiver instanceof RfManaReceiver ? (RfManaReceiver) receiver : null;
    }
    public static int receiveSpark(IManaReceiver receiver,int offered) {
        if(receiver instanceof RfManaReceiver) return ((RfManaReceiver)receiver).receive(offered);
        receiver.recieveMana(offered); return offered;
    }
    /** Resize the original payload before Botania subtracts startingMana from the spreader. */
    public static EntityManaBurst limitBurst(EntityManaBurst burst,IManaReceiver receiver) {
        if(burst==null || !(receiver instanceof RfManaReceiver)) return burst;
        int mana=((RfManaReceiver)receiver).simulateReceive(burst.getMana());
        if(mana<=0) return null;
        burst.setMana(mana); burst.setStartingMana(mana); return burst;
    }
    public static void receiveBurst(IManaReceiver receiver,int offered,EntityManaBurst burst) {
        if(!(receiver instanceof RfManaReceiver)) { receiver.recieveMana(offered); return; }
        int accepted=((RfManaReceiver)receiver).receive(Math.min(offered,burst.getMana()));
        burst.setMana(burst.getMana()-accepted);
    }
    /** Unaccepted mana stays in the moving original burst and follows its native lifetime/loss. */
    public static void finishBurstImpact(EntityManaBurst burst,RayTraceResult hit) {
        if(!burst.isFake() && burst.getMana()>0 && hit.typeOfHit==RayTraceResult.Type.BLOCK
                && impactReceiver(((Entity)burst).world.getTileEntity(hit.getBlockPos()),hit) instanceof RfManaReceiver) return;
        ((Entity)burst).setDead();
    }
    public static ISparkAttachable tickSpark(ISparkAttachable receiver, EntitySpark target) {
        if (!(receiver instanceof RfManaReceiver) || ((Entity)target).world.isRemote
                || receiver.isFull() || target.getUpgrade() == SparkUpgradeType.ISOLATED) return receiver;
        // The native spark already resolved its attachment; no tile sweep or spark rediscovery is needed.
        Entity entity = target;
        for (ISparkEntity source : SparkHelper.getSparksAround(entity.world, entity.posX, entity.posY + entity.height / 2.0, entity.posZ)) {
            if (source != target && source.getUpgrade() == SparkUpgradeType.NONE && source.getAttachedTile() instanceof IManaPool) {
                source.registerTransfer(target);
            }
        }
        return receiver;
    }
}
