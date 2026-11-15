package com.eastward.custom;

import android.app.NativeActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * Full-screen landscape host activity for Eastward (010071B00F63A000).
 * Runs in the dedicated `:game` process loading `libdmhost_010071B00F63A000.so`.
 */
public class GameActivity extends NativeActivity {
    public static volatile GameActivity current;

    @Override
    protected void onCreate(Bundle b) {
        current = this;
        super.onCreate(b);
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) immersive();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        GameMenu.onActivityResult(this, req, res, data);
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    private void immersive() {
        int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
}
