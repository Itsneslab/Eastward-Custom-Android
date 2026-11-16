package com.eastward.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Custom non-blinking Eastward touch controller + interactive drag/resize layout editor.
 * Keeps the exact JNI surface required by `front_android.c` and `haptics_android.c`.
 */
public final class Gamepad extends View {
    static native void nativeTouchPad(long buttons, int lx, int ly, int rx, int ry);
    static native void nativeGameTouch(int[] ids, float[] xs, float[] ys, int n);
    static native void nativeShake(boolean on);
    static native int nativeOpMode();
    static native int nativeDefaultLayout();
    static native void nativeLog(String s);
    static native String nativeEnv(String name);
    static native long nativeFrames();
    static native boolean nativePause(boolean on);

    static final int B_A = 0, B_B = 1, B_X = 2, B_Y = 3, B_LS = 4, B_RS = 5, B_L = 6, B_R = 7, B_ZL = 8, B_ZR = 9,
            B_PLUS = 10, B_MINUS = 11, B_LEFT = 12, B_UP = 13, B_RIGHT = 14, B_DOWN = 15;
    static final String[] NAMES = {"A", "B", "X", "Y", "L3", "R3", "L", "R", "ZL", "ZR", "+", "−", null, null, null, null};
    static final String[] HINTS = {"Act", "Back", "Bomb", "Hit", "L3", "R3", "Bag", "Gun", "ZL", "Swap", "Pause", "Map"};

    // Drag-and-drop / scale-editable control slots
    static final int S_A = 0, S_B = 1, S_X = 2, S_Y = 3, S_L = 4, S_R = 5, S_ZL = 6, S_ZR = 7,
            S_PLUS = 8, S_MINUS = 9, S_STICK = 10, S_DPAD = 11, S_COUNT = 12;
    static final int[] SLOT_BTN = {B_A, B_B, B_X, B_Y, B_L, B_R, B_ZL, B_ZR, B_PLUS, B_MINUS, -1, -1};
    static final String[] SLOT_LABEL = {"A", "B", "X", "Y", "L", "R", "ZL", "ZR", "+", "−", "Stick", "D-Pad"};

    static final int PRESET_RPG = 0, PRESET_2D = 1, PRESET_3D = 2, PRESET_TAP = 3;
    static final String[] PRESET_NAMES = {"RPG", "2D", "3D", "Tap"};

    private static final int C_NONE = 0, C_STICK = 1, C_DPAD = 2, C_SLOT = 3, C_SWAP = 4, C_MENU = 5, C_GAME = 6;
    private static final float K_DEAD = 0.10f, K_FULL = 0.80f;

    final float dp;
    final SharedPreferences prefs;
    int preset;
    int opacityPct;
    float globalScale;
    boolean padOff;
    boolean autoHideOnPad;
    boolean editMode;
    int editSel = -1;
    int editPtr = -1;
    float editLastX, editLastY;

    // Custom normalized center (0..1 of view size) and per-slot scale per preset
    final float[] customNx = new float[S_COUNT];
    final float[] customNy = new float[S_COUNT];
    final float[] customSc = new float[S_COUNT];

    final long[] owner = new long[64];
    final boolean[] down = new boolean[64];
    final float[] px = new float[64], py = new float[64];
    final int[] game = new int[16];
    int[] stick = {-1, -1};
    float[] stickX = new float[2], stickY = new float[2];
    float rcx, rcy;
    long activeBits;
    boolean swapHeld, menuHeld, menuGo;
    int hapticSticks;

    float insL, insR, insT, insB;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint subText = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint editPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static volatile Gamepad view;
    static PopupWindow popup;
    static Context ctx;
    static Activity hostActivity;

    // Menu state (modal View-based slide-in overlay via GameMenu)
    boolean menuOpen;

    // FPS overlay
    String fpsStr;
    long fpsFrames, fpsAt;
    boolean fpsOn;
    Paint fpsPaint;
    final Handler uiH = new Handler(Looper.getMainLooper());
    final Runnable fpsTickRun = new Runnable() { public void run() { fpsTick(); } };

    // Pre-loaded crisp Eastward vector SVG icons (rendered into 128x128 RGBA PNGs)
    android.graphics.Bitmap iconA, iconB, iconX, iconY, iconL, iconR, iconZr, iconPlus, iconMinus;
    final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final android.graphics.PorterDuffColorFilter cfPressed = new android.graphics.PorterDuffColorFilter(0xF518140C, android.graphics.PorterDuff.Mode.SRC_IN);
    int cachedFilterOpacity = -1;
    android.graphics.PorterDuffColorFilter cfPrimary, cfIdle;
    final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint badgeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint badgeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    final android.graphics.RectF iconRect = new android.graphics.RectF();
    final android.graphics.RectF badgeRect = new android.graphics.RectF();

    // Auto-fading menu icon when hardware gamepad connected
    static final long MENU_SHOW_MS = 3000, MENU_FADE_MS = 600;
    long menuShownAt = -MENU_SHOW_MS - MENU_FADE_MS;
    final Runnable menuFadeRun = new Runnable() {
        public void run() {
            invalidate();
            if (padOff && menuAlpha() > 0) postDelayed(this, 50);
        }
    };

    // Screen-idle auto-dim: lowers opacity of all controls after 8s untouched; wakes immediately on any touch
    static final long IDLE_DIM_AFTER_MS = 8000, IDLE_FADE_MS = 600;
    static final float IDLE_MIN_MULT = 0.16f;
    long lastTouchAt = android.os.SystemClock.uptimeMillis();
    final Runnable idleFadeRun = new Runnable() {
        public void run() {
            if (editMode || menuOpen || padOff) return;
            long idle = android.os.SystemClock.uptimeMillis() - lastTouchAt;
            invalidate();
            if (idle < IDLE_DIM_AFTER_MS) {
                postDelayed(this, IDLE_DIM_AFTER_MS - idle + 16);
            } else if (idle < IDLE_DIM_AFTER_MS + IDLE_FADE_MS) {
                postDelayed(this, 32);
            }
        }
    };

