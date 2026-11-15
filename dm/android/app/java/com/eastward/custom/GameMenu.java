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
        brandCol.addView(text(a, "Custom v1.1", 11, ACCENT, true));
        brand.addView(brandCol);
        rail.addView(brand);

        railList = new LinearLayout(a);
        railList.setOrientation(LinearLayout.VERTICAL);
        rail.addView(railList, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView exitRail = text(a, "Exit Game", 13, DANGER, true);
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
        for (int i = 0; i < TABS.length; i++) {
            final String id = TABS[i];
            boolean on = id.equals(activeTab);
            TextView tab = text(a, TAB_LABELS[i], 14, on ? INK : SUB, true);
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
    }

    // ---------- Pages ----------

    private static void buildQuick(Activity a) {
        heading(a, "Game Session");

        // Primary CTA row: Resume + Edit Touch Layout
        LinearLayout ctaRow = new LinearLayout(a);
        ctaRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.bottomMargin = dp(a, 12);
        body.addView(ctaRow, rlp);

        TextView resume = text(a, "Resume Game", 15, INK, true);
        resume.setGravity(Gravity.CENTER);
        resume.setPadding(dp(a, 18), dp(a, 13), dp(a, 18), dp(a, 13));
        resume.setBackground(round(ACCENT, dp(a, 16), 0, 0));
        resume.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f);
        l1.rightMargin = dp(a, 8);
        ctaRow.addView(resume, l1);

        TextView edit = text(a, "Edit Touch Layout", 14, TEXT, true);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(a, 14), dp(a, 13), dp(a, 14), dp(a, 13));
        edit.setBackground(round(CARD, dp(a, 16), dp(a, 1), ACCENT));
        edit.setOnClickListener(v -> {
            skipUnpauseOnDismiss = true;
            dismiss();
        });
        ctaRow.addView(edit, new LinearLayout.LayoutParams(0, -2, 1f));

        heading(a, "Quick Controls");
        presetCard(a);
        toggleRowCard(a, "Show FPS Counter",
                "Display live frame rate counter in top-left corner",
                pad != null && pad.fpsOn,
                null,
                checked -> {
                    if (pad != null) pad.fpsSet(checked);
                    GameSettings.set(a, "DM_SHOW_FPS", checked ? "1" : "0");
                });
        envToggleCard(a, "Mali FP16 Fast Math", "DM_VK_MALI_FP16", true,
                "Keep enabled on Dimensity / Mali GPUs for smoother shader throughput");
    }

    private static void buildControls(Activity a) {
        heading(a, "Touch Controller");
        presetCard(a);

        sliderCard(a, "Touch Opacity", pad != null ? pad.opacityPct : 75, 20, 100,
                v -> v + " %",
                v -> { if (pad != null) pad.setOpacity(v); });

        int scalePct = pad != null ? Math.round(pad.globalScale * 100f) : 100;
        sliderCard(a, "Button Scale", scalePct, 65, 135,
                v -> v + " %",
                v -> { if (pad != null) pad.setGlobalScale(v / 100f); });

        actionCard(a, "Edit Touch Positions",
                "Drag any control to move it, or tap and adjust individual button scale.",
                () -> {
                    skipUnpauseOnDismiss = true;
                    dismiss();
                });

        actionCard(a, "Reset Touch Layout",
                "Restore default Eastward RPG positions for the current preset.",
                () -> {
                    if (pad != null) pad.resetPresetLayout();
                    redraw();
                });
    }

    private static void buildGraphics(Activity a) {
        heading(a, "Display & Performance");
        toggleRowCard(a, "FPS Overlay",
                "Show live frames-per-second counter in corner",
                pad != null && pad.fpsOn,
                null,
                checked -> {
                    if (pad != null) pad.fpsSet(checked);
                    GameSettings.set(a, "DM_SHOW_FPS", checked ? "1" : "0");
                });

        envChoiceCard(a, "Render Resolution", "DM_OPMODE", "0", new String[][]{
            {"0", "Handheld 720p", "Recommended for smooth 60 fps and cooler battery"},
            {"1", "Docked 1080p", "Sharper picture at 1920x1080; heavier GPU load"}
        });

        heading(a, "Vulkan Pipeline Flags");
        envToggleCard(a, "Mali FP16 Fast Math", "DM_VK_MALI_FP16", true,
                "Lowers shader register pressure on Mali / Dimensity GPUs");
        envToggleCard(a, "RenderPass Clear LoadOp", "DM_VK_CLEAR_LOADOP", true,
                "Uses fast tile clear; recommended for both Adreno and Mali");
        envToggleCard(a, "Descriptor Set Reuse", "DM_VK_SET_REUSE", true,
                "Reduces per-frame Vulkan descriptor allocation overhead");
        envToggleCard(a, "Precise SPIR-V Float", "DM_SPV_PRECISE", false,
                "Enable only if troubleshooting rare shader math artifacts");
    }

    private static void buildSystem(Activity a) {
        heading(a, "Device & Vulkan Runtime");
        LinearLayout infoCard = card(a);
        infoCard.addView(text(a, "Active Vulkan Driver", 15, TEXT, true));
        TextView drv = text(a, GameSettings.driverLabel(a), 14, ACCENT, true);
        drv.setPadding(0, dp(a, 4), 0, dp(a, 6));
        infoCard.addView(drv);

        Map<String, String> vk = GameSettings.vkInfo(a);
        String vkMeta = vk.isEmpty()
                ? "Detected automatically per device (Turnip on Adreno, System Vulkan on Mali/others)."
                : String.format(Locale.ROOT, "Vendor: %s  |  Vulkan: %s  |  Mode: %s",
                        nonEmpty(vk.get("vendor"), "Auto"),
                        nonEmpty(vk.get("api"), "1.1+"),
                        nonEmpty(vk.get("mode"), "Normal"));
        infoCard.addView(text(a, vkMeta, 12, SUB, false));

        heading(a, "Launcher & Session");
        actionCard(a, "Open Setup & Driver Manager",
                "Close game session and return to Eastward Custom Setup.",
                () -> {
                    Intent it = new Intent(a, SetupActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    a.startActivity(it);
                    a.finish();
                    Process.killProcess(Process.myPid());
                });

        if (!confirmQuit) {
            actionCard(a, "Quit Eastward",
                    "Close the game cleanly and stop the runtime process.",
                    () -> {
                        confirmQuit = true;
                        redraw();
                    });
        } else {
            LinearLayout qc = card(a);
            qc.addView(text(a, "Close Eastward now?", 15, TEXT, true));
            TextView sub = text(a, "Make sure you saved your progress inside the game first.", 13, SUB, false);
            sub.setPadding(0, dp(a, 4), 0, dp(a, 10));
            qc.addView(sub);

            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView yes = pillButton(a, "Quit Now", true, () -> {
                a.finishAffinity();
                Process.killProcess(Process.myPid());
            });
            yes.setBackground(round(DANGER, dp(a, 14), 0, 0));
            yes.setTextColor(0xFF1A0909);

            TextView cancel = pillButton(a, "Cancel", false, () -> {
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
        titleRow(a, c, "Controller Preset", null);
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
        String tag = restartNeeded.contains(key) ? "Applies next start" : null;
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
                restartNeeded.contains(key) ? "Applies next start" : null,
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
