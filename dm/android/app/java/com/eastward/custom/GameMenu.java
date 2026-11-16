package com.eastward.custom;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sleek dark-glass right-edge slide-in pause drawer styled for Eastward Custom.
 */
public final class GameMenu {
    private static final int BG        = 0xF2121419;
    private static final int RAIL_BG   = 0xFF0D0F13;
    private static final int CARD      = 0xFF1C1F26;
    private static final int CARD_LINE = 0xFF2A2E37;
    private static final int CHIP      = 0xFF262A33;
    private static final int TEXT      = 0xFFF2F3F5;
    private static final int SUB       = 0xFF9AA0AB;
    private static final int ACCENT    = 0xFFF5C428;
    private static final int INK       = 0xFF1A1710;
    private static final int WARN      = 0xFFFFB74D;
    private static final int DANGER    = 0xFFFF726F;
    private static final int REQ_LOG_EXPORT = 9001;

    private static final String[] TABS = {"quick", "controls", "graphics", "system"};
    private static final String[] TAB_LABELS = {"Quick", "Controls", "Graphics", "System"};
    private static final String[] PRESET_DESCS = {
        "RPG: ergonomic Eastward action arc + golden SVG icons with button badges.",
        "2D: clean Switch face diamond without L/ZR clutter.",
        "3D: Switch layout plus right-half camera drag.",
        "Tap: touch controls hidden (use a physical controller)."
    };

    private static Dialog dlg;
    private static Activity host;
    private static Gamepad pad;
    private static String activeTab = "quick";
    private static boolean skipUnpauseOnDismiss;
    private static boolean confirmQuit;
    private static final Set<String> restartNeeded = new HashSet<>();

    private static LinearLayout railList;
    private static LinearLayout body;
    private static TextView exitRailBtn;

    private GameMenu() {}

    public static void show(final Activity a, final Gamepad g) {
        if (dlg != null && dlg.isShowing()) return;
        host = a;
        pad = g;
        skipUnpauseOnDismiss = false;
        confirmQuit = false;
        if (activeTab == null) activeTab = "quick";

        Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCanceledOnTouchOutside(true);

        int sw = a.getResources().getDisplayMetrics().widthPixels;
        int sh = a.getResources().getDisplayMetrics().heightPixels;
        int sheetW = Math.min(dp(a, 600), Math.round(Math.max(sw, sh) * 0.64f));

        // Root sheet: left nav rail + right scrollable body
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.HORIZONTAL);
        sheet.setBackground(leftRounded(BG, dp(a, 22), dp(a, 1), CARD_LINE));

        // --- Left Rail ---
        LinearLayout rail = new LinearLayout(a);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setBackground(leftRounded(RAIL_BG, dp(a, 22), 0, 0));
        rail.setPadding(dp(a, 14), dp(a, 18), dp(a, 14), dp(a, 14));

