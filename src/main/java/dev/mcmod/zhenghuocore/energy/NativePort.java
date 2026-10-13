package dev.mcmod.zhenghuocore.energy;

/** All amounts are native integer units; callbacks return accepted/extracted amounts, never remainders. */
public interface NativePort {
    boolean canReceive();
    boolean canExtract();
    long receive(long amount, boolean simulate);
    long extract(long amount, boolean simulate);
    long stored();
    long capacity();
}