    void wakeControls() {
        lastTouchAt = android.os.SystemClock.uptimeMillis();
        removeCallbacks(idleFadeRun);
        if (!editMode && !menuOpen && !padOff) {
            postDelayed(idleFadeRun, IDLE_DIM_AFTER_MS + 16);
        }
        invalidate();
    }

    float idleAlphaMult() {
        if (editMode || menuOpen || padOff) return 1.0f;
        long idle = android.os.SystemClock.uptimeMillis() - lastTouchAt;
        if (idle < IDLE_DIM_AFTER_MS) return 1.0f;
        long over = idle - IDLE_DIM_AFTER_MS;
        if (over >= IDLE_FADE_MS) return IDLE_MIN_MULT;
        float t = over / (float) IDLE_FADE_MS;
        return 1.0f - (1.0f - IDLE_MIN_MULT) * t;
    }

    static android.graphics.Bitmap loadIconAsset(Context c, String name) {
        try (java.io.InputStream is = c.getAssets().open("icons/" + name)) {
            return android.graphics.BitmapFactory.decodeStream(is);
        } catch (Throwable t) {
            return null;
        }
    }

    Gamepad(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        prefs = c.getSharedPreferences("eastward_pad", Context.MODE_PRIVATE);
        Arrays.fill(game, -1);

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.5f * dp);
        fill.setStyle(Paint.Style.FILL);
        sheenPaint.setStyle(Paint.Style.STROKE);
        sheenPaint.setStrokeWidth(1f * dp);
        badgeFill.setStyle(Paint.Style.FILL);
        badgeLine.setStyle(Paint.Style.STROKE);
        badgeLine.setStrokeWidth(1.2f * dp);
        badgeText.setTextAlign(Paint.Align.CENTER);
        badgeText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        subText.setTextAlign(Paint.Align.CENTER);
        subText.setTypeface(Typeface.DEFAULT);
        editPaint.setStyle(Paint.Style.STROKE);
        editPaint.setStrokeWidth(2f * dp);

        iconA = loadIconAsset(c, "a.png");
        iconB = loadIconAsset(c, "b.png");
        iconX = loadIconAsset(c, "x.png");
        iconY = loadIconAsset(c, "y.png");
        iconL = loadIconAsset(c, "l.png");
        iconR = loadIconAsset(c, "r.png");
        iconZr = loadIconAsset(c, "zr.png");
        iconPlus = loadIconAsset(c, "plus.png");
        iconMinus = loadIconAsset(c, "minus.png");

