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
        Boolean mali = detectMaliGpu(c);
        boolean adreno = (mali != null && !mali);
        map.put("DM_VK_MALI_FP16", "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_VK_ASYNC_PIPE", "1");
        map.put("DM_VK_PREWARM", "3000");
        map.put("DM_VK_PRESENT_TIMING", "1");
        map.put("DM_VK_MID_DRAWS", "256");
        map.put("DM_VK_RECORDER", Runtime.getRuntime().availableProcessors() >= 4 ? "1" : "0");
        if (adreno) map.put("DM_VK_GPU_FLOOR", "85");
        map.put("DM_SPV_PRECISE", "1");
        int[] masks = detectCpuClusterMasks();
        if (masks[0] != 0 && masks[1] != 0) {
            map.put("DM_VK_PIN", Integer.toHexString(masks[0]));
            map.put("DM_VK_PIN_DONE", Integer.toHexString(masks[1]));
        }
        map.put("DM_VK_UCLAMP_MIN", android.os.Build.VERSION.SDK_INT >= 30 ? "800" : "0");
        map.put("DM_VK_FB_SCALE", "100");
        map.put("DM_AUTOTUNED", "4");
        saveEnv(c, map);
    }

    /** Ensure first launch auto-configures chipset-appropriate CPU/GPU flags once without overwriting user tweaks later. */
    public static void ensureAutoTuned(Context c) {
        Map<String, String> map = loadEnv(c);
        String ver = map.get("DM_AUTOTUNED");
        if (!"1".equals(ver) && !"2".equals(ver) && !"3".equals(ver) && !"4".equals(ver)) {
            autoTuneDevice(c);
        } else if ("1".equals(ver) || "2".equals(ver) || "3".equals(ver)) {
            // Non-destructive upgrade to v1.1.7: restore crisp 100% native FBO scale and keep FP16/recorder opts enabled.
            Boolean mali = detectMaliGpu(c);
            boolean adreno = (mali != null && !mali);
            map.put("DM_VK_MALI_FP16", "1");
            if (!map.containsKey("DM_VK_MID_DRAWS")) map.put("DM_VK_MID_DRAWS", "256");
            if (!map.containsKey("DM_VK_RECORDER")) {
                map.put("DM_VK_RECORDER", Runtime.getRuntime().availableProcessors() >= 4 ? "1" : "0");
            }
            if (adreno && !map.containsKey("DM_VK_GPU_FLOOR")) map.put("DM_VK_GPU_FLOOR", "85");
            if ("3".equals(ver) || !map.containsKey("DM_VK_FB_SCALE")) map.put("DM_VK_FB_SCALE", "100");
            map.put("DM_AUTOTUNED", "4");
            saveEnv(c, map);
        }
    }

    /** Apply a universal performance profile suitable across Snapdragon, MediaTek, and Exynos devices. */
    public static void applyProfile(Context c, String profile) {
        Map<String, String> map = loadEnv(c);
        Boolean mali = detectMaliGpu(c);
        boolean adreno = (mali != null && !mali);
        boolean multi = Runtime.getRuntime().availableProcessors() >= 4;
        map.put("DM_VK_CLEAR_LOADOP", "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_VK_ASYNC_PIPE", "1");
        map.put("DM_VK_MALI_FP16", "1");
        map.put("DM_VK_RECORDER", multi ? "1" : "0");
        map.put("DM_SPV_PRECISE", "1");
        map.put("DM_PRESENT_HZ", "");
        map.put("DM_VK_FB_SCALE", "100");
        if ("quality".equals(profile)) {
            map.put("DM_OPMODE", "1");
            map.put("DM_VK_PREWARM", "6000");
            map.put("DM_VK_MID_DRAWS", "256");
            if (adreno) map.put("DM_VK_GPU_FLOOR", "85");
        } else if ("balanced".equals(profile)) {
            map.put("DM_OPMODE", "0");
            map.put("DM_VK_PREWARM", "3000");
            map.put("DM_VK_MID_DRAWS", "256");
            if (adreno) map.put("DM_VK_GPU_FLOOR", "85");
        } else {
            map.put("DM_OPMODE", "0");
            map.put("DM_VK_PREWARM", "1500");
            map.put("DM_VK_MID_DRAWS", "512");
            if (adreno) map.put("DM_VK_GPU_FLOOR", "100");
            map.put("DM_VK_UCLAMP_MIN", android.os.Build.VERSION.SDK_INT >= 30 ? "800" : "0");
        }
        map.put("DM_AUTOTUNED", "4");
        saveEnv(c, map);
    }

    /**
     * Auto-tune CPU core affinity, GPU FP16 optimization, KGSL clock floor, and Android UClamp boost
     * based on the host device's actual CPU cluster frequencies, Vulkan GPU, and OS version.
     */
    public static String autoTuneDevice(Context c) {
        Map<String, String> map = loadEnv(c);
        Boolean mali = detectMaliGpu(c);
        boolean adreno = (mali != null && !mali);
        String fbScale = "100";
        map.put("DM_OPMODE", "0");
        map.put("DM_VK_FB_SCALE", fbScale);
        map.put("DM_PRESENT_HZ", "");
        map.put("DM_VK_MALI_FP16", "1");
        map.put("DM_VK_CLEAR_LOADOP", "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_VK_ASYNC_PIPE", "1");
        map.put("DM_VK_PREWARM", "1500");
        map.put("DM_VK_PRESENT_TIMING", "1");
        map.put("DM_VK_MID_DRAWS", "256");
        map.put("DM_VK_RECORDER", Runtime.getRuntime().availableProcessors() >= 4 ? "1" : "0");
        if (adreno) map.put("DM_VK_GPU_FLOOR", "85");
        else map.remove("DM_VK_GPU_FLOOR");
        map.put("DM_SPV_PRECISE", "1");
        map.put("DM_AUTOTUNED", "4");

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

        String gpuTag = (mali == null) ? "Vulkan Auto" : (adreno ? "Adreno" : "Mali");
        String cpuTag = (bigMask != 0)
                ? ("0x" + Integer.toHexString(bigMask) + "/0x" + Integer.toHexString(littleMask))
                : "Auto";
        return gpuTag + " • CPU " + cpuTag + " • " + fbScale + "%";
    }

    /** Returns true for entry/midrange GPUs (Adreno 610/5xx, Mali-G52/G57, PowerVR) that benefit from 75% FB scale. */
    private static boolean isEntryMidGpu(Context c) {
        String gpu = vkInfo(c).get("gpu");
        String gl = gpu != null ? gpu.toLowerCase(java.util.Locale.ROOT) : "";
        if (!gl.isEmpty()) {
            if (gl.contains("610") || gl.contains("612") || gl.contains("616") || gl.contains("618") || gl.contains("619")
                    || gl.contains("adreno (tm) 5") || gl.contains("adreno 5")
                    || gl.contains("mali-g5") || gl.contains("mali-g71") || gl.contains("mali-g72")
                    || gl.contains("powervr")) {
                return true;
            }
            if (gl.contains("adreno (tm) 7") || gl.contains("adreno (tm) 8") || gl.contains("adreno 650")
                    || gl.contains("adreno (tm) 650") || gl.contains("adreno (tm) 660") || gl.contains("mali-g78")
                    || gl.contains("mali-g710") || gl.contains("mali-g715") || gl.contains("immortalis")) {
                return false;
            }
        }
        String soc = android.os.Build.VERSION.SDK_INT >= 31 && android.os.Build.SOC_MODEL != null
                ? android.os.Build.SOC_MODEL : "";
        String hw = ((android.os.Build.HARDWARE != null ? android.os.Build.HARDWARE : "") + " "
                + (android.os.Build.BOARD != null ? android.os.Build.BOARD : "") + " " + soc)
                .toLowerCase(java.util.Locale.ROOT);
        if (hw.contains("bengal") || hw.contains("khaje") || hw.contains("trinket")
                || hw.contains("sm6225") || hw.contains("sm6115")
                || hw.contains("mt6768") || hw.contains("mt6769") || hw.contains("mt6765") || hw.contains("mt6785")) {
            return true;
        }
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                if (mi.totalMem > 0 && mi.totalMem <= (4500L << 20)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Returns true for Mali, false for Adreno/Qualcomm/PowerVR/Xclipse, or null to let native Vulkan auto-detect. */
    private static Boolean detectMaliGpu(Context c) {
        Map<String, String> vk = vkInfo(c);
        String combined = ((vk.get("gpu") != null ? vk.get("gpu") : "") + " "
                + (vk.get("driver") != null ? vk.get("driver") : "")).toLowerCase(java.util.Locale.ROOT);
        if (combined.contains("adreno") || combined.contains("turnip") || combined.contains("qualcomm")
                || combined.contains("xclipse") || combined.contains("powervr")) {
            return Boolean.FALSE;
        }
        if (combined.contains("mali")) {
            return Boolean.TRUE;
        }
        String hw = ((android.os.Build.HARDWARE != null ? android.os.Build.HARDWARE : "") + " "
                + (android.os.Build.BOARD != null ? android.os.Build.BOARD : ""))
                .toLowerCase(java.util.Locale.ROOT);
        if (hw.contains("qcom") || hw.contains("qualcomm") || hw.contains("msm") || hw.contains("taro")
                || hw.contains("kalama") || hw.contains("pineapple") || hw.contains("lahaina")) {
            return Boolean.FALSE;
        }
        if (hw.startsWith("mt") || hw.contains("mt6") || hw.contains("mt8")) {
            return Boolean.TRUE;
        }
        return null;
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
