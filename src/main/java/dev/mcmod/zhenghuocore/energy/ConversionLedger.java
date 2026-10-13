package dev.mcmod.zhenghuocore.energy;

/** Converts only exact integer batches. Energy that cannot form a batch stays in its original device. */
public final class ConversionLedger {
    private static long clamp(long amount, long max) { return Math.max(0, Math.min(max, amount)); }
    public int receive(int maxRf, long scale, NativePort port, boolean simulate) {
        if (maxRf <= 0 || scale <= 0 || !port.canReceive()) return 0;
        return transfer(maxRf, scale, port, simulate, true);
    }
    public int extract(int maxRf, long scale, NativePort port, boolean simulate) {
        if (maxRf <= 0 || scale <= 0 || !port.canExtract()) return 0;
        return transfer(maxRf, scale, port, simulate, false);
    }
    private int transfer(int maxRf, long scale, NativePort port, boolean simulate, boolean receiving) {
        long common = gcd(scale, EnergyBuffer.MICRO);
        long rfBatch = scale / common, nativeBatch = EnergyBuffer.MICRO / common;
        long requested = (maxRf / rfBatch) * nativeBatch;
        if (requested == 0) return 0;
        long available = receiving ? port.receive(requested, true) : port.extract(requested, true);
        long exact = clamp(available, requested) / nativeBatch * nativeBatch;
        if (exact == 0) return 0;
        if (simulate) return (int) (exact / nativeBatch * rfBatch);
        long actual = receiving ? port.receive(exact, false) : port.extract(exact, false);
        if (actual < 0 || actual > exact || actual % nativeBatch != 0)
            throw new IllegalStateException("Native energy interface broke its simulated integer transfer: " + actual + " / " + exact);
        return (int) (actual / nativeBatch * rfBatch);
    }
    private static long gcd(long a, long b) {
        while (b != 0) { long next = a % b; a = b; b = next; }
        return a;
    }
    public int stored(long scale, NativePort port) { return rfCount(scale, port.stored()); }
    public int capacity(long scale, NativePort port) { return rfCount(scale, port.capacity()); }
    private int rfCount(long scale, long units) {
        double rf = Math.max(0, units) * (double) scale / EnergyBuffer.MICRO;
        return (int) Math.min(Integer.MAX_VALUE, rf);
    }
}
