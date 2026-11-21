package com.eastward.custom;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
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
        boolean adreno = Boolean.FALSE.equals(detectMaliGpu(c));
        Map<String, String> map = new LinkedHashMap<>();
        map.put("UI_LANG", uiLang);
        map.put("DM_LANG", "en-US");
        map.put("DM_OPMODE", "0");
        map.put("DM_VK_MALI_FP16", adreno ? "0" : "1");
        map.put("DM_VK_MAILBOX", "1");
        map.put("DM_VK_FAST_FX", "1");
        map.put("DM_VK_FB_SCALE", "100");
        map.put("DM_SCREEN_MODE", "0");
        applyDetectedEngineDefaults(c, map);
        syncModOverlayMap(c, map);
        map.put("DM_AUTOTUNED", "9");
        saveEnv(c, map);
    }

    /** Ensure launch auto-configures chipset-appropriate internal CPU/GPU flags without exposing confusing toggles. */
    public static void ensureAutoTuned(Context c) {
        Map<String, String> map = loadEnv(c);
        String ver = map.get("DM_AUTOTUNED");
        boolean changed = syncModOverlayMap(c, map);
        if (!"1".equals(ver) && !"2".equals(ver) && !"3".equals(ver) && !"4".equals(ver)
                && !"5".equals(ver) && !"6".equals(ver) && !"7".equals(ver)
                && !"8".equals(ver) && !"9".equals(ver)) {
            autoTuneDevice(c);
            return;
        } else if (!"9".equals(ver)) {
            // Upgrade to v1.1.9-test3 / pre-1.2.0: keep FP16 Mali-only (disable forced FP16 on Adreno to avoid shader glitches).
            boolean adreno = Boolean.FALSE.equals(detectMaliGpu(c));
            applyDetectedEngineDefaults(c, map);
            if ("30".equals(map.get("DM_FPS_TARGET"))) map.remove("DM_FPS_TARGET");
            map.put("DM_VK_FB_SCALE", "100");
            if (adreno) map.put("DM_VK_MALI_FP16", "0");
            else if (!map.containsKey("DM_VK_MALI_FP16")) map.put("DM_VK_MALI_FP16", "1");
            if (!map.containsKey("DM_VK_MAILBOX")) map.put("DM_VK_MAILBOX", "1");
            if (!map.containsKey("DM_VK_FAST_FX")) map.put("DM_VK_FAST_FX", "1");
            map.put("DM_AUTOTUNED", "9");
            changed = true;
        }
        if (changed) saveEnv(c, map);
    }

    public static void syncModOverlay(Context c) {
        Map<String, String> map = loadEnv(c);
        if (syncModOverlayMap(c, map)) saveEnv(c, map);
    }

    public static boolean hasModFiles(Context c) {
        File modRomfs = new File(gameDir(c), "mods/romfs");
        return hasAnyFile(modRomfs);
    }

    public static int countModFiles(Context c) {
        return countFiles(new File(gameDir(c), "mods/romfs"));
    }

    private static int countFiles(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return 1;
        File[] list = f.listFiles();
        if (list == null) return 0;
        int n = 0;
        for (File ch : list) n += countFiles(ch);
        return n;
    }

    public static String appVersionTag(Context c) {
        try {
            String vn = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            if (vn != null && !vn.trim().isEmpty()) return vn.trim();
        } catch (Throwable ignored) {}
        return "1.2.0";
    }

    private static boolean hasAnyFile(File f) {
        if (f == null || !f.exists()) return false;
        if (f.isFile()) return true;
        File[] list = f.listFiles();
        if (list == null) return false;
        for (File ch : list) if (hasAnyFile(ch)) return true;
        return false;
    }

    public static String installedUpdateSummary(Context c) {
        File f = new File(gameDir(c), "update_info.txt");
        if (!f.isFile()) return null;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String name = r.readLine();
            String ver = r.readLine();
            String v = (ver != null && !ver.trim().isEmpty()) ? ("v" + ver.trim()) : "";
            String n = (name != null && !name.trim().isEmpty()) ? name.trim() : "";
            if (!v.isEmpty() && !n.isEmpty()) return v + " (" + n + ")";
            return !v.isEmpty() ? v : (!n.isEmpty() ? n : "Installed");
        } catch (IOException ignored) {
            return "Installed";
        }
    }

    public static String installedGameVersion(Context c) {
        File f = new File(gameDir(c), "game_version.txt");
        if (!f.isFile()) return "";
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String ln = r.readLine();
            return ln != null ? ln.trim() : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private static boolean syncModOverlayMap(Context c, Map<String, String> map) {
        File modRomfs = new File(gameDir(c), "mods/romfs");
        String target = modRomfs.getPath();
        String cur = map.get("DM_ROMFS_OVERLAY");
        if (hasModFiles(c)) {
            if (!target.equals(cur)) { map.put("DM_ROMFS_OVERLAY", target); return true; }
        } else if (cur != null) {
            map.remove("DM_ROMFS_OVERLAY");
            return true;
        }
        return false;
    }

    private static void applyDetectedEngineDefaults(Context c, Map<String, String> map) {
        Boolean mali = detectMaliGpu(c);
        boolean adreno = (mali != null && !mali);
        map.put("DM_PRESENT_HZ", "");
        map.put("DM_VK_CLEAR_LOADOP", "1");
        map.put("DM_VK_SET_REUSE", "1");
        map.put("DM_VK_ASYNC_PIPE", "1");
        map.put("DM_VK_PREWARM", "1500");
        map.put("DM_VK_PRESENT_TIMING", "1");
        map.put("DM_VK_MID_DRAWS", "256");
        map.put("DM_VK_RECORDER", "0");
        map.put("DM_VK_HD_ONLY", "1");
        map.put("DM_VK_IMAGE_EVICT_AGE", "3600");
        map.put("DM_VK_FAST_SHADERS", "1");
        map.put("DM_VK_FAST_LOAD", "1");
        map.put("DM_VK_MAX_ANISO", "1");
        if (adreno) map.put("DM_VK_GPU_FLOOR", "85");
        else map.remove("DM_VK_GPU_FLOOR");
        map.put("DM_SPV_PRECISE", "1");
        int[] masks = detectCpuClusterMasks();
        if (masks[0] != 0 && masks[1] != 0) {
            map.put("DM_VK_PIN", Integer.toHexString(masks[0]));
            map.put("DM_VK_PIN_DONE", Integer.toHexString(masks[1]));
        } else {
            map.remove("DM_VK_PIN");
            map.remove("DM_VK_PIN_DONE");
        }
        map.put("DM_VK_UCLAMP_MIN", android.os.Build.VERSION.SDK_INT >= 30 ? "800" : "0");
    }

    /**
     * Auto-tune CPU core affinity, GPU FP16 optimization, KGSL clock floor, and Android UClamp boost
     * based on the host device's actual CPU cluster frequencies, Vulkan GPU, and OS version.
     */
    public static String autoTuneDevice(Context c) {
        Map<String, String> map = loadEnv(c);
        boolean adreno = Boolean.FALSE.equals(detectMaliGpu(c));
        map.put("DM_OPMODE", "0");
        map.put("DM_VK_FB_SCALE", "100");
        map.put("DM_VK_MALI_FP16", adreno ? "0" : "1");
        map.put("DM_VK_MAILBOX", "1");
        map.put("DM_VK_FAST_FX", "1");
        applyDetectedEngineDefaults(c, map);
        syncModOverlayMap(c, map);
        map.put("DM_AUTOTUNED", "9");
        saveEnv(c, map);
        return detectedChipsetSummary(c);
    }

    private static volatile String cachedGlRenderer;

    private static String detectGlGpuName() {
        String cached = cachedGlRenderer;
        if (cached != null) return cached;
        String found = "";
        EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
        EGLSurface surf = EGL14.EGL_NO_SURFACE;
        EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        EGLContext prevCtx = EGL14.eglGetCurrentContext();
        EGLDisplay prevDpy = EGL14.eglGetCurrentDisplay();
        EGLSurface prevDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        EGLSurface prevRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
        try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            if (dpy != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(dpy, ver, 0, ver, 1)) {
                int[] cfgAttr = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE
                };
                EGLConfig[] cfgs = new EGLConfig[1];
                int[] num = new int[1];
                if (EGL14.eglChooseConfig(dpy, cfgAttr, 0, cfgs, 0, 1, num, 0) && num[0] > 0) {
                    int[] surfAttr = { EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE };
                    surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], surfAttr, 0);
                    int[] ctxAttr = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
                    ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
                    if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT && surf != null && surf != EGL14.EGL_NO_SURFACE
                            && EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) {
                        String r = GLES20.glGetString(GLES20.GL_RENDERER);
                        if (r != null) found = r.trim();
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (prevDpy != null && prevDpy != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(prevDpy, prevDraw, prevRead, prevCtx);
                } else if (dpy != null && dpy != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                }
                if (surf != null && surf != EGL14.EGL_NO_SURFACE && dpy != EGL14.EGL_NO_DISPLAY) EGL14.eglDestroySurface(dpy, surf);
                if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT && dpy != EGL14.EGL_NO_DISPLAY) EGL14.eglDestroyContext(dpy, ctx);
            } catch (Throwable ignored) {}
        }
        cachedGlRenderer = found;
        return found;
    }

    public static String phoneModelLabel() {
        String mfg = Build.MANUFACTURER != null ? Build.MANUFACTURER.trim() : "";
        String mdl = Build.MODEL != null ? Build.MODEL.trim() : "";
        if (mdl.isEmpty()) return mfg.isEmpty() ? "Android" : mfg;
        if (!mfg.isEmpty() && !mdl.toLowerCase(java.util.Locale.ROOT).startsWith(mfg.toLowerCase(java.util.Locale.ROOT))) {
            return mfg + " " + mdl;
        }
        return mdl;
    }

    public static String gpuDisplayName(Context c) {
        String vkGpu = vkInfo(c).get("gpu");
        if (vkGpu != null && !vkGpu.trim().isEmpty()) return vkGpu.trim();
        return detectGlGpuName();
    }

    public static boolean isAdrenoGpu(Context c) {
        return Boolean.FALSE.equals(detectMaliGpu(c));
    }

    public static boolean isMaliGpu(Context c) {
        return Boolean.TRUE.equals(detectMaliGpu(c));
    }

    public static boolean isCustomDriverSelected(Context c) {
        String drv = get(c, "DM_VK_DRIVER", "");
        if (!drv.isEmpty()) return true;
        String d = vkInfo(c).get("driver");
        if (d == null) return false;
        String low = d.toLowerCase(java.util.Locale.ROOT);
        return low.contains("turnip") || low.contains("mesa") || low.contains("freedreno")
                || low.contains("panvk") || low.contains("panfrost") || low.contains("frist") || low.contains("firstone");
    }

    public static boolean isTurnipSelected(Context c) {
        String low = (get(c, "DM_VK_DRIVER", "") + " " + (vkInfo(c).get("driver") != null ? vkInfo(c).get("driver") : ""))
                .toLowerCase(java.util.Locale.ROOT);
        return low.contains("turnip") || low.contains("freedreno");
    }

    /** Short human-readable summary of auto-detected GPU/CPU optimizations active on this phone. */
    public static String detectedChipsetSummary(Context c) {
        Boolean mali = detectMaliGpu(c);
        String gpu = gpuDisplayName(c);
        String gpuTag = (gpu != null && !gpu.isEmpty()) ? gpu
                : ((mali == null) ? "Vulkan Auto" : (mali ? "Mali GPU" : "Adreno GPU"));
        int[] masks = detectCpuClusterMasks();
        String cpuTag = (masks[0] != 0)
                ? ("Big 0x" + Integer.toHexString(masks[0]))
                : (Runtime.getRuntime().availableProcessors() + " Cores Auto");
        String boostTag = (mali != null && !mali) ? "Adreno Floor + TBDR Auto"
                : (mali != null ? "Mali FP16 + TBDR Auto" : "TBDR + Thread Auto");
        return phoneModelLabel() + " • " + gpuTag + " • " + cpuTag + " • " + boostTag;
    }

    /** Returns true for Mali, false for Adreno/Qualcomm/PowerVR/Xclipse, or null to let native Vulkan auto-detect. */
    private static Boolean detectMaliGpu(Context c) {
        Map<String, String> vk = vkInfo(c);
        String glGpu = detectGlGpuName();
        String combined = ((vk.get("gpu") != null ? vk.get("gpu") : "") + " "
                + (vk.get("driver") != null ? vk.get("driver") : "") + " "
                + glGpu).toLowerCase(java.util.Locale.ROOT);
        if (combined.contains("adreno") || combined.contains("turnip") || combined.contains("qualcomm")
                || combined.contains("xclipse") || combined.contains("powervr")) {
            return Boolean.FALSE;
        }
        if (combined.contains("panvk") || combined.contains("panfrost") || combined.contains("mali")) {
            return Boolean.TRUE;
        }
        String soc = "";
        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null) {
            soc = Build.SOC_MODEL;
        }
        String hw = ((Build.HARDWARE != null ? Build.HARDWARE : "") + " "
                + (Build.BOARD != null ? Build.BOARD : "") + " "
                + soc).toLowerCase(java.util.Locale.ROOT);
        if (hw.contains("qcom") || hw.contains("qualcomm") || hw.contains("msm") || hw.contains("sdm")
                || hw.contains("sm") || hw.contains("qcs") || hw.contains("taro") || hw.contains("kalama")
                || hw.contains("pineapple") || hw.contains("lahaina") || hw.contains("kona") || hw.contains("bengal")
                || hw.contains("holi") || hw.contains("blair") || hw.contains("khaje") || hw.contains("atoll")
                || hw.contains("lito") || hw.contains("lagoon") || hw.contains("cape") || hw.contains("waipio")
                || hw.contains("sun")) {
            return Boolean.FALSE;
        }
        if (hw.startsWith("mt") || hw.contains(" mt") || hw.contains("mt6") || hw.contains("mt8")) {
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
