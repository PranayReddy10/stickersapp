package com.stickersanimated.kissing.ads;

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/** One ad of your own, as the panel describes it. */
public class HouseAd implements Serializable {

    @SerializedName("id") public int id;
    @SerializedName("format") public String format;
    @SerializedName("title") public String title;
    @SerializedName("body") public String body;
    @SerializedName("cta") public String cta;
    @SerializedName("image") public String image;
    @SerializedName("url") public String url;
    @SerializedName("weight") public int weight = 1;

    /** Whether this one could actually be drawn and tapped. */
    public boolean usable() {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(format)) {
            return false;
        }
        // Native is built from its words; every other shape is the picture itself.
        if (AdFormat.NATIVE.name().equals(format)) {
            return !TextUtils.isEmpty(title);
        }
        return !TextUtils.isEmpty(image);
    }

    public String buttonText() {
        return TextUtils.isEmpty(cta) ? "Open" : cta;
    }

    /** Never below one, or an ad could never be picked at all. */
    public int share() {
        return weight < 1 ? 1 : weight;
    }
}