        // Brand badge at top of rail
        LinearLayout brand = new LinearLayout(a);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(a, 6), 0, dp(a, 4), dp(a, 16));

        Bitmap emblem = loadAssetBitmap(a, "icons/emblem.png");
        if (emblem != null) {
            ImageView iv = new ImageView(a);
            iv.setImageBitmap(emblem);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(a, 34), dp(a, 34));
            ilp.rightMargin = dp(a, 10);
            brand.addView(iv, ilp);
        }
        LinearLayout brandCol = new LinearLayout(a);
        brandCol.setOrientation(LinearLayout.VERTICAL);
        brandCol.addView(text(a, "Eastward", 15, TEXT, true));
        brandCol.addView(text(a, "Custom v1.1.7", 11, ACCENT, true));
        brand.addView(brandCol);
        rail.addView(brand);

        railList = new LinearLayout(a);
        railList.setOrientation(LinearLayout.VERTICAL);
        rail.addView(railList, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView exitRail = text(a, UiText.tr(a, "Exit Game", "Keluar Game"), 13, DANGER, true);
        exitRailBtn = exitRail;
        exitRail.setGravity(Gravity.CENTER);
        exitRail.setPadding(dp(a, 12), dp(a, 9), dp(a, 12), dp(a, 9));
        exitRail.setBackground(round(CHIP, dp(a, 12), 1, CARD_LINE));
        exitRail.setOnClickListener(v -> {
            activeTab = "system";
            confirmQuit = true;
            redraw();
        });
        rail.addView(exitRail, new LinearLayout.LayoutParams(-1, -2));

        // Divider between rail and body
        View divider = new View(a);
        divider.setBackgroundColor(0xFF232730);

        // --- Right Scrollable Content ---
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 18), dp(a, 18), dp(a, 28), dp(a, 24));
        sv.addView(body, new ViewGroup.LayoutParams(-1, -2));

        sheet.addView(rail, new LinearLayout.LayoutParams(dp(a, 162), -1));
        sheet.addView(divider, new LinearLayout.LayoutParams(dp(a, 1), -1));
        sheet.addView(sv, new LinearLayout.LayoutParams(0, -1, 1f));

        d.setContentView(sheet, new ViewGroup.LayoutParams(sheetW, -1));
        Window w = d.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = sheetW;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.END | Gravity.FILL_VERTICAL;
            lp.dimAmount = 0.48f;
            lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            w.setAttributes(lp);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        d.setOnDismissListener(di -> {
            Dialog cur = dlg;
            dlg = null;
            railList = null;
            body = null;
            exitRailBtn = null;
            if (cur == di && pad != null) {
                if (skipUnpauseOnDismiss) {
                    pad.menuOpen = false;
                    pad.enterEditMode();
                } else {
                    pad.openPauseMenu(false);
                }
            }
        });

        dlg = d;
        redraw();
        d.show();
        if (w != null) {
            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    public static void dismiss() {
        if (dlg != null && dlg.isShowing()) {
            dlg.dismiss();
        }
    }

    private static void exportLogs(Activity a) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, LogExporter.suggestedFileName());
        a.startActivityForResult(i, REQ_LOG_EXPORT);
    }

    public static void onActivityResult(final Activity a, int req, int res, Intent data) {
        if (req != REQ_LOG_EXPORT || res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(() -> {
            try {
                final LogExporter.ExportResult r = LogExporter.exportZip(a, uri);
                a.runOnUiThread(() -> Toast.makeText(a,
                        UiText.tr(a,
                                String.format(Locale.ROOT, "Exported %d log files (%d KB)", r.fileCount, Math.max(1, r.zipBytes >> 10)),
                                String.format(Locale.ROOT, "Berhasil ekspor %d file log (%d KB)", r.fileCount, Math.max(1, r.zipBytes >> 10))),
                        Toast.LENGTH_LONG).show());
            } catch (final Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a,
                        UiText.tr(a, "Log export failed: ", "Gagal ekspor log: ") + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static void redraw() {
        if (host == null || railList == null || body == null) return;
        rebuildRail(host);
        body.removeAllViews();
        if ("quick".equals(activeTab)) buildQuick(host);
        else if ("controls".equals(activeTab)) buildControls(host);
        else if ("graphics".equals(activeTab)) buildGraphics(host);
        else buildSystem(host);
    }

    private static void rebuildRail(Activity a) {
        railList.removeAllViews();
        String[] tabNames = new String[]{
            UiText.tr(a, "Quick", "Cepat"),
            UiText.tr(a, "Controls", "Kontrol"),
            UiText.tr(a, "Graphics", "Grafis"),
            UiText.tr(a, "System", "Sistem")
        };
        for (int i = 0; i < TABS.length; i++) {
            final String id = TABS[i];
            boolean on = id.equals(activeTab);
            TextView tab = text(a, tabNames[i], 14, on ? INK : SUB, true);
            tab.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
            tab.setBackground(on ? round(ACCENT, dp(a, 12), 0, 0) : null);
            tab.setOnClickListener(v -> {
                if (!id.equals(activeTab)) {
                    activeTab = id;
                    confirmQuit = false;
                    redraw();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(a, 4);
            railList.addView(tab, lp);
        }

        // Export Log button right above ID | ENG pill at bottom of railList
        View spacer = new View(a);
        railList.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView btnExportLog = text(a, UiText.tr(a, "Export Log", "Ekspor Log"), 11, ACCENT, true);
        btnExportLog.setGravity(Gravity.CENTER);
        btnExportLog.setPadding(dp(a, 8), dp(a, 7), dp(a, 8), dp(a, 7));
        btnExportLog.setBackground(round(CHIP, dp(a, 10), 1, CARD_LINE));
        btnExportLog.setOnClickListener(v -> exportLogs(a));
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(-1, -2);
        exLp.topMargin = dp(a, 8);
        exLp.bottomMargin = dp(a, 6);
        railList.addView(btnExportLog, exLp);

        LinearLayout langBox = new LinearLayout(a);
        langBox.setOrientation(LinearLayout.HORIZONTAL);
        langBox.setPadding(dp(a, 3), dp(a, 3), dp(a, 3), dp(a, 3));
        langBox.setBackground(round(CHIP, dp(a, 10), 1, CARD_LINE));
        boolean isId = UiText.isId(a);

        TextView btnId = text(a, "ID", 11, isId ? INK : SUB, true);
        btnId.setGravity(Gravity.CENTER);
        btnId.setPadding(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        if (isId) btnId.setBackground(round(ACCENT, dp(a, 8), 0, 0));
        btnId.setOnClickListener(v -> {
            if (!UiText.isId(a)) {
                UiText.setLang(a, "id");
                redraw();
            }
        });

        TextView btnEng = text(a, "ENG", 11, !isId ? INK : SUB, true);
        btnEng.setGravity(Gravity.CENTER);
        btnEng.setPadding(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        if (!isId) btnEng.setBackground(round(ACCENT, dp(a, 8), 0, 0));
        btnEng.setOnClickListener(v -> {
            if (UiText.isId(a)) {
                UiText.setLang(a, "en");
                redraw();
            }
        });

        langBox.addView(btnId, new LinearLayout.LayoutParams(0, -2, 1f));
        langBox.addView(btnEng, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.bottomMargin = dp(a, 8);
        railList.addView(langBox, llp);

        if (exitRailBtn != null) {
            exitRailBtn.setText(UiText.tr(a, "Exit Game", "Keluar Game"));
        }
    }

    // ---------- Pages ----------

    private static void buildQuick(Activity a) {
        heading(a, UiText.tr(a, "Game Session", "Sesi Game"));

        // Primary CTA row: Resume + Edit Touch Layout
        LinearLayout ctaRow = new LinearLayout(a);
        ctaRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.bottomMargin = dp(a, 12);
        body.addView(ctaRow, rlp);

        TextView resume = text(a, UiText.tr(a, "Resume Game", "Lanjutkan Game"), 15, INK, true);
        resume.setGravity(Gravity.CENTER);
        resume.setPadding(dp(a, 18), dp(a, 13), dp(a, 18), dp(a, 13));
        resume.setBackground(round(ACCENT, dp(a, 16), 0, 0));
        resume.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f);
        l1.rightMargin = dp(a, 8);
        ctaRow.addView(resume, l1);

        TextView edit = text(a, UiText.tr(a, "Edit Touch Layout", "Atur Tombol Layar"), 14, TEXT, true);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(a, 14), dp(a, 13), dp(a, 14), dp(a, 13));
        edit.setBackground(round(CARD, dp(a, 16), dp(a, 1), ACCENT));
        edit.setOnClickListener(v -> {
            skipUnpauseOnDismiss = true;
            dismiss();
        });
        ctaRow.addView(edit, new LinearLayout.LayoutParams(0, -2, 1f));

        heading(a, UiText.tr(a, "Quick Controls", "Kontrol Cepat"));
        presetCard(a);
        actionCard(a, UiText.tr(a, "Auto-Tune This Device", "Sesuaikan Otomatis HP Ini"),
                UiText.tr(a,
                        "Detects your CPU core layout, GPU (Mali/Adreno) & optimal 720p settings. Applies next start.",
                        "Deteksi otomatis tipe GPU (Mali/Adreno), core CPU & setelan 720p paling pas. Aktif saat restart."),
                () -> {
                    GameSettings.autoTuneDevice(a);
                    restartNeeded.add("DM_OPMODE");
                    redraw();
                });
        toggleRowCard(a, UiText.tr(a, "Show FPS Counter", "Tampilkan Counter FPS"),
                UiText.tr(a, "Display live frame rate counter in top-left corner",
                        "Tampilkan penghitung FPS langsung di pojok kiri atas"),
                pad != null && pad.fpsOn,
                null,
                checked -> {
                    if (pad != null) pad.fpsSet(checked);
                    GameSettings.set(a, "DM_SHOW_FPS", checked ? "1" : "0");
                });
        envToggleCard(a, UiText.tr(a, "FP16 Shader Fast Math", "Optimasi Shader FP16"), "DM_VK_MALI_FP16", true,
                UiText.tr(a, "Speeds up fragment shaders on Mali & Adreno GPUs; turn off if visual glitches appear",
                        "Percepat shader fragment di GPU Mali & Adreno; matikan jika muncul glitch visual"));
    }

    private static void buildControls(Activity a) {
        heading(a, UiText.tr(a, "Touch Controller", "Kontrol Sentuh"));
        presetCard(a);

        toggleRowCard(a, UiText.tr(a, "Auto-Hide Touch on Gamepad", "Sembunyikan Saat Stick Aktif"),
                UiText.tr(a, "Keep OFF to ensure on-screen buttons and D-Pad always stay visible",
                        "Biarkan MATI agar tombol & D-Pad layar selalu tampil"),
                pad != null && pad.autoHideOnPad,
                null,
                checked -> {
                    if (pad != null) pad.setAutoHideOnPad(checked);
                });

        sliderCard(a, UiText.tr(a, "Touch Opacity", "Transparansi Tombol"), pad != null ? pad.opacityPct : 75, 20, 100,
                v -> v + " %",
                v -> { if (pad != null) pad.setOpacity(v); });

        int scalePct = pad != null ? Math.round(pad.globalScale * 100f) : 100;
        sliderCard(a, UiText.tr(a, "Button Scale", "Ukuran Tombol"), scalePct, 65, 135,
                v -> v + " %",
                v -> { if (pad != null) pad.setGlobalScale(v / 100f); });

        actionCard(a, UiText.tr(a, "Edit Touch Positions", "Ubah Posisi Tombol"),
                UiText.tr(a, "Drag any control to move it, or tap and adjust individual button scale.",
                        "Geser tombol ke posisi mana saja, atau ketuk untuk ubah ukuran per tombol."),
                () -> {
                    skipUnpauseOnDismiss = true;
                    dismiss();
                });

        actionCard(a, UiText.tr(a, "Reset Touch Layout", "Reset Posisi Tombol"),
                UiText.tr(a, "Restore default Eastward RPG positions for the current preset.",
                        "Kembalikan posisi default RPG Eastward sesuai preset aktif."),
                () -> {
                    if (pad != null) pad.resetPresetLayout();
                    redraw();
                });
    }

    private static void buildGraphics(Activity a) {
        heading(a, UiText.tr(a, "Display & Performance", "Tampilan & Performa"));
        toggleRowCard(a, UiText.tr(a, "FPS Overlay", "Overlay FPS"),
                UiText.tr(a, "Show live frames-per-second counter in corner",
                        "Tampilkan counter FPS langsung di sudut layar"),
                pad != null && pad.fpsOn,
                null,
                checked -> {
                    if (pad != null) pad.fpsSet(checked);
                    GameSettings.set(a, "DM_SHOW_FPS", checked ? "1" : "0");
                });

        actionCard(a, UiText.tr(a, "Auto-Tune This Device", "Sesuaikan Otomatis HP Ini"),
                UiText.tr(a,
                        "Detects your GPU vendor, CPU big/little cluster mask & optimal settings.",
                        "Deteksi otomatis jenis GPU, posisi core CPU besar/kecil & profil terbaik untuk HP ini."),
                () -> {
                    GameSettings.autoTuneDevice(a);
                    restartNeeded.add("DM_VK_PIN");
                    redraw();
                });

        envChoiceCard(a, UiText.tr(a, "Render Resolution", "Resolusi Render"), "DM_OPMODE", "0", new String[][]{
            {"0", UiText.tr(a, "Handheld 720p", "720p Ringan"),
                    UiText.tr(a, "Recommended for entry/midrange phones and cooler battery",
                            "Direkomendasikan untuk mayoritas HP Android agar lancar & adem")},
            {"1", UiText.tr(a, "Docked 1080p", "1080p Tajam"),
                    UiText.tr(a, "Sharper picture at 1920x1080 for flagship SoCs",
                            "Gambar lebih tajam 1920x1080 untuk HP kelas flagship")}
        });

        envChoiceCard(a, UiText.tr(a, "GPU Render Scale", "Skala Render GPU"), "DM_VK_FB_SCALE", "100", new String[][]{
            {"100", UiText.tr(a, "100% Native", "100% Asli"),
                    UiText.tr(a, "Full native pixel count; best for flagship GPUs",
                            "Resolusi penuh tanpa skala; untuk GPU flagship")},
            {"80", UiText.tr(a, "80% Crisp (-36% GPU load)", "80% Tajam (-36% beban GPU)"),
                    UiText.tr(a, "Sharp visual balance with lower fillrate load",
                            "Keseimbangan tajam dan ringan di GPU")},
            {"75", UiText.tr(a, "75% Smooth 540p (-44% GPU load)", "75% Lancar 540p (-44% beban GPU)"),
                    UiText.tr(a, "Recommended for Snapdragon 6xx & Helio Gxx GPUs for 40-60 fps",
                            "Direkomendasikan untuk Adreno 610 & Mali-G52 agar tembus 40-60 fps")},
            {"67", UiText.tr(a, "67% Fast 480p (-55% GPU load)", "67% Ngebut 480p (-55% beban GPU)"),
                    UiText.tr(a, "Maximum GPU headroom on heavy outdoor areas",
                            "Ringankan beban GPU maksimal di area berat")}
        });

        envChoiceCard(a, UiText.tr(a, "Frame Pacing", "Sinkronisasi Frame"), "DM_PRESENT_HZ", "", new String[][]{
            {"", UiText.tr(a, "60 Hz VSync (Default)", "60 Hz VSync (Standar)"),
                    UiText.tr(a, "Standard Switch frame pacing sync",
                            "Sinkronisasi pacing frame standar Switch")},
            {"0", UiText.tr(a, "Uncapped (Experimental)", "Tanpa Batas (Eksperimen)"),
                    UiText.tr(a, "Disables NVN present wait; may shift frame-counted UI",
                            "Matikan jeda present NVN; untuk tes performa mentah")}
        });

        heading(a, UiText.tr(a, "CPU & Chipset Tuning", "Tuning CPU & Chipset"));
        cpuPinCard(a);
        envChoiceCard(a, UiText.tr(a, "CPU Clock Boost (UClamp)", "Boost Clock CPU (UClamp)"), "DM_VK_UCLAMP_MIN", "0", new String[][]{
            {"0", UiText.tr(a, "Auto / Off", "Otomatis / Mati"),
                    UiText.tr(a, "Let Android governor manage CPU clocks",
                            "Biarkan sistem Android mengatur frekuensi CPU")},
            {"512", UiText.tr(a, "Balanced (512)", "Seimbang (512)"),
                    UiText.tr(a, "Moderate CPU clamp (Android 11+ / supported kernels)",
                            "Batas bawah frekuensi sedang (aktif di Android 11+ / kernel support)")},
            {"800", UiText.tr(a, "Performance (800)", "Performa (800)"),
                    UiText.tr(a, "High boost for render thread on supported kernels",
                            "Boost tinggi untuk thread render di kernel yang mendukung")},
            {"1024", UiText.tr(a, "Max (1024)", "Maksimal (1024)"),
                    UiText.tr(a, "Request peak clocks; warmer battery",
                            "Minta clock CPU tertinggi; lebih hangat & boros baterai")}
        });

        envChoiceCard(a, UiText.tr(a, "Adreno GPU Clock Boost", "Boost Clock GPU Adreno"), "DM_VK_GPU_FLOOR", "0", new String[][]{
            {"0", UiText.tr(a, "Off", "Mati"),
                    UiText.tr(a, "Let Adreno GMU scale GPU clock dynamically",
                            "Biarkan governor Adreno mengatur clock GPU otomatis")},
            {"85", UiText.tr(a, "High Floor (85%)", "Floor Tinggi (85%)"),
                    UiText.tr(a, "Prevents Adreno downclocking between CPU chunks (Snapdragon only)",
                            "Cegah clock Adreno turun di sela chunk CPU (khusus Snapdragon)")},
            {"100", UiText.tr(a, "Max Lock (100%)", "Kunci Maksimal (100%)"),
                    UiText.tr(a, "Request peak Adreno KGSL clock; warmer battery",
                            "Minta clock tertinggi KGSL Adreno; baterai lebih hangat")}
        });

        heading(a, UiText.tr(a, "GPU & Shader Flags", "Pengaturan GPU & Shader"));
        envChoiceCard(a, UiText.tr(a, "Render Pass Batch", "Batch Render Pass"), "DM_VK_MID_DRAWS", "64", new String[][]{
            {"64", UiText.tr(a, "Standard (64)", "Standar (64)"),
                    UiText.tr(a, "Default split after 64 draws",
                            "Pecah render pass standar tiap 64 draw")},
            {"256", UiText.tr(a, "TBDR Tuned (256)", "TBDR Optimal (256)"),
                    UiText.tr(a, "Reduces tile memory reloads on Adreno/Mali GPUs",
                            "Kurangi reload tile GMEM di GPU mobile")},
            {"512", UiText.tr(a, "Max Batch (512)", "Batch Maksimal (512)"),
                    UiText.tr(a, "Minimum pass splits for peak throughput",
                            "Minimalisir pemecahan render pass untuk FPS maksimal")}
        });
        envToggleCard(a, UiText.tr(a, "FP16 Shader Fast Math", "Optimasi Shader FP16"), "DM_VK_MALI_FP16", true,
                UiText.tr(a, "Speeds up 16-bit fragment math on Mali & Adreno; turn off if artifacts appear",
                        "Percepat hitung fragment 16-bit di Mali & Adreno; matikan jika ada glitch"));
        envToggleCard(a, UiText.tr(a, "Threaded Vulkan Recorder", "Perekam Vulkan Multi-Thread"), "DM_VK_RECORDER", false,
                UiText.tr(a, "Offloads vkCmd recording to a dedicated worker thread",
                        "Jalankan perekaman perintah Vulkan di thread terpisah"));
        envToggleCard(a, UiText.tr(a, "TBDR Clear LoadOp", "TBDR Clear LoadOp"), "DM_VK_CLEAR_LOADOP", true,
                UiText.tr(a, "Fast tile clear; recommended for both Adreno and Mali",
                        "Clear tile cepat untuk GPU mobile (Adreno & Mali)"));
        envToggleCard(a, UiText.tr(a, "Descriptor Set Reuse", "Gunakan Ulang Descriptor"), "DM_VK_SET_REUSE", true,
                UiText.tr(a, "Reduces per-frame Vulkan CPU allocation overhead",
                        "Kurangi beban CPU alokasi descriptor Vulkan tiap frame"));
        envToggleCard(a, UiText.tr(a, "Async Shader Compile", "Kompilasi Shader Latar"), "DM_VK_ASYNC_PIPE", true,
                UiText.tr(a, "Compiles new shaders in background to prevent freezes",
                        "Bangun shader baru di latar belakang agar tidak patah-patah"));
        envToggleCard(a, UiText.tr(a, "Display Timing Sync", "Sinkronisasi Timing Layar"), "DM_VK_PRESENT_TIMING", true,
                UiText.tr(a, "Turn off if frame pacing stutters on your ROM or driver",
                        "Matikan jika terasa tersendat pada driver/ROM HP tertentu"));
        envToggleCard(a, UiText.tr(a, "Precise SPIR-V Float", "Presisi Float SPIR-V"), "DM_SPV_PRECISE", true,
                UiText.tr(a, "Keep enabled to avoid screen and depth glitches on Mali & mobile GPUs",
                        "Biarkan aktif untuk mencegah glitch layar dan shader di GPU HP"));
    }

    private static void cpuPinCard(Activity a) {
        String cur = GameSettings.get(a, "DM_VK_PIN", "");
        String[][] opts = new String[][]{
            {"", UiText.tr(a, "OS Auto Scheduler", "Otomatis (OS Scheduler)"),
                    UiText.tr(a, "Default: let Android decide CPU core placement",
                            "Standar: biarkan sistem Android mengatur penempatan core")},
            {"c0", UiText.tr(a, "2+6 Big.LITTLE (0xC0 / 0x3F)", "2+6 Big.LITTLE (0xC0 / 0x3F)"),
                    UiText.tr(a, "For 2-big + 6-little SoCs (MediaTek Helio / mid-tier 8-core)",
                            "Untuk chipset 2 core besar + 6 core kecil (cpu6-7 untuk render)")},
            {"f0", UiText.tr(a, "4+4 Big.LITTLE (0xF0 / 0x0F)", "4+4 Big.LITTLE (0xF0 / 0x0F)"),
                    UiText.tr(a, "For 4-big + 4-little SoCs (Snapdragon / Exynos 4+4)",
                            "Untuk chipset 4 core besar + 4 core kecil (cpu4-7 untuk render)")},
            {"80", UiText.tr(a, "1+3+4 Prime Core (0x80 / 0x7F)", "1+3+4 Prime Core (0x80 / 0x7F)"),
                    UiText.tr(a, "For tri-cluster SoCs with 1 prime core (Snapdragon 7+/8, Dimensity 8/9)",
                            "Untuk chipset flagship 1 prime core (cpu7 untuk render)")}
        };
        LinearLayout c = card(a);
        titleRow(a, c, UiText.tr(a, "CPU Thread Placement", "Penempatan Thread CPU"),
                restartNeeded.contains("DM_VK_PIN") ? UiText.tr(a, "Applies next start", "Aktif saat restart") : null);
        for (int i = 0; i < opts.length; i++) {
            final String val = opts[i][0];
            boolean sel = cur.equalsIgnoreCase(val);
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a, 10), dp(a, 9), dp(a, 10), dp(a, 9));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? 0x26F5C428 : CHIP);
            bg.setCornerRadius(dp(a, 10));
            if (sel) bg.setStroke(dp(a, 1), ACCENT);
            row.setBackground(bg);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = dp(a, i == 0 ? 8 : 6);
            row.setLayoutParams(rlp);

            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            col.addView(text(a, opts[i][1], 13, sel ? ACCENT : TEXT, true));
            TextView sub = text(a, opts[i][2], 11, SUB, false);
            sub.setPadding(0, dp(a, 1), 0, 0);
            col.addView(sub);
            row.addView(col);
            if (sel) row.addView(text(a, "\u2713", 15, ACCENT, true));
            row.setOnClickListener(v -> {
                if (!cur.equalsIgnoreCase(val)) {
                    GameSettings.setCpuPin(a, val);
                    restartNeeded.add("DM_VK_PIN");
                    redraw();
                }
            });
            c.addView(row);
        }
    }

    private static void buildSystem(Activity a) {
        heading(a, UiText.tr(a, "Menu Language", "Bahasa Menu"));
        LinearLayout langCard = card(a);
        titleRow(a, langCard, UiText.tr(a, "Interface Language (ID | ENG)", "Bahasa Tampilan (ID | ENG)"), null);
        LinearLayout langRow = new LinearLayout(a);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        langRow.setPadding(0, dp(a, 10), 0, 0);
        boolean isId = UiText.isId(a);
        TextView chId = pillButton(a, "Indonesia (ID)", isId, () -> {
            UiText.setLang(a, "id");
            redraw();
        });
        TextView chEng = pillButton(a, "English (ENG)", !isId, () -> {
            UiText.setLang(a, "en");
            redraw();
        });
        LinearLayout.LayoutParams lpLang = new LinearLayout.LayoutParams(0, -2, 1f);
        lpLang.rightMargin = dp(a, 8);
        langRow.addView(chId, lpLang);
        langRow.addView(chEng, new LinearLayout.LayoutParams(0, -2, 1f));
        langCard.addView(langRow);

        heading(a, UiText.tr(a, "Device & Vulkan Runtime", "Perangkat & Runtime Vulkan"));
        LinearLayout infoCard = card(a);
        infoCard.addView(text(a, UiText.tr(a, "Active Vulkan Driver", "Driver Vulkan Aktif"), 15, TEXT, true));
        TextView drv = text(a, GameSettings.driverLabel(a), 14, ACCENT, true);
        drv.setPadding(0, dp(a, 4), 0, dp(a, 6));
        infoCard.addView(drv);

        Map<String, String> vk = GameSettings.vkInfo(a);
        String vkMeta = vk.isEmpty()
                ? UiText.tr(a,
                        "Detected automatically per device (Turnip on Adreno, System Vulkan on Mali/others).",
                        "Terdeteksi otomatis per HP (Turnip di Adreno, Vulkan Bawaan di Mali/lainnya).")
                : String.format(Locale.ROOT, "Vendor: %s  |  Vulkan: %s  |  Mode: %s",
                        nonEmpty(vk.get("vendor"), "Auto"),
                        nonEmpty(vk.get("api"), "1.1+"),
                        nonEmpty(vk.get("mode"), "Normal"));
        infoCard.addView(text(a, vkMeta, 12, SUB, false));

        heading(a, UiText.tr(a, "Launcher & Session", "Launcher & Sesi"));
        actionCard(a, UiText.tr(a, "Open Setup & Driver Manager", "Buka Pengaturan Awal & Driver"),
                UiText.tr(a, "Close game session and return to Eastward Custom Setup.",
                        "Tutup sesi game dan kembali ke halaman Setup Eastward Custom."),
                () -> {
                    Intent it = new Intent(a, SetupActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    a.startActivity(it);
                    a.finish();
                    Process.killProcess(Process.myPid());
                });

        if (!confirmQuit) {
            actionCard(a, UiText.tr(a, "Quit Eastward", "Keluar dari Eastward"),
                    UiText.tr(a, "Close the game cleanly and stop the runtime process.",
                            "Tutup game dengan aman dan hentikan proses runtime."),
                    () -> {
                        confirmQuit = true;
                        redraw();
                    });
        } else {
            LinearLayout qc = card(a);
            qc.addView(text(a, UiText.tr(a, "Close Eastward now?", "Tutup Eastward sekarang?"), 15, TEXT, true));
            TextView sub = text(a, UiText.tr(a,
                    "Make sure you saved your progress inside the game first.",
                    "Pastikan kamu sudah melakukan Save di dalam game terlebih dahulu."), 13, SUB, false);
            sub.setPadding(0, dp(a, 4), 0, dp(a, 10));
            qc.addView(sub);

            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView yes = pillButton(a, UiText.tr(a, "Quit Now", "Keluar Sekarang"), true, () -> {
                a.finishAffinity();
                Process.killProcess(Process.myPid());
            });
            yes.setBackground(round(DANGER, dp(a, 14), 0, 0));
            yes.setTextColor(0xFF1A0909);

            TextView cancel = pillButton(a, UiText.tr(a, "Cancel", "Batal"), false, () -> {
                confirmQuit = false;
                redraw();
            });
            LinearLayout.LayoutParams lpY = new LinearLayout.LayoutParams(0, -2, 1f);
            lpY.rightMargin = dp(a, 8);
            row.addView(yes, lpY);
            row.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
            qc.addView(row);
        }
    }

    // ---------- Reusable Cards & Custom Controls ----------

    private static void presetCard(Activity a) {
        LinearLayout c = card(a);
        titleRow(a, c, UiText.tr(a, "Controller Preset", "Preset Kontroler"), null);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        row.setPadding(0, dp(a, 10), 0, dp(a, 6));
        hs.addView(row);
        c.addView(hs);

        int cur = pad != null ? pad.preset : 0;
        for (int i = 0; i < Gamepad.PRESET_NAMES.length; i++) {
            final int idx = i;
            boolean on = (i == cur);
            TextView ch = text(a, Gamepad.PRESET_NAMES[i], 13, on ? INK : TEXT, true);
            ch.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
            ch.setBackground(on ? round(ACCENT, dp(a, 18), 0, 0) : round(CHIP, dp(a, 18), 1, CARD_LINE));
            ch.setOnClickListener(v -> {
                if (pad != null && pad.preset != idx) {
                    pad.setPreset(idx);
                    redraw();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(a, 8);
            row.addView(ch, lp);
        }
        String desc = (cur >= 0 && cur < PRESET_DESCS.length) ? PRESET_DESCS[cur] : "";
        c.addView(text(a, desc, 12, SUB, false));
    }

    private static void envChoiceCard(Activity a, String title, String key, String def, String[][] opts) {
        LinearLayout c = card(a);
        String tag = restartNeeded.contains(key) ? UiText.tr(a, "Applies next start", "Aktif saat restart") : null;
        titleRow(a, c, title, tag);

        String cur = GameSettings.get(a, key, def);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(a);
        chips.setPadding(0, dp(a, 10), 0, dp(a, 6));
        hs.addView(chips);
        c.addView(hs);

        String activeDesc = "";
        for (String[] o : opts) {
            boolean on = o[0].equals(cur);
            if (on && o.length > 2) activeDesc = o[2];
            TextView ch = text(a, o[1], 13, on ? INK : TEXT, true);
            ch.setPadding(dp(a, 15), dp(a, 8), dp(a, 15), dp(a, 8));
            ch.setBackground(on ? round(ACCENT, dp(a, 18), 0, 0) : round(CHIP, dp(a, 18), 1, CARD_LINE));
            ch.setOnClickListener(v -> {
                if (!o[0].equals(GameSettings.get(a, key, def))) {
                    GameSettings.set(a, key, o[0]);
                    restartNeeded.add(key);
                    redraw();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(a, 8);
            chips.addView(ch, lp);
        }
        if (!activeDesc.isEmpty()) c.addView(text(a, activeDesc, 12, SUB, false));
    }

    private static void envToggleCard(Activity a, String title, String key, boolean defVal, String desc) {
        boolean cur = "1".equals(GameSettings.get(a, key, defVal ? "1" : "0"));
        toggleRowCard(a, title, desc, cur,
                restartNeeded.contains(key) ? UiText.tr(a, "Applies next start", "Aktif saat restart") : null,
                checked -> {
                    GameSettings.set(a, key, checked ? "1" : "0");
                    restartNeeded.add(key);
                    redraw();
                });
    }

    interface BoolListener { void onChange(boolean v); }
    interface IntListener { void onChange(int v); }
    interface IntFmt { String format(int v); }

    private static void toggleRowCard(Activity a, String title, String sub, boolean initial, String badge, BoolListener cb) {
        LinearLayout c = card(a);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleLine = new LinearLayout(a);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.addView(text(a, title, 15, TEXT, true));
        if (badge != null) {
            TextView tg = text(a, badge, 10, WARN, true);
            tg.setBackground(round(0, dp(a, 8), dp(a, 1), WARN));
            tg.setPadding(dp(a, 7), dp(a, 2), dp(a, 7), dp(a, 2));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.leftMargin = dp(a, 8);
            titleLine.addView(tg, tlp);
        }
        left.addView(titleLine);

        if (sub != null && !sub.isEmpty()) {
            TextView s = text(a, sub, 12, SUB, false);
            s.setPadding(0, dp(a, 3), dp(a, 10), 0);
            left.addView(s);
        }
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        View sw = pillSwitch(a, initial, v -> cb.onChange(v));
        row.addView(sw);
        c.addView(row);
    }

    private static View pillSwitch(Activity a, boolean onInit, BoolListener cb) {
        final boolean[] state = {onInit};
        int w = dp(a, 46), h = dp(a, 26), pad = dp(a, 3), thumbSz = h - pad * 2;
        FrameLayout track = new FrameLayout(a);
        track.setLayoutParams(new LinearLayout.LayoutParams(w, h));

        View thumb = new View(a);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.OVAL);
        thumb.setBackground(thumbBg);

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(thumbSz, thumbSz);
        tlp.gravity = Gravity.CENTER_VERTICAL;
        track.addView(thumb, tlp);

        Runnable sync = () -> {
            track.setBackground(round(state[0] ? ACCENT : 0xFF2B303B, h * 0.5f, state[0] ? 0 : dp(a, 1), CARD_LINE));
            thumbBg.setColor(state[0] ? INK : 0xFFE2E5EC);
            tlp.leftMargin = state[0] ? (w - pad - thumbSz) : pad;
            thumb.setLayoutParams(tlp);
        };
        sync.run();

        track.setOnClickListener(v -> {
            state[0] = !state[0];
            sync.run();
            cb.onChange(state[0]);
        });
        return track;
    }

    private static void sliderCard(Activity a, String title, int cur, int min, int max, IntFmt fmt, IntListener cb) {
        LinearLayout c = card(a);
        LinearLayout top = titleRow(a, c, title, null);
        int clamped = Math.max(min, Math.min(max, cur));
        TextView val = text(a, fmt.format(clamped), 14, ACCENT, true);
        top.addView(val);

        SeekBar sb = new SeekBar(a);
        sb.setMax(max - min);
        sb.setProgress(clamped - min);
        sb.setPadding(dp(a, 8), dp(a, 12), dp(a, 8), dp(a, 4));

        GradientDrawable track = new GradientDrawable();
        track.setColor(0xFF2B303B);
        track.setCornerRadius(dp(a, 3));
        track.setSize(0, dp(a, 5));

        GradientDrawable fill = new GradientDrawable();
        fill.setColor(ACCENT);
        fill.setCornerRadius(dp(a, 3));
        fill.setSize(0, dp(a, 5));

        LayerDrawable bar = new LayerDrawable(new android.graphics.drawable.Drawable[]{
            track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)
        });
        bar.setId(0, android.R.id.background);
        bar.setId(1, android.R.id.progress);
        bar.setLayerHeight(0, dp(a, 5));
        bar.setLayerHeight(1, dp(a, 5));
        bar.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        bar.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        sb.setProgressDrawable(bar);
        sb.setMinHeight(dp(a, 5));
        sb.setMaxHeight(dp(a, 5));

        GradientDrawable knob = new GradientDrawable();
        knob.setShape(GradientDrawable.OVAL);
        knob.setColor(0xFFFFFFFF);
        knob.setStroke(dp(a, 3), ACCENT);
        knob.setSize(dp(a, 20), dp(a, 20));
        sb.setThumb(knob);
        sb.setThumbOffset(dp(a, 10));
        sb.setSplitTrack(false);
        sb.setBackground(null);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                int v = p + min;
                val.setText(fmt.format(v));
                if (user) cb.onChange(v);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        c.addView(sb);
    }

    private static void actionCard(Activity a, String title, String sub, Runnable r) {
        LinearLayout c = card(a);
        LinearLayout row = titleRow(a, c, title, null);
        row.addView(text(a, "\u203A", 22, ACCENT, true));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(a, sub, 12, SUB, false);
            s.setPadding(0, dp(a, 4), 0, 0);
            c.addView(s);
        }
        c.setOnClickListener(v -> r.run());
    }

    // ---------- Style Helpers ----------

    private static void heading(Activity a, String s) {
        TextView h = text(a, s.toUpperCase(Locale.ROOT), 11, ACCENT, true);
        h.setLetterSpacing(0.08f);
        h.setPadding(dp(a, 4), dp(a, 6), dp(a, 4), dp(a, 8));
        body.addView(h);
    }

    private static LinearLayout card(Activity a) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, dp(a, 16), dp(a, 1), CARD_LINE));
        c.setPadding(dp(a, 16), dp(a, 13), dp(a, 16), dp(a, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a, 10);
        body.addView(c, lp);
        return c;
    }

    private static LinearLayout titleRow(Activity a, LinearLayout c, String title, String tag) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(text(a, title, 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        if (tag != null) {
            TextView tg = text(a, tag, 10, WARN, true);
            tg.setBackground(round(0, dp(a, 8), dp(a, 1), WARN));
            tg.setPadding(dp(a, 7), dp(a, 2), dp(a, 7), dp(a, 2));
            r.addView(tg);
        }
        c.addView(r);
        return r;
    }

    private static TextView pillButton(Activity a, String label, boolean primary, Runnable onClick) {
        TextView b = text(a, label, 14, primary ? INK : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        b.setBackground(primary ? round(ACCENT, dp(a, 14), 0, 0) : round(CHIP, dp(a, 14), dp(a, 1), CARD_LINE));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private static TextView text(Activity a, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static GradientDrawable round(int color, float radius, int strokeW, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        if (color != 0) g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, strokeColor);
        return g;
    }

    private static GradientDrawable leftRounded(int color, float r, int strokeW, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        if (strokeW > 0) g.setStroke(strokeW, strokeColor);
        return g;
    }

    private static Bitmap loadAssetBitmap(Activity a, String path) {
        try (InputStream is = a.getAssets().open(path)) {
            return BitmapFactory.decodeStream(is);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String nonEmpty(String s, String def) {
        return (s != null && !s.isEmpty()) ? s : def;
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
