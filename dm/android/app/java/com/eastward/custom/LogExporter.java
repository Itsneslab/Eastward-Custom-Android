package com.eastward.custom;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Collects runtime diagnostics, device capabilities, Vulkan start info, and recent logcat
 * into a single shareable `.zip` bug report (never includes saves or game dumps).
 */
public final class LogExporter {
    public static final class ExportResult {
        public final int fileCount;
        public final long zipBytes;

        ExportResult(int fileCount, long zipBytes) {
            this.fileCount = fileCount;
            this.zipBytes = zipBytes;
        }
    }

    private LogExporter() {}

    public static String suggestedFileName() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "eastward_log_" + ts + ".zip";
    }

    public static ExportResult exportZip(Context c, Uri outUri) throws IOException {
        File ext = GameSettings.extRoot(c);
        File gameDir = GameSettings.gameDir(c);

        ByteArrayOutputStream rawZip = new ByteArrayOutputStream(65536);
        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(rawZip)) {
            count += addTextEntry(zos, "device_info.txt", buildDeviceReport(c));
            count += addFileEntry(zos, new File(ext, "env.txt"), "env.txt");
            count += addFileEntry(zos, new File(ext, "log.txt"), "log.txt");
            count += addFileEntry(zos, new File(ext, "log.prev.txt"), "log.prev.txt");

            count += addFileEntry(zos, new File(gameDir, "vk_start.txt"), "vk_start.txt");
            count += addFileEntry(zos, new File(gameDir, "defaults.txt"), "defaults.txt");
            count += addFileEntry(zos, new File(gameDir, "launcher.txt"), "launcher.txt");
            count += addFileEntry(zos, new File(gameDir, "setup_work/setup.log"), "setup.log");

            String logcat = captureLogcat();
            if (!logcat.isEmpty()) {
                count += addTextEntry(zos, "logcat.txt", logcat);
            }
            zos.finish();
        }

        byte[] bytes = rawZip.toByteArray();
        try (OutputStream out = c.getContentResolver().openOutputStream(outUri)) {
            if (out == null) throw new IOException("Cannot open destination URI");
            out.write(bytes);
            out.flush();
        }
        return new ExportResult(count, bytes.length);
    }

    private static int addTextEntry(ZipOutputStream zos, String name, String content) throws IOException {
        if (content == null || content.isEmpty()) return 0;
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
        return 1;
    }

    private static int addFileEntry(ZipOutputStream zos, File src, String zipName) throws IOException {
        if (src == null || !src.isFile() || src.length() == 0) return 0;
        zos.putNextEntry(new ZipEntry(zipName));
        byte[] buf = new byte[32768];
        try (FileInputStream fis = new FileInputStream(src)) {
            int n;
            while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
        return 1;
    }

    private static String buildDeviceReport(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Eastward Custom Bug Report ===\n");
        sb.append("Timestamp: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n');

        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            sb.append("App: ").append(c.getPackageName())
                    .append(" v").append(pi.versionName)
                    .append(" (").append(pi.getLongVersionCode()).append(")\n");
        } catch (Exception ignored) {
            sb.append("App: ").append(c.getPackageName()).append('\n');
        }

        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Hardware/Board: ").append(Build.HARDWARE).append(" / ").append(Build.BOARD).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append("RAM: total=").append(mi.totalMem >> 20).append(" MB, avail=")
                    .append(mi.availMem >> 20).append(" MB, lowMem=").append(mi.lowMemory).append('\n');
        }

        sb.append("CPU cores: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        for (int cpu = 0; cpu < 16; cpu++) {
            File f = new File("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_max_freq");
            if (!f.isFile()) break;
            String kHz = readSmallLine(f);
            if (!kHz.isEmpty()) {
                sb.append("  cpu").append(cpu).append("_max_kHz=").append(kHz).append('\n');
            }
        }

        File gameDir = GameSettings.gameDir(c);
        sb.append("Game ready: ").append(GameSettings.isReady(c)).append('\n');
        sb.append("DLC (aoc/) present: ").append(new File(gameDir, "aoc").isDirectory()).append('\n');
        sb.append("RomFS present: ").append(new File(gameDir, "RomFS").isDirectory()).append('\n');
        sb.append("Active driver: ").append(GameSettings.driverLabel(c)).append('\n');

        Map<String, String> vk = GameSettings.vkInfo(c);
        if (!vk.isEmpty()) {
            sb.append("vk_start parsed:\n");
            for (Map.Entry<String, String> e : vk.entrySet()) {
                sb.append("  ").append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String readSmallLine(File f) {
        try (BufferedReader r = new BufferedReader(new java.io.FileReader(f))) {
            String s = r.readLine();
            return s != null ? s.trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String captureLogcat() {
        Process p = null;
        try {
            p = new ProcessBuilder("logcat", "-d", "-t", "500", "-v", "time")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String ln;
                int lines = 0;
                while ((ln = r.readLine()) != null && lines < 600 && sb.length() < 262144) {
                    sb.append(ln).append('\n');
                    lines++;
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "logcat unavailable: " + e.getMessage() + "\n";
        } finally {
            if (p != null) p.destroy();
        }
    }
}
