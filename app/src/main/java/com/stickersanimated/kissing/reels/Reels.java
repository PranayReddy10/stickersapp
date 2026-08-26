package com.stickersanimated.kissing.reels;

import android.content.Context;

import com.stickersanimated.kissing.Manager.PrefManager;

/** Whether the app shows reels at all, as the panel's Settings page decides. */
public final class Reels {

    /** Must match ADMIN_REELS_ENABLED, served by the settings API. */
    private static final String KEY = "ADMIN_REELS_ENABLED";

    private Reels() {
    }

    /**
     * On unless the panel says otherwise. An install that has never seen the setting -
     * an old panel, or a first run before the settings call lands - keeps its reels
     * rather than losing a tab it already had.
     */
    public static boolean enabled(Context context) {
        final String value = new PrefManager(context.getApplicationContext()).getString(KEY);
        if (value == null) {
            return true;
        }
        final String trimmed = value.trim();
        return !("FALSE".equalsIgnoreCase(trimmed) || "0".equals(trimmed)
                || "OFF".equalsIgnoreCase(trimmed) || "NO".equalsIgnoreCase(trimmed));
    }
}
