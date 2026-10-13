package dev.mcmod.zhenghuocore;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CoreConfigTest {
    @Rule public TemporaryFolder files = new TemporaryFolder(new File("build"));
    private static Field minecraftHome;
    private static Object previousHome;

    @BeforeClass public static void initializeForgeConfigDirectory() throws Exception {
        // Configuration needs the game directory that the Forge launcher normally supplies.
        minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        previousHome = minecraftHome.get(null);
        minecraftHome.set(null, new File("build").getAbsoluteFile());
    }

    @AfterClass public static void restoreForgeConfigDirectory() throws Exception {
        minecraftHome.set(null, previousHome);
    }

    @After public void restoreDefaults() {
        CoreConfig.load(new File(files.getRoot(), "reset.cfg"));
    }

    @Test public void freshFileWritesRequestedDefaults() {
        File file = new File(files.getRoot(), "zhenghuocore.cfg");
        CoreConfig.load(file);
        Configuration saved = new Configuration(file);
        saved.load();
        assertEquals(4, saved.get("conversion", "rfPerEu", -1).getInt());
        assertEquals(40, saved.get("conversion", "rfPerMj", -1).getInt());
        assertEquals(10, saved.get("conversion", "rfPerMana", -1).getInt());
        assertEquals(8, saved.get("starlight", "euPerStarlight", -1.0).getDouble(), 0);
        assertEquals(8192, saved.get("starlight", "outputVoltage", -1).getInt());
        assertEquals(4, saved.get("starlight", "outputCurrent", -1).getInt());
        assertEquals(5, VoltageConfig.defaultTier());
        assertFalse(saved.hasCategory("eu"));
    }

    @Test public void editedRatiosAndStarlightSettingsSurviveReloadAndRetiredKeyRemoval() throws Exception {
        File file = new File(files.getRoot(), "custom.cfg");
        String cfg = "conversion {\n I:rfPerEu=8\n I:rfPerMj=7\n I:rfPerMana=3\n}\n"
                + "starlight {\n S:euPerStarlight=200000.0\n I:outputVoltage=4096\n I:outputCurrent=32\n"
                + " I:maxGeneratedEuPerTick=128\n S:ambientStarlightPerTick=4.0\n}\n"
                + "machines {\n I:transferRfPerTick=4096\n}\n"
                + "mana {\n I:burstBufferRf=64000\n}\n"
                + "eu {\n I:euTier=1\n}\n";
        Files.write(file.toPath(), cfg.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 2; i++) {
            CoreConfig.load(file);
            assertEquals(8, CoreConfig.rfPerEu);
            assertEquals(7, CoreConfig.rfPerMj);
            assertEquals(3, CoreConfig.rfPerMana);
            assertEquals(200000, CoreConfig.euPerStarlight, 0);
            assertEquals(4096, CoreConfig.starlightVoltage);
            assertEquals(32, CoreConfig.starlightCurrent);
            assertEquals(4, CoreConfig.ambientStarlightPerTick, 0);
        }
        Configuration saved = new Configuration(file);
        saved.load();
        assertFalse(saved.getCategory("starlight").containsKey("maxGeneratedEuPerTick"));
        assertFalse(saved.getCategory("machines").containsKey("transferRfPerTick"));
        assertFalse(saved.hasCategory("mana"));
        assertFalse(saved.hasCategory("eu"));
        assertEquals(5, VoltageConfig.defaultTier());
    }
}
