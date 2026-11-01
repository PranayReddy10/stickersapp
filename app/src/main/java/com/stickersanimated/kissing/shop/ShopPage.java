package com.stickersanimated.kissing.shop;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** One page of products, with enough of the shop's paging to know when to stop. */
public class ShopPage {

    @SerializedName("data") public List<ShopProduct> data;
    @SerializedName("meta") public Meta meta;

    public static class Meta {
        @SerializedName("page") public int page;
        @SerializedName("per_page") public int perPage;
        @SerializedName("total") public int total;
        @SerializedName("last_page") public int lastPage;
    }

    public boolean hasMoreAfter(int page) {
        return meta != null && page < meta.lastPage;
    }
}
