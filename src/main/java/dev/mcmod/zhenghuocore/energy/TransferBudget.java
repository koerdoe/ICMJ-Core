package dev.mcmod.zhenghuocore.energy;

/** A shared per-tick budget, including pushes, pulls and every face. Simulations never spend it. */
public final class TransferBudget {
    private long tick = Long.MIN_VALUE, input, output;
    private void refresh(long now) { if (now != tick) { tick = now; input = output = 0; } }
    public long room(long now, long limit, boolean receiving) {
        refresh(now); return Math.max(0, limit - (receiving ? input : output));
    }
    public void spend(long now, long amount, boolean receiving) {
        refresh(now);
        if (amount < 0) throw new IllegalArgumentException("amount");
        if (receiving) input += amount; else output += amount;
    }
}
