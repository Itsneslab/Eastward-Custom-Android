package com.eastward.custom;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.system.Os;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Clean Eastward-only standalone setup, driver, save & launch hub.
 */
public final class SetupActivity extends Activity {
    private static final int REQ_PERM = 100, REQ_GAME = 101, REQ_KEYS = 102, REQ_DRIVER = 103,
            REQ_SAVE_EXPORT = 104, REQ_SAVE_IMPORT = 105;

    private static volatile boolean setupRunning;
    private static final List<String> selectedPaths = new ArrayList<>();
    private static final List<Uri> selectedUris = new ArrayList<>();
    private static boolean allowDamagedOffer;

    private static final int C_BG        = 0xFF0D0F13;
    private static final int C_CARD      = 0xFF1C1F26;
    private static final int C_CARD_LINE = 0xFF2A2E37;
    private static final int C_CHIP      = 0xFF262A33;
    private static final int C_TEXT      = 0xFFF2F3F5;
    private static final int C_SUB       = 0xFF9AA0AB;
    private static final int C_ACCENT    = 0xFFF5C428;
    private static final int C_INK       = 0xFF1A1710;
    private static final int C_OK        = 0xFF6BE28A;
    private static final int C_WARN      = 0xFFFFB74D;
    private static final String GITHUB_URL = "https://github.com/Itsneslab/Eastward-Custom-Android";

