package dev.mcmod.zhenghuocore.energy;

/** Integer source quote bounded by the configured voltage and current; remainders stay at the source. */
public final class RfEuOutput {
    public static final RfEuOutput NONE = new RfEuOutput(0, 0);
    public final long voltage, power;
    public final int current;

    private RfEuOutput(long voltage, int current) {
        this.voltage = voltage;
        this.current = current;
        this.power = voltage * current;
    }

    public static RfEuOutput fromAvailableEu(long availableEu, long maxVoltage, int current) {
        if (maxVoltage <= 0 || current <= 0) throw new IllegalArgumentException("Voltage and current must be positive");
        if (availableEu <= 0) return NONE;
        long voltage = Math.min(maxVoltage, availableEu / current);
        return voltage == 0 ? NONE : new RfEuOutput(voltage, current);
    }

    public static RfEuOutput fromAvailableRf(int availableRf, int rfPerEu, long maxVoltage, int current) {
        if (rfPerEu <= 0) throw new IllegalArgumentException("rfPerEu must be positive");
        return fromAvailableEu(Math.max(0, availableRf) / rfPerEu, maxVoltage, current);
    }
}
