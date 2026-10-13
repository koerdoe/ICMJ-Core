package dev.mcmod.zhenghuocore;

import dev.mcmod.ic2grid.energy.PowerUnits;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.util.ResourceLocation;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class VoltageConfigTest {
    @Rule public TemporaryFolder files = new TemporaryFolder(new File("build"));
    @After public void reset() { VoltageConfig.load(new File(files.getRoot(), "reset.json")); }

    @Test public void firstStartCreatesIvAndEmptyWhitelist() throws Exception {
        File config = new File(files.getRoot(), "voltage.json");
        VoltageConfig.load(config);
        assertEquals(5, VoltageConfig.tier(new ResourceLocation("test:unlisted")));
        assertEquals(8192, PowerUnits.voltageForTier(VoltageConfig.defaultTier()), 0);
        assertEquals(1, VoltageConfig.current(new ResourceLocation("test:unlisted")));
        assertNull(VoltageConfig.override(new ResourceLocation("test:unlisted")));
        assertTrue(new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8).contains("\"IV\""));
    }

    @Test public void manualBlockOverridesDefaultAndReloadPreservesFile() throws Exception {
        File config = new File(files.getRoot(), "voltage.json");
        byte[] content = ("{\"defaultTier\":\"MV\",\"whitelist\":{\"test:machine\":\"HV\","
                + "\"other:machine\":\"ULV\",\"missing_mod:future_block\":\"luv\"}}\n").getBytes(StandardCharsets.UTF_8);
        Files.write(config.toPath(), content);
        for (int i=0; i<2; i++) {
            VoltageConfig.load(config);
            assertEquals(128, PowerUnits.voltageForTier(VoltageConfig.tier(new ResourceLocation("test:unlisted"))), 0);
            assertEquals(512, PowerUnits.voltageForTier(VoltageConfig.tier(new ResourceLocation("test:machine"))), 0);
            assertEquals(8, PowerUnits.voltageForTier(VoltageConfig.tier(new ResourceLocation("other:machine"))), 0);
            assertEquals("LuV", VoltageConfig.tierName(VoltageConfig.override(new ResourceLocation("missing_mod:future_block"))));
        }
        assertArrayEquals(content, Files.readAllBytes(config.toPath()));
    }

    @Test public void currentOverridesAreIndependentFromVoltageAndPersist() throws Exception {
        File config = new File(files.getRoot(), "currents.json");
        Files.write(config.toPath(), ("{\"defaultTier\":\"IV\",\"whitelist\":{\"test:hv\":\"HV\"},"
                + "\"defaultCurrent\":3,\"currentWhitelist\":{\"test:hv\":2,\"test:current_only\":4}}").getBytes(StandardCharsets.UTF_8));
        VoltageConfig.load(config);
        assertEquals(3, VoltageConfig.defaultCurrent());
        assertEquals(2, VoltageConfig.current(new ResourceLocation("test:hv")));
        assertEquals(3, VoltageConfig.tier(new ResourceLocation("test:hv")));
        assertEquals(4, VoltageConfig.current(new ResourceLocation("test:current_only")));
        assertEquals(5, VoltageConfig.tier(new ResourceLocation("test:current_only")));
        assertTrue(VoltageConfig.listed(new ResourceLocation("test:current_only")));
    }

    @Test public void invalidEntriesFailWithLocationWithoutOverwritingOrPartiallyApplying() throws Exception {
        File config = new File(files.getRoot(), "voltage.json");
        VoltageConfig.load(config);
        for (String content : new String[]{
                "{\"defaultTier\":5,\"whitelist\":{}}",
                "{\"defaultTier\":\"5\",\"whitelist\":{}}",
                "{\"defaultTier\":\"LV\",\"whitelist\":{\"test:machine\":\"TYPO\"}}",
                "{\"defaultTier\":\"LV\",\"whitelist\":{\"missing_namespace\":\"HV\"}}",
                "{\"defaultTier\":\"IV\",\"whitelist\":[]}",
                "{\"defaultTier\":\"IV\",\"whitelist\":{},\"defaultCurrent\":0}",
                "{\"defaultTier\":\"IV\",\"whitelist\":{},\"defaultCurrent\":1.5}",
                "{\"defaultTier\":\"IV\",\"whitelist\":{},\"defaultCurrent\":2147483648}",
                "{\"defaultTier\":\"IV\",\"whitelist\":{},\"currentWhitelist\":{\"test:machine\":-1}}", "{"}) {
            byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
            Files.write(config.toPath(), bytes);
            try { VoltageConfig.load(config); fail("Invalid settings accepted: " + content); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains(config.getAbsolutePath())); }
            assertEquals(5, VoltageConfig.defaultTier());
            assertNull(VoltageConfig.override(new ResourceLocation("test:machine")));
            assertArrayEquals(bytes, Files.readAllBytes(config.toPath()));
        }
    }
}
