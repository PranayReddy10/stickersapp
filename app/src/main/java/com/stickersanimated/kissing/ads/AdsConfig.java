package com.stickersanimated.kissing.ads;

import android.content.Context;
import android.text.TextUtils;

import com.stickersanimated.kissing.Manager.PrefManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Reads the ad settings the panel pushes into {@link PrefManager} and turns them into a
 * waterfall: an ordered list of networks to try for a given format.
 *
 * <p>Supported keys, per format ({@code BANNER}, {@code NATIVE}, {@code INTERSTITIAL},
 * {@code REWARDED} - the rewarded type key is {@code ADMIN_REWARDED_AD_TYPE}):
 *
 * <pre>
 *   ADMIN_&lt;FORMAT&gt;_TYPE       primary network, or FALSE to disable the format.
 *                             A comma separated list is also accepted.
 *   ADMIN_&lt;FORMAT&gt;_ORDER      explicit waterfall, e.g. "ADMOB,MAX,FACEBOOK,UNITY".
 *   ADMIN_&lt;FORMAT&gt;_ADMOB_ID   AdMob unit id
 *   ADMIN_&lt;FORMAT&gt;_MAX_ID     AppLovin MAX unit id (falls back to the AdMob key, which is
 *                             what earlier builds of this app used for MAX)
 *   ADMIN_&lt;FORMAT&gt;_APPLOVIN_ID    AppLovin direct zone id (optional)
 *   ADMIN_&lt;FORMAT&gt;_FACEBOOK_ID    Meta Audience Network placement id
 *   ADMIN_&lt;FORMAT&gt;_UNITY_ID       Unity Ads placement id
 *
 *   ADMIN_AD_FALLBACK         TRUE (default) to auto-append every other configured
 *                             network after the primary one, FALSE to use only what
 *                             _ORDER / _TYPE lists.
 *   ADMIN_AD_TIMEOUT          seconds to wait for one network before moving to the next
 *                             (default 10).
 *   ADMIN_UNITY_GAME_ID       Unity Ads game id, required for the Unity network.
 * </pre>
 *
 * Nothing here is mandatory: an install that only has the legacy keys keeps behaving
 * exactly as before, except that a failing network now hands over to the next one.
 */
public final class AdsConfig {

    private static final String KEY_SUBSCRIBED = "SUBSCRIBED";
    private static final String KEY_FALLBACK_ENABLED = "ADMIN_AD_FALLBACK";
    private static final String KEY_TIMEOUT_SECONDS = "ADMIN_AD_TIMEOUT";
    private static final String KEY_UNITY_GAME_ID = "ADMIN_UNITY_GAME_ID";
    private static final String KEY_INTERSTITIAL_CLICKS = "ADMIN_INTERSTITIAL_CLICKS";
    private static final String KEY_NATIVE_LINES = "ADMIN_NATIVE_LINES";
    private static final String KEY_DOWNLOAD_AD_TYPE = "ADMIN_DOWNLOAD_AD_TYPE";

    private static final long DEFAULT_TIMEOUT_MS = 10_000L;
    private static final long MIN_TIMEOUT_MS = 3_000L;
    private static final long MAX_TIMEOUT_MS = 60_000L;

    private static final int DEFAULT_NATIVE_LINES = 3;
    private static final int MIN_NATIVE_LINES = 1;

    private final PrefManager pref;

    public AdsConfig(Context context) {
        this.pref = new PrefManager(context.getApplicationContext());
    }

    /** Paying users never see ads. */
    public boolean isSubscribed() {
        return "TRUE".equalsIgnoreCase(string(KEY_SUBSCRIBED));
    }

    /** True when the format is switched on at all (the panel sends FALSE to disable it). */
    public boolean isEnabled(AdFormat format) {
        if (isSubscribed()) {
            return false;
        }
        return !isDisabledValue(string(format.typeKey())) && !waterfall(format).isEmpty();
    }

    /**
     * The ordered list of networks to try for {@code format}. Networks without a usable
     * unit id, or that cannot serve the format, are already filtered out, so the caller can
     * simply walk the list.
     */
    public List<AdNetwork> waterfall(AdFormat format) {
        final LinkedHashSet<AdNetwork> ordered = new LinkedHashSet<>();

        // 1. An explicit waterfall always wins.
        addAll(ordered, string(format.orderKey()));
        // 2. Otherwise the legacy single-network key, which may itself carry a list.
        addAll(ordered, string(format.typeKey()));
        // 3. Finally every other network that has been given credentials, unless the panel
        //    turned automatic fallback off.
        if (isFallbackEnabled()) {
            for (AdNetwork network : AdNetwork.values()) {
                ordered.add(network);
            }
        }

        final List<AdNetwork> usable = new ArrayList<>(ordered.size());
        for (AdNetwork network : ordered) {
            if (network.supports(format) && isUsable(format, network)) {
                usable.add(network);
            }
        }
        return usable;
    }

