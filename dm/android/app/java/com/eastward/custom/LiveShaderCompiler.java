package com.eastward.custom;

import android.app.Activity;
import android.content.Context;
import android.system.Os;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Keeps runtime-captured GLSLC outputs translated into Vulkan SPIR-V inside the :game process
 * so players never hit a first-launch or area-transition black screen.
 */
public final class LiveShaderCompiler {
    private static final String TAG = "EastwardLive";
    private static final Object LOCK = new Object();

    private static Process proc;
    private static BufferedReader outReader;
    private static BufferedWriter inWriter;
    private static String activeCaptureDir;
    private static String activeSpvDir;

    private LiveShaderCompiler() {}

    public static int translateNow(Activity a) {
        if (a == null) return 0;
        final Context ctx = a.getApplicationContext();
        synchronized (LOCK) {
            try {
                String cap = Gamepad.nativeEnv("DM_GLSLC_CAPTURE");
                if (cap == null || cap.isEmpty()) {
                    cap = new File(GameSettings.gameDir(ctx), "glslc_capture").getPath();
                }
                String spv = Gamepad.nativeEnv("DM_SPV_DIR");
                if (spv == null || spv.isEmpty()) {
                    spv = new File(GameSettings.gameDir(ctx), "spv").getPath();
                }

                File capDir = new File(cap);
                String[] list = capDir.list((d, n) -> n.endsWith(".glslc"));
                if (list == null || list.length == 0) return 0;

                ensureStarted(ctx, cap, spv);
                if (inWriter == null || outReader == null) return 0;

                inWriter.write("SYNC\n");
                inWriter.flush();

                String line;
                while ((line = outReader.readLine()) != null) {
                    if (line.startsWith("OK ")) {
                        try {
                            return Integer.parseInt(line.substring(3).trim());
                        } catch (NumberFormatException ignored) {
                            return 0;
                        }
                    }
                    if (line.startsWith("ERR ")) {
                        return 0;
                    }
                }
                stopLocked();
            } catch (Throwable t) {
                Log.w(TAG, "live shader sync failed: " + t.getMessage());
                stopLocked();
            }
            return 0;
        }
    }

    private static void ensureStarted(Context ctx, String capDir, String spvDir) throws IOException {
        if (proc != null && proc.isAlive()
                && capDir.equals(activeCaptureDir)
                && spvDir.equals(activeSpvDir)) {
            return;
        }
        stopLocked();

        File filesDir = ctx.getFilesDir();
        File py = unpackAssetZip(ctx, "python.zip", new File(filesDir, "python"));
        File dm = unpackAssetZip(ctx, "dm.zip", new File(filesDir, "dm"));
        File bin = prepareBinDir(ctx, filesDir);

        File script = new File(dm, "android/tools/glslc_live.py");
        if (!script.isFile()) {
            throw new IOException("missing " + script.getPath());
        }
        File xlate = new File(bin, "dmsh_xlate");
        File work = new File(ctx.getCacheDir(), "live_spv_work");
        work.mkdirs();
        File facts = new File(dm, "games/" + GameSettings.TITLE_ID + "/shader_facts.tsv");

        String libDir = ctx.getApplicationInfo().nativeLibraryDir;
        List<String> cmd = Arrays.asList(
                libDir + "/libpython_exe.so",
                script.getPath(),
                capDir,
                spvDir,
                xlate.getPath(),
                work.getPath(),
                "--facts",
                facts.getPath()
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        env.put("PYTHONHOME", py.getPath());
        env.put("PYTHONPATH", new File(dm, "android/pyshim").getPath()
                + ":" + new File(dm, "android/tools").getPath()
                + ":" + new File(dm, "gfx/tools").getPath());
        env.put("LD_LIBRARY_PATH", bin.getPath() + ":" + libDir);
        env.put("LIBCAPSTONE_PATH", libDir);
        env.put("NX_NATIVE_LIB_DIR", libDir);
        env.put("DOTNET_OPENSSL_VERSION_OVERRIDE", "3");
        env.put("DOTNET_SYSTEM_GLOBALIZATION_INVARIANT", "1");
        env.put("TMPDIR", ctx.getCacheDir().getPath());
        env.put("HOME", filesDir.getPath());
        env.put("DM_GAME", GameSettings.TITLE_ID);
        env.put("DM_NO_FORK", "1");

        Process p = pb.start();
        final BufferedReader err = new BufferedReader(
                new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
        Thread t = new Thread(() -> {
            try {
                String ln;
                while ((ln = err.readLine()) != null) {
                    Log.i(TAG, ln);
                    try { Gamepad.nativeLog(ln); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }, "glslc-live-err");
        t.setDaemon(true);
        t.start();

        BufferedReader out = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter in = new BufferedWriter(
                new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));

        String ready = out.readLine();
        if (!"READY".equals(ready)) {
            p.destroyForcibly();
            throw new IOException("glslc_live did not become READY (got " + ready + ")");
        }

        proc = p;
        outReader = out;
        inWriter = in;
        activeCaptureDir = capDir;
        activeSpvDir = spvDir;
    }

    private static void stopLocked() {
        try { if (inWriter != null) inWriter.close(); } catch (Throwable ignored) {}
        try { if (outReader != null) outReader.close(); } catch (Throwable ignored) {}
        if (proc != null) {
            try { proc.destroyForcibly(); } catch (Throwable ignored) {}
        }
        proc = null;
        outReader = null;
        inWriter = null;
        activeCaptureDir = null;
        activeSpvDir = null;
    }

    private static File prepareBinDir(Context ctx, File filesDir) throws IOException {
        File d = new File(filesDir, "bin");
        d.mkdirs();
        String lib = ctx.getApplicationInfo().nativeLibraryDir;
        String[][] links = {
                {"nx_extract", "libnx_extract.so"},
                {"dmsh_xlate", "libdmsh_xlate.so"},
                {"libssl.so.3", "libssl_python.so"},
                {"libcrypto.so.3", "libcrypto_python.so"}
        };
        for (String[] l : links) {
            File f = new File(d, l[0]);
            f.delete();
            try {
                Os.symlink(lib + "/" + l[1], f.getPath());
            } catch (Exception e) {
                throw new IOException("symlink " + l[0] + ": " + e.getMessage());
            }
        }
        return d;
    }

    private static File unpackAssetZip(Context ctx, String assetName, File dst) throws IOException {
        long ver;
        try {
            ver = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            ver = 1;
        }
        File stamp = new File(dst, ".unpacked");
        try (BufferedReader r = new BufferedReader(new FileReader(stamp))) {
            if (Long.parseLong(r.readLine().trim()) == ver) return dst;
        } catch (Exception ignored) {}

        dst.mkdirs();
        String canonicalDst = dst.getCanonicalPath() + File.separator;
        byte[] buf = new byte[32768];
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(ctx.getAssets().open(assetName)))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                File out = new File(dst, e.getName());
                if (!out.getCanonicalPath().startsWith(canonicalDst)) continue;
                if (e.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    try (FileOutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = z.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
            }
        }
        try (FileWriter w = new FileWriter(stamp)) {
            w.write(ver + "\n");
        }
        return dst;
    }
}
