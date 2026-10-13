package dev.mcmod.zhenghuocore.compat;

import dev.mcmod.zhenghuocore.CoreConfig;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class EuCompatibilityTest {
    private int previousRatio;
    @Before public void rememberRatio() { previousRatio = CoreConfig.rfPerEu; }
    @After public void restoreRatio() { CoreConfig.rfPerEu = previousRatio; }

    @Test public void defaultRatioRetainsIntegerBoundaryValues() {
        CoreConfig.rfPerEu = 4;
        for (int rf : new int[]{0, 1, 4, 8192, Integer.MAX_VALUE})
            assertEquals(rf, EuCompatibility.exactRf(rf / 4.0, rf));
    }
    @Test public void largeSevenAndFiftyThreeRatiosTolerateOnlyRoundingNoise() {
        CoreConfig.rfPerEu = 7;
        int below = 2147480002;
        assertTrue(below / 7.0 * 7 < below);
        assertEquals(below, EuCompatibility.exactRf(below / 7.0, below));
        CoreConfig.rfPerEu = 53;
        int above = 2147480005;
        assertTrue(above / 53.0 * 53 > above);
        assertEquals(above, EuCompatibility.exactRf(above / 53.0, above));
    }
    @Test public void configuredIntegerRoundTripsKeepTheExactFeAmount() {
        Random random = new Random(1907);
        for (int ratio : new int[]{4, 7, 53}) {
            CoreConfig.rfPerEu = ratio;
            for (int i = 0; i < 10000; i++) {
                int rf = random.nextInt(Integer.MAX_VALUE);
                assertEquals(rf, EuCompatibility.exactRf(rf / (double) ratio, rf));
            }
        }
    }
    @Test public void genuineFractionalFeIsStillRejectedAtSmallAndLargeValues() {
        for (int ratio : new int[]{4, 7, 53}) {
            CoreConfig.rfPerEu = ratio;
            rejects(1.25 / ratio, Integer.MAX_VALUE);
            rejects((2147480000.0 + .25) / ratio, Integer.MAX_VALUE);
            rejects((2147480000.0 + .001) / ratio, Integer.MAX_VALUE);
        }
    }
    @Test public void valuesAboveTheRequestedMaximumAreNotClampedIntoSuccess() {
        for (int ratio : new int[]{4, 7, 53}) {
            CoreConfig.rfPerEu = ratio;
            rejects(101.0 / ratio, 100);
            rejects(2147480005.0 / ratio, 2147480004);
            rejects(((double) Integer.MAX_VALUE + 1) / ratio, Integer.MAX_VALUE);
            rejects(100.25 / ratio, 100);
        }
    }
    @Test public void negativeAndNonFiniteTransactionsAreRejected() {
        CoreConfig.rfPerEu = 7;
        rejects(-Double.MIN_VALUE, Integer.MAX_VALUE);
        rejects(-1, Integer.MAX_VALUE);
        rejects(Double.NaN, Integer.MAX_VALUE);
        rejects(Double.POSITIVE_INFINITY, Integer.MAX_VALUE);
        rejects(Double.NEGATIVE_INFINITY, Integer.MAX_VALUE);
        rejects(Double.MAX_VALUE, Integer.MAX_VALUE);
    }
    private static void rejects(double eu, int maximum) {
        try {
            EuCompatibility.exactRf(eu, maximum);
            fail("Accepted a non-integer or out-of-bounds FE transaction: " + eu);
        } catch (IllegalStateException expected) { }
    }
}
