package dev.mcmod.zhenghuocore.energy;

import dev.mcmod.zhenghuocore.CoreConfig;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversionLedgerTest {
    private static final class Store implements NativePort {
        long value, cap = 1000000;
        public boolean canReceive() { return true; }
        public boolean canExtract() { return true; }
        public long receive(long n, boolean simulate) { long a = Math.min(n, cap - value); if (!simulate) value += a; return a; }
        public long extract(long n, boolean simulate) { long a = Math.min(n, value); if (!simulate) value -= a; return a; }
        public long stored() { return value; }
        public long capacity() { return cap; }
    }
    @Test public void randomizedRoundTripsConserveEnergyInNativeStorage() {
        for (long scale : new long[]{1, 7, 10, 40, 10000, 1000000, 7000000, 10000000}) {
            Store store = new Store(); ConversionLedger ledger = new ConversionLedger(); Random random = new Random(1701 + scale);
            long netRf = 0;
            for (int i = 0; i < 10000; i++) {
                int amount = random.nextInt(5000);
                long before = store.value;
                boolean receive = random.nextBoolean();
                int predicted = receive ? ledger.receive(amount, scale, store, true) : ledger.extract(amount, scale, store, true);
                assertEquals(before, store.value);
                int actual = receive ? ledger.receive(amount, scale, store, false) : ledger.extract(amount, scale, store, false);
                assertEquals(predicted, actual); netRf += receive ? actual : -actual;
                assertEquals(netRf * EnergyBuffer.MICRO, store.value * scale);
                assertTrue(store.value >= 0 && store.value <= store.cap);
            }
        }
    }
    @Test public void subManaRequestsLeaveEnergyAtTheSource() {
        Store store = new Store(); ConversionLedger ledger = new ConversionLedger(); long scale = 10 * EnergyBuffer.MICRO;
        for (int i = 0; i < 10; i++) assertEquals(0, ledger.receive(1, scale, store, false));
        assertEquals(0, store.value);
        assertEquals(10, ledger.receive(19, scale, store, false));
        assertEquals(1, store.value);
        for (int i = 0; i < 10; i++) assertEquals(0, ledger.extract(1, scale, store, false));
        assertEquals(1, store.value);
        assertEquals(10, ledger.extract(19, scale, store, false));
        assertEquals(0, store.value);
    }
    @Test public void fullNativeStorageAlwaysRefusesFurtherEnergy() {
        Store store = new Store(); store.value = store.cap; ConversionLedger ledger = new ConversionLedger();
        for (long scale : new long[]{1, 7, 10, 40, EnergyBuffer.MICRO, 10 * EnergyBuffer.MICRO}) {
            assertEquals(0, ledger.receive(Integer.MAX_VALUE, scale, store, true));
            assertEquals(0, ledger.receive(Integer.MAX_VALUE, scale, store, false));
            assertEquals(store.cap, store.value);
            assertEquals(ledger.capacity(scale, store), ledger.stored(scale, store));
        }
    }
    @Test public void microMjRemaindersStayInTheOriginalDevice() {
        Store store = new Store(); ConversionLedger ledger = new ConversionLedger();
        store.value = 999999;
        assertEquals(0, ledger.extract(100, 7, store, false));
        assertEquals(999999, store.value);
        store.value = 1000000;
        assertEquals(7, ledger.extract(100, 7, store, false));
        assertEquals(0, store.value);
        assertEquals(0, ledger.receive(6, 7, store, false));
        assertEquals(7, ledger.receive(7, 7, store, false));
        assertEquals(1000000, store.value);
    }
    @Test public void unifiedMjRatioUsesTwentyFiveThousandMicroMjPerRf() {
        assertEquals(40, CoreConfig.rfPerMj);
        assertEquals(CoreConfig.rfPerMj, 10 * CoreConfig.rfPerEu);
        assertEquals(CoreConfig.rfPerMj, 4 * CoreConfig.rfPerMana);
        Store store = new Store(); ConversionLedger ledger = new ConversionLedger();
        assertEquals(1, ledger.receive(1, CoreConfig.rfPerMj, store, false));
        assertEquals(25000, store.value);
        assertEquals(1, ledger.extract(1, CoreConfig.rfPerMj, store, false));
        assertEquals(0, store.value);
        store.value = 24999;
        assertEquals(0, ledger.extract(1, CoreConfig.rfPerMj, store, false));
        assertEquals(24999, store.value);
        store.value = 1000000;
        assertEquals(40, ledger.extract(100, CoreConfig.rfPerMj, store, false));
        assertEquals(0, store.value);
    }
    @Test public void partialNativeRoomCannotBecomeExtraAdapterCapacity() {
        Store store = new Store(); store.cap = 1; ConversionLedger ledger = new ConversionLedger();
        assertEquals(0, ledger.receive(100, 7, store, true));
        assertEquals(0, ledger.receive(100, 7, store, false));
        assertEquals(0, store.value);
        assertEquals(0, ledger.capacity(7, store));
    }
    @Test public void storedAndCapacityDoNotDependOnPreviousConversions() {
        Store store = new Store(); store.cap = 100; ConversionLedger ledger = new ConversionLedger();
        long scale = 10 * EnergyBuffer.MICRO;
        assertEquals(20, ledger.receive(29, scale, store, false));
        assertEquals(10, ledger.extract(19, scale, store, false));
        assertEquals(10, ledger.stored(scale, store));
        assertEquals(1000, ledger.capacity(scale, store));
    }
    @Test public void sharedTickBudgetDoesNotResetOnSimulation() {
        TransferBudget b = new TransferBudget(); assertEquals(4096, b.room(10, 4096, true));
        b.spend(10, 3000, true); assertEquals(1096, b.room(10, 4096, true));
        assertEquals(1096, b.room(10, 4096, true)); assertEquals(4096, b.room(10, 4096, false));
        assertEquals(4096, b.room(11, 4096, true));
    }
    @Test public void corruptedPersistenceIsBounded() {
        EnergyBuffer buffer = new EnergyBuffer(100); buffer.restore(Long.MAX_VALUE); assertEquals(buffer.capacity(), buffer.stored());
        buffer.restore(-1); assertEquals(0, buffer.stored());
    }
}