    private TextView statusTv, gameFilesTv, keysTv, driverTv, readyBadgeTv;
    private ProgressBar progressBar;
    private TextView btnSetup, btnPlay, btnSetupAnyway;
    private ImageView iconView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (getIntent().getBooleanExtra("relaunch", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startGame();
            }, 250);
            return;
        }

        boolean showSetup = getIntent().getBooleanExtra("setup", false);
        registerDynamicShortcut();

        if (GameSettings.isReady(this) && !showSetup && !setupRunning) {
            startGame();
            return;
        }

        buildUi();
        requestStoragePermissionIfNeeded();
        if (!GameSettings.isReady(this) && selectedPaths.isEmpty() && selectedUris.isEmpty()) {
            File[] cached = new File(GameSettings.gameDir(this), "setup_work/import").listFiles();
            if (cached != null) {
                for (File f : cached) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (f.isFile() && f.length() > 0 && (n.endsWith(".nsp") || n.endsWith(".xci"))) {
                        selectedPaths.add(f.getPath());
                    }
                }
            }
        }
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (readyBadgeTv != null) refreshUi();
    }

    private void buildUi() {
        float dp = getResources().getDisplayMetrics().density;
        try {
            getWindow().setStatusBarColor(C_BG);
            getWindow().setNavigationBarColor(C_BG);
        } catch (Throwable ignored) {}

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setBackgroundColor(C_BG);
        rootScroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * dp);
        root.setPadding(p, (int) (14 * dp), p, p);
        rootScroll.addView(root);

        // Modern dark-glass Hero Header Card + GitHub Link + Play CTA
        LinearLayout header = card(dp);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        iconView = new ImageView(this);
        int icoSz = (int) (54 * dp);
        LinearLayout.LayoutParams icoLp = new LinearLayout.LayoutParams(icoSz, icoSz);
        icoLp.rightMargin = (int) (14 * dp);
        try (InputStream es = getAssets().open("icons/emblem.png")) {
            Bitmap em = BitmapFactory.decodeStream(es);
            if (em != null) iconView.setImageBitmap(em);
            else iconView.setVisibility(View.GONE);
        } catch (Throwable t) {
            iconView.setVisibility(View.GONE);
        }
        header.addView(iconView, icoLp);

        LinearLayout hTxt = new LinearLayout(this);
        hTxt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Eastward Custom");
        title.setTextColor(C_TEXT);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title);

        TextView verTag = new TextView(this);
        verTag.setText("v1.1.0");
        verTag.setTextColor(C_ACCENT);
        verTag.setTextSize(11);
        verTag.setTypeface(Typeface.DEFAULT_BOLD);
        verTag.setPadding((int) (8 * dp), (int) (2 * dp), (int) (8 * dp), (int) (2 * dp));
        GradientDrawable vbg = new GradientDrawable();
        vbg.setColor(0x22F5C428);
        vbg.setCornerRadius(10 * dp);
        vbg.setStroke((int) Math.max(1, dp), C_ACCENT);
        verTag.setBackground(vbg);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = (int) (10 * dp);
        titleRow.addView(verTag, vlp);
        hTxt.addView(titleRow);

        readyBadgeTv = new TextView(this);
        readyBadgeTv.setTextSize(13);
        readyBadgeTv.setPadding(0, (int) (4 * dp), 0, (int) (6 * dp));
        hTxt.addView(readyBadgeTv);

        // Clickable GitHub Profile / Repo Link Pill
        TextView ghPill = new TextView(this);
        ghPill.setText("GitHub  \u2022  Itsneslab/Eastward-Custom-Android  \u2197");
        ghPill.setTextColor(C_ACCENT);
        ghPill.setTextSize(12);
        ghPill.setTypeface(Typeface.DEFAULT_BOLD);
        ghPill.setPadding((int) (11 * dp), (int) (5 * dp), (int) (11 * dp), (int) (5 * dp));
        GradientDrawable ghBg = new GradientDrawable();
        ghBg.setColor(C_CHIP);
        ghBg.setCornerRadius(12 * dp);
        ghBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        ghPill.setBackground(ghBg);
        ghPill.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
            } catch (Exception e) {
                toast("GitHub: " + GITHUB_URL);
            }
        });
        LinearLayout.LayoutParams ghlp = new LinearLayout.LayoutParams(-2, -2);
        hTxt.addView(ghPill, ghlp);

        header.addView(hTxt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnPlay = actionBtn("Play Eastward", C_ACCENT, C_INK, dp, true);
        btnPlay.setPadding((int) (20 * dp), (int) (12 * dp), (int) (20 * dp), (int) (12 * dp));
        btnPlay.setOnClickListener(v -> startGame());
        header.addView(btnPlay);
        root.addView(header, cardLp(dp));

        // 2-column landscape body (Left: Setup .nsp + keys & Saves, Right: Driver & Engine Settings)
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);

        // Left Card 1: Game Files & Keys Setup
        LinearLayout setupCard = card(dp);
        cardTitle(setupCard, "GAME SETUP (.NSP / .XCI + PROD.KEYS)", dp);

        gameFilesTv = infoLine(setupCard, "Game files: none chosen", dp);
        TextView btnPickGame = actionBtn("Choose .nsp / .xci Files", C_CHIP, C_TEXT, dp, false);
        btnPickGame.setOnClickListener(v -> pickGameFiles());
        setupCard.addView(btnPickGame, btnLp(dp));

        keysTv = infoLine(setupCard, "Keys: checking...", dp);
        TextView btnPickKeys = actionBtn("Choose prod.keys (or title.keys)", C_CHIP, C_TEXT, dp, false);
        btnPickKeys.setOnClickListener(v -> pickKeys());
        setupCard.addView(btnPickKeys, btnLp(dp));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(400);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        GradientDrawable pbTrack = new GradientDrawable();
        pbTrack.setColor(C_CHIP);
        pbTrack.setCornerRadius(4 * dp);
        GradientDrawable pbFill = new GradientDrawable();
        pbFill.setColor(C_ACCENT);
        pbFill.setCornerRadius(4 * dp);
        android.graphics.drawable.LayerDrawable pbLayer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{
                    pbTrack,
                    new android.graphics.drawable.ClipDrawable(pbFill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
                });
        pbLayer.setId(0, android.R.id.background);
        pbLayer.setId(1, android.R.id.progress);
        progressBar.setProgressDrawable(pbLayer);
        setupCard.addView(progressBar, btnLp(dp));

        statusTv = infoLine(setupCard, "Pick your Eastward dump + prod.keys and tap Set up.", dp);
        statusTv.setTextColor(C_SUB);

        LinearLayout runRow = new LinearLayout(this);
        runRow.setOrientation(LinearLayout.HORIZONTAL);
        btnSetup = actionBtn("Set up Eastward", C_ACCENT, C_INK, dp, true);
        btnSetup.setOnClickListener(v -> startSetup(false));
        runRow.addView(btnSetup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnSetupAnyway = actionBtn("Set up anyway", C_WARN, C_INK, dp, true);
        btnSetupAnyway.setVisibility(View.GONE);
        btnSetupAnyway.setOnClickListener(v -> startSetup(true));
        LinearLayout.LayoutParams anywayLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        anywayLp.leftMargin = (int) (8 * dp);
        runRow.addView(btnSetupAnyway, anywayLp);
        setupCard.addView(runRow, btnLp(dp));

        leftCol.addView(setupCard, cardLp(dp));

        // Left Card 2: Save Data
        LinearLayout saveCard = card(dp);
        cardTitle(saveCard, "SAVE DATA BACKUP", dp);
        LinearLayout saveRow = new LinearLayout(this);
        saveRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnExport = actionBtn("Export Save (.zip)", C_CHIP, C_TEXT, dp, false);
        btnExport.setOnClickListener(v -> exportSave());
        LinearLayout.LayoutParams s1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        s1.rightMargin = (int) (8 * dp);
        saveRow.addView(btnExport, s1);

        TextView btnImport = actionBtn("Import Save (.zip)", C_CHIP, C_TEXT, dp, false);
        btnImport.setOnClickListener(v -> importSave());
        LinearLayout.LayoutParams s2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveRow.addView(btnImport, s2);
        saveCard.addView(saveRow, btnLp(dp));
        leftCol.addView(saveCard, cardLp(dp));

        // Right Card 1: Vulkan Driver (Turnip / PanVK)
        LinearLayout drvCard = card(dp);
        cardTitle(drvCard, "VULKAN GPU DRIVER (ADRENO TURNIP / MALI SYSTEM)", dp);
        driverTv = infoLine(drvCard, "", dp);
        LinearLayout drvRow = new LinearLayout(this);
        drvRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnPickDrv = actionBtn("Install Driver (.zip)", C_CHIP, C_TEXT, dp, false);
        btnPickDrv.setOnClickListener(v -> pickDriver());
        LinearLayout.LayoutParams d1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        d1.rightMargin = (int) (8 * dp);
        drvRow.addView(btnPickDrv, d1);

        TextView btnResetDrv = actionBtn("Use Phone Driver", C_CHIP, C_SUB, dp, false);
        btnResetDrv.setOnClickListener(v -> {
            DriverPackages.clearDriver(this);
            refreshUi();
            toast("Switched back to system Vulkan driver.");
        });
        LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        drvRow.addView(btnResetDrv, d2);
        drvCard.addView(drvRow, btnLp(dp));
        rightCol.addView(drvCard, cardLp(dp));

        // Right Card 2: Quick Settings
        LinearLayout cfgCard = card(dp);
        cardTitle(cfgCard, "EASTWARD ENGINE SETTINGS", dp);
        addChoiceRow(cfgCard, "Resolution", "DM_OPMODE", "0",
                new String[]{"720p Handheld", "1080p Docked"}, new String[]{"0", "1"}, dp);
        addChoiceRow(cfgCard, "Frame Target", "DM_FPS_TARGET", "",
                new String[]{"Auto", "30 FPS", "60 FPS"}, new String[]{"", "30", "60"}, dp);
        addChoiceRow(cfgCard, "Language", "DM_LANG", "en-US",
                new String[]{"English", "Japanese", "Chinese"}, new String[]{"en-US", "ja", "zh-Hans"}, dp);
        addToggleRow(cfgCard, "Mali FP16 Optimize", "DM_VK_MALI_FP16", true, dp);
        addToggleRow(cfgCard, "TBDR Clear LoadOp", "DM_VK_CLEAR_LOADOP", true, dp);
        addToggleRow(cfgCard, "Descriptor Reuse", "DM_VK_SET_REUSE", true, dp);
        addToggleRow(cfgCard, "Show FPS Counter", "DM_SHOW_FPS", false, dp);

        TextView btnResetEnv = actionBtn("Reset Engine Defaults", C_CHIP, C_SUB, dp, false);
        btnResetEnv.setOnClickListener(v -> {
            GameSettings.resetDefaults(this);
            recreate();
        });
        cfgCard.addView(btnResetEnv, btnLp(dp));
        rightCol.addView(cfgCard, cardLp(dp));

        LinearLayout.LayoutParams colL = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colL.rightMargin = (int) (12 * dp);
        cols.addView(leftCol, colL);
        LinearLayout.LayoutParams colR = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cols.addView(rightCol, colR);
        root.addView(cols);

        setContentView(rootScroll);
    }

    private void refreshUi() {
        boolean ready = GameSettings.isReady(this);
        File ico = new File(GameSettings.gameDir(this), "icon.jpg");
        if (ico.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(ico.getPath());
            if (b != null) {
                iconView.setImageBitmap(b);
                iconView.setVisibility(View.VISIBLE);
                setTaskDescription(new ActivityManager.TaskDescription("Eastward Custom", b));
            }
        }
        readyBadgeTv.setText(ready ? "Ready to play (010071B00F63A000)" : "Not set up yet — choose files below");
        readyBadgeTv.setTextColor(ready ? Color.rgb(125, 235, 145) : Color.rgb(255, 200, 110));
        btnPlay.setEnabled(ready && !setupRunning);
        btnPlay.setAlpha(ready && !setupRunning ? 1f : 0.45f);

        File pk = new File(GameSettings.keysDir(this), "prod.keys");
        boolean hasKeys = pk.isFile() && pk.length() > 0;
        keysTv.setText(hasKeys ? "Keys: prod.keys ready" : "Keys: prod.keys missing");
        keysTv.setTextColor(hasKeys ? Color.rgb(140, 225, 160) : Color.rgb(255, 180, 130));

        int count = selectedPaths.size() + selectedUris.size();
        if (count == 0) {
            if (hasExtractedCodeAndRomfs()) {
                gameFilesTv.setText("Game files: extracted & code patched (ready for shaders)");
            } else {
                gameFilesTv.setText("Game files: none chosen");
            }
        } else {
            StringBuilder sb = new StringBuilder("Game files (" + count + "): ");
            for (String p : selectedPaths) sb.append(new File(p).getName()).append(" ");
            for (Uri u : selectedUris) sb.append(queryName(u)).append(" ");
            gameFilesTv.setText(sb.toString().trim());
        }

        driverTv.setText("Active: " + GameSettings.driverLabel(this));
        btnSetup.setEnabled(!setupRunning);
        btnSetupAnyway.setVisibility(allowDamagedOffer && !setupRunning ? View.VISIBLE : View.GONE);
    }

    // ---- File Picking ----
    private void pickGameFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_GAME);
    }

    private void pickKeys() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_KEYS);
    }

    private void pickDriver() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(i, REQ_DRIVER);
    }

    private void exportSave() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, "eastward_save.zip");
        startActivityForResult(i, REQ_SAVE_EXPORT);
    }

    private void importSave() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(i, REQ_SAVE_IMPORT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;

        List<Uri> uris = new ArrayList<>();
        ClipData cd = data.getClipData();
        if (cd != null) {
            for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;

        if (req == REQ_GAME) {
            selectedPaths.clear();
            selectedUris.clear();
            for (Uri u : uris) {
                String name = queryName(u);
                if (name.toLowerCase(Locale.ROOT).endsWith(".keys")) {
                    importKeyUri(u, name);
                    continue;
                }
                String direct = resolveDirectPath(u);
                if (direct != null && new File(direct).canRead()) selectedPaths.add(direct);
                else selectedUris.add(u);
            }
            allowDamagedOffer = false;
            refreshUi();
        } else if (req == REQ_KEYS) {
            int ok = 0;
            for (Uri u : uris) {
                if (importKeyUri(u, queryName(u))) ok++;
            }
            if (ok > 0) toast("Keys imported.");
            refreshUi();
        } else if (req == REQ_DRIVER) {
            try {
                String installed = DriverPackages.installZip(this, uris.get(0));
                toast("Installed driver: " + installed);
            } catch (Exception e) {
                showError("Driver Install Failed", e.getMessage());
            }
            refreshUi();
        } else if (req == REQ_SAVE_EXPORT) {
            try {
                int n = SaveManager.exportZip(this, uris.get(0));
                toast("Exported " + n + " save files.");
            } catch (Exception e) {
                showError("Save Export Failed", e.getMessage());
            }
        } else if (req == REQ_SAVE_IMPORT) {
            try {
                int n = SaveManager.importZip(this, uris.get(0));
                toast("Imported " + n + " save files cleanly.");
            } catch (Exception e) {
                showError("Save Import Failed", e.getMessage());
            }
        }
    }

    private boolean importKeyUri(Uri u, String rawName) {
        String lower = rawName.toLowerCase(Locale.ROOT);
        String dstName = lower.contains("title") ? "title.keys" : "prod.keys";
        File dst = new File(GameSettings.keysDir(this), dstName);
        try (InputStream is = getContentResolver().openInputStream(u);
             FileOutputStream os = new FileOutputStream(dst)) {
            if (is == null) return false;
            copyStream(is, os);
            return true;
        } catch (IOException e) {
            showError("Key Import Error", e.getMessage());
            return false;
        }
    }

    // ---- On-Device Setup Runner ----
    private boolean hasExtractedCodeAndRomfs() {
        File g = GameSettings.gameDir(this);
        boolean hasCode = new File(g, "code/main.patched.bin").isFile();
        boolean hasRomfs = new File(g, "RomFS").isDirectory()
                || new File(g, "setup_work/extract/RomFS").isDirectory();
        return hasCode && hasRomfs;
    }

    private void startSetup(final boolean allowDamaged) {
        if (setupRunning) return;
        final boolean resumeShadersOnly = selectedPaths.isEmpty() && selectedUris.isEmpty()
                && hasExtractedCodeAndRomfs();
        if (!resumeShadersOnly) {
            File pk = new File(GameSettings.keysDir(this), "prod.keys");
            if (!pk.isFile() || pk.length() == 0) {
                toast("Choose your prod.keys first.");
                return;
            }
            if (selectedPaths.isEmpty() && selectedUris.isEmpty()) {
                toast("Choose your Eastward .nsp / .xci file first.");
                return;
            }
        }

        setupRunning = true;
        allowDamagedOffer = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusTv.setText("Preparing Python & extraction tools...");
        refreshUi();

        final List<String> pathsSnap = new ArrayList<>(selectedPaths);
        final List<Uri> urisSnap = new ArrayList<>(selectedUris);

        new Thread(() -> {
            String err = null;
            int[] stepNo = {1}, stepOf = {4};
            String[] stepName = {"unpack"};
            Process proc = null;
            try {
                File py = unpackAssetZip("python.zip", new File(getFilesDir(), "python"));
                File dm = unpackAssetZip("dm.zip", new File(getFilesDir(), "dm"));
                File bin = prepareBinDir();
                File gameDir = GameSettings.gameDir(this);
                File work = new File(gameDir, "setup_work");
                work.mkdirs();
                if (!resumeShadersOnly) {
                    deleteTree(new File(work, "extract"));
                    deleteTree(new File(work, "aoc"));
                }
                deleteTree(new File(work, "shader_pkg"));

                List<String> inputs = new ArrayList<>(pathsSnap);
                if (!urisSnap.isEmpty()) {
                    File impDir = new File(work, "import");
                    deleteTree(impDir);
                    impDir.mkdirs();
                    for (int idx = 0; idx < urisSnap.size(); idx++) {
                        Uri u = urisSnap.get(idx);
                        String nm = queryName(u);
                        final String copyName = nm.isEmpty() ? "dump_" + idx + ".nsp" : nm;
                        File dst = new File(impDir, copyName);
                        runOnUiThread(() -> statusTv.setText("Copying " + copyName + " into workspace..."));
                        try (InputStream is = getContentResolver().openInputStream(u);
                             FileOutputStream os = new FileOutputStream(dst)) {
                            if (is == null) throw new IOException("Cannot open " + nm);
                            copyStream(is, os);
                        }
                        inputs.add(dst.getPath());
                    }
                }

                // Order largest first so base NSP precedes smaller update/DLC if needed
                Collections.sort(inputs, (a, b) -> Long.compare(new File(b).length(), new File(a).length()));

                String libDir = getApplicationInfo().nativeLibraryDir;
                List<String> cmd = new ArrayList<>();
                cmd.add(libDir + "/libpython_exe.so");
                cmd.add(new File(dm, "android/tools/setup_game.py").getPath());
                if (resumeShadersOnly) {
                    cmd.add("--shaders-only");
                } else {
                    cmd.add("--keys"); cmd.add(GameSettings.keysDir(this).getPath());
                    cmd.add("--base"); cmd.add(inputs.get(0));
                    for (int k = 1; k < inputs.size(); k++) {
                        cmd.add("--dlc"); cmd.add(inputs.get(k));
                    }
                }
                cmd.add("--out"); cmd.add(gameDir.getPath());
                cmd.add("--work"); cmd.add(work.getPath());
                cmd.add("--bin"); cmd.add(bin.getPath());
                File stdF = new File(dm, "android/assets/standard.ttf");
                File extF = new File(dm, "android/assets/nintendo_ext.ttf");
                if (stdF.isFile()) { cmd.add("--std-font"); cmd.add(stdF.getPath()); }
                if (extF.isFile()) { cmd.add("--ext-font"); cmd.add(extF.getPath()); }
                if (allowDamaged) cmd.add("--allow-damaged");

                ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
                java.util.Map<String, String> env = pb.environment();
                env.put("PYTHONHOME", py.getPath());
                env.put("PYTHONPATH", new File(dm, "android/pyshim").getPath());
                env.put("LD_LIBRARY_PATH", bin.getPath() + ":" + libDir);
                env.put("LIBCAPSTONE_PATH", libDir);
                env.put("NX_NATIVE_LIB_DIR", libDir);
                env.put("DOTNET_OPENSSL_VERSION_OVERRIDE", "3");
                env.put("DOTNET_SYSTEM_GLOBALIZATION_INVARIANT", "1");
                env.put("TMPDIR", getCacheDir().getPath());
                env.put("DM_GAME", GameSettings.TITLE_ID);
                env.put("J", String.valueOf(setupWorkers()));

                proc = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String ln;
                    while ((ln = r.readLine()) != null) {
                        final String line = ln;
                        if (line.startsWith("STEP ")) {
                            String[] parts = line.split(" ", 3);
                            String[] frac = parts[1].split("/");
                            stepNo[0] = Integer.parseInt(frac[0]);
                            stepOf[0] = Integer.parseInt(frac[1]);
                            stepName[0] = parts.length > 2 ? parts[2] : "";
                            runOnUiThread(() -> {
                                progressBar.setProgress((stepNo[0] - 1) * 100);
                                statusTv.setText(String.format(Locale.ROOT, "Step %d/%d: %s...", stepNo[0], stepOf[0], stepName[0]));
                            });
                        } else if (line.startsWith("PROGRESS ")) {
                            String[] f = line.substring(9).trim().split("/");
                            long a = Long.parseLong(f[0]), total = Math.max(1, Long.parseLong(f[1]));
                            int pct = (int) Math.min(100, a * 100 / total);
                            int curStep = stepNo[0], totStep = stepOf[0];
                            String curName = stepName[0];
                            runOnUiThread(() -> {
                                progressBar.setProgress((curStep - 1) * 100 + pct);
                                statusTv.setText(String.format(Locale.ROOT, "Step %d/%d: %s... %d%%", curStep, totStep, curName, pct));
                            });
                        } else if (line.startsWith("OFFER allow-damaged")) {
                            allowDamagedOffer = true;
                        } else if (line.startsWith("FAILED ")) {
                            err = line.substring(7).trim();
                        }
                    }
                }
                int rc = proc.waitFor();
                if (rc != 0 && err == null) err = "setup stopped (" + rc + ")";
            } catch (Exception ex) {
                err = ex.toString();
                if (proc != null) proc.destroy();
            }

            final String finalErr = err;
            runOnUiThread(() -> {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setupRunning = false;
                if (finalErr == null && GameSettings.isReady(this)) {
                    progressBar.setProgress(400);
                    statusTv.setText("Eastward setup complete! Ready to play.");
                    refreshUi();
                    toast("Setup complete!");
                } else {
                    progressBar.setVisibility(View.GONE);
                    statusTv.setText("Setup failed: " + (finalErr != null ? finalErr : "unknown error"));
                    refreshUi();
                    showError("Setup Error", finalErr != null ? finalErr : "Unknown error");
                }
            });
        }).start();
    }

    private int setupWorkers() {
        int cores = Runtime.getRuntime().availableProcessors();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        int byRam = mi.totalMem > 0 ? (int) Math.max(1, mi.totalMem / (1536L << 20)) : 2;
        return Math.max(1, Math.min(4, Math.min(cores, byRam)));
    }

    private File prepareBinDir() throws IOException {
        File d = new File(getFilesDir(), "bin");
        d.mkdirs();
        String lib = getApplicationInfo().nativeLibraryDir;
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

    private File unpackAssetZip(String assetName, File dst) throws IOException {
        long ver;
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
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
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(getAssets().open(assetName)))) {
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

    // ---- Permissions & Path Helpers ----
    private void requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_PERM);
        }
    }

    private String resolveDirectPath(Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) return uri.getPath();
        if (DocumentsContract.isDocumentUri(this, uri)
                && "com.android.externalstorage.documents".equals(uri.getAuthority())) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":", 2);
            if ("primary".equalsIgnoreCase(split[0]) && split.length > 1) {
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (!split[0].isEmpty() && split.length > 1) {
                return "/storage/" + split[0] + "/" + split[1];
            }
        }
        return null;
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last != null ? new File(last).getName() : "file";
    }

    private void registerDynamicShortcut() {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null) return;
        Intent setup = new Intent(this, SetupActivity.class).setAction(Intent.ACTION_MAIN).putExtra("setup", true);
        ShortcutInfo si = new ShortcutInfo.Builder(this, "setup")
                .setShortLabel("Setup & Settings")
                .setLongLabel("Eastward Setup & Settings")
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_manage))
                .setIntent(setup)
                .build();
        try { sm.setDynamicShortcuts(Arrays.asList(si)); } catch (Exception ignored) {}
    }

    private void startGame() {
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, String msg) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] ch = f.listFiles();
        if (ch != null) for (File c : ch) deleteTree(c);
        f.delete();
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
    }

    // ---- UI Builders (Obsidian Glass + Eastward Gold) ----
    private LinearLayout card(float dp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(C_CARD);
        d.setCornerRadius(18 * dp);
        d.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        l.setBackground(d);
        int p = (int) (16 * dp);
        l.setPadding(p, (int) (14 * dp), p, (int) (14 * dp));
        return l;
    }

    private LinearLayout.LayoutParams cardLp(float dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * dp);
        return lp;
    }

    private LinearLayout.LayoutParams btnLp(float dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * dp);
        return lp;
    }

    private void cardTitle(LinearLayout p, String text, float dp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(C_ACCENT);
        t.setLetterSpacing(0.06f);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, (int) (6 * dp));
        p.addView(t);
    }

    private TextView infoLine(LinearLayout p, String text, float dp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(C_SUB);
        t.setTextSize(13);
        t.setPadding(0, (int) (4 * dp), 0, (int) (2 * dp));
        p.addView(t);
        return t;
    }

    private TextView actionBtn(String text, int bg, int fg, float dp, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        styleBtn(b, bg, dp, primary);
        return b;
    }

    private void styleBtn(TextView b, int bg, float dp, boolean primary) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(14 * dp);
        if (!primary) d.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        b.setBackground(d);
        b.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (10 * dp));
    }

    private void addChoiceRow(LinearLayout parent, String label, String key, String def,
                              String[] labels, String[] vals, float dp) {
        TextView lbl = infoLine(parent, label, dp);
        lbl.setTextColor(C_TEXT);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String cur = GameSettings.get(this, key, def);
        final TextView[] btns = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean sel = cur.equals(vals[i]);
            TextView b = actionBtn(labels[i], sel ? C_ACCENT : C_CHIP, sel ? C_INK : C_TEXT, dp, sel);
            b.setOnClickListener(v -> {
                GameSettings.set(this, key, vals[idx]);
                for (int k = 0; k < btns.length; k++) {
                    boolean active = (k == idx);
                    styleBtn(btns[k], active ? C_ACCENT : C_CHIP, dp, active);
                    btns[k].setTextColor(active ? C_INK : C_TEXT);
                }
            });
            btns[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < labels.length - 1) lp.rightMargin = (int) (6 * dp);
            row.addView(b, lp);
        }
        parent.addView(row, btnLp(dp));
    }

    private void addToggleRow(LinearLayout parent, String label, String key, boolean def, float dp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(C_TEXT);
        tv.setTextSize(13);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final boolean[] state = {"1".equals(GameSettings.get(this, key, def ? "1" : "0"))};
        int w = (int) (46 * dp), h = (int) (25 * dp), pad = (int) (3 * dp), thumbSz = h - pad * 2;
        android.widget.FrameLayout track = new android.widget.FrameLayout(this);
        track.setLayoutParams(new LinearLayout.LayoutParams(w, h));

        View thumb = new View(this);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.OVAL);
        thumb.setBackground(thumbBg);
        android.widget.FrameLayout.LayoutParams tlp = new android.widget.FrameLayout.LayoutParams(thumbSz, thumbSz);
        tlp.gravity = Gravity.CENTER_VERTICAL;
        track.addView(thumb, tlp);

        Runnable sync = () -> {
            GradientDrawable trBg = new GradientDrawable();
            trBg.setColor(state[0] ? C_ACCENT : 0xFF2B303B);
            trBg.setCornerRadius(h * 0.5f);
            if (!state[0]) trBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
            track.setBackground(trBg);
            thumbBg.setColor(state[0] ? C_INK : 0xFFE2E5EC);
            tlp.leftMargin = state[0] ? (w - pad - thumbSz) : pad;
            thumb.setLayoutParams(tlp);
        };
        sync.run();

        track.setOnClickListener(v -> {
            state[0] = !state[0];
            sync.run();
            GameSettings.set(this, key, state[0] ? "1" : "0");
        });
        row.addView(track);
        parent.addView(row);
    }
}
