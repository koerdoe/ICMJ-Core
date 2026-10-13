package dev.mcmod.zhenghuocore;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class CoreConfig {
    public static int rfPerEu = 4, rfPerMj = 40, rfPerMana = 10;
    public static int capacityRf = 1_000_000;
    public static int starlightVoltage = 8192, starlightCurrent = 4;
    public static double euPerStarlight = 8;
    public static double ambientStarlightPerTick = 4;
    private CoreConfig() {}
    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        c.setCategoryComment("conversion", "双向换算共用同一比例，FE 与 RF 在此为同一单位。默认 1 MJ = 40 FE = 10 EU = 4 Mana。修改后重启游戏／服务端生效。");
        rfPerEu = c.getInt("rfPerEu", "conversion", 4, 1, Integer.MAX_VALUE, "每 1 EU 对应多少 FE/RF；EU↔FE 双向使用。");
        rfPerMj = c.getInt("rfPerMj", "conversion", 40, 1, Integer.MAX_VALUE, "每 1 MJ 对应多少 FE/RF；MJ↔FE 双向使用。不足完整换算单位的能量留在源设备。");
        rfPerMana = c.getInt("rfPerMana", "conversion", 10, 1, Integer.MAX_VALUE, "每 1 Mana 对应多少 FE/RF；Mana↔FE 双向使用。");
        capacityRf = c.getInt("capacityRf", "machines", 1000000, 100000, 1000000000,
                "星能发电机原有容量计数：4 单位 = 1 EU，默认 1000000 = 250000 EU；独立于 conversion.rfPerEu，修改换算比例不会改变已存 EU。");
        c.getCategory("machines").remove("transferRfPerTick");
        c.getCategory("eu").remove("rfToEuMaxRfPerTick");
        if (c.hasCategory("mana")) c.removeCategory(c.getCategory("mana"));
        c.getCategory("eu").remove("euTier");
        c.getCategory("eu").remove("euCurrent");
        if (c.getCategory("eu").isEmpty()) c.removeCategory(c.getCategory("eu"));
        c.setCategoryComment("starlight", "星能发电与 EU 输出分别设置；修改后重启生效。发电量 = 实际收到的星能 × euPerStarlight；最大输出 = outputVoltage × outputCurrent EU/t。");
        starlightVoltage = c.getInt("outputVoltage", "starlight", 8192, 1, Integer.MAX_VALUE,
                "输出电压上限（V）。电网按负载自动降压；本项不改变发电量。");
        starlightCurrent = c.getInt("outputCurrent", "starlight", 4, 1, Integer.MAX_VALUE,
                "整份输出电流（A），电网不拆流。默认 8192 V × 4 A = 32768 EU/t；缓存至少须容纳 1 V × 此电流。");
        euPerStarlight = c.getFloat("euPerStarlight", "starlight", 8, 0, Float.MAX_VALUE,
                "发电倍率：每单位星能产生的 EU。默认 8；设为 16 即默认发电量的两倍。自然星能与水晶星能共用此倍率，不另设每 tick 发电限额，满储能时停止积累。");
        c.getCategory("starlight").remove("maxGeneratedEuPerTick");
        ambientStarlightPerTick = c.getFloat("ambientStarlightPerTick", "starlight", 4, 0, 10000,
                "Base open-sky starlight collection, scaled by Astral Sorcery sky strength and local distribution. Zero disables ambient collection. All starlight shares one conversion and storage buffer without a generation-rate cap.");
        if ((long) starlightCurrent * 4 > capacityRf)
            throw new IllegalArgumentException("Starlight buffer cannot hold the minimum 1 V times whole output current");
        VoltageConfig.load(new File(file.getAbsoluteFile().getParentFile(), VoltageConfig.FILE_NAME));
        if (c.hasChanged()) c.save();
    }
}
