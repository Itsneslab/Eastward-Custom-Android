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
        String uiLang = get(c, "UI_LANG", "en");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("UI_LANG", uiLang);
        map.put("DM_LANG", "en-US");
        map.put("DM_OPMODE", "0");
        map.put("DM_PRESENT_HZ", "");
        map.put("DM_VK_CLEAR_LOADOP", "1");
        map.put("DM_VK_MALI_FP16", isAdrenoGpu(c) ? "0" : "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_VK_ASYNC_PIPE", "1");
        map.put("DM_VK_PREWARM", "3000");
        map.put("DM_VK_PRESENT_TIMING", "1");
        map.put("DM_SPV_PRECISE", "1");
        int[] masks = detectCpuClusterMasks();
        if (masks[0] != 0 && masks[1] != 0) {
            map.put("DM_VK_PIN", Integer.toHexString(masks[0]));
            map.put("DM_VK_PIN_DONE", Integer.toHexString(masks[1]));
        }
        map.put("DM_VK_UCLAMP_MIN", android.os.Build.VERSION.SDK_INT >= 30 ? "800" : "0");
        map.put("DM_AUTOTUNED", "1");
        saveEnv(c, map);
    }

    /** Ensure first launch auto-configures chipset-appropriate CPU/GPU flags once without overwriting user tweaks later. */
    public static void ensureAutoTuned(Context c) {
        Map<String, String> map = loadEnv(c);
        if (!"1".equals(map.get("DM_AUTOTUNED"))) {
            autoTuneDevice(c);
        }
    }

    /** Apply a universal performance profile suitable across Snapdragon, MediaTek, and Exynos devices. */
    public static void applyProfile(Context c, String profile) {
        Map<String, String> map = loadEnv(c);
        map.put("DM_VK_CLEAR_LOADOP", "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_VK_ASYNC_PIPE", "1");
        map.put("DM_SPV_PRECISE", "1");
        map.put("DM_PRESENT_HZ", "");
        if ("quality".equals(profile)) {
            map.put("DM_OPMODE", "1");
            map.put("DM_VK_PREWARM", "6000");
        } else if ("balanced".equals(profile)) {
            map.put("DM_OPMODE", "0");
            map.put("DM_VK_PREWARM", "3000");
        } else {
            map.put("DM_OPMODE", "0");
            map.put("DM_VK_PREWARM", "1500");
        }
        saveEnv(c, map);
    }

    /**
     * Auto-tune CPU core affinity, GPU FP16 optimization, and Android UClamp boost
     * based on the host device's actual CPU cluster frequencies, Vulkan GPU, and OS version.
     */
    public static String autoTuneDevice(Context c) {
        Map<String, String> map = loadEnv(c);
        boolean adreno = isAdrenoGpu(c);
        map.put("DM_OPMODE", "0");
        map.put("DM_PRESENT_HZ", "");
        map.put("DM_VK_MALI_FP16", adreno ? "0" : "1");
        map.put("DM_VK_CLEAR_LOADOP", "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_VK_ASYNC_PIPE", "1");
        map.put("DM_VK_PREWARM", "1500");
        map.put("DM_VK_PRESENT_TIMING", "1");
        map.put("DM_SPV_PRECISE", "1");
        map.put("DM_AUTOTUNED", "1");

        int[] masks = detectCpuClusterMasks();
        int bigMask = masks[0], littleMask = masks[1];
        if (bigMask != 0 && littleMask != 0) {
            map.put("DM_VK_PIN", Integer.toHexString(bigMask));
            map.put("DM_VK_PIN_DONE", Integer.toHexString(littleMask));
        } else {
            map.remove("DM_VK_PIN");
            map.remove("DM_VK_PIN_DONE");
        }
        // Util-clamp sched_setattr only applies reliably on Android 11+ (API 30+) GKI kernels.
        map.put("DM_VK_UCLAMP_MIN", android.os.Build.VERSION.SDK_INT >= 30 ? "800" : "0");
        saveEnv(c, map);

        String gpuTag = adreno ? "Adreno" : "Mali/Vulkan";
        String cpuTag = (bigMask != 0)
                ? ("0x" + Integer.toHexString(bigMask) + "/0x" + Integer.toHexString(littleMask))
                : "Auto";
        return gpuTag + " • CPU " + cpuTag + " • 720p";
    }

    private static boolean isAdrenoGpu(Context c) {
        Map<String, String> vk = vkInfo(c);
        String combined = ((vk.get("gpu") != null ? vk.get("gpu") : "") + " "
                + (vk.get("driver") != null ? vk.get("driver") : "")).toLowerCase(java.util.Locale.ROOT);
        return combined.contains("adreno") || combined.contains("turnip") || combined.contains("qualcomm");
    }

    /** Read sysfs cpuinfo_max_freq to split big/prime cores from efficiency cores on any SoC. */
    private static int[] detectCpuClusterMasks() {
        int n = Math.min(16, Math.max(1, Runtime.getRuntime().availableProcessors()));
        long[] freqs = new long[n];
        long minF = Long.MAX_VALUE, maxF = 0;
        int valid = 0;
        for (int i = 0; i < n; i++) {
            File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (!f.isFile()) continue;
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line = r.readLine();
                if (line != null) {
                    long khz = Long.parseLong(line.trim());
                    freqs[i] = khz;
                    if (khz < minF) minF = khz;
                    if (khz > maxF) maxF = khz;
                    valid++;
                }
            } catch (Exception ignored) {}
        }
        if (valid < 2 || maxF <= minF) return new int[]{0, 0};
        int bigMask = 0, littleMask = 0;
        for (int i = 0; i < n; i++) {
            if (freqs[i] == 0) continue;
            if (freqs[i] > minF) bigMask |= (1 << i);
            else littleMask |= (1 << i);
        }
        return new int[]{bigMask, littleMask};
    }

    /** Set replay/completion CPU affinity masks consistently for common Android SoC topologies. */
    public static void setCpuPin(Context c, String pin) {
        Map<String, String> map = loadEnv(c);
        String done = "";
        if ("c0".equalsIgnoreCase(pin)) done = "3f";
        else if ("f0".equalsIgnoreCase(pin)) done = "0f";
        else if ("80".equalsIgnoreCase(pin)) done = "7f";
        else if (pin != null && !pin.isEmpty()) {
            try {
                int m = Integer.parseInt(pin, 16);
                int all = (1 << Math.min(16, Math.max(1, Runtime.getRuntime().availableProcessors()))) - 1;
                int rest = all & ~m;
                if (rest != 0) done = Integer.toHexString(rest);
            } catch (NumberFormatException ignored) {}
        }
        if (pin == null || pin.isEmpty()) {
            map.remove("DM_VK_PIN");
            map.remove("DM_VK_PIN_DONE");
        } else {
            map.put("DM_VK_PIN", pin.toLowerCase(java.util.Locale.ROOT));
            if (done.isEmpty()) map.remove("DM_VK_PIN_DONE");
            else map.put("DM_VK_PIN_DONE", done);
        }
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
