package dev.mcmod.zhenghuocore.energy;

import org.junit.Test;
import static org.junit.Assert.*;

public class RfEuOutputTest {
    @Test public void defaultIvAlwaysCapsVoltageAt8192AndCurrentAtOne() {
        for (long available : new long[]{1,8192,8193,8194,16385,32768,40000,Long.MAX_VALUE}) {
            RfEuOutput result=RfEuOutput.fromAvailableEu(available,8192,1);
            assertEquals(Math.min(available,8192),result.power);
            assertEquals(1,result.current); assertEquals(result.power,result.voltage);
        }
    }
    @Test public void configuredHvTwoAmpsCapsPowerAt1024() {
        RfEuOutput result=RfEuOutput.fromAvailableEu(40000,512,2);
        assertEquals(512,result.voltage); assertEquals(2,result.current); assertEquals(1024,result.power);
        result=RfEuOutput.fromAvailableEu(8194,8192,2);
        assertEquals(4097,result.voltage); assertEquals(8194,result.power);
    }
    @Test public void rfAndWholeCurrentRemaindersStayAtSource() {
        RfEuOutput result=RfEuOutput.fromAvailableRf(8193*4+3,4,8192,2);
        assertEquals(4096,result.voltage); assertEquals(2,result.current);
        assertEquals(7,8193*4+3-result.power*4);
        assertSame(RfEuOutput.NONE,RfEuOutput.fromAvailableRf(7,4,512,2));
    }
    @Test public void settingsNeverCreateEnergyOrExceedEitherCeiling() {
        for (int current : new int[]{1,2,3,4,16,1024,Integer.MAX_VALUE})
            for (int available=0;available<20000;available+=7) {
                RfEuOutput result=RfEuOutput.fromAvailableEu(available,512,current);
                assertTrue(result.power<=available); assertTrue(result.voltage<=512);
                assertEquals(result.voltage*result.current,result.power);
                if(result!=RfEuOutput.NONE) assertEquals(current,result.current);
            }
    }
    @Test public void emptyAndExtremeValuesCannotOverflow() {
        assertSame(RfEuOutput.NONE,RfEuOutput.fromAvailableEu(-1,8192,1));
        RfEuOutput result=RfEuOutput.fromAvailableEu(Long.MAX_VALUE,Long.MAX_VALUE,Integer.MAX_VALUE);
        assertTrue(result.power>0 && result.power<=Long.MAX_VALUE);
        result=RfEuOutput.fromAvailableRf(Integer.MAX_VALUE,1,2147483648L,1);
        assertEquals(Integer.MAX_VALUE,result.power); assertEquals(1,result.current);
    }
}
