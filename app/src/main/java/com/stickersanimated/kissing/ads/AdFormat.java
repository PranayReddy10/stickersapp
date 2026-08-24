package com.stickersanimated.kissing.ads;

/** The four ad surfaces the app uses, and the preference keys that configure each of them. */
public enum AdFormat {

    BANNER("ADMIN_BANNER_TYPE", "ADMIN_BANNER_ORDER", "ADMIN_BANNER_"),
    NATIVE("ADMIN_NATIVE_TYPE", "ADMIN_NATIVE_ORDER", "ADMIN_NATIVE_"),
    INTERSTITIAL("ADMIN_INTERSTITIAL_TYPE", "ADMIN_INTERSTITIAL_ORDER", "ADMIN_INTERSTITIAL_"),
    // The rewarded type key has always been spelled differently on the panel side.
    REWARDED("ADMIN_REWARDED_AD_TYPE", "ADMIN_REWARDED_ORDER", "ADMIN_REWARDED_");

    private final String typeKey;
    private final String orderKey;
    private final String idPrefix;

    AdFormat(String typeKey, String orderKey, String idPrefix) {
        this.typeKey = typeKey;
        this.orderKey = orderKey;
        this.idPrefix = idPrefix;
    }

    /** Legacy single-network key, e.g. {@code ADMIN_BANNER_TYPE=ADMOB}. */
    public String typeKey() {
        return typeKey;
    }

    /** Waterfall key, e.g. {@code ADMIN_BANNER_ORDER=ADMOB,MAX,FACEBOOK,UNITY}. */
    public String orderKey() {
        return orderKey;
    }

    /** Unit id key for a network, e.g. {@code ADMIN_BANNER_FACEBOOK_ID}. */
    public String unitIdKey(AdNetwork network) {
        return idPrefix + network.idSuffix();
    }
}
