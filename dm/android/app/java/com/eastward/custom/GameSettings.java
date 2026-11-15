package com.eastward.custom;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Eastward-only settings & path helpers backed by `<externalFilesDir>/env.txt`.
 */
public final class GameSettings {
    public static final String TITLE_ID = "010071B00F63A000";
    public static final String GAME_NAME = "Eastward";
    public static final String EXPECTED_CHAIN = "1a0d0a59a263";

    private GameSettings() {}

    public static File extRoot(Context c) {
        File f = c.getExternalFilesDir(null);
        if (f == null) f = c.getFilesDir();
        f.mkdirs();
        return f;
    }

    public static File gameDir(Context c) {
        File d = new File(new File(extRoot(c), "games"), TITLE_ID);
        d.mkdirs();
        return d;
    }

    public static File saveDir(Context c) {
        File d = new File(new File(c.getFilesDir(), "saves"), TITLE_ID);
        d.mkdirs();
        return d;
    }

    public static File keysDir(Context c) {
        File d = new File(c.getFilesDir(), "keys");
        d.mkdirs();
        return d;
    }

    public static File driversDir(Context c) {
        File d = new File(extRoot(c), "drivers");
        d.mkdirs();
        return d;
    }

    public static File envFile(Context c) {
        return new File(extRoot(c), "env.txt");
    }

    public static boolean isReady(Context c) {
        File g = gameDir(c);
        File ready = new File(g, "ready");
        File fixups = new File(new File(g, "code"), "main.fixups.tsv");
        File spv = new File(g, "spv");
        return ready.isFile() && fixups.isFile() && spv.isDirectory();
    }

    public static Map<String, String> loadEnv(Context c) {
        Map<String, String> map = new LinkedHashMap<>();
        File f = envFile(c);
        if (!f.isFile()) return map;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                ln = ln.trim();
                if (ln.isEmpty() || ln.startsWith("#")) continue;
                int eq = ln.indexOf('=');
                if (eq > 0) map.put(ln.substring(0, eq).trim(), ln.substring(eq + 1).trim());
            }
        } catch (IOException ignored) {}
        return map;
    }

    public static void saveEnv(Context c, Map<String, String> map) {
        File f = envFile(c);
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            w.write("# Eastward Custom environment overrides\n");
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    w.write(e.getKey() + "=" + e.getValue() + "\n");
                }
            }
        } catch (IOException ignored) {}
    }

    public static String get(Context c, String key, String def) {
        String v = loadEnv(c).get(key);
        return v != null ? v : def;
    }

    public static void set(Context c, String key, String val) {
        Map<String, String> map = loadEnv(c);
        if (val == null || val.isEmpty()) map.remove(key);
        else map.put(key, val);
        saveEnv(c, map);
    }

    public static void resetDefaults(Context c) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("DM_LANG", "en-US");
        map.put("DM_OPMODE", "0");
        map.put("DM_VK_CLEAR_LOADOP", "1");
        map.put("DM_VK_MALI_FP16", "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_SPV_PRECISE", "0");
        map.put("DM_VK_PIN", "c0");
        map.put("DM_VK_PIN_DONE", "3f");
        saveEnv(c, map);
    }

    public static Map<String, String> vkInfo(Context c) {
        Map<String, String> info = new LinkedHashMap<>();
        File f = new File(gameDir(c), "vk_start.txt");
        if (!f.isFile()) return info;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                int eq = ln.indexOf('=');
                if (eq > 0) info.put(ln.substring(0, eq).trim(), ln.substring(eq + 1).trim());
            }
        } catch (IOException ignored) {}
        return info;
    }

    public static String driverLabel(Context c) {
        String drv = get(c, "DM_VK_DRIVER", "");
        if (!drv.isEmpty()) return new File(drv).getName();
        Map<String, String> vk = vkInfo(c);
        String name = vk.get("driver");
        String gpu = vk.get("gpu");
        if (name != null && !name.isEmpty()) {
            return (gpu != null && !gpu.isEmpty()) ? gpu + " (" + name + ")" : name;
        }
        return "System Vulkan Driver (Auto)";
    }
}
