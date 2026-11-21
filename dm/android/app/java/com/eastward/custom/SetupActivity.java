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
            REQ_UPDATE = 106, REQ_DLC = 107, REQ_LOG_EXPORT = 108, REQ_MOD = 109;

    private static volatile boolean setupRunning;
    private static String basePath;
    private static Uri baseUri;
    private static String updatePath;
    private static Uri updateUri;
    private static final List<String> dlcPaths = new ArrayList<>();
    private static final List<Uri> dlcUris = new ArrayList<>();
    private static boolean allowDamagedOffer;

    private static final int C_BG        = 0xFF0A0C10;
    private static final int C_CARD      = 0xFF151822;
    private static final int C_PANEL     = 0xFF0F1219;
    private static final int C_CARD_LINE = 0xFF272D3B;
    private static final int C_CHIP      = 0xFF202634;
    private static final int C_TEXT      = 0xFFF4F6FA;
    private static final int C_SUB       = 0xFF97A1B3;
    private static final int C_ACCENT    = 0xFFF5C428;
    private static final int C_INK       = 0xFF16130B;
    private static final int C_OK        = 0xFF6BE28A;
    private static final int C_WARN      = 0xFFFFB74D;
    private static final String GITHUB_URL = "https://github.com/Itsneslab/Eastward-Custom-Android";

    private TextView statusTv, baseFileTv, updateFileTv, dlcFilesTv, modStatusTv, keysTv, deviceDetectTv, driverTv, driverTipTv, readyBadgeTv;
    private TextView btnClearUpdate, btnClearDlc, btnClearMod, btnPickDrv, btnResetDrv;
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
        GameSettings.syncModOverlay(this);
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

        // Modern obsidian-gold Hero Header Card + Status Pill + Gradient Play CTA
        LinearLayout header = card(dp);
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1E2331, 0xFF131620});
        heroBg.setCornerRadius(20 * dp);
        heroBg.setStroke((int) Math.max(1, dp), 0xFF31384B);
        header.setBackground(heroBg);
        header.setElevation(8 * dp);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        android.widget.FrameLayout iconFrame = new android.widget.FrameLayout(this);
        GradientDrawable icoBg = new GradientDrawable();
        icoBg.setColor(0xFF1D1B14);
        icoBg.setCornerRadius(16 * dp);
        icoBg.setStroke((int) Math.max(1, 1.5f * dp), 0x66F5C428);
        iconFrame.setBackground(icoBg);
        int icoPad = (int) (5 * dp);
        iconFrame.setPadding(icoPad, icoPad, icoPad, icoPad);

        iconView = new ImageView(this);
        int icoSz = (int) (52 * dp);
        try (InputStream es = getAssets().open("icons/emblem.png")) {
            Bitmap em = BitmapFactory.decodeStream(es);
            if (em != null) iconView.setImageBitmap(em);
            else iconFrame.setVisibility(View.GONE);
        } catch (Throwable t) {
            iconFrame.setVisibility(View.GONE);
        }
        iconFrame.addView(iconView, new android.widget.FrameLayout.LayoutParams(icoSz, icoSz));
        LinearLayout.LayoutParams icoLp = new LinearLayout.LayoutParams(-2, -2);
        icoLp.rightMargin = (int) (14 * dp);
        header.addView(iconFrame, icoLp);

        LinearLayout hTxt = new LinearLayout(this);
        hTxt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Eastward Custom");
        title.setTextColor(C_TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title);

        TextView verTag = new TextView(this);
        verTag.setText("v" + GameSettings.appVersionTag(this));
        verTag.setTextColor(C_ACCENT);
        verTag.setTextSize(10);
        verTag.setTypeface(Typeface.DEFAULT_BOLD);
        verTag.setPadding((int) (9 * dp), (int) (3 * dp), (int) (9 * dp), (int) (3 * dp));
        GradientDrawable vbg = new GradientDrawable();
        vbg.setColor(0x24F5C428);
        vbg.setCornerRadius(10 * dp);
        vbg.setStroke((int) Math.max(1, dp), 0x88F5C428);
        verTag.setBackground(vbg);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = (int) (10 * dp);
        titleRow.addView(verTag, vlp);
        hTxt.addView(titleRow);

        readyBadgeTv = new TextView(this);
        readyBadgeTv.setTextSize(12);
        readyBadgeTv.setTypeface(Typeface.DEFAULT_BOLD);
        readyBadgeTv.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        GradientDrawable stBg = new GradientDrawable();
        stBg.setColor(0xFF0E131B);
        stBg.setCornerRadius(10 * dp);
        stBg.setStroke((int) Math.max(1, dp), 0xFF263042);
        readyBadgeTv.setBackground(stBg);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-2, -2);
        stLp.topMargin = (int) (5 * dp);
        stLp.bottomMargin = (int) (6 * dp);
        hTxt.addView(readyBadgeTv, stLp);

        // Clickable GitHub Profile / Repo Link Pill
        TextView ghPill = new TextView(this);
        ghPill.setText("GitHub  \u2022  Itsneslab/Eastward-Custom-Android  \u2197");
        ghPill.setTextColor(C_SUB);
        ghPill.setTextSize(11);
        ghPill.setTypeface(Typeface.DEFAULT_BOLD);
        ghPill.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        GradientDrawable ghBg = new GradientDrawable();
        ghBg.setColor(C_CHIP);
        ghBg.setCornerRadius(10 * dp);
        ghBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
        ghPill.setBackground(ghBg);
        ghPill.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
            } catch (Exception e) {
                toast("GitHub: " + GITHUB_URL);
            }
        });
        hTxt.addView(ghPill, new LinearLayout.LayoutParams(-2, -2));

        header.addView(hTxt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Compact vertical utility stack: Export Log (.zip) directly above ID | ENG
        LinearLayout rightHdr = new LinearLayout(this);
        rightHdr.setOrientation(LinearLayout.VERTICAL);
        rightHdr.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView btnExportLog = new TextView(this);
        btnExportLog.setText(UiText.tr(this, "Export Log", "Ekspor Log"));
        btnExportLog.setTextColor(C_TEXT);
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
        hdrStackLp.rightMargin = (int) (12 * dp);
        header.addView(rightHdr, hdrStackLp);

        btnPlay = actionBtn(UiText.tr(this, "\u25B6  Play Eastward", "\u25B6  Mainkan Eastward"), C_ACCENT, C_INK, dp, true);
        GradientDrawable playBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFFDA54, C_ACCENT, 0xFFE0A610});
        playBg.setCornerRadius(16 * dp);
        btnPlay.setBackground(playBg);
        btnPlay.setElevation(8 * dp);
        btnPlay.setTextSize(14);
        btnPlay.setPadding((int) (22 * dp), (int) (16 * dp), (int) (22 * dp), (int) (16 * dp));
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

        // Left Card 1: 2 Main Required Slots (Base Game & Keys) + 3 Optional Slots (Update, DLC, Mod)
        LinearLayout setupCard = card(dp);
        cardHeaderBadge(setupCard,
                UiText.tr(this, "MAIN GAME & KEYS", "FILE UTAMA GAME & KEYS"),
                UiText.tr(this, "REQUIRED", "WAJIB"),
                0x2AF5C428, C_ACCENT, dp);

        LinearLayout filesPanel = panel(dp);
        GradientDrawable mainPanelBg = new GradientDrawable();
        mainPanelBg.setColor(0xFF11151F);
        mainPanelBg.setCornerRadius(14 * dp);
        mainPanelBg.setStroke((int) Math.max(1.5f, 1.5f * dp), 0x88F5C428);
        filesPanel.setBackground(mainPanelBg);

        baseFileTv = new TextView(this);
        addFileSlotRow(filesPanel,
                UiText.tr(this, "Base Game (.nsp / .xci)", "Base Game (.nsp / .xci)"),
                baseFileTv,
                UiText.tr(this, "Choose", "Pilih"),
                v -> pickBaseGame(), null, dp);
        filesPanel.addView(divider(dp));

        keysTv = new TextView(this);
        addFileSlotRow(filesPanel,
                UiText.tr(this, "Switch Keys (prod.keys)", "Switch Keys (prod.keys)"),
                keysTv,
                UiText.tr(this, "Choose", "Pilih"),
                v -> pickKeys(), null, dp);
        setupCard.addView(filesPanel, btnLp(dp));

        LinearLayout optHdrWrap = new LinearLayout(this);
        optHdrWrap.setOrientation(LinearLayout.VERTICAL);
        optHdrWrap.setPadding(0, (int) (14 * dp), 0, (int) (2 * dp));
        cardHeaderBadge(optHdrWrap,
                UiText.tr(this, "ADD-ONS & MOD", "TAMBAHAN & MOD"),
                UiText.tr(this, "OPTIONAL", "OPSIONAL"),
                0xFF202634, C_SUB, dp);
        setupCard.addView(optHdrWrap);

        LinearLayout optPanel = panel(dp);
        updateFileTv = new TextView(this);
        btnClearUpdate = addFileSlotRow(optPanel,
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
        optPanel.addView(divider(dp));

        dlcFilesTv = new TextView(this);
        btnClearDlc = addFileSlotRow(optPanel,
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
        optPanel.addView(divider(dp));

        modStatusTv = new TextView(this);
        btnClearMod = addFileSlotRow(optPanel,
                UiText.tr(this, "Mod / Translation (Optional)", "Mod / Bahasa Indonesia (Opsional)"),
                modStatusTv,
                UiText.tr(this, "Install", "Isi"),
                v -> pickModFile(),
                v -> {
                    deleteTree(new File(GameSettings.gameDir(this), "mods/romfs"));
                    GameSettings.syncModOverlay(this);
                    toast(UiText.tr(this, "Mod removed.", "Mod dihapus."));
                    refreshUi();
                }, dp);
        setupCard.addView(optPanel, btnLp(dp));

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
        btnSetup = actionBtn(UiText.tr(this, "Set up Eastward", "Setup / Ekstrak Eastward"), C_ACCENT, C_INK, dp, true);
        GradientDrawable setupBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFFD84D, C_ACCENT, 0xFFE3A812});
        setupBg.setCornerRadius(14 * dp);
        btnSetup.setBackground(setupBg);
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

        // Right Card 1: Vulkan Driver (Turnip / System)
        LinearLayout drvCard = card(dp);
        cardTitle(drvCard, UiText.tr(this, "VULKAN GPU DRIVER", "DRIVER GPU VULKAN"), dp);
        deviceDetectTv = new TextView(this);
        deviceDetectTv.setTextSize(11);
        deviceDetectTv.setTypeface(Typeface.DEFAULT_BOLD);
        deviceDetectTv.setPadding((int) (10 * dp), (int) (5 * dp), (int) (10 * dp), (int) (5 * dp));
        LinearLayout.LayoutParams detLp = new LinearLayout.LayoutParams(-1, -2);
        detLp.topMargin = (int) (4 * dp);
        detLp.bottomMargin = (int) (6 * dp);
        drvCard.addView(deviceDetectTv, detLp);

        driverTv = infoLine(drvCard, "", dp);
        driverTv.setTextColor(C_TEXT);
        driverTipTv = infoLine(drvCard, "", dp);
        driverTipTv.setTextSize(11);
        driverTipTv.setTextColor(C_SUB);
        driverTipTv.setPadding((int) (10 * dp), (int) (7 * dp), (int) (10 * dp), (int) (7 * dp));
        LinearLayout drvRow = new LinearLayout(this);
        drvRow.setOrientation(LinearLayout.HORIZONTAL);

        btnPickDrv = actionBtn(UiText.tr(this, "Install Driver (.zip)", "Pasang Driver (.zip)"), C_CHIP, C_TEXT, dp, false);
        btnPickDrv.setOnClickListener(v -> pickDriver());
        LinearLayout.LayoutParams d1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        d1.rightMargin = (int) (8 * dp);
        drvRow.addView(btnPickDrv, d1);

        btnResetDrv = actionBtn(UiText.tr(this, "Use Phone Driver", "Pakai Driver Bawaan"), C_CHIP, C_SUB, dp, false);
        btnResetDrv.setOnClickListener(v -> {
            DriverPackages.clearDriver(this);
            refreshUi();
            toast(UiText.tr(this, "Switched back to system Vulkan driver.", "Kembali memakai driver Vulkan bawaan HP."));
        });
        LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        drvRow.addView(btnResetDrv, d2);
        drvCard.addView(drvRow, btnLp(dp));
        rightCol.addView(drvCard, cardLp(dp));

        // Right Card 2: Display & Performance Settings
        LinearLayout cfgCard = card(dp);
        cardTitle(cfgCard, UiText.tr(this, "DISPLAY & PERFORMANCE", "TAMPILAN & PERFORMA"), dp);

        boolean detAny = GameSettings.isMaliGpu(this) || GameSettings.isAdrenoGpu(this);
        TextView autoInfo = new TextView(this);
        autoInfo.setText((detAny
                ? UiText.tr(this, "✓ DETECTED: ", "✓ HP TERDETEKSI: ")
                : UiText.tr(this, "• AUTO PROFILE: ", "• PROFIL UMUM: "))
                + GameSettings.detectedChipsetSummary(this));
        autoInfo.setTextColor(detAny ? C_OK : C_WARN);
        autoInfo.setTextSize(11);
        autoInfo.setTypeface(Typeface.DEFAULT_BOLD);
        autoInfo.setPadding((int) (10 * dp), (int) (6 * dp), (int) (10 * dp), (int) (6 * dp));
        GradientDrawable autoBg = new GradientDrawable();
        autoBg.setColor(detAny ? 0xFF111A16 : 0xFF1B160F);
        autoBg.setCornerRadius(10 * dp);
        autoBg.setStroke((int) Math.max(1, dp), detAny ? 0x446BE28A : 0x44FFB74D);
        autoInfo.setBackground(autoBg);
        cfgCard.addView(autoInfo, new LinearLayout.LayoutParams(-1, -2));

        addChoiceRow(cfgCard, UiText.tr(this, "Resolution", "Resolusi"), "DM_OPMODE", "0",
                new String[]{UiText.tr(this, "720p Handheld", "720p Ringan"),
                        UiText.tr(this, "1080p Docked", "1080p Tajam")},
                new String[]{"0", "1"}, dp);
        addChoiceRow(cfgCard, UiText.tr(this, "Pixel Quality (Render Scale)", "Kualitas Piksel (Skala Render)"), "DM_VK_FB_SCALE", "100",
                new String[]{"100% Asli", "90% Tajam", "80% Lancar", "70% Ngebut"},
                new String[]{"100", "90", "80", "70"}, dp);
        addChoiceRow(cfgCard, UiText.tr(this, "Screen Size (Video Mode)", "Ukuran Layar (Rasio)"), "DM_SCREEN_MODE", "0",
                new String[]{UiText.tr(this, "16:9 Fit", "16:9 Biasa"),
                        UiText.tr(this, "Wide Full", "Wide Full")},
                new String[]{"0", "1"}, dp);
        addChoiceRow(cfgCard, UiText.tr(this, "Game Language", "Bahasa Game"), "DM_LANG", "en-US",
                new String[]{"English", "Japanese", "Chinese"}, new String[]{"en-US", "ja", "zh-Hans"}, dp);

        LinearLayout togPanel = panel(dp);
        addToggleRow(togPanel,
                UiText.tr(this, "Fast Post-FX Shaders (+FPS)", "Efek Post-FX Cepat (+FPS)"),
                UiText.tr(this, "100% sharp visuals; cuts bloom/grain/sharpen GPU fetches in half", "Tetap 100% tajam tanpa burik; pangkas beban bloom, grain & filter layar"),
                "DM_VK_FAST_FX", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "FP16 Shader Optimization", "Optimasi Shader FP16"),
                UiText.tr(this, "Auto-on for Mali; off by default on Adreno to avoid shader glitches", "Otomatis aktif di Mali; mati default di Adreno agar bebas glitch visual"),
                "DM_VK_MALI_FP16", !GameSettings.isAdrenoGpu(this), dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "Unlock 30fps Swapchain", "Bebas Lock 30fps (Mailbox)"),
                UiText.tr(this, "Use Mailbox/Relaxed VSync to avoid 30fps lock; turn off if flickering", "Pakai Mailbox/Relaxed agar tidak terkunci 30fps saat jalan; matikan jika berkedip"),
                "DM_VK_MAILBOX", true, dp);
        togPanel.addView(divider(dp));
        addToggleRow(togPanel,
                UiText.tr(this, "Show FPS Counter", "Tampilkan Counter FPS"),
                UiText.tr(this, "Display live FPS overlay during gameplay", "Tampilkan angka FPS saat bermain"),
                "DM_SHOW_FPS", false, dp);
        cfgCard.addView(togPanel, btnLp(dp));

        TextView btnResetEnv = actionBtn(UiText.tr(this, "Reset Defaults (Auto-Chipset)", "Reset Default (Auto-Chipset)"), C_CHIP, C_SUB, dp, false);
        btnResetEnv.setOnClickListener(v -> {
            GameSettings.resetDefaults(this);
            toast(UiText.tr(this, "Restored crisp 100% + auto chipset settings.", "Kembali ke pengaturan 100% tajam + auto chipset."));
            buildUi();
            refreshUi();
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
        String updSummary = GameSettings.installedUpdateSummary(this);
        String gameVer = GameSettings.installedGameVersion(this);
        boolean hasMod = GameSettings.hasModFiles(this);
        File ico = new File(GameSettings.gameDir(this), "icon.jpg");
        if (ico.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(ico.getPath());
            if (b != null) {
                iconView.setImageBitmap(b);
                iconView.setVisibility(View.VISIBLE);
                setTaskDescription(new ActivityManager.TaskDescription("Eastward Custom", b));
            }
        }
        if (ready) {
            StringBuilder rb = new StringBuilder("\u25CF  ").append(UiText.tr(this, "Ready to play", "Siap dimainkan"));
            if (!gameVer.isEmpty()) rb.append(" v").append(gameVer);
            if (hasAoc) rb.append(" \u2022 DLC");
            if (hasMod) rb.append(" \u2022 Mod");
            readyBadgeTv.setText(rb.toString());
        } else {
            readyBadgeTv.setText("\u25CF  " + UiText.tr(this, "Not set up yet \u2014 pick Base Game & prod.keys", "Belum di-setup \u2014 pilih Base Game & prod.keys"));
        }
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
        } else if (ready) {
            baseFileTv.setText(UiText.tr(this, "Installed & Ready", "Terpasang & Siap Main")
                    + (!gameVer.isEmpty() ? (" (v" + gameVer + ")") : ""));
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
        } else if (updSummary != null) {
            updateFileTv.setText(UiText.tr(this, "Installed: ", "Terpasang: ") + updSummary);
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

        if (modStatusTv != null) {
            if (hasMod) {
                int modCnt = GameSettings.countModFiles(this);
                modStatusTv.setText(UiText.tr(this,
                        "Mod active (" + modCnt + " file" + (modCnt == 1 ? "" : "s") + ")",
                        "Mod aktif terpasang (" + modCnt + " file)"));
                modStatusTv.setTextColor(C_OK);
            } else {
                modStatusTv.setText(UiText.tr(this, "Pick locale.g or mod .zip (optional)", "Pilih locale.g atau .zip mod (opsional)"));
                modStatusTv.setTextColor(C_SUB);
            }
        }
        if (btnClearMod != null) btnClearMod.setVisibility(hasMod && !setupRunning ? View.VISIBLE : View.GONE);

        float dp = getResources().getDisplayMetrics().density;
        boolean adreno = GameSettings.isAdrenoGpu(this);
        boolean mali = GameSettings.isMaliGpu(this);
        boolean customDrv = GameSettings.isCustomDriverSelected(this);
        String phone = GameSettings.phoneModelLabel();
        String gpuName = GameSettings.gpuDisplayName(this);
        String gpuShow = (gpuName != null && !gpuName.isEmpty()) ? gpuName
                : (adreno ? "Adreno GPU" : (mali ? "Mali GPU" : UiText.tr(this, "Auto Vulkan GPU", "GPU Vulkan Auto")));

        if (deviceDetectTv != null) {
            int bgCol = (adreno || mali) ? (adreno && !customDrv ? 0xFF1D180F : 0xFF111B16) : 0xFF181C24;
            int strokeCol = (adreno || mali) ? (adreno && !customDrv ? 0x66F5C428 : 0x556BE28A) : 0x4497A1B3;
            int txtCol = (adreno || mali) ? (adreno && !customDrv ? C_ACCENT : C_OK) : C_WARN;
            GradientDrawable detBg = new GradientDrawable();
            detBg.setColor(bgCol);
            detBg.setCornerRadius(9 * dp);
            detBg.setStroke((int) Math.max(1, dp), strokeCol);
            deviceDetectTv.setBackground(detBg);
            deviceDetectTv.setTextColor(txtCol);
            if (adreno) {
                deviceDetectTv.setText(UiText.tr(this,
                        "✓ DETECTED: " + phone + " • " + gpuShow + " (Snapdragon / Adreno)",
                        "✓ TERDETEKSI DI HP INI: " + phone + " • " + gpuShow + " (Snapdragon / Adreno)"));
            } else if (mali) {
                deviceDetectTv.setText(UiText.tr(this,
                        "✓ DETECTED: " + phone + " • " + gpuShow + " (MediaTek / Mali)",
                        "✓ TERDETEKSI DI HP INI: " + phone + " • " + gpuShow + " (MediaTek / Mali)"));
            } else {
                deviceDetectTv.setText(UiText.tr(this,
                        "• UNIDENTIFIED GPU: " + phone + " • " + gpuShow,
                        "• GPU BELUM TERIDENTIFIKASI: " + phone + " • " + gpuShow));
            }
        }

        driverTv.setText(UiText.tr(this, "Active: ", "Aktif: ") + GameSettings.driverLabel(this));
        if (driverTipTv != null) {
            GradientDrawable tipBg = new GradientDrawable();
            tipBg.setCornerRadius(10 * dp);
            if (adreno) {
                if (customDrv) {
                    tipBg.setColor(0xFF101914);
                    tipBg.setStroke((int) Math.max(1, dp), 0x446BE28A);
                    driverTipTv.setTextColor(C_OK);
                    driverTipTv.setText(UiText.tr(this,
                            "★ ADVICE FOR YOUR PHONE (Adreno • Turnip Active): Custom driver active! If glitches appear, try another Turnip version or switch back to Phone Driver.",
                            "★ SARAN KHUSUS HP INI (Adreno • Driver Kustom Aktif): Sudah pakai driver kustom! Jika muncul glitch, coba versi Turnip lain atau kembali ke Driver Bawaan."));
                } else {
                    tipBg.setColor(0xFF1B160D);
                    tipBg.setStroke((int) Math.max(1, dp), 0x55F5C428);
                    driverTipTv.setTextColor(C_WARN);
                    driverTipTv.setText(UiText.tr(this,
                            "★ ADVICE FOR YOUR PHONE (Snapdragon / Adreno): Recommended to install Mesa Turnip (.zip, e.g. 3.0.3 / v24+) for higher FPS & fewer stock-driver glitches.",
                            "★ SARAN KHUSUS HP INI (Snapdragon / Adreno): Disarankan pasang Mesa Turnip (.zip, mis. 3.0.3 / v24+) agar FPS lebih tinggi & lebih stabil. (Driver bawaan HP tetap aman)."));
                }
            } else if (mali) {
                if (customDrv) {
                    if (GameSettings.isTurnipSelected(this)) {
                        tipBg.setColor(0xFF1F1412);
                        tipBg.setStroke((int) Math.max(1, dp), 0x66FFB74D);
                        driverTipTv.setTextColor(C_WARN);
                        driverTipTv.setText(UiText.tr(this,
                                "⚠ DRIVER MISMATCH (MediaTek / Mali): Turnip is for Snapdragon/Adreno. Switch to Phone Driver or a Mali PanVK/Panfrost driver (.zip).",
                                "⚠ DRIVER KURANG COCOK (MediaTek / Mali): Turnip khusus Snapdragon/Adreno! Pakai Driver Bawaan HP atau driver Mali PanVK/Panfrost (.zip)."));
                    } else {
                        tipBg.setColor(0xFF101914);
                        tipBg.setStroke((int) Math.max(1, dp), 0x446BE28A);
                        driverTipTv.setTextColor(C_OK);
                        driverTipTv.setText(UiText.tr(this,
                                "★ ADVICE FOR YOUR PHONE (MediaTek / Mali • Custom Driver Active): PanVK / Panfrost Mali drivers (e.g. FristOneRR v3.70+) can help unlock FPS. Switch back to Phone Driver if glitches occur.",
                                "★ SARAN KHUSUS HP INI (MediaTek / Mali • Driver Kustom Aktif): Driver Mali PanVK/Panfrost (mis. FristOneRR v3.70+) cocok untuk Mali. Kembali ke Driver Bawaan jika ada glitch."));
                    }
                } else {
                    tipBg.setColor(0xFF101914);
                    tipBg.setStroke((int) Math.max(1, dp), 0x446BE28A);
                    driverTipTv.setTextColor(C_OK);
                    driverTipTv.setText(UiText.tr(this,
                            "★ ADVICE FOR YOUR PHONE (MediaTek / Mali): Phone Driver works out of the box. If still locked at 30fps on your device, try a Mali PanVK/Panfrost (.zip) like FristOneRR v3.70+ (do NOT use Turnip).",
                            "★ SARAN KHUSUS HP INI (MediaTek / Mali): Driver Bawaan HP sudah aman dipakai. Jika di HP Anda masih ter-lock 30fps, bisa coba PanVK/Panfrost Mali (.zip) seperti FristOneRR v3.70+ (JANGAN pakai Turnip)."));
                }
            } else {
                tipBg.setColor(0xFF151922);
                tipBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
                driverTipTv.setTextColor(C_SUB);
                driverTipTv.setText(UiText.tr(this,
                        "• GENERAL TIP: Start with Phone System Driver. Once you launch the game once, this screen will lock onto your exact GPU recommendation.",
                        "• SARAN UMUM: Pakai Driver Bawaan HP dulu. Setelah game dijalankan 1x, penanda HP & saran driver otomatis menyesuaikan GPU Anda."));
            }
            driverTipTv.setBackground(tipBg);
        }
        if (btnPickDrv != null && btnResetDrv != null) {
            if (adreno && !customDrv) {
                btnPickDrv.setText(UiText.tr(this, "Install Turnip (.zip) ★", "Pasang Turnip (.zip) ★"));
                btnPickDrv.setTextColor(C_ACCENT);
                btnResetDrv.setTextColor(C_SUB);
            } else if (mali && !customDrv) {
                btnPickDrv.setText(UiText.tr(this, "Install Driver (.zip)", "Pasang Driver (.zip)"));
                btnPickDrv.setTextColor(C_SUB);
                btnResetDrv.setTextColor(C_OK);
            } else {
                btnPickDrv.setText(UiText.tr(this, "Install Driver (.zip)", "Pasang Driver (.zip)"));
                btnPickDrv.setTextColor(C_TEXT);
                btnResetDrv.setTextColor(mali && customDrv && GameSettings.isTurnipSelected(this) ? C_WARN : C_TEXT);
            }
        }
        boolean pendingFiles = basePath != null || baseUri != null || hasUpdate || dlcCount > 0;
        if (ready && !pendingFiles) {
            btnSetup.setText(UiText.tr(this, "Re-Setup / Update Files", "Setup Ulang / Tambah File"));
            GradientDrawable secBg = new GradientDrawable();
            secBg.setColor(C_CHIP);
            secBg.setCornerRadius(14 * dp);
            secBg.setStroke((int) Math.max(1, dp), C_CARD_LINE);
            btnSetup.setBackground(secBg);
            btnSetup.setTextColor(C_SUB);
        } else {
            btnSetup.setText(UiText.tr(this, "Set up Eastward", "Setup / Ekstrak Eastward"));
            GradientDrawable setupBg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFFFFD84D, C_ACCENT, 0xFFE3A812});
            setupBg.setCornerRadius(14 * dp);
            btnSetup.setBackground(setupBg);
            btnSetup.setTextColor(C_INK);
        }
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

    private void pickModFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(i, REQ_MOD);
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
        } else if (req == REQ_MOD) {
            try {
                int count = installModUri(uris.get(0));
                toast(UiText.tr(this,
                        "Mod installed (" + count + " file" + (count == 1 ? "" : "s") + ").",
                        "Mod terpasang (" + count + " file)."));
            } catch (Exception e) {
                showError(UiText.tr(this, "Mod Install Failed", "Gagal Pasang Mod"), e.getMessage());
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
                    reconcileModPaths();
                    GameSettings.syncModOverlay(this);
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

    private int installModUri(Uri uri) throws IOException {
        String name = queryName(uri);
        if (name == null || name.isEmpty()) name = "mod_file";
        File gameDir = GameSettings.gameDir(this);
        File modRomfs = new File(gameDir, "mods/romfs");
        modRomfs.mkdirs();
        File romfsDir = new File(gameDir, "RomFS");
        int count = 0;

        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            String canonicalMod = modRomfs.getCanonicalPath() + File.separator;
            byte[] buf = new byte[32768];
            try (InputStream raw = getContentResolver().openInputStream(uri);
                 ZipInputStream zis = raw != null ? new ZipInputStream(new BufferedInputStream(raw)) : null) {
                if (zis == null) throw new IOException("Cannot read selected mod .zip");
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String rel = normalizeModEntryPath(e.getName());
                    if (rel.isEmpty()) continue;
                    File out = new File(modRomfs, rel);
                    if (!out.getCanonicalPath().startsWith(canonicalMod)) continue;
                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    count++;
                }
            }
            if (count == 0) throw new IOException("Mod .zip contains no files");
        } else {
            String rel = null;
            if (romfsDir.isDirectory()) {
                rel = findRelativeMatchInRomfs(romfsDir, name, "");
            }
            if (rel == null || rel.isEmpty()) {
                rel = name.equalsIgnoreCase("locale.g") ? "game/locale.g" : name;
            }
            File out = new File(modRomfs, rel);
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(out)) {
                if (is == null) throw new IOException("Cannot read selected mod file");
                copyStream(is, fos);
            }
            count = 1;
        }
        reconcileModPaths();
        GameSettings.syncModOverlay(this);
        return count;
    }

    private String normalizeModEntryPath(String rawName) {
        String p = rawName.replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        String lower = p.toLowerCase(Locale.ROOT);
        if (lower.startsWith("__macosx/") || lower.contains("/__macosx/")
                || lower.endsWith("/.ds_store") || lower.equals(".ds_store")
                || lower.endsWith("/thumbs.db") || lower.equals("thumbs.db")
                || lower.endsWith("/desktop.ini") || lower.equals("desktop.ini")) {
            return "";
        }
        int idx = lower.indexOf("/romfs/");
        if (idx >= 0) {
            p = p.substring(idx + "/romfs/".length());
        } else if (lower.startsWith("mods/romfs/")) {
            p = p.substring("mods/romfs/".length());
        } else if (lower.startsWith("romfs/")) {
            p = p.substring("romfs/".length());
        }
        String lower2 = p.toLowerCase(Locale.ROOT);
        int gIdx = lower2.indexOf("/game/");
        if (gIdx >= 0) {
            p = p.substring(gIdx + 1);
            lower2 = p.toLowerCase(Locale.ROOT);
        }
        if (lower2.equals("locale.g") || (lower2.endsWith("/locale.g") && !lower2.equals("game/locale.g"))) {
            p = "game/locale.g";
        }
        return p;
    }

    private void reconcileModPaths() {
        File gameDir = GameSettings.gameDir(this);
        File modRomfs = new File(gameDir, "mods/romfs");
        File romfsDir = new File(gameDir, "RomFS");
        if (!modRomfs.isDirectory()) return;
        File[] top = modRomfs.listFiles();
        if (top == null) return;
        for (File f : top) {
            if (!f.isFile()) continue;
            String rel = romfsDir.isDirectory() ? findRelativeMatchInRomfs(romfsDir, f.getName(), "") : null;
            if (rel == null && f.getName().equalsIgnoreCase("locale.g")) {
                rel = "game/locale.g";
            }
            if (rel != null && !rel.equals(f.getName())) {
                File target = new File(modRomfs, rel);
                if (target.getParentFile() != null) target.getParentFile().mkdirs();
                if (!target.exists() || target.delete()) {
                    f.renameTo(target);
                }
            }
        }
    }

    private String findRelativeMatchInRomfs(File dir, String fileName, String prefix) {
        File[] list = dir.listFiles();
        if (list == null) return null;
        for (File f : list) {
            String rel = prefix.isEmpty() ? f.getName() : (prefix + "/" + f.getName());
            if (f.isFile() && f.getName().equalsIgnoreCase(fileName)) return rel;
            if (f.isDirectory()) {
                String hit = findRelativeMatchInRomfs(f, fileName, rel);
                if (hit != null) return hit;
            }
        }
        return null;
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
        l.setElevation(4 * dp);
        int p = (int) (16 * dp);
        l.setPadding(p, (int) (14 * dp), p, (int) (14 * dp));
        return l;
    }

    private void cardHeaderBadge(LinearLayout p, String title, String badgeText, int badgeBg, int badgeFg, float dp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, (int) (4 * dp));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_ACCENT);
        t.setLetterSpacing(0.06f);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView b = new TextView(this);
        b.setText(badgeText);
        b.setTextColor(badgeFg);
        b.setTextSize(9);
        b.setLetterSpacing(0.05f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding((int) (8 * dp), (int) (2 * dp), (int) (8 * dp), (int) (2 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(badgeBg);
        bg.setCornerRadius(8 * dp);
        b.setBackground(bg);
        row.addView(b);
        p.addView(row);
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
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
