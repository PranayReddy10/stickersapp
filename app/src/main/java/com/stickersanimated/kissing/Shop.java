package com.stickersanimated.kissing;

import android.content.Context;

import com.stickersanimated.kissing.Manager.PrefManager;

/**
 * The shop the panel can point this app at.
 *
 * <p>Everything here comes from the panel's Shop page, downloaded with the rest of the
 * settings, so the button can be turned on, renamed or moved without a new build.
 *
 * <p>The shop's API key is deliberately not among these settings and never reaches the
 * phone: when the app needs products it asks our own server, which holds the key and
 * asks the shop. Anything shipped in an APK can be read by anyone who unzips it.
 */
public final class Shop {

    /** Must match the keys served by the settings API. */
    private static final String BUTTON = "ADMIN_SHOP_BUTTON";
    private static final String URL = "ADMIN_SHOP_URL";
    private static final String NAME = "ADMIN_SHOP_NAME";

    private Shop() {
    }

    /**
     * Off unless the panel has said otherwise and given somewhere to go. The opposite
     * default to reels: an install talking to an older panel has no shop at all, so the
     * quiet answer is to show nothing rather than a menu item that leads nowhere.
     */
    public static boolean enabled(Context context) {
        final PrefManager prf = new PrefManager(context.getApplicationContext());
        final String on = value(prf.getString(BUTTON));
        if (!("TRUE".equalsIgnoreCase(on) || "1".equals(on)
                || "ON".equalsIgnoreCase(on) || "YES".equalsIgnoreCase(on))) {
            return false;
        }
        return url(context) != null;
    }

    /** Where the button goes, or null when the panel has not given a usable address. */
    public static String url(Context context) {
        final String url = value(new PrefManager(context.getApplicationContext()).getString(URL));
        if (url == null) {
            return null;
        }
        // A bare domain typed into the panel is still what was meant; a browser just
        // needs telling which scheme to use.
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.contains("://")) {
            return null;
        }
        return "https://" + url;
    }

    /** What the button says. Falls back to the app's own wording. */
    public static String label(Context context, String fallback) {
        final String name = value(new PrefManager(context.getApplicationContext()).getString(NAME));
        return name == null ? fallback : name;
    }

    /** PrefManager answers "" for a setting it has never been given. */
    private static String value(String raw) {
        if (raw == null) {
            return null;
        }
        final String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
