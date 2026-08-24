package com.stickersanimated.kissing.ads;

import java.util.Locale;

/**
 * Every ad network the app can serve from. The order of the constants is also the
 * default waterfall order used when the backend does not send an explicit one.
 */
public enum AdNetwork {

    ADMOB("ADMOB_ID"),
    MAX("MAX_ID"),
    APPLOVIN("APPLOVIN_ID"),
    FACEBOOK("FACEBOOK_ID"),
    UNITY("UNITY_ID");

    private final String idSuffix;

    AdNetwork(String idSuffix) {
        this.idSuffix = idSuffix;
    }

    /** Suffix of the {@code ADMIN_<FORMAT>_<SUFFIX>} preference holding this network's unit id. */
    public String idSuffix() {
        return idSuffix;
    }

    /**
     * AppLovin's direct (non MAX) integration is driven by the SDK key alone, so it is the
     * only network that can serve without a per-placement unit id.
     */
    public boolean requiresUnitId() {
        return this != APPLOVIN;
    }

    /** Whether this network is able to serve the given format at all. */
    public boolean supports(AdFormat format) {
        switch (this) {
            case ADMOB:
            case MAX:
                return true;
            case FACEBOOK:
                return true;
            case APPLOVIN:
                // AppLovin direct has no native ad surface.
                return format != AdFormat.NATIVE;
            case UNITY:
                // Unity Ads offers banner, interstitial and rewarded only.
                return format != AdFormat.NATIVE;
            default:
                return false;
        }
    }

    /**
     * Parses a network name coming from the backend. Returns {@code null} for unknown or
     * disabled values so callers can simply skip them.
     */
    public static AdNetwork from(String raw) {
        if (raw == null) {
            return null;
        }
        final String key = raw.trim().toUpperCase(Locale.US);
        switch (key) {
            case "ADMOB":
            case "GOOGLE":
            case "ADMANAGER":
            case "AD_MANAGER":
                return ADMOB;
            case "MAX":
            case "APPLOVINMAX":
            case "APPLOVIN_MAX":
                return MAX;
            case "APPLOVIN":
            case "APPLOVIN_DISCOVERY":
                return APPLOVIN;
            case "FACEBOOK":
            case "FB":
            case "META":
            case "FAN":
            case "AUDIENCE_NETWORK":
                return FACEBOOK;
            case "UNITY":
            case "UNITYADS":
            case "UNITY_ADS":
                return UNITY;
            default:
                return null;
        }
    }
}
