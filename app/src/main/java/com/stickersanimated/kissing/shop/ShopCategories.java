package com.stickersanimated.kissing.shop;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** The shop's categories, for the filter row. */
public class ShopCategories {

    @SerializedName("data") public List<ShopProduct.Category> data;
}