        preset = prefs.getInt("preset", PRESET_RPG);
        if (preset < 0 || preset > PRESET_TAP) preset = PRESET_RPG;
        if (preset == PRESET_TAP && !prefs.getBoolean("tap_confirmed", false)) preset = PRESET_RPG;
        autoHideOnPad = prefs.getBoolean("auto_hide_pad", false);
        opacityPct = prefs.getInt("opacity", 75);
        globalScale = prefs.getFloat("scale", 1.0f);
        loadSlotOverrides();
        setHapticFeedbackEnabled(true);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        if (android.os.Build.VERSION.SDK_INT >= 28 && in.getDisplayCutout() != null) {
            android.view.DisplayCutout dc = in.getDisplayCutout();
            insL = dc.getSafeInsetLeft();
            insR = dc.getSafeInsetRight();
            insT = dc.getSafeInsetTop();
            insB = dc.getSafeInsetBottom();
        }
        invalidate();
        return in;
    }

    float baseU() {
        float h = getHeight();
        return h > 0 ? dp * Math.min(h / dp / 430f, 1.35f) * globalScale : dp;
    }

    // ---- Preset Coordinates (px) ----
    float defX(int slot) {
        float w = getWidth(), h = getHeight();
        float s = Math.min(h, w * 0.62f);
        float u = baseU();
        if (preset == PRESET_RPG) {
            switch (slot) {
                case S_STICK: return Math.max(insL + 0.22f * s, 0.20f * s);
                case S_DPAD:  return Math.max(insL + 0.45f * s, 0.42f * s);
                case S_Y:     return w - insR - 0.22f * s;
                case S_B:     return w - insR - 0.42f * s;
                case S_X:     return w - insR - 0.39f * s;
                case S_A:     return w - insR - 0.20f * s;
                case S_ZR:    return w - insR - 0.16f * s;
                case S_R:     return w - insR - 0.33f * s;
                case S_L:     return insL + 0.12f * s;
                case S_ZL:    return insL + 0.27f * s;
                case S_PLUS:  return w - insR - 0.12f * s;
                case S_MINUS: return w - insR - 0.26f * s;
            }
        }
        // Standard 2D / 3D Switch layout (non-overlapping top center & left controls)
        float cL = insL * 0.5f, cR = w - insR * 0.5f;
        float fCx = cR - 86 * u, fGap = 54 * u;
        float mid = (insL + w - insR) * 0.5f;
        switch (slot) {
            case S_A:     return fCx + fGap;
            case S_B:     return fCx;
            case S_X:     return fCx;
            case S_Y:     return fCx - fGap;
            case S_STICK: return cL + 84 * u;
            case S_DPAD:  return cL + 234 * u;
            case S_ZL:    return insL + 44 * u;
            case S_L:     return insL + 44 * u;
            case S_ZR:    return w - insR - 44 * u;
            case S_R:     return w - insR - 44 * u;
            case S_PLUS:  return mid + 135 * u;
            case S_MINUS: return mid - 135 * u;
        }
        return w * 0.5f;
    }

    float defY(int slot) {
        float w = getWidth(), h = getHeight();
        float s = Math.min(h, w * 0.62f);
        float u = baseU();
        if (preset == PRESET_RPG) {
            switch (slot) {
                case S_STICK: return h - insB - 0.29f * s;
                case S_DPAD:  return h - insB - 0.15f * s;
                case S_Y:     return h - insB - 0.22f * s;
                case S_B:     return h - insB - 0.16f * s;
                case S_X:     return h - insB - 0.37f * s;
                case S_A:     return h - insB - 0.45f * s;
                case S_ZR:    return h - insB - 0.65f * s;
                case S_R:     return h - insB - 0.61f * s;
                case S_L:     return insT + 0.12f * s;
                case S_ZL:    return insT + 0.12f * s;
                case S_PLUS:  return insT + 0.10f * s;
                case S_MINUS: return insT + 0.10f * s;
            }
        }
        float cB = h - insB;
        float fCy = cB - 86 * u, fGap = 54 * u;
        switch (slot) {
            case S_A:     return fCy;
            case S_B:     return fCy + fGap;
            case S_X:     return fCy - fGap;
            case S_Y:     return fCy;
            case S_STICK: return cB - 86 * u;
            case S_DPAD:  return cB - 66 * u;
            case S_ZL:    return insT + 38 * u;
            case S_L:     return insT + 106 * u;
            case S_ZR:    return insT + 38 * u;
            case S_R:     return insT + 106 * u;
            case S_PLUS:  return insT + 26 * u;
            case S_MINUS: return insT + 26 * u;
        }
        return h * 0.5f;
    }

    float defR(int slot) {
        float w = getWidth(), h = getHeight();
        float s = Math.min(h, w * 0.62f);
        float u = baseU();
        if (preset == PRESET_RPG) {
            switch (slot) {
                case S_STICK: return 0.16f * s * globalScale;
                case S_DPAD:  return 0.105f * s * globalScale;
                case S_Y:     return 0.092f * s * globalScale;
                case S_A:
                case S_X:     return 0.066f * s * globalScale;
                case S_B:     return 0.062f * s * globalScale;
                case S_R:
                case S_ZR:
                case S_L:
                case S_ZL:    return 0.052f * s * globalScale;
                default:      return 0.048f * s * globalScale;
            }
        }
        switch (slot) {
            case S_STICK: return 68 * u;
            case S_DPAD:  return 45 * u;
            case S_ZL:
            case S_ZR:    return 24 * u;
            case S_L:
            case S_R:     return 22 * u;
            case S_PLUS:
            case S_MINUS: return 21 * u;
            default:      return 26 * u;
        }
    }

    boolean slotVisible(int slot) {
        if (preset == PRESET_TAP) return false;
        if (preset == PRESET_RPG && slot == S_ZL) return false;
        if (preset == PRESET_2D && (slot == S_ZL || slot == S_ZR)) return false;
        return true;
    }

    float slotX(int slot) {
        return customNx[slot] >= 0 ? customNx[slot] * getWidth() : defX(slot);
    }

    float slotY(int slot) {
        return customNy[slot] >= 0 ? customNy[slot] * getHeight() : defY(slot);
    }

    float slotR(int slot) {
        return defR(slot) * customSc[slot];
    }

    float swapCx() { return (insL + getWidth() - insR) * 0.5f + 22 * baseU(); }
    float swapCy() { return insT + 22 * baseU(); }
    float menuCx() { return (insL + getWidth() - insR) * 0.5f - 22 * baseU(); }
    float menuCy() { return swapCy(); }
    float toolR()  { return 15 * baseU(); }

    void loadSlotOverrides() {
        String p = "p" + preset + "_";
        for (int i = 0; i < S_COUNT; i++) {
            customNx[i] = prefs.getFloat(p + "nx_" + i, -1f);
            customNy[i] = prefs.getFloat(p + "ny_" + i, -1f);
            customSc[i] = prefs.getFloat(p + "sc_" + i, 1.0f);
        }
    }

    void saveSlotOverrides() {
        SharedPreferences.Editor ed = prefs.edit();
        String p = "p" + preset + "_";
        for (int i = 0; i < S_COUNT; i++) {
            ed.putFloat(p + "nx_" + i, customNx[i]);
            ed.putFloat(p + "ny_" + i, customNy[i]);
            ed.putFloat(p + "sc_" + i, customSc[i]);
        }
        ed.apply();
    }

    void resetPresetLayout() {
        for (int i = 0; i < S_COUNT; i++) {
            customNx[i] = -1f;
            customNy[i] = -1f;
            customSc[i] = 1.0f;
        }
        saveSlotOverrides();
        invalidate();
    }

    void adjustSelectedScale(float delta) {
        if (editSel < 0 || editSel >= S_COUNT) return;
        customSc[editSel] = Math.max(0.6f, Math.min(1.6f, customSc[editSel] + delta));
        saveSlotOverrides();
        invalidate();
    }

    void setOpacity(int pct) {
        opacityPct = Math.max(15, Math.min(100, pct));
        prefs.edit().putInt("opacity", opacityPct).apply();
        wakeControls();
    }

    void setGlobalScale(float sc) {
        globalScale = Math.max(0.65f, Math.min(1.35f, sc));
        prefs.edit().putFloat("scale", globalScale).apply();
        wakeControls();
    }

    void setPreset(int p) {
        resetTouches();
        preset = Math.max(0, Math.min(PRESET_TAP, p));
        prefs.edit().putInt("preset", preset).putBoolean("tap_confirmed", preset == PRESET_TAP).apply();
        loadSlotOverrides();
        wakeControls();
    }

    // ---- Edit-mode toolbar rects ----
    RectF editBtn(int idx) {
        float u = baseU();
        float w = 78 * u, h = 34 * u, gap = 10 * u;
        float total = 4 * w + 3 * gap;
        float x0 = (getWidth() - total) * 0.5f + idx * (w + gap);
        float y0 = insT + 14 * u;
        return new RectF(x0, y0, x0 + w, y0 + h);
    }

    static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x0 - x1, y0 - y1);
    }

    int hitSlot(float x, float y, float padMult) {
        int best = -1;
        float bestRatio = 1f;
        for (int s = 0; s < S_COUNT; s++) {
            if (!slotVisible(s)) continue;
            float r = slotR(s) * padMult;
            float d = dist(x, y, slotX(s), slotY(s)) / Math.max(1f, r);
            if (d <= 1f && d < bestRatio) {
                bestRatio = d;
                best = s;
            }
        }
        return best;
    }

    long dpadBitsAt(float x, float y) {
        float dx = x - slotX(S_DPAD), dy = y - slotY(S_DPAD);
        if (Math.hypot(dx, dy) < slotR(S_DPAD) * 0.22f) return 0;
        double a = Math.toDegrees(Math.atan2(-dy, dx));
        long m = 0;
        if (a > -67.5 && a < 67.5) m |= 1L << B_RIGHT;
        if (a > 22.5 && a < 157.5) m |= 1L << B_UP;
        if (a > 112.5 || a < -112.5) m |= 1L << B_LEFT;
        if (a < -22.5 && a > -157.5) m |= 1L << B_DOWN;
        return m;
    }

    float rStickR() { return 58f * baseU(); }
    boolean inRightArea(float x) { return preset == PRESET_3D && x >= getWidth() * 0.66f; }

    float[] knob(int i) {
        if (stick[i] < 0) return new float[]{0, 0};
        float cx = i == 0 ? slotX(S_STICK) : rcx;
        float cy = i == 0 ? slotY(S_STICK) : rcy;
        float r = i == 0 ? slotR(S_STICK) : rStickR();
        float dx = stickX[i] - cx, dy = stickY[i] - cy;
        float d = (float) Math.hypot(dx, dy);
        if (d > r) { dx *= r / d; dy *= r / d; }
        return new float[]{dx, dy};
    }

    boolean inSwap(float x, float y) { return dist(x, y, swapCx(), swapCy()) <= toolR() * 1.6f; }
    boolean inMenu(float x, float y) { return menuAlpha() > 0 && dist(x, y, menuCx(), menuCy()) <= toolR() * 1.5f; }
    float menuAlpha() { return !padOff || menuHeld ? 1f : menuAlphaAfter(android.os.SystemClock.uptimeMillis() - menuShownAt); }
    float menuAlphaAfter(long ms) { return ms < MENU_SHOW_MS ? 1f : ms < MENU_SHOW_MS + MENU_FADE_MS ? 1f - (ms - MENU_SHOW_MS) / (float) MENU_FADE_MS : 0f; }
    void menuReveal() { if (!padOff) return; menuShownAt = android.os.SystemClock.uptimeMillis(); removeCallbacks(menuFadeRun); post(menuFadeRun); }

    void recompute() {
        long bits = 0;
        boolean swap = false, menu = false;
        for (int p = 0; p < 64; p++) {
            if (!down[p]) continue;
            int c = (int) (owner[p] & 0xff);
            if (c == C_SLOT) {
                int slot = (int) (owner[p] >> 8);
                if (slot >= 0 && slot < S_COUNT && SLOT_BTN[slot] >= 0) bits |= 1L << SLOT_BTN[slot];
            } else if (c == C_DPAD) {
                bits |= dpadBitsAt(px[p], py[p]);
            } else if (c == C_SWAP) {
                swap = true;
            } else if (c == C_MENU) {
                menu = true;
            }
        }
        int[] ax = new int[4];
        for (int i = 0; i < 2; i++) {
            float[] o = knob(i);
            float r = i == 0 ? slotR(S_STICK) : rStickR();
            float d = (float) Math.hypot(o[0], o[1]), dz = r * K_DEAD;
            if (d <= dz) continue;
            float m = Math.min((d - dz) / (r * K_FULL - dz), 1f) / d;
            ax[2 * i] = Math.round(o[0] * m * 32767);
            ax[2 * i + 1] = Math.round(-o[1] * m * 32767);
        }
        long pressed = bits & ~activeBits;
        int gripping = (stick[0] >= 0 ? 1 : 0) | (stick[1] >= 0 ? 2 : 0);
        if (pressed != 0 || (swap && !swapHeld) || (menu && !menuHeld) || (gripping & ~hapticSticks) != 0) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        hapticSticks = gripping;
        activeBits = bits;
        swapHeld = swap;
        menuHeld = menu;
        nativeTouchPad(bits, ax[0], ax[1], ax[2], ax[3]);
        invalidate();
    }

    void pushGame() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (w / h > 16f / 9f) w = h * 16f / 9f;
        else h = w * 9f / 16f;
        float x0 = (getWidth() - w) * 0.5f, y0 = (getHeight() - h) * 0.5f;
        int[] ids = new int[16];
        float[] xs = new float[16], ys = new float[16];
        int n = 0;
        for (int i = 0; i < 16; i++) {
            if (game[i] >= 0) {
                ids[n] = i;
                xs[n] = Math.min(Math.max((px[game[i]] - x0) / w, 0f), 0.999f);
                ys[n] = Math.min(Math.max((py[game[i]] - y0) / h, 0f), 0.999f);
                n++;
            }
        }
        nativeGameTouch(ids, xs, ys, n);
    }

    boolean gameBegin(int p) {
        for (int i = 0; i < 16; i++) {
            if (game[i] < 0) {
                game[i] = p;
                owner[p] = C_GAME | ((long) i << 8);
                return true;
            }
        }
        return false;
    }

    void begin(int p, float x, float y) {
        down[p] = true;
        px[p] = x;
        py[p] = y;
        owner[p] = C_NONE;

        boolean menuThere = inMenu(x, y);
        menuReveal();
        if (menuThere) {
            owner[p] = C_MENU;
            return;
        }
        if (padOff || preset == PRESET_TAP) {
            if (inSwap(x, y) && !padOff) owner[p] = C_SWAP;
            else if (gameBegin(p)) pushGame();
            return;
        }
        if (inSwap(x, y)) {
            owner[p] = C_SWAP;
            return;
        }
        int s = hitSlot(x, y, 1.28f);
        if (s == S_STICK && stick[0] < 0) {
            owner[p] = C_STICK;
            stick[0] = p;
            stickX[0] = x;
            stickY[0] = y;
        } else if (s == S_DPAD) {
            owner[p] = C_DPAD;
        } else if (s >= 0 && SLOT_BTN[s] >= 0) {
            owner[p] = C_SLOT | ((long) s << 8);
        } else if (stick[1] < 0 && inRightArea(x)) {
            owner[p] = C_STICK | (1L << 8);
            stick[1] = p;
            rcx = x;
            rcy = y;
            stickX[1] = x;
            stickY[1] = y;
        } else if (gameBegin(p)) {
            pushGame();
        }
    }

    void move(int p, float x, float y) {
        if (!down[p]) return;
        px[p] = x;
        py[p] = y;
        int c = (int) (owner[p] & 0xff);
        if (c == C_STICK) {
            int idx = (int) (owner[p] >> 8);
            stickX[idx] = x;
            stickY[idx] = y;
            if (idx == 1) {
                float r = rStickR(), d = dist(x, y, rcx, rcy);
                if (d > r) {
                    rcx = x - (x - rcx) * r / d;
                    rcy = y - (y - rcy) * r / d;
                }
            }
        }
    }

    boolean end(int p, boolean ended) {
        if (!down[p]) return false;
        boolean swap = ended && (owner[p] & 0xff) == C_SWAP && inSwap(px[p], py[p]);
        if (ended && (owner[p] & 0xff) == C_MENU && inMenu(px[p], py[p])) menuGo = true;
        if ((owner[p] & 0xff) == C_GAME) game[(int) (owner[p] >> 8) & 15] = -1;
        for (int i = 0; i < 2; i++) if (stick[i] == p) stick[i] = -1;
        down[p] = false;
        owner[p] = 0;
        return swap;
    }

    void resetTouches() {
        for (int p = 0; p < 64; p++) end(p, false);
        Arrays.fill(game, -1);
        pushGame();
        recompute();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (menuOpen) return true;
        wakeControls();
        if (editMode) return editTouch(e);

        int a = e.getActionMasked(), ai = e.getActionIndex();
        boolean swap = false, gameMoved = false;
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                begin(e.getPointerId(ai) & 63, e.getX(ai), e.getY(ai));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int p = e.getPointerId(i) & 63;
                    move(p, e.getX(i), e.getY(i));
                    if ((owner[p] & 0xff) == C_GAME) gameMoved = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int p = e.getPointerId(ai) & 63;
                boolean g = (owner[p] & 0xff) == C_GAME;
                swap = end(p, true);
                if (g) pushGame();
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                resetTouches();
                break;
        }
        if (gameMoved) pushGame();
        recompute();
        if (swap) setPreset(preset == PRESET_TAP ? PRESET_RPG : (preset + 1) % 3);
        if (menuGo) {
            menuGo = false;
            openPauseMenu(true);
        }
        return true;
    }

    boolean editTouch(MotionEvent e) {
        int a = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        if (a == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < 4; i++) {
                if (editBtn(i).contains(x, y)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (i == 0) adjustSelectedScale(-0.10f);
                    else if (i == 1) adjustSelectedScale(0.10f);
                    else if (i == 2) resetPresetLayout();
                    else if (i == 3) {
                        editMode = false;
                        editSel = -1;
                        saveSlotOverrides();
                        if (preset == PRESET_TAP) setPreset(PRESET_RPG);
                        if (padOff || autoHideOnPad) {
                            autoHideOnPad = false;
                            padOff = false;
                            prefs.edit().putBoolean("auto_hide_pad", false).apply();
                        }
                        nativePause(false);
                        wakeControls();
                    }
                    return true;
                }
            }
            int s = hitSlot(x, y, 1.35f);
            if (s >= 0) {
                editSel = s;
                editPtr = e.getPointerId(0);
                editLastX = x;
                editLastY = y;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
            }
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE && editSel >= 0 && e.getPointerId(0) == editPtr) {
            float dx = x - editLastX, dy = y - editLastY;
            editLastX = x;
            editLastY = y;
            float w = Math.max(1f, getWidth()), h = Math.max(1f, getHeight());
            float nx = (slotX(editSel) + dx) / w;
            float ny = (slotY(editSel) + dy) / h;
            customNx[editSel] = Math.max(0.04f, Math.min(0.96f, nx));
            customNy[editSel] = Math.max(0.06f, Math.min(0.94f, ny));
            invalidate();
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            editPtr = -1;
            saveSlotOverrides();
            return true;
        }
        return true;
    }

    void enterEditMode() {
        openPauseMenu(false);
        nativePause(true);
        editMode = true;
        editSel = S_Y;
        invalidate();
    }

    // ---- Drawing ----
    void fpsTick() {
        long n = nativeFrames(), t = android.os.SystemClock.elapsedRealtime();
        if (fpsAt != 0 && t > fpsAt) {
            fpsStr = String.format(Locale.ROOT, "%.0f fps", (n - fpsFrames) * 1000.0 / (t - fpsAt));
            invalidate();
        }
        fpsFrames = n;
        fpsAt = t;
        uiH.postDelayed(fpsTickRun, 500);
    }

    public void fpsSet(boolean on) {
        uiH.removeCallbacks(fpsTickRun);
        fpsOn = on;
        fpsStr = null;
        fpsAt = 0;
        if (on) fpsTick();
        else invalidate();
    }

    void drawFps(Canvas c) {
        if (fpsStr == null) return;
        if (fpsPaint == null) {
            fpsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fpsPaint.setTypeface(Typeface.DEFAULT_BOLD);
            fpsPaint.setShadowLayer(3, 1, 1, Color.BLACK);
        }
        float u = baseU();
        fpsPaint.setTextSize(15 * u);
        fpsPaint.setColor(Color.argb(235, 160, 255, 180));
        c.drawText(fpsStr, Math.max(12 * u, insL + 10 * u), Math.max(24 * u, insT + 20 * u), fpsPaint);
    }

    android.graphics.Bitmap iconForLabel(String label) {
        if ("A".equals(label)) return iconA;
        if ("B".equals(label)) return iconB;
        if ("X".equals(label)) return iconX;
        if ("Y".equals(label)) return iconY;
        if ("L".equals(label)) return iconL;
        if ("R".equals(label)) return iconR;
        if ("ZR".equals(label)) return iconZr;
        if ("+".equals(label)) return iconPlus;
        if ("-".equals(label) || "\u2212".equals(label)) return iconMinus;
        return null;
    }

    int alphaByte(int base) {
        return Math.max(12, Math.min(255, Math.round(base * opacityPct / 100f)));
    }

    void drawButton(Canvas c, float cx, float cy, float r, String label, String hint, boolean on, boolean highlighted) {
        boolean isPrimary = "Y".equals(label) && preset == PRESET_RPG;
        android.graphics.Bitmap icon = (preset == PRESET_RPG) ? iconForLabel(label) : null;

        int aIdle = alphaByte(isPrimary ? 168 : 145);
        int aLine = alphaByte(isPrimary ? 245 : 195);

        if (on) {
            fill.setColor(Color.argb(230, 246, 198, 52));
        } else if (isPrimary) {
            fill.setColor(Color.argb(aIdle, 56, 44, 16));
        } else {
            fill.setColor(Color.argb(aIdle, 24, 24, 30));
        }

        if (highlighted) {
            line.setColor(Color.argb(255, 255, 224, 110));
            line.setStrokeWidth(2.8f * dp);
        } else if (on) {
            line.setColor(Color.argb(245, 255, 242, 178));
            line.setStrokeWidth(2.2f * dp);
        } else if (isPrimary) {
            line.setColor(Color.argb(aLine, 245, 196, 40));
            line.setStrokeWidth(2.1f * dp);
        } else {
            line.setColor(Color.argb(aLine, 250, 212, 74));
            line.setStrokeWidth(1.5f * dp);
        }

        c.drawCircle(cx, cy, r, fill);
        if (!on && r > 12f * dp) {
            sheenPaint.setColor(Color.argb(alphaByte(28), 255, 248, 220));
            c.drawCircle(cx, cy, r - 2.2f * dp, sheenPaint);
        }
        c.drawCircle(cx, cy, r, line);

        if (icon != null) {
            float ir = r * 0.56f;
            float iconCy = cy - r * 0.04f;
            iconRect.set(cx - ir, iconCy - ir, cx + ir, iconCy + ir);
            if (cachedFilterOpacity != opacityPct || cfPrimary == null) {
                cachedFilterOpacity = opacityPct;
                cfPrimary = new android.graphics.PorterDuffColorFilter(
                        Color.argb(alphaByte(252), 255, 244, 202), android.graphics.PorterDuff.Mode.SRC_IN);
                cfIdle = new android.graphics.PorterDuffColorFilter(
                        Color.argb(alphaByte(238), 248, 245, 234), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            iconPaint.setColorFilter(on ? cfPressed : (isPrimary ? cfPrimary : cfIdle));
            c.drawBitmap(icon, null, iconRect, iconPaint);

            // Mini keycap badge on corner (e.g. "Y" on Pan, "R" on Sword, "A" on Hand)
            String cap = "-".equals(label) ? "\u2212" : label;
            float bh = Math.max(12.5f * dp, r * 0.44f);
            float bw = cap.length() > 1 ? bh * 1.44f : bh;
            float bx = cx + r * 0.64f;
            float by = cy + r * 0.64f;
            badgeRect.set(bx - bw * 0.5f, by - bh * 0.5f, bx + bw * 0.5f, by + bh * 0.5f);
            float br = bh * 0.5f;

            badgeFill.setColor(on
                    ? Color.argb(245, 24, 20, 12)
                    : Color.argb(Math.max(175, alphaByte(235)), 16, 18, 24));
            badgeLine.setColor(on
                    ? Color.argb(250, 255, 242, 178)
                    : Color.argb(Math.max(160, alphaByte(230)), 245, 196, 40));
            c.drawRoundRect(badgeRect, br, br, badgeFill);
            c.drawRoundRect(badgeRect, br, br, badgeLine);

            badgeText.setTextSize(bh * 0.62f);
            badgeText.setColor(on ? Color.argb(255, 250, 212, 74) : Color.argb(Math.max(200, alphaByte(250)), 255, 224, 118));
            Paint.FontMetrics bfm = badgeText.getFontMetrics();
            c.drawText(cap, bx, by - (bfm.ascent + bfm.descent) * 0.5f, badgeText);
            return;
        }

        text.setColor(on ? Color.argb(240, 24, 20, 12) : Color.argb(alphaByte(232), 255, 248, 228));
        text.setTextSize(r * (label.length() > 1 ? 0.50f : 0.66f));
        Paint.FontMetrics fm = text.getFontMetrics();
        float labelY = cy - (fm.ascent + fm.descent) * 0.5f - (hint != null && preset == PRESET_RPG ? r * 0.14f : 0f);
        c.drawText(label, cx, labelY, text);

        if (hint != null && preset == PRESET_RPG) {
            subText.setColor(on ? Color.argb(200, 30, 24, 14) : Color.argb(alphaByte(165), 245, 212, 110));
            subText.setTextSize(r * 0.28f);
            c.drawText(hint, cx, cy + r * 0.52f, subText);
        }
    }

    static Path dpadArm(float cx, float cy, float r, float w, float in, double angle) {
        float[][] v = {{-w, -r}, {w, -r}, {w, -(in + w)}, {0, -in}, {-w, -(in + w)}};
        double cs = Math.cos(angle), sn = Math.sin(angle);
        Path p = new Path();
        for (int i = 0; i < 5; i++) {
            float x = (float) (cx + v[i][0] * cs - v[i][1] * sn);
            float y = (float) (cy + v[i][0] * sn + v[i][1] * cs);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    @Override
    protected void onDraw(Canvas c) {
        drawFps(c);
        if (menuOpen) return;

        float im = idleAlphaMult();
        int idleLayer = (im < 0.999f)
                ? c.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.max(20, Math.round(255 * im)))
                : -1;

        float u = baseU();
        if (!padOff || editMode) {
            if (preset != PRESET_TAP || editMode) {
                for (int s = 0; s < S_COUNT; s++) {
                    if (!slotVisible(s)) continue;
                    boolean sel = editMode && editSel == s;
                    float cx = slotX(s), cy = slotY(s), r = slotR(s);
                    if (s == S_STICK) {
                        fill.setColor(Color.argb(alphaByte(120), 22, 20, 18));
                        line.setColor(sel ? Color.argb(255, 255, 224, 110) : Color.argb(alphaByte(210), 250, 212, 74));
                        line.setStrokeWidth((sel ? 2.8f : 1.7f) * dp);
                        c.drawCircle(cx, cy, r, fill);
                        c.drawCircle(cx, cy, r, line);

                        sheenPaint.setColor(Color.argb(alphaByte(65), 250, 212, 74));
                        c.drawCircle(cx, cy, r * 0.72f, sheenPaint);

                        float[] k = knob(0);
                        float kr = r * 0.42f;
                        fill.setColor(stick[0] >= 0
                                ? Color.argb(235, 246, 198, 52)
                                : Color.argb(alphaByte(195), 245, 196, 48));
                        line.setColor(stick[0] >= 0
                                ? Color.argb(245, 255, 242, 178)
                                : Color.argb(alphaByte(225), 255, 232, 145));
                        line.setStrokeWidth(1.5f * dp);
                        c.drawCircle(cx + k[0], cy + k[1], kr, fill);
                        c.drawCircle(cx + k[0], cy + k[1], kr, line);
                    } else if (s == S_DPAD) {
                        int[] arms = {B_UP, B_RIGHT, B_DOWN, B_LEFT};
                        for (int i = 0; i < 4; i++) {
                            boolean on = (activeBits >> arms[i] & 1) != 0;
                            Path p = dpadArm(cx, cy, r, r * 0.34f, r * 0.22f, i * Math.PI / 2.0);
                            fill.setColor(on ? Color.argb(230, 246, 198, 52) : Color.argb(alphaByte(145), 24, 24, 30));
                            line.setColor(sel
                                    ? Color.argb(255, 255, 224, 110)
                                    : on
                                            ? Color.argb(245, 255, 242, 178)
                                            : Color.argb(alphaByte(195), 250, 212, 74));
                            line.setStrokeWidth((sel ? 2.6f : 1.5f) * dp);
                            c.drawPath(p, fill);
                            c.drawPath(p, line);
                        }
                        fill.setColor(Color.argb(alphaByte(150), 24, 24, 30));
                        line.setColor(Color.argb(alphaByte(140), 250, 212, 74));
                        line.setStrokeWidth(1.1f * dp);
                        c.drawCircle(cx, cy, r * 0.16f, fill);
                        c.drawCircle(cx, cy, r * 0.16f, line);
                    } else {
                        int b = SLOT_BTN[s];
                        boolean on = (activeBits >> b & 1) != 0;
                        drawButton(c, cx, cy, r, NAMES[b], HINTS[s], on, sel);
                    }
                }
            }
            if (!editMode) {
                drawButton(c, swapCx(), swapCy(), toolR(), PRESET_NAMES[preset], null, swapHeld, false);
            }
        }

        if (!editMode) {
            float ma = menuAlpha();
            if (ma > 0) {
                int sv = c.saveLayerAlpha(menuCx() - toolR() * 1.4f, menuCy() - toolR() * 1.4f,
                        menuCx() + toolR() * 1.4f, menuCy() + toolR() * 1.4f, (int) (255 * ma));
                drawButton(c, menuCx(), menuCy(), toolR(), "≡", null, menuHeld, false);
                c.restoreToCount(sv);
            }
        } else {
            drawEditBar(c, u);
        }

        if (idleLayer >= 0) c.restoreToCount(idleLayer);
    }

    void drawEditBar(Canvas c, float u) {
        String[] labels = {"Size −", "Size +", "Reset", "Save"};
        text.setTextSize(13 * u);
        Paint.FontMetrics fm = text.getFontMetrics();
        for (int i = 0; i < 4; i++) {
            RectF r = editBtn(i);
            fill.setColor(i == 3 ? Color.argb(235, 52, 168, 83) : Color.argb(220, 32, 36, 46));
            line.setColor(Color.argb(200, 255, 255, 255));
            line.setStrokeWidth(1.2f * dp);
            c.drawRoundRect(r, 10 * dp, 10 * dp, fill);
            c.drawRoundRect(r, 10 * dp, 10 * dp, line);
            text.setColor(Color.WHITE);
            c.drawText(labels[i], r.centerX(), r.centerY() - (fm.ascent + fm.descent) * 0.5f, text);
        }
        if (editSel >= 0) {
            text.setTextSize(12 * u);
            text.setColor(Color.argb(230, 140, 230, 255));
            String info = String.format(Locale.ROOT, "Drag any button • Selected: %s (%.0f%%)",
                    SLOT_LABEL[editSel], customSc[editSel] * 100f);
            c.drawText(info, getWidth() * 0.5f, editBtn(0).bottom + 18 * u, text);
        }
    }

    // ---- Menu & Controller Navigation ----
    void openPauseMenu(boolean open) {
        if (open == menuOpen) return;
        if (open) {
            resetTouches();
            nativePause(true);
            menuOpen = true;
            invalidate();
            GameMenu.show(hostActivity, this);
        } else {
            menuOpen = false;
            nativePause(false);
            wakeControls();
        }
    }

    public static void menuKey(final int k) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                if (view == null) return;
                if (k == 0) view.openPauseMenu(!view.menuOpen);
                else if (k == 6 && view.menuOpen) GameMenu.dismiss();
            }
        });
    }

    void setAutoHideOnPad(boolean on) {
        autoHideOnPad = on;
        prefs.edit().putBoolean("auto_hide_pad", on).apply();
        padsChanged();
        wakeControls();
    }

    void padsChanged() {
        boolean found = false;
        if (autoHideOnPad) {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice d = InputDevice.getDevice(id);
                if (d == null || d.isVirtual()) continue;
                int s = d.getSources();
                if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                        && d.getControllerNumber() > 0) {
                    boolean[] has = d.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B);
                    if (has.length >= 2 && (has[0] || has[1])) {
                        found = true;
                        break;
                    }
                }
            }
        }
        if (found != padOff) {
            padOff = found;
            if (padOff) resetTouches();
            invalidate();
        }
    }

    // ---- JNI Lifecycle hooks ----
    public static void start(final Activity a) {
        ctx = a.getApplicationContext();
        hostActivity = a;
        final Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            public void run() {
                View decor = a.getWindow().getDecorView();
                if (decor.getWindowToken() == null) {
                    h.postDelayed(this, 50);
                    return;
                }
                if (popup != null) {
                    try { popup.dismiss(); } catch (Throwable ignored) {}
                    popup = null;
                }
                view = new Gamepad(a);
                popup = new PopupWindow(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, false);
                popup.setTouchable(true);
                popup.setFocusable(false);
                popup.setClippingEnabled(false);
                if (android.os.Build.VERSION.SDK_INT >= 22) popup.setAttachedInDecor(false);
                popup.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
                view.wakeControls();

                InputManager im = (InputManager) a.getSystemService(Context.INPUT_SERVICE);
                if (im != null) {
                    im.registerInputDeviceListener(new InputManager.InputDeviceListener() {
                        public void onInputDeviceAdded(int id) { view.padsChanged(); }
                        public void onInputDeviceRemoved(int id) { view.padsChanged(); }
                        public void onInputDeviceChanged(int id) { view.padsChanged(); }
                    }, h);
                }
                view.padsChanged();
                shakeStart(a);
                if ("1".equals(nativeEnv("DM_SHOW_FPS"))) view.fpsSet(true);
                nativeLog("Eastward Custom gamepad started (" + PRESET_NAMES[view.preset] + ")");
            }
        });
    }

    public static void reset_() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                if (view != null) view.resetTouches();
            }
        });
    }

    public static void rumble(float level) {
        android.os.Vibrator v = null;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && d.getControllerNumber() > 0 && d.getVibrator() != null && d.getVibrator().hasVibrator()) {
                v = d.getVibrator();
                break;
            }
        }
        if (v == null && ctx != null) v = (android.os.Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (level <= 0) { v.cancel(); return; }
        int amp = v.hasAmplitudeControl() ? Math.max(1, Math.min(255, Math.round(level * 255)))
                : android.os.VibrationEffect.DEFAULT_AMPLITUDE;
        v.vibrate(android.os.VibrationEffect.createOneShot(1000, amp),
                new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_GAME).build());
    }

    static void shakeStart(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor acc = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc == null) return;
        final double thr = 1.5;
        sm.registerListener(new SensorEventListener() {
            double gx, gy, gz;
            long last = -1;
            boolean init, on;
            public void onSensorChanged(SensorEvent ev) {
                double x = ev.values[0] / 9.81, y = ev.values[1] / 9.81, z = ev.values[2] / 9.81;
                if (!init) { gx = x; gy = y; gz = z; init = true; }
                double g = Math.sqrt((x - gx) * (x - gx) + (y - gy) * (y - gy) + (z - gz) * (z - gz));
                gx += (x - gx) * 0.02; gy += (y - gy) * 0.02; gz += (z - gz) * 0.02;
                if (g > thr) last = ev.timestamp;
                boolean now = last >= 0 && ev.timestamp - last < 120000000L;
                if (!now && last >= 0) last = -1;
                if (now != on) { on = now; nativeShake(now); }
            }
            public void onAccuracyChanged(Sensor s, int a) {}
        }, acc, 16667, new Handler(Looper.getMainLooper()));
    }

    static String decodeUtf8(byte[] b) {
        return b == null ? "" : new String(b, StandardCharsets.UTF_8);
    }

    public static byte[] ask(final Activity a, byte[] title8, byte[] sub8, byte[] guide8, byte[] initial8, final int maxChars, int minChars) {
        final String title = decodeUtf8(title8), sub = decodeUtf8(sub8), guide = decodeUtf8(guide8), initial = decodeUtf8(initial8);
        final int min = maxChars > 0 ? Math.min(minChars, maxChars) : minChars;
        final String[] out = {null};
        final CountDownLatch done = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                LinearLayout box = new LinearLayout(a);
                box.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
                box.setPadding(pad, pad / 2, pad, 0);
                if (!sub.isEmpty()) {
                    TextView t = new TextView(a);
                    t.setText(sub);
                    box.addView(t);
                }
                final EditText in = new EditText(a);
                in.setSingleLine(true);
                in.setText(initial);
                in.setSelection(in.getText().length());
                if (!guide.isEmpty()) in.setHint(guide);
                if (maxChars > 0) in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxChars)});
                box.addView(in);
                AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(title.isEmpty() ? null : title)
                        .setView(box)
                        .setPositiveButton(android.R.string.ok, (di, w) -> { out[0] = in.getText().toString(); done.countDown(); })
                        .setNegativeButton(android.R.string.cancel, (di, w) -> done.countDown())
                        .setOnCancelListener(di -> done.countDown())
                        .create();
                d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                d.show();
                in.requestFocus();
                final Button ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
                ok.setEnabled(in.length() >= min);
                in.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    public void afterTextChanged(Editable s) { ok.setEnabled(s.length() >= min); }
                });
            }
        });
        try { done.await(); } catch (InterruptedException e) { return null; }
        return out[0] == null ? null : out[0].getBytes(StandardCharsets.UTF_8);
    }
}
