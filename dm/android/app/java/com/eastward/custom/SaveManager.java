package com.eastward.custom;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Exports and imports Eastward save files cleanly as `.zip` archives.
 */
public final class SaveManager {
    private SaveManager() {}

    public static int exportZip(Context c, Uri outUri) throws IOException {
        File saveRoot = GameSettings.saveDir(c);
        int count = 0;
        try (OutputStream raw = c.getContentResolver().openOutputStream(outUri)) {
            if (raw == null) throw new IOException("Cannot open destination URI");
            ZipOutputStream zos = new ZipOutputStream(raw);
            count = zipWalk(saveRoot, "", zos);
            zos.finish();
        }
        return count;
    }

    public static int importZip(Context c, Uri inUri) throws IOException {
        File saveRoot = GameSettings.saveDir(c);
        File tmp = new File(c.getCacheDir(), "save_import_tmp");
        deleteDir(tmp);
        tmp.mkdirs();

        int count = 0;
        byte[] buf = new byte[32768];
        try (InputStream raw = c.getContentResolver().openInputStream(inUri)) {
            if (raw == null) throw new IOException("Cannot open selected save ZIP");
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw));
            ZipEntry e;
            String canonicalRoot = tmp.getCanonicalPath() + File.separator;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                while (name.startsWith("/")) name = name.substring(1);
                if (name.startsWith("saves/010071B00F63A000/")) {
                    name = name.substring("saves/010071B00F63A000/".length());
                } else if (name.startsWith("010071B00F63A000/")) {
                    name = name.substring("010071B00F63A000/".length());
                }
                if (name.isEmpty()) continue;
                File out = new File(tmp, name);
                if (!out.getCanonicalPath().startsWith(canonicalRoot)) continue;
                out.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                }
                count++;
            }
        }

        if (count == 0) {
            deleteDir(tmp);
            throw new IOException("Selected ZIP contains no save files");
        }

        File backup = new File(saveRoot.getParentFile(), GameSettings.TITLE_ID + ".prev");
        deleteDir(backup);
        if (saveRoot.exists()) saveRoot.renameTo(backup);
        if (!tmp.renameTo(saveRoot)) {
            copyDir(tmp, saveRoot);
            deleteDir(tmp);
        }
        return count;
    }

    private static int zipWalk(File dir, String prefix, ZipOutputStream zos) throws IOException {
        if (!dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        byte[] buf = new byte[32768];
        for (File f : files) {
            String rel = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
            if (f.isDirectory()) {
                count += zipWalk(f, rel, zos);
            } else {
                zos.putNextEntry(new ZipEntry(rel));
                try (FileInputStream fis = new FileInputStream(f)) {
                    int n;
                    while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
                count++;
            }
        }
        return count;
    }

    private static void copyDir(File src, File dst) throws IOException {
        dst.mkdirs();
        File[] ch = src.listFiles();
        if (ch == null) return;
        byte[] buf = new byte[32768];
        for (File f : ch) {
            File to = new File(dst, f.getName());
            if (f.isDirectory()) copyDir(f, to);
            else try (FileInputStream is = new FileInputStream(f); FileOutputStream os = new FileOutputStream(to)) {
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
        }
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] ch = f.listFiles();
        if (ch != null) for (File c : ch) deleteDir(c);
        f.delete();
    }
}
