package com.eastward.custom;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;

/**
 * Installs Adreno Turnip or Mali PanVK Vulkan driver packages (.zip) into `<ext>/drivers/`
 * and selects them via `DM_VK_DRIVER` in `env.txt`.
 */
public final class DriverPackages {
    private DriverPackages() {}

    public static String installZip(Context c, Uri uri) throws IOException {
        File tmpDir = new File(c.getCacheDir(), "drv_unpack");
        deleteDir(tmpDir);
        tmpDir.mkdirs();

        String libName = null;
        String metaName = null;
        try (InputStream raw = c.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("Cannot read selected driver file");
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw));
            ZipEntry e;
            byte[] buf = new byte[32768];
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String base = new File(e.getName()).getName();
                if (base.isEmpty()) continue;
                File out = new File(tmpDir, base);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                }
                if (base.equalsIgnoreCase("meta.json")) {
                    try {
                        String txt = readText(out);
                        JSONObject j = new JSONObject(txt);
                        libName = j.optString("libraryName", libName);
                        metaName = j.optString("name", metaName);
                    } catch (Exception ignored) {}
                }
            }
        }

        File mainSo = (libName != null && !libName.isEmpty()) ? new File(tmpDir, libName) : null;
        if (mainSo == null || !mainSo.isFile()) {
            File[] files = tmpDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".so") && (n.contains("vulkan") || mainSo == null)) {
                        mainSo = f;
                        if (n.contains("vulkan")) break;
                    }
                }
            }
        }
        if (mainSo == null || !mainSo.isFile()) {
            deleteDir(tmpDir);
            throw new IOException("No Vulkan .so library found inside driver .zip");
        }

        File drvDir = GameSettings.driversDir(c);
        String slug = (metaName != null && !metaName.isEmpty() ? metaName : mainSo.getName().replace(".so", ""))
                .replaceAll("[^A-Za-z0-9._-]+", "_");
        File destSub = new File(drvDir, slug);
        deleteDir(destSub);
        destSub.mkdirs();

        File[] extracted = tmpDir.listFiles();
        if (extracted != null) {
            for (File f : extracted) {
                if (f.getName().toLowerCase(Locale.ROOT).endsWith(".so") || f.getName().equalsIgnoreCase("meta.json")) {
                    copyFile(f, new File(destSub, f.getName()));
                }
            }
        }
        deleteDir(tmpDir);

        File finalSo = new File(destSub, mainSo.getName());
        GameSettings.set(c, "DM_VK_DRIVER", "drivers/" + slug + "/" + finalSo.getName());
        return metaName != null && !metaName.isEmpty() ? metaName : finalSo.getName();
    }

    public static void clearDriver(Context c) {
        GameSettings.set(c, "DM_VK_DRIVER", "");
    }

    private static String readText(File f) throws IOException {
        byte[] b = new byte[(int) Math.min(f.length(), 65536)];
        try (InputStream is = new java.io.FileInputStream(f)) {
            int n = is.read(b);
            return new String(b, 0, Math.max(0, n), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        byte[] buf = new byte[32768];
        try (InputStream is = new java.io.FileInputStream(src);
             FileOutputStream os = new FileOutputStream(dst)) {
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] ch = f.listFiles();
        if (ch != null) for (File c : ch) deleteDir(c);
        f.delete();
    }
}
