package dev.mcmod.zhenghuocore.energy;

/** Integer micro-RF ledger. Native micro-MJ and fractional EU never round up on intake. */
public final class EnergyBuffer {
    public static final long MICRO = 1_000_000L;
    private final long capacity;
    private long stored;
    public EnergyBuffer(long capacityRf) {
        if (capacityRf < 1 || capacityRf > Long.MAX_VALUE / MICRO) throw new IllegalArgumentException("capacity");
        capacity = capacityRf * MICRO;
    }
    public long stored() { return stored; }
    public long capacity() { return capacity; }
    public long room() { return capacity - stored; }
    public void restore(long value) { stored = Math.max(0, Math.min(capacity, value)); }
    public long insert(long amount, boolean simulate) {
        long accepted = Math.min(Math.max(0, amount), room());
        if (!simulate) stored += accepted;
        return accepted;
    }
    public long extract(long amount, boolean simulate) {
        long extracted = Math.min(Math.max(0, amount), stored);
        if (!simulate) stored -= extracted;
        return extracted;
    }
    public static long floorUnits(double amount, long scale) {
        if (!Double.isFinite(amount) || amount <= 0 || scale < 1) return 0;
        double value = amount * scale;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(value);
    }
    public static long ceilUnits(double amount, long scale) {
        if (!Double.isFinite(amount) || amount <= 0 || scale < 1) return 0;
        double value = amount * scale;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(value);
    }
}
