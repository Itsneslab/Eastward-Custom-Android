package com.eastward.custom;

import android.content.Context;

/**
 * Lightweight runtime ID | ENG UI localization helper backed by GameSettings (env.txt)
 * so SetupActivity (main process) and GameMenu (:game process) stay immediately in sync.
 */
public final class UiText {
    private UiText() {}

    public static boolean isId(Context c) {
        return "id".equalsIgnoreCase(GameSettings.get(c, "UI_LANG", "en"));
    }

    public static String lang(Context c) {
        return isId(c) ? "id" : "en";
    }

    public static void setLang(Context c, String lang) {
        GameSettings.set(c, "UI_LANG", "id".equalsIgnoreCase(lang) ? "id" : "en");
    }

    public static String tr(Context c, String en, String id) {
        return isId(c) ? id : en;
    }
}
