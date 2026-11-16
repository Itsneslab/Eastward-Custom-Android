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
            REQ_SAVE_EXPORT = 104, REQ_SAVE_IMPORT = 105,
            REQ_UPDATE = 106, REQ_DLC = 107, REQ_LOG_EXPORT = 108;

    private static volatile boolean setupRunning;
    private static String basePath;
    private static Uri baseUri;
    private static String updatePath;
    private static Uri updateUri;
    private static final List<String> dlcPaths = new ArrayList<>();
    private static final List<Uri> dlcUris = new ArrayList<>();
    private static boolean allowDamagedOffer;

    private static final int C_BG        = 0xFF0D0F13;
    private static final int C_CARD      = 0xFF1C1F26;
    private static final int C_PANEL     = 0xFF151820;
    private static final int C_CARD_LINE = 0xFF2A2E37;
    private static final int C_CHIP      = 0xFF262A33;
    private static final int C_TEXT      = 0xFFF2F3F5;
    private static final int C_SUB       = 0xFF9AA0AB;
    private static final int C_ACCENT    = 0xFFF5C428;
    private static final int C_INK       = 0xFF1A1710;
    private static final int C_OK        = 0xFF6BE28A;
    private static final int C_WARN      = 0xFFFFB74D;
    private static final String GITHUB_URL = "https://github.com/Itsneslab/Eastward-Custom-Android";

    private TextView statusTv, baseFileTv, updateFileTv, dlcFilesTv, keysTv, driverTv, readyBadgeTv;
    private TextView btnClearUpdate, btnClearDlc;
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

        registerDynamicShortcut();
        GameSettings.ensureAutoTuned(this);

        buildUi();
        requestStoragePermissionIfNeeded();
        if (!GameSettings.isReady(this) && basePath == null && baseUri == null) {
            File[] cached = new File(GameSettings.gameDir(this), "setup_work/import").listFiles();
            if (cached != null) {
                Arrays.sort(cached, (a, c) -> Long.compare(c.length(), a.length()));
                for (File f : cached) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (!f.isFile() || f.length() <= 0 || !(n.endsWith(".nsp") || n.endsWith(".xci"))) continue;
                    if (n.startsWith("update_") && updatePath == null && updateUri == null) {
                        updatePath = f.getPath();
                    } else if (n.startsWith("dlc_")) {
                        dlcPaths.add(f.getPath());
                    } else if (basePath == null) {
                        basePath = f.getPath();
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
        verTag.setText("v1.1.2");
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

        // Compact vertical utility stack: Export Log (.zip) directly above ID | ENG
        LinearLayout rightHdr = new LinearLayout(this);
        rightHdr.setOrientation(LinearLayout.VERTICAL);
        rightHdr.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView btnExportLog = new TextView(this);
        btnExportLog.setText(UiText.tr(this, "Export Log", "Ekspor Log"));
        btnExportLog.setTextColor(C_ACCENT);
        btnExportLog.setTextSize(11);
        btnExportLog.setSingleLine(true);
        btnExportLog.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnExportLog.setGravity(Gravity.CENTER);
        btnExportLog.setPadding((int) (14 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
        GradientDrawable exBg = new GradientDrawable();
        exBg.setColor(C_CHIP);
        exBg.setCornerRadius(10 * dp);
        exBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        btnExportLog.setBackground(exBg);
        btnExportLog.setOnClickListener(v -> exportLogs());
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exLp.bottomMargin = (int) (5 * dp);
        rightHdr.addView(btnExportLog, exLp);

        LinearLayout langBox = new LinearLayout(this);
        langBox.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable langBg = new android.graphics.drawable.GradientDrawable();
        langBg.setColor(C_PANEL);
        langBg.setCornerRadius(10 * dp);
        langBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        langBox.setBackground(langBg);
        int lpad = (int) (3 * dp);
        langBox.setPadding(lpad, lpad, lpad, lpad);
        boolean isId = UiText.isId(this);
        TextView btnId = new TextView(this);
        btnId.setText("ID");
        btnId.setTextSize(11);
        btnId.setSingleLine(true);
        btnId.setGravity(Gravity.CENTER);
        btnId.setMinWidth((int) (42 * dp));
        btnId.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnId.setPadding((int) (12 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
        if (isId) {
            android.graphics.drawable.GradientDrawable act = new android.graphics.drawable.GradientDrawable();
            act.setColor(C_ACCENT);
            act.setCornerRadius(8 * dp);
            btnId.setBackground(act);
            btnId.setTextColor(C_INK);
        } else {
            btnId.setTextColor(C_SUB);
        }
        btnId.setOnClickListener(v -> {
            if (!UiText.isId(this)) {
                UiText.setLang(this, "id");
                buildUi();
                refreshUi();
            }
        });
        TextView btnEng = new TextView(this);
        btnEng.setText("ENG");
        btnEng.setTextSize(11);
        btnEng.setSingleLine(true);
        btnEng.setGravity(Gravity.CENTER);
        btnEng.setMinWidth((int) (46 * dp));
        btnEng.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnEng.setPadding((int) (12 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
        if (!isId) {
            android.graphics.drawable.GradientDrawable act = new android.graphics.drawable.GradientDrawable();
            act.setColor(C_ACCENT);
            act.setCornerRadius(8 * dp);
            btnEng.setBackground(act);
            btnEng.setTextColor(C_INK);
        } else {
            btnEng.setTextColor(C_SUB);
        }
        btnEng.setOnClickListener(v -> {
            if (UiText.isId(this)) {
                UiText.setLang(this, "en");
                buildUi();
                refreshUi();
            }
        });
        langBox.addView(btnId, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        langBox.addView(btnEng, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rightHdr.addView(langBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams hdrStackLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hdrStackLp.rightMargin = (int) (10 * dp);
        header.addView(rightHdr, hdrStackLp);

        btnPlay = actionBtn(UiText.tr(this, "Play Eastward", "Mainkan Eastward"), C_ACCENT, C_INK, dp, true);
        btnPlay.setPadding((int) (20 * dp), (int) (14 * dp), (int) (20 * dp), (int) (14 * dp));
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

        // Left Card 1: Game Files, Update, DLC & Keys Setup
        LinearLayout setupCard = card(dp);
        cardTitle(setupCard, UiText.tr(this, "GAME FILES, UPDATE, DLC & KEYS", "FILE GAME, UPDATE, DLC & KEYS"), dp);

        LinearLayout filesPanel = panel(dp);
        baseFileTv = new TextView(this);
        addFileSlotRow(filesPanel,
                UiText.tr(this, "Base Game (.nsp / .xci)", "Base Game (.nsp / .xci)"),
                baseFileTv,
                UiText.tr(this, "Choose", "Pilih"),
                v -> pickBaseGame(), null, dp);
        filesPanel.addView(divider(dp));

        updateFileTv = new TextView(this);
        btnClearUpdate = addFileSlotRow(filesPanel,
                UiText.tr(this, "Update (.nsp, Optional)", "Update (.nsp, Opsional)"),
                updateFileTv,
                UiText.tr(this, "Upload", "Pilih"),
                v -> pickUpdateFile(),
                v -> {
                    updatePath = null;
                    updateUri = null;
                    allowDamagedOffer = false;
                    refreshUi();
                }, dp);
        filesPanel.addView(divider(dp));

        dlcFilesTv = new TextView(this);
        btnClearDlc = addFileSlotRow(filesPanel,
                UiText.tr(this, "DLC (.nsp, Optional)", "DLC (.nsp, Opsional)"),
                dlcFilesTv,
                UiText.tr(this, "Upload", "Pilih"),
                v -> pickDlcFiles(),
                v -> {
                    dlcPaths.clear();
                    dlcUris.clear();
                    allowDamagedOffer = false;
                    refreshUi();
                }, dp);
        filesPanel.addView(divider(dp));

        keysTv = new TextView(this);
        addFileSlotRow(filesPanel,
                UiText.tr(this, "Switch Keys (prod.keys)", "Switch Keys (prod.keys)"),
                keysTv,
                UiText.tr(this, "Choose", "Pilih"),
                v -> pickKeys(), null, dp);
        setupCard.addView(filesPanel, btnLp(dp));

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

        statusTv = infoLine(setupCard, UiText.tr(this, "Pick your Eastward dump + prod.keys and tap Set up.", "Pilih file Eastward + prod.keys lalu tekan Mulai Setup."), dp);
        statusTv.setTextColor(C_SUB);

        LinearLayout runRow = new LinearLayout(this);
        runRow.setOrientation(LinearLayout.HORIZONTAL);
        btnSetup = actionBtn(UiText.tr(this, "Set up Eastward", "Setup Eastward"), C_ACCENT, C_INK, dp, true);
        btnSetup.setOnClickListener(v -> startSetup(false));
        runRow.addView(btnSetup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnSetupAnyway = actionBtn(UiText.tr(this, "Set up anyway", "Tetap Setup"), C_WARN, C_INK, dp, true);
        btnSetupAnyway.setVisibility(View.GONE);
        btnSetupAnyway.setOnClickListener(v -> startSetup(true));
        LinearLayout.LayoutParams anywayLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        anywayLp.leftMargin = (int) (8 * dp);
        runRow.addView(btnSetupAnyway, anywayLp);
        setupCard.addView(runRow, btnLp(dp));

        leftCol.addView(setupCard, cardLp(dp));

        // Left Card 2: Save Data
        LinearLayout saveCard = card(dp);
        cardTitle(saveCard, UiText.tr(this, "SAVE DATA BACKUP", "CADANGAN SAVE DATA"), dp);
        LinearLayout saveRow = new LinearLayout(this);
        saveRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnExport = actionBtn(UiText.tr(this, "Export Save (.zip)", "Ekspor Save (.zip)"), C_CHIP, C_TEXT, dp, false);
        btnExport.setOnClickListener(v -> exportSave());
        LinearLayout.LayoutParams s1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        s1.rightMargin = (int) (8 * dp);
        saveRow.addView(btnExport, s1);

        TextView btnImport = actionBtn(UiText.tr(this, "Import Save (.zip)", "Impor Save (.zip)"), C_CHIP, C_TEXT, dp, false);
        btnImport.setOnClickListener(v -> importSave());
        LinearLayout.LayoutParams s2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveRow.addView(btnImport, s2);
        saveCard.addView(saveRow, btnLp(dp));
        leftCol.addView(saveCard, cardLp(dp));

        // Right Card 1: Vulkan Driver (Turnip / PanVK)
        LinearLayout drvCard = card(dp);
        cardTitle(drvCard, UiText.tr(this, "VULKAN GPU DRIVER (ADRENO TURNIP / MALI SYSTEM)", "DRIVER GPU VULKAN (ADRENO TURNIP / MALI)"), dp);
        driverTv = infoLine(drvCard, "", dp);
        LinearLayout drvRow = new LinearLayout(this);
        drvRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnPickDrv = actionBtn(UiText.tr(this, "Install Driver (.zip)", "Pasang Driver (.zip)"), C_CHIP, C_TEXT, dp, false);
        btnPickDrv.setOnClickListener(v -> pickDriver());
        LinearLayout.LayoutParams d1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        d1.rightMargin = (int) (8 * dp);
        drvRow.addView(btnPickDrv, d1);

        TextView btnResetDrv = actionBtn(UiText.tr(this, "Use Phone Driver", "Pakai Driver Bawaan"), C_CHIP, C_SUB, dp, false);
        btnResetDrv.setOnClickListener(v -> {
            DriverPackages.clearDriver(this);
            refreshUi();
            toast(UiText.tr(this, "Switched back to system Vulkan driver.", "Kembali memakai driver Vulkan bawaan HP."));
        });
        LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        drvRow.addView(btnResetDrv, d2);
        drvCard.addView(drvRow, btnLp(dp));
        rightCol.addView(drvCard, cardLp(dp));

        // Right Card 2: Quick Settings
        LinearLayout cfgCard = card(dp);
        cardTitle(cfgCard, UiText.tr(this, "ENGINE & CHIPSET SETTINGS",
                "PENGATURAN ENGINE & CHIPSET"), dp);

        // Universal Auto-Tune + preset bar across Snapdragon / MediaTek / Exynos
        TextView btnAutoTune = actionBtn(UiText.tr(this, "Auto-Tune This Device",
                "Sesuaikan Otomatis HP Ini"), C_ACCENT, C_INK, dp, true);
        btnAutoTune.setOnClickListener(v -> {
            String summary = GameSettings.autoTuneDevice(this);
            toast(UiText.tr(this, "Auto-tuned: ", "Disesuaikan otomatis: ") + summary);
            buildUi();
            refreshUi();
        });
        cfgCard.addView(btnAutoTune, btnLp(dp));

        LinearLayout profRow = new LinearLayout(this);
        profRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] profKeys = {"performance", "balanced", "quality"};
        String[] profLabels = {
            UiText.tr(this, "Performance", "Performa"),
            UiText.tr(this, "Balanced", "Seimbang"),
            UiText.tr(this, "Quality 1080p", "Kualitas 1080p")
        };
        for (int i = 0; i < profKeys.length; i++) {
            final String pk = profKeys[i];
            TextView pb = actionBtn(profLabels[i], C_CHIP, C_TEXT, dp, false);
            pb.setTextSize(12);
            pb.setOnClickListener(v -> {
                GameSettings.applyProfile(this, pk);
                toast(UiText.tr(this, "Profile applied: ", "Profil diterapkan: ") + pb.getText());
                buildUi();
                refreshUi();
            });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) plp.leftMargin = (int) (6 * dp);
            profRow.addView(pb, plp);
        }
        cfgCard.addView(profRow, btnLp(dp));

        addChoiceRow(cfgCard, UiText.tr(this, "Resolution", "Resolusi"), "DM_OPMODE", "0",
                new String[]{UiText.tr(this, "720p Handheld", "720p Ringan"),
                        UiText.tr(this, "1080p Docked", "1080p Tajam")},
                new String[]{"0", "1"}, dp);
        addChoiceRow(cfgCard, UiText.tr(this, "Frame Pacing", "Sinkronisasi Frame"), "DM_PRESENT_HZ", "",
                new String[]{UiText.tr(this, "60 Hz Sync", "60 Hz Normal"),
                        UiText.tr(this, "Uncapped", "Tanpa Batas")},
                new String[]{"", "0"}, dp);
        addChoiceRow(cfgCard, UiText.tr(this, "Game Language", "Bahasa Game"), "DM_LANG", "en-US",
                new String[]{"English", "Japanese", "Chinese"}, new String[]{"en-US", "ja", "zh-Hans"}, dp);
        addChoiceRow(cfgCard, UiText.tr(this, "CPU Core Topology", "Topologi Core CPU"), "DM_VK_PIN", "",
                new String[]{UiText.tr(this, "Auto", "Otomatis"), "2+6 (c0)", "4+4 (f0)", "1+3+4 (80)"},
                new String[]{"", "c0", "f0", "80"}, dp);
        addChoiceRow(cfgCard, UiText.tr(this, "CPU Boost (UClamp)", "Boost CPU (UClamp)"), "DM_VK_UCLAMP_MIN", "0",
                new String[]{UiText.tr(this, "Off", "Mati"), "512", "800", "1024"},
                new String[]{"0", "512", "800", "1024"}, dp);

        TextView vkSec = infoLine(cfgCard, UiText.tr(this, "Vulkan & Graphics Toggles", "Pengaturan Toggle Vulkan & Grafis"), dp);
        vkSec.setTextColor(C_TEXT);
        vkSec.setTypeface(Typeface.DEFAULT_BOLD);
        vkSec.setPadding(0, (int) (10 * dp), 0, (int) (2 * dp));

        LinearLayout togPanel = panel(dp);
        addToggleRow(togPanel,
                UiText.tr(this, "Mali FP16 Optimize", "Optimasi Mali FP16"),
                UiText.tr(this, "Faster 16-bit shader math on Mali GPUs", "Percepat hitung shader 16-bit di GPU Mali"),
                "DM_VK_MALI_FP16", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "TBDR Clear LoadOp", "Tile Clear Cepat (TBDR)"),
                UiText.tr(this, "Skip full-frame clear stores on mobile tile GPUs", "Hemat bandwidth tile GPU HP saat clear layar"),
                "DM_VK_CLEAR_LOADOP", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "Descriptor Reuse", "Gunakan Ulang Descriptor"),
                UiText.tr(this, "Cut CPU draw-call overhead each frame", "Kurangi beban CPU tiap frame render"),
                "DM_VK_SET_REUSE", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "Async Shader Compile", "Kompilasi Shader Latar"),
                UiText.tr(this, "Avoid micro-stutters when new shaders appear", "Kurangi patah-patah saat shader baru dimuat"),
                "DM_VK_ASYNC_PIPE", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "Display Timing Sync", "Sinkronisasi Timing Layar"),
                UiText.tr(this, "Smooth frame pacing on Google/Mali/Snapdragon", "Haluskan frame pacing di layar HP"),
                "DM_VK_PRESENT_TIMING", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "Precise SPIR-V Float", "Presisi Float SPIR-V"),
                UiText.tr(this, "Prevent screen/depth glitches on Mali & mobile GPUs", "Cegah glitch layar & kedalaman di GPU Mali/HP"),
                "DM_SPV_PRECISE", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "Show FPS Counter", "Tampilkan Counter FPS"),
                UiText.tr(this, "Display live FPS overlay during gameplay", "Tampilkan angka FPS saat bermain"),
                "DM_SHOW_FPS", false, dp);
        cfgCard.addView(togPanel, btnLp(dp));

        TextView btnResetEnv = actionBtn(UiText.tr(this, "Reset Engine Defaults", "Reset ke Pengaturan Awal"), C_CHIP, C_SUB, dp, false);
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
        boolean hasAoc = new File(GameSettings.gameDir(this), "aoc").isDirectory();
        File ico = new File(GameSettings.gameDir(this), "icon.jpg");
        if (ico.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(ico.getPath());
            if (b != null) {
                iconView.setImageBitmap(b);
                iconView.setVisibility(View.VISIBLE);
                setTaskDescription(new ActivityManager.TaskDescription("Eastward Custom", b));
            }
        }
        readyBadgeTv.setText(ready
                ? (hasAoc
                        ? UiText.tr(this, "Ready to play \u2022 DLC active (010071B00F63A000)", "Siap dimainkan \u2022 DLC aktif (010071B00F63A000)")
                        : UiText.tr(this, "Ready to play (010071B00F63A000)", "Siap dimainkan (010071B00F63A000)"))
                : UiText.tr(this, "Not set up yet \u2014 choose files below", "Belum di-setup \u2014 pilih file di bawah"));
        readyBadgeTv.setTextColor(ready ? Color.rgb(125, 235, 145) : Color.rgb(255, 200, 110));
        btnPlay.setEnabled(ready && !setupRunning);
        btnPlay.setAlpha(ready && !setupRunning ? 1f : 0.45f);

        File pk = new File(GameSettings.keysDir(this), "prod.keys");
        boolean hasKeys = pk.isFile() && pk.length() > 0;
        keysTv.setText(hasKeys
                ? UiText.tr(this, "prod.keys ready", "prod.keys siap")
                : UiText.tr(this, "prod.keys missing", "prod.keys belum ada"));
        keysTv.setTextColor(hasKeys ? C_OK : C_WARN);

        if (basePath != null || baseUri != null) {
            baseFileTv.setText(basePath != null ? new File(basePath).getName() : queryName(baseUri));
            baseFileTv.setTextColor(C_OK);
        } else if (hasExtractedCodeAndRomfs()) {
            baseFileTv.setText(UiText.tr(this,
                    "Extracted & patched (ready for shaders)",
                    "Sudah diekstrak & patch (siap shader)"));
            baseFileTv.setTextColor(C_OK);
        } else {
            baseFileTv.setText(UiText.tr(this, "Not chosen yet", "Belum dipilih"));
            baseFileTv.setTextColor(C_SUB);
        }

        boolean hasUpdate = updatePath != null || updateUri != null;
        if (hasUpdate) {
            updateFileTv.setText(updatePath != null ? new File(updatePath).getName() : queryName(updateUri));
            updateFileTv.setTextColor(C_OK);
        } else {
            updateFileTv.setText(UiText.tr(this, "None selected (optional)", "Belum ada (opsional)"));
            updateFileTv.setTextColor(C_SUB);
        }
        if (btnClearUpdate != null) btnClearUpdate.setVisibility(hasUpdate && !setupRunning ? View.VISIBLE : View.GONE);

        int dlcCount = dlcPaths.size() + dlcUris.size();
        if (dlcCount > 0) {
            StringBuilder dsb = new StringBuilder();
            for (String p : dlcPaths) dsb.append(new File(p).getName()).append(" ");
            for (Uri u : dlcUris) dsb.append(queryName(u)).append(" ");
            dlcFilesTv.setText(dlcCount + "x: " + dsb.toString().trim());
            dlcFilesTv.setTextColor(C_OK);
        } else if (hasAoc) {
            dlcFilesTv.setText(UiText.tr(this, "Active DLC installed (aoc/)", "DLC aktif terpasang (aoc/)"));
            dlcFilesTv.setTextColor(C_OK);
        } else {
            dlcFilesTv.setText(UiText.tr(this, "None selected (optional)", "Belum ada (opsional)"));
            dlcFilesTv.setTextColor(C_SUB);
        }
        if (btnClearDlc != null) btnClearDlc.setVisibility(dlcCount > 0 && !setupRunning ? View.VISIBLE : View.GONE);

        driverTv.setText(UiText.tr(this, "Active: ", "Aktif: ") + GameSettings.driverLabel(this));
        btnSetup.setEnabled(!setupRunning);
        btnSetupAnyway.setVisibility(allowDamagedOffer && !setupRunning ? View.VISIBLE : View.GONE);
    }

    // ---- File Picking ----
    private void pickBaseGame() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(i, REQ_GAME);
    }

    private void pickUpdateFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(i, REQ_UPDATE);
    }

    private void pickDlcFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_DLC);
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

    private void exportLogs() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, LogExporter.suggestedFileName());
        startActivityForResult(i, REQ_LOG_EXPORT);
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
            for (Uri u : uris) {
                String name = queryName(u);
                if (name.toLowerCase(Locale.ROOT).endsWith(".keys")) {
                    importKeyUri(u, name);
                    continue;
                }
                String direct = resolveDirectPath(u);
                if (direct != null && new File(direct).canRead()) {
                    basePath = direct;
                    baseUri = null;
                } else {
                    baseUri = u;
                    basePath = null;
                }
            }
            allowDamagedOffer = false;
            refreshUi();
        } else if (req == REQ_UPDATE) {
            Uri u = uris.get(0);
            String direct = resolveDirectPath(u);
            if (direct != null && new File(direct).canRead()) {
                updatePath = direct;
                updateUri = null;
            } else {
                updateUri = u;
                updatePath = null;
            }
            allowDamagedOffer = false;
            refreshUi();
        } else if (req == REQ_DLC) {
            dlcPaths.clear();
            dlcUris.clear();
            for (Uri u : uris) {
                String direct = resolveDirectPath(u);
                if (direct != null && new File(direct).canRead()) dlcPaths.add(direct);
                else dlcUris.add(u);
            }
            allowDamagedOffer = false;
            refreshUi();
        } else if (req == REQ_KEYS) {
            int ok = 0;
            for (Uri u : uris) {
                if (importKeyUri(u, queryName(u))) ok++;
            }
            if (ok > 0) toast(UiText.tr(this, "Keys imported.", "Keys berhasil diimpor."));
            refreshUi();
        } else if (req == REQ_DRIVER) {
            try {
                String installed = DriverPackages.installZip(this, uris.get(0));
                toast(UiText.tr(this, "Installed driver: ", "Driver terpasang: ") + installed);
            } catch (Exception e) {
                showError(UiText.tr(this, "Driver Install Failed", "Gagal Pasang Driver"), e.getMessage());
            }
            refreshUi();
        } else if (req == REQ_LOG_EXPORT) {
            final Uri outUri = uris.get(0);
            new Thread(() -> {
                try {
                    final LogExporter.ExportResult r = LogExporter.exportZip(this, outUri);
                    runOnUiThread(() -> toast(UiText.tr(this,
                            String.format(Locale.ROOT, "Exported %d log files (%d KB)", r.fileCount, Math.max(1, r.zipBytes >> 10)),
                            String.format(Locale.ROOT, "Berhasil ekspor %d file log (%d KB)", r.fileCount, Math.max(1, r.zipBytes >> 10)))));
                } catch (final Exception e) {
                    runOnUiThread(() -> showError(UiText.tr(this, "Log Export Failed", "Gagal Ekspor Log"), e.getMessage()));
                }
            }).start();
        } else if (req == REQ_SAVE_EXPORT) {
            try {
                int n = SaveManager.exportZip(this, uris.get(0));
                toast(UiText.tr(this, "Exported " + n + " save files.", "Berhasil ekspor " + n + " file save."));
            } catch (Exception e) {
                showError(UiText.tr(this, "Save Export Failed", "Gagal Ekspor Save"), e.getMessage());
            }
        } else if (req == REQ_SAVE_IMPORT) {
            try {
                int n = SaveManager.importZip(this, uris.get(0));
                toast(UiText.tr(this, "Imported " + n + " save files cleanly.", "Berhasil impor " + n + " file save."));
            } catch (Exception e) {
                showError(UiText.tr(this, "Save Import Failed", "Gagal Impor Save"), e.getMessage());
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
        final boolean hasBase = basePath != null || baseUri != null;
        final boolean hasExtra = updatePath != null || updateUri != null || !dlcPaths.isEmpty() || !dlcUris.isEmpty();
        final boolean resumeShadersOnly = !hasBase && !hasExtra && hasExtractedCodeAndRomfs();
        if (!resumeShadersOnly) {
            File pk = new File(GameSettings.keysDir(this), "prod.keys");
            if (!pk.isFile() || pk.length() == 0) {
                toast(UiText.tr(this, "Choose your prod.keys first.", "Pilih file prod.keys terlebih dahulu."));
                return;
            }
            if (!hasBase) {
                toast(UiText.tr(this,
                        hasExtra
                                ? "Also select your Base Game .nsp/.xci so Update/DLC can be merged."
                                : "Choose your Base Game .nsp / .xci file first.",
                        hasExtra
                                ? "Pilih juga file Base Game .nsp/.xci agar Update/DLC bisa digabung."
                                : "Pilih file Base Game .nsp / .xci terlebih dahulu."));
                return;
            }
        }

        setupRunning = true;
        allowDamagedOffer = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusTv.setText(UiText.tr(this, "Preparing Python & extraction tools...", "Menyiapkan Python & alat ekstraksi..."));
        refreshUi();

        final String snapBasePath = basePath;
        final Uri snapBaseUri = baseUri;
        final String snapUpdatePath = updatePath;
        final Uri snapUpdateUri = updateUri;
        final List<String> snapDlcPaths = new ArrayList<>(dlcPaths);
        final List<Uri> snapDlcUris = new ArrayList<>(dlcUris);

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

                File impDir = new File(work, "import");
                boolean needCopy = snapBaseUri != null || snapUpdateUri != null || !snapDlcUris.isEmpty();
                if (needCopy) {
                    deleteTree(impDir);
                    impDir.mkdirs();
                }

                String baseInput = snapBasePath;
                if (baseInput == null && snapBaseUri != null) {
                    String nm = queryName(snapBaseUri);
                    final String copyName = "base_" + (nm.isEmpty() ? "game.nsp" : nm);
                    File dst = new File(impDir, copyName);
                    runOnUiThread(() -> statusTv.setText(UiText.tr(this, "Copying ", "Menyalin ") + nm + "..."));
                    try (InputStream is = getContentResolver().openInputStream(snapBaseUri);
                         FileOutputStream os = new FileOutputStream(dst)) {
                        if (is == null) throw new IOException("Cannot open " + nm);
                        copyStream(is, os);
                    }
                    baseInput = dst.getPath();
                }

                String updateInput = snapUpdatePath;
                if (updateInput == null && snapUpdateUri != null) {
                    String nm = queryName(snapUpdateUri);
                    final String copyName = "update_" + (nm.isEmpty() ? "patch.nsp" : nm);
                    File dst = new File(impDir, copyName);
                    runOnUiThread(() -> statusTv.setText(UiText.tr(this, "Copying ", "Menyalin ") + nm + "..."));
                    try (InputStream is = getContentResolver().openInputStream(snapUpdateUri);
                         FileOutputStream os = new FileOutputStream(dst)) {
                        if (is == null) throw new IOException("Cannot open " + nm);
                        copyStream(is, os);
                    }
                    updateInput = dst.getPath();
                }

                List<String> dlcInputs = new ArrayList<>(snapDlcPaths);
                for (int idx = 0; idx < snapDlcUris.size(); idx++) {
                    Uri u = snapDlcUris.get(idx);
                    String nm = queryName(u);
                    final String copyName = "dlc_" + idx + "_" + (nm.isEmpty() ? "addon.nsp" : nm);
                    File dst = new File(impDir, copyName);
                    runOnUiThread(() -> statusTv.setText(UiText.tr(this, "Copying ", "Menyalin ") + nm + "..."));
                    try (InputStream is = getContentResolver().openInputStream(u);
                         FileOutputStream os = new FileOutputStream(dst)) {
                        if (is == null) throw new IOException("Cannot open " + nm);
                        copyStream(is, os);
                    }
                    dlcInputs.add(dst.getPath());
                }

                String libDir = getApplicationInfo().nativeLibraryDir;
                List<String> cmd = new ArrayList<>();
                cmd.add(libDir + "/libpython_exe.so");
                cmd.add(new File(dm, "android/tools/setup_game.py").getPath());
                if (resumeShadersOnly) {
                    cmd.add("--shaders-only");
                } else {
                    cmd.add("--keys"); cmd.add(GameSettings.keysDir(this).getPath());
                    cmd.add("--base"); cmd.add(baseInput);
                    if (updateInput != null) {
                        cmd.add("--update"); cmd.add(updateInput);
                    }
                    for (String dlcIn : dlcInputs) {
                        cmd.add("--dlc"); cmd.add(dlcIn);
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
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                     FileWriter logW = new FileWriter(new File(work, "setup.log"), false)) {
                    String ln;
                    while ((ln = r.readLine()) != null) {
                        logW.write(ln);
                        logW.write('\n');
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
                    statusTv.setText(UiText.tr(this, "Eastward setup complete! Ready to play.", "Setup Eastward selesai! Siap dimainkan."));
                    refreshUi();
                    toast(UiText.tr(this, "Setup complete!", "Setup selesai!"));
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

    private LinearLayout panel(float dp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(C_PANEL);
        d.setCornerRadius(14 * dp);
        d.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        l.setBackground(d);
        return l;
    }

    private View divider(float dp) {
        View v = new View(this);
        v.setBackgroundColor(C_CARD_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Math.max(1, dp));
        lp.leftMargin = (int) (12 * dp);
        lp.rightMargin = (int) (12 * dp);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView addFileSlotRow(LinearLayout parent, String title, TextView statusOut,
                                    String btnLabel, View.OnClickListener onPick,
                                    View.OnClickListener onClear, float dp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int hpad = (int) (12 * dp), vpad = (int) (9 * dp);
        row.setPadding(hpad, vpad, hpad, vpad);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView ttl = new TextView(this);
        ttl.setText(title);
        ttl.setTextColor(C_TEXT);
        ttl.setTextSize(13);
        ttl.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(ttl);

        statusOut.setTextSize(12);
        statusOut.setTextColor(C_SUB);
        statusOut.setSingleLine(true);
        statusOut.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        statusOut.setPadding(0, (int) (2 * dp), 0, 0);
        left.addView(statusOut);

        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.rightMargin = (int) (8 * dp);
        row.addView(left, llp);

        TextView clearBtn = null;
        if (onClear != null) {
            clearBtn = new TextView(this);
            clearBtn.setText("\u2715");
            clearBtn.setTextColor(C_SUB);
            clearBtn.setTextSize(12);
            clearBtn.setTypeface(Typeface.DEFAULT_BOLD);
            clearBtn.setGravity(Gravity.CENTER);
            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(C_CHIP);
            cbg.setCornerRadius(10 * dp);
            cbg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
            clearBtn.setBackground(cbg);
            clearBtn.setPadding((int) (10 * dp), (int) (7 * dp), (int) (10 * dp), (int) (7 * dp));
            clearBtn.setOnClickListener(onClear);
            clearBtn.setVisibility(View.GONE);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = (int) (6 * dp);
            row.addView(clearBtn, clp);
        }

        TextView pickBtn = actionBtn(btnLabel, C_CHIP, C_TEXT, dp, false);
        pickBtn.setTextSize(12);
        pickBtn.setPadding((int) (12 * dp), (int) (7 * dp), (int) (12 * dp), (int) (7 * dp));
        pickBtn.setOnClickListener(onPick);
        row.addView(pickBtn);

        parent.addView(row);
        return clearBtn;
    }

    private void addChoiceRow(LinearLayout parent, String label, String key, String def,
                              String[] labels, String[] vals, float dp) {
        TextView lbl = infoLine(parent, label, dp);
        lbl.setTextColor(C_TEXT);
        lbl.setTextSize(12);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setPadding(0, (int) (10 * dp), 0, (int) (4 * dp));

        LinearLayout segBox = new LinearLayout(this);
        segBox.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable segBg = new GradientDrawable();
        segBg.setColor(C_PANEL);
        segBg.setCornerRadius(13 * dp);
        segBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        segBox.setBackground(segBg);
        int sp = (int) (3 * dp);
        segBox.setPadding(sp, sp, sp, sp);

        String cur = GameSettings.get(this, key, def);
        final TextView[] btns = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean sel = cur.equals(vals[i]);
            TextView b = new TextView(this);
            b.setText(labels[i]);
            b.setTextSize(12);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setGravity(Gravity.CENTER);
            b.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
            styleSegment(b, sel, dp);
            b.setOnClickListener(v -> {
                if ("DM_VK_PIN".equals(key)) {
                    GameSettings.setCpuPin(this, vals[idx]);
                } else {
                    GameSettings.set(this, key, vals[idx]);
                }
                for (int k = 0; k < btns.length; k++) {
                    styleSegment(btns[k], k == idx, dp);
                }
            });
            btns[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < labels.length - 1) lp.rightMargin = (int) (3 * dp);
            segBox.addView(b, lp);
        }
        parent.addView(segBox);
    }

    private void styleSegment(TextView b, boolean active, float dp) {
        if (active) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(C_ACCENT);
            d.setCornerRadius(10 * dp);
            b.setBackground(d);
            b.setTextColor(C_INK);
        } else {
            b.setBackground(null);
            b.setTextColor(C_SUB);
        }
    }

    private void addToggleRow(LinearLayout parent, String label, String sub, String key, boolean def, float dp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int hpad = (int) (12 * dp), vpad = (int) (10 * dp);
        row.setPadding(hpad, vpad, hpad, vpad);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(C_TEXT);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        txt.addView(tv);
        if (sub != null && !sub.isEmpty()) {
            TextView stv = new TextView(this);
            stv.setText(sub);
            stv.setTextColor(C_SUB);
            stv.setTextSize(11);
            stv.setPadding(0, (int) (1 * dp), 0, 0);
            txt.addView(stv);
        }
        LinearLayout.LayoutParams txtLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txtLp.rightMargin = (int) (12 * dp);
        row.addView(txt, txtLp);

        final boolean[] state = {"1".equals(GameSettings.get(this, key, def ? "1" : "0"))};
        int w = (int) (46 * dp), h = (int) (25 * dp), pad = (int) (3 * dp), thumbSz = h - pad * 2;
        android.widget.FrameLayout track = new android.widget.FrameLayout(this);
        track.setLayoutParams(new LinearLayout.LayoutParams(w, h));

        View thumb = new View(this);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.OVAL);
        thumb.setBackground(thumbBg);
        android.widget.FrameLayout.LayoutParams tlp = new android.widget.FrameLayout.LayoutParams(
                thumbSz, thumbSz, Gravity.CENTER_VERTICAL | Gravity.START);
        track.addView(thumb, tlp);

        Runnable sync = () -> {
            GradientDrawable trBg = new GradientDrawable();
            trBg.setColor(state[0] ? C_ACCENT : 0xFF2B303B);
            trBg.setCornerRadius(h * 0.5f);
            trBg.setStroke((int) Math.max(1, dp), state[0] ? C_ACCENT : C_CARD_LINE);
            track.setBackground(trBg);
            thumbBg.setColor(state[0] ? C_INK : 0xFFE2E5EC);
            tlp.leftMargin = state[0] ? (w - pad - thumbSz) : pad;
            thumb.setLayoutParams(tlp);
        };
        sync.run();

        View.OnClickListener toggle = v -> {
            state[0] = !state[0];
            sync.run();
            GameSettings.set(this, key, state[0] ? "1" : "0");
        };
        row.setOnClickListener(toggle);
        track.setOnClickListener(toggle);
        row.addView(track);
        parent.addView(row);
    }
}
