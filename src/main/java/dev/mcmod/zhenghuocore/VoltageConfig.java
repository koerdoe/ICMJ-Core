package dev.mcmod.zhenghuocore;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

/** Startup-only block voltage settings; runtime queries are constant-time map lookups. */
public final class VoltageConfig {
    public static final String FILE_NAME = "zhenghuocore-voltage.json";
    // Same names and integer tier indices as the IC grid. No GregTech dependency is required.
    private static final String[] NAMES = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX"
    };
    private static int defaultTier = 5;
    private static int defaultCurrent = 1;
    private static Map<ResourceLocation, Integer> whitelist = Collections.emptyMap();
    private static Map<ResourceLocation, Integer> currentWhitelist = Collections.emptyMap();
    private VoltageConfig() { }

    public static void load(File file) {
        try {
            if (!file.exists()) {
                Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
                JsonObject defaults = new JsonObject();
                defaults.addProperty("defaultTier", "IV");
                defaults.add("whitelist", new JsonObject());
                defaults.addProperty("defaultCurrent", 1);
                defaults.add("currentWhitelist", new JsonObject());
                try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(defaults, out);
                    out.write('\n');
                }
            }
            JsonObject config;
            try (Reader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                config = new JsonParser().parse(in).getAsJsonObject();
            }
            int nextDefault = parseTier(config.get("defaultTier"), "defaultTier");
            JsonElement entries = config.get("whitelist");
            if (entries == null || !entries.isJsonObject())
                throw new IllegalArgumentException("whitelist must be an object: {\"modid:block\":\"HV\"}");
            Map<ResourceLocation, Integer> next = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : entries.getAsJsonObject().entrySet()) {
                next.put(blockId(entry.getKey()), parseTier(entry.getValue(), entry.getKey()));
            }
            int nextCurrent = config.has("defaultCurrent") ? parseCurrent(config.get("defaultCurrent"), "defaultCurrent") : 1;
            Map<ResourceLocation, Integer> nextCurrents = new LinkedHashMap<>();
            if (config.has("currentWhitelist")) {
                if (!config.get("currentWhitelist").isJsonObject())
                    throw new IllegalArgumentException("currentWhitelist must be an object: {\"modid:block\":2}");
                for (Map.Entry<String, JsonElement> entry : config.getAsJsonObject("currentWhitelist").entrySet())
                    nextCurrents.put(blockId(entry.getKey()), parseCurrent(entry.getValue(), entry.getKey()));
            }
            // Publish only a completely validated configuration. Never rewrite custom values on load.
            defaultTier = nextDefault;
            whitelist = Collections.unmodifiableMap(next);
            defaultCurrent = nextCurrent;
            currentWhitelist = Collections.unmodifiableMap(nextCurrents);
        } catch (IOException | RuntimeException error) {
            throw new IllegalArgumentException("Invalid voltage configuration " + file.getAbsolutePath() + ": " + error.getMessage(), error);
        }
    }

    private static ResourceLocation blockId(String id) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid block ID: " + id + "; expected modid:block");
        return new ResourceLocation(id);
    }

    private static int parseCurrent(JsonElement value, String key) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                int current=value.getAsBigDecimal().intValueExact();
                if (current>0) return current;
            } catch (ArithmeticException ignored) { }
        }
        throw new IllegalArgumentException(key + " current must be an integer from 1 to " + Integer.MAX_VALUE + " A");
    }

    private static int parseTier(JsonElement value, String key) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String name = value.getAsString().trim();
            for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) return i;
        }
        throw new IllegalArgumentException(key + " must use a voltage name: " + String.join(", ", NAMES));
    }

    public static int defaultTier() { return defaultTier; }
    public static int defaultCurrent() { return defaultCurrent; }
    public static String tierName(int tier) { return NAMES[tier]; }
    public static Integer override(ResourceLocation blockId) { return whitelist.get(blockId); }
    public static int tier(ResourceLocation blockId) { return whitelist.getOrDefault(blockId, defaultTier); }
    public static int tier(TileEntity tile) { return tier(tile.getBlockType().getRegistryName()); }
    public static boolean listed(ResourceLocation blockId) { return whitelist.containsKey(blockId) || currentWhitelist.containsKey(blockId); }
    public static int current(ResourceLocation blockId) { return currentWhitelist.getOrDefault(blockId, defaultCurrent); }
    public static int current(TileEntity tile) { return current(tile.getBlockType().getRegistryName()); }
}
