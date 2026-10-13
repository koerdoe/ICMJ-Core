package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.energy.EnergyBuffer;
import java.util.Random;
import net.minecraftforge.energy.IEnergyStorage;
import org.junit.Test;
import static org.junit.Assert.*;

public class MjCompatibilityTest {
    private static final class Store implements IEnergyStorage {
        int energy, capacity = 100000, quantum = 1, commits;
        public int receiveEnergy(int maximum, boolean simulate) {
            int accepted = Math.min(maximum, capacity - energy) / quantum * quantum;
            if (!simulate) { energy += accepted; commits++; }
            return accepted;
        }
        public int extractEnergy(int maximum, boolean simulate) {
            int accepted = Math.min(maximum, energy) / quantum * quantum;
            if (!simulate) { energy -= accepted; commits++; }
            return accepted;
        }
        public int getEnergyStored() { return energy; }
        public int getMaxEnergyStored() { return capacity; }
        public boolean canReceive() { return true; }
        public boolean canExtract() { return true; }
    }
    @Test public void sevenRfPerMjUsesExactBatchesInBothDirections() {
        Store rf = new Store();
        assertEquals(0, MjCompatibility.transfer(rf, 999999, 0, 7, true, false));
        assertEquals(0, rf.energy);
        assertEquals(1000000, MjCompatibility.transfer(rf, 1999999, 0, 7, true, true));
        assertEquals(0, rf.energy);
        assertEquals(1000000, MjCompatibility.transfer(rf, 1999999, 0, 7, true, false));
        assertEquals(7, rf.energy);
        assertEquals(0, MjCompatibility.transfer(rf, 999999, 0, 7, false, false));
        assertEquals(7, rf.energy);
        assertEquals(1000000, MjCompatibility.transfer(rf, 1999999, 0, 7, false, false));
        assertEquals(0, rf.energy);
    }
    @Test public void defaultFortyRfRatioStillAcceptsSingleRfBatches() {
        Store rf = new Store();
        assertEquals(25000, MjCompatibility.transfer(rf, 49999, 0, 40, true, false));
        assertEquals(1, rf.energy);
        assertEquals(25000, MjCompatibility.transfer(rf, 49999, 0, 40, false, false));
        assertEquals(0, rf.energy);
    }
    @Test public void fullOrSubBatchSpaceDoesNotChargeMj() {
        Store rf = new Store(); rf.capacity = 6;
        assertEquals(0, MjCompatibility.transfer(rf, Long.MAX_VALUE, 0, 7, true, false));
        assertEquals(0, rf.energy);
        rf.energy = rf.capacity;
        assertEquals(0, MjCompatibility.transfer(rf, Long.MAX_VALUE, 0, 7, true, false));
        assertEquals(6, rf.energy);
        assertEquals(0, rf.commits);
    }
    @Test public void anotherAdaptersWholeManaBatchIsRespected() {
        Store rf = new Store(); rf.quantum = 10; rf.capacity = 100;
        // 14 MJ offers 98 RF, but only 70 RF is a whole number of both 7-RF and 10-RF batches.
        assertEquals(10000000, MjCompatibility.transfer(rf, 14000000, 0, 7, true, true));
        assertEquals(0, rf.energy);
        assertEquals(10000000, MjCompatibility.transfer(rf, 14000000, 0, 7, true, false));
        assertEquals(70, rf.energy);
        assertEquals(0, MjCompatibility.transfer(rf, 14000000, 10000001, 7, false, false));
        assertEquals(70, rf.energy);
        assertEquals(10000000, MjCompatibility.transfer(rf, 14000000, 10000000, 7, false, false));
        assertEquals(0, rf.energy);
    }
    @Test public void randomConfiguredRatiosConserveExactEnergyWithoutBalances() {
        for (int ratio : new int[]{1, 3, 7, 40, 53, 10000, Integer.MAX_VALUE}) {
            Store rf = new Store(); Random random = new Random(1234L + ratio);
            long netMj = 0;
            for (int i = 0; i < 1000; i++) {
                long maximum = random.nextInt(100000000);
                boolean input = random.nextBoolean();
                int before = rf.energy, commits = rf.commits;
                long simulated = MjCompatibility.transfer(rf, maximum, 0, ratio, input, true);
                assertEquals(before, rf.energy); assertEquals(commits, rf.commits);
                long actual = MjCompatibility.transfer(rf, maximum, 0, ratio, input, false);
                assertEquals(simulated, actual);
                netMj += input ? actual : -actual;
                assertEquals(netMj * ratio, (long) rf.energy * EnergyBuffer.MICRO);
            }
        }
    }
    @Test public void maximumRequestsCannotOverflowTheRfInterface() {
        Store rf = new Store(); rf.capacity = Integer.MAX_VALUE;
        for (int ratio : new int[]{1, 7, 40, Integer.MAX_VALUE}) {
            rf.energy = 0;
            long accepted = MjCompatibility.transfer(rf, Long.MAX_VALUE, 0, ratio, true, false);
            assertTrue(accepted > 0);
            assertEquals(accepted * ratio, (long) rf.energy * EnergyBuffer.MICRO);
            assertEquals(accepted, MjCompatibility.transfer(rf, Long.MAX_VALUE, 0, ratio, false, false));
            assertEquals(0, rf.energy);
        }
    }
}
