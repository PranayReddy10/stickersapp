package com.stickersanimated.kissing.shop;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One thing the shop sells, as the shop describes it.
 *
 * <p>Only the fields the app shows are mapped. The shop is free to grow others - Gson
 * ignores what it was not asked for - so a new field on the shop's side never stops
 * this screen loading.
 */
public class ShopProduct implements Serializable {

    @SerializedName("id") public int id;
    @SerializedName("sku") public String sku;
    @SerializedName("name") public String name;
    @SerializedName("url") public String url;
    @SerializedName("type") public String type;

    @SerializedName("price") public double price;
    @SerializedName("regular_price") public double regularPrice;
    @SerializedName("max_price") public double maxPrice;
    @SerializedName("on_sale") public boolean onSale;
    @SerializedName("discount_percent") public int discountPercent;
    @SerializedName("currency") public String currency;

    @SerializedName("in_stock") public boolean inStock;
    @SerializedName("stock_qty") public Integer stockQty;

    @SerializedName("short_description") public String shortDescription;
    @SerializedName("description_html") public String descriptionHtml;
    @SerializedName("description_text") public String descriptionText;

    @SerializedName("image") public String image;
    @SerializedName("thumbnail") public String thumbnail;
    @SerializedName("images") public List<Image> images;

    @SerializedName("category") public String category;
    @SerializedName("categories") public List<Category> categories;

    @SerializedName("variants") public List<Variant> variants;
    @SerializedName("personalisation") public List<Personalisation> personalisation;

    @SerializedName("production_days") public Integer productionDays;
    @SerializedName("brand") public String brand;
    @SerializedName("rating") public Rating rating;

    public static class Image implements Serializable {
        @SerializedName("url") public String url;
        @SerializedName("alt") public String alt;
    }

    public static class Category implements Serializable {
        @SerializedName("id") public int id;
        @SerializedName("name") public String name;
        @SerializedName("slug") public String slug;
        @SerializedName("url") public String url;
        @SerializedName("parent_id") public Integer parentId;
        @SerializedName("image") public String image;
        @SerializedName("products") public Integer products;
    }

    /** One buyable combination of a variable product - a pack size, a colour. */
    public static class Variant implements Serializable {
        @SerializedName("id") public int id;
        @SerializedName("sku") public String sku;
        @SerializedName("options") public Map<String, String> options;
        @SerializedName("price") public double price;
        @SerializedName("regular_price") public double regularPrice;
        @SerializedName("in_stock") public boolean inStock;
        @SerializedName("image") public String image;
        @SerializedName("url") public String url;

        /** "Pack-3", or "Red - Large" when the shop uses more than one option. */
        public String label() {
            if (options == null || options.isEmpty()) {
                return sku == null ? "Option" : sku;
            }
            final StringBuilder out = new StringBuilder();
            for (String value : options.values()) {
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(" · ");
                }
                out.append(value.trim());
            }
            return out.length() == 0 ? String.valueOf(id) : out.toString();
        }
    }

    /** Something the shop will ask the buyer for - a photo, a name to print. */
    public static class Personalisation implements Serializable {
        @SerializedName("label") public String label;
        @SerializedName("type") public String type;
        @SerializedName("required") public boolean required;
        @SerializedName("help") public String help;
        @SerializedName("max_files") public Integer maxFiles;
    }

    public static class Rating implements Serializable {
        @SerializedName("average") public double average;
        @SerializedName("count") public int count;
    }

    /** Every picture the shop has, starting with the main one. */
    public List<String> gallery() {
        final List<String> out = new ArrayList<>();
        if (image != null && !image.trim().isEmpty()) {
            out.add(image.trim());
        }
        if (images != null) {
            for (Image extra : images) {
                if (extra == null || extra.url == null || extra.url.trim().isEmpty()) {
                    continue;
                }
                if (!out.contains(extra.url.trim())) {
                    out.add(extra.url.trim());
                }
            }
        }
        return out;
    }

    /** The thumbnail if there is one, otherwise the main picture. */
    public String cover() {
        if (thumbnail != null && !thumbnail.trim().isEmpty()) {
            return thumbnail.trim();
        }
        return image == null || image.trim().isEmpty() ? null : image.trim();
    }

    public boolean isVariable() {
        return variants != null && !variants.isEmpty();
    }

    /** True when the shop is charging less than the usual price. */
    public boolean discounted() {
        return onSale && regularPrice > price;
    }
}
