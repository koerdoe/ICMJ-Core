package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.energy.NativePort;
import net.minecraft.tileentity.TileEntity;
import vazkii.botania.api.mana.IManaCollector;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IManaReceiver;
import vazkii.botania.api.mana.spark.ISparkAttachable;

final class ManaCompatibility {
    static NativePort port(TileEntity tile) {
        if (!(tile instanceof IManaReceiver)) return null;
        IManaReceiver receiver = (IManaReceiver) tile;
        // These APIs expose a verifiable capacity and reversible storage (pools, spreaders, collectors).
        if (!(receiver instanceof IManaCollector) && !(receiver instanceof IManaPool && receiver instanceof ISparkAttachable)) return null;
        return new NativePort() {
            private int mana() { return Math.max(0, receiver.getCurrentMana()); }
            private int space() {
                if (receiver.isFull()) return 0;
                return Math.max(0, receiver instanceof IManaCollector ? ((IManaCollector) receiver).getMaxMana() - mana()
                        : ((ISparkAttachable) receiver).getAvailableSpaceForMana());
            }
            @Override public boolean canReceive() { return true; }
            @Override public boolean canExtract() { return true; }
            @Override public long receive(long amount, boolean simulate) {
                int n = (int) Math.min(Math.max(0, amount), space());
                if (simulate || n == 0) return n;
                int before = mana(); receiver.recieveMana(n);
                int accepted = Math.max(0, Math.min(n, mana() - before));
                if (accepted > 0) tile.markDirty(); return accepted;
            }
            @Override public long extract(long amount, boolean simulate) {
                int n = (int) Math.min(Math.max(0, amount), mana());
                if (simulate || n == 0) return n;
                int before = mana(); receiver.recieveMana(-n);
                int taken = Math.max(0, Math.min(n, before - mana()));
                if (taken > 0) tile.markDirty(); return taken;
            }
            @Override public long stored() { return mana(); }
            @Override public long capacity() { return (long) mana() + space(); }
        };
    }
}