    /** Unit / placement id for a network, empty when it has not been configured. */
    public String unitId(AdFormat format, AdNetwork network) {
        String id = string(format.unitIdKey(network));
        if (TextUtils.isEmpty(id) && network == AdNetwork.MAX) {
            // Older panels stored the MAX unit id under the AdMob key.
            id = string(format.unitIdKey(AdNetwork.ADMOB));
        }
        if (TextUtils.isEmpty(id) && format == AdFormat.NATIVE && network == AdNetwork.FACEBOOK) {
            id = string("ADMIN_NATIVE_BANNER_FACEBOOK_ID");
        }
        return id == null ? "" : id.trim();
    }

    public String unityGameId() {
        return string(KEY_UNITY_GAME_ID);
    }

    /** How long one network may take before the waterfall moves on. */
    public long timeoutMillis() {
        final String raw = string(KEY_TIMEOUT_SECONDS);
        if (TextUtils.isEmpty(raw)) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            final long millis = Long.parseLong(raw.trim()) * 1000L;
            return Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, millis));
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    /** Number of clicks between two interstitials. */
    public int interstitialClicks() {
        return pref.getInt(KEY_INTERSTITIAL_CLICKS);
    }

    /**
     * How many packs sit between two in-feed native ads. The panel sends this as
     * {@code ADMIN_NATIVE_LINES}; a missing or nonsensical value falls back to a sane
     * default instead of crashing the list.
     */
    public int packsBetweenNativeAds() {
        final String raw = string(KEY_NATIVE_LINES);
        int lines = DEFAULT_NATIVE_LINES;
        if (!TextUtils.isEmpty(raw)) {
            try {
                lines = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                lines = DEFAULT_NATIVE_LINES;
            }
        }
        return Math.max(MIN_NATIVE_LINES, lines);
    }

    /**
     * What to show when a free pack is added to WhatsApp / Telegram / Signal, from
     * {@code ADMIN_DOWNLOAD_AD_TYPE}.
     */
    public DownloadAd downloadAd() {
        if (isSubscribed()) {
            return DownloadAd.NONE;
        }
        final String raw = string(KEY_DOWNLOAD_AD_TYPE);
        if (isDisabledValue(raw)) {
            return DownloadAd.NONE;
        }
        final String value = raw.trim().toUpperCase(Locale.US);
        if ("REWARDED".equals(value) || "REWARD".equals(value)) {
            return DownloadAd.REWARDED;
        }
        return DownloadAd.INTERSTITIAL;
    }

    /** The ad the panel wants shown on the download / add-to-app action. */
    public enum DownloadAd {
        /** No ad, the pack is added straight away. */
        NONE,
        /** Full screen ad; the pack is added whether or not the ad showed. */
        INTERSTITIAL,
        /** Rewarded video; the pack is added once the user has earned the reward. */
        REWARDED
    }

    private boolean isFallbackEnabled() {
        final String raw = string(KEY_FALLBACK_ENABLED);
        return TextUtils.isEmpty(raw) || !"FALSE".equalsIgnoreCase(raw.trim());
    }

    private boolean isUsable(AdFormat format, AdNetwork network) {
        if (network == AdNetwork.UNITY && TextUtils.isEmpty(unityGameId())) {
            return false;
        }
        return !network.requiresUnitId() || !TextUtils.isEmpty(unitId(format, network));
    }

    private void addAll(LinkedHashSet<AdNetwork> target, String rawList) {
        if (isDisabledValue(rawList)) {
            return;
        }
        for (String part : rawList.split("[,;|]")) {
            final AdNetwork network = AdNetwork.from(part);
            if (network != null) {
                target.add(network);
            }
        }
    }

    private static boolean isDisabledValue(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return true;
        }
        final String value = raw.trim().toUpperCase(Locale.US);
        return "FALSE".equals(value) || "NONE".equals(value) || "OFF".equals(value) || "0".equals(value);
    }

    private String string(String key) {
        final String value = pref.getString(key);
        return value == null ? "" : value;
    }
}
