package com.stickersanimated.kissing.shop;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.stickersanimated.kissing.R;

import java.util.List;

/**
 * One product, in full.
 *
 * <p>The product arrives whole from the grid that was already holding it, so opening
 * this screen costs no network call and nothing to wait for. Buying happens on the
 * shop's own site: it is the shop that takes the photo upload, the address and the
 * payment, none of which belongs in a sticker app.
 */
public class ShopProductActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "product";

    private ShopProduct product;

    /** The variant the buyer has picked, if the product has any. */
    private ShopProduct.Variant variant;

    private TextView priceView;
    private TextView wasPriceView;
    private TextView stockView;
    private TextView counterView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shop_product);

        product = read(getIntent());
        if (product == null) {
            // Nothing to show, and an empty page is worse than going back.
            Toast.makeText(this, R.string.error_shop, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final Toolbar toolbar = findViewById(R.id.toolbar_shop_product);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        priceView = findViewById(R.id.text_view_product_price);
        wasPriceView = findViewById(R.id.text_view_product_was_price);
        stockView = findViewById(R.id.text_view_product_stock);
        counterView = findViewById(R.id.text_view_product_counter);

        bindImages();
        bindHeader();
        bindVariants();
        bindPersonalisation();
        bindDescription();
        bindMeta();
        bindPrice();

        final Button buy = findViewById(R.id.button_product_buy);
        buy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openOnWebsite();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private ShopProduct read(Intent intent) {
        if (intent == null) {
            return null;
        }
        try {
            final Object extra = intent.getSerializableExtra(EXTRA_PRODUCT);
            return extra instanceof ShopProduct ? (ShopProduct) extra : null;
        } catch (Exception e) {
            // A truncated or unreadable extra is a missing product, not a crash.
            return null;
        }
    }

    private void bindImages() {
        final ViewPager2 pager = findViewById(R.id.view_pager_product_images);
        final List<String> gallery = product.gallery();

        if (gallery.isEmpty()) {
            pager.setVisibility(View.GONE);
            counterView.setVisibility(View.GONE);
            return;
        }

        pager.setAdapter(new ShopImagesAdapter(this, gallery));
        if (gallery.size() < 2) {
            counterView.setVisibility(View.GONE);
            return;
        }

        counterView.setVisibility(View.VISIBLE);
        counterView.setText(getString(R.string.shop_counter, 1, gallery.size()));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                counterView.setText(getString(R.string.shop_counter,
                        position + 1, gallery.size()));
            }
        });
    }

    private void bindHeader() {
        final TextView category = findViewById(R.id.text_view_product_category);
        final TextView name = findViewById(R.id.text_view_product_name);

        name.setText(product.name == null ? "" : product.name);
        if (TextUtils.isEmpty(product.category)) {
            category.setVisibility(View.GONE);
        } else {
            category.setText(product.category);
        }

        final TextView rating = findViewById(R.id.text_view_product_rating);
        if (product.rating == null || product.rating.count <= 0) {
            rating.setVisibility(View.GONE);
        } else {
            rating.setText(getString(R.string.shop_rating,
                    String.valueOf(product.rating.average), product.rating.count));
        }
    }

    /** Price, discount and stock - re-run whenever a variant is picked. */
    private void bindPrice() {
        final double price = variant == null ? product.price : variant.price;
        final double regular = variant == null ? product.regularPrice : variant.regularPrice;
        final boolean inStock = variant == null ? product.inStock : variant.inStock;

        priceView.setText(ShopPrice.format(price, product.currency));

        if (regular > price) {
            wasPriceView.setVisibility(View.VISIBLE);
            wasPriceView.setText(ShopPrice.format(regular, product.currency));
            wasPriceView.setPaintFlags(
                    wasPriceView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            wasPriceView.setVisibility(View.GONE);
        }

        stockView.setText(inStock ? R.string.shop_in_stock : R.string.shop_out_of_stock);
        stockView.setTextColor(getResources().getColor(
                inStock ? R.color.colorAccent : R.color.primary_text_light));

        final Button buy = findViewById(R.id.button_product_buy);
        buy.setEnabled(inStock);
        buy.setText(inStock ? R.string.shop_buy : R.string.shop_out_of_stock);
    }

    private void bindVariants() {
        final View section = findViewById(R.id.linear_layout_product_variants);
        final ChipGroup group = findViewById(R.id.chip_group_product_variants);

        if (!product.isVariable()) {
            section.setVisibility(View.GONE);
            return;
        }

        final java.util.List<ShopProduct.Variant> shown = new java.util.ArrayList<>();
        for (final ShopProduct.Variant option : product.variants) {
            if (option == null) {
                continue;
            }
            shown.add(option);
            final Chip chip = new Chip(this);
            chip.setText(option.label());
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnabled(option.inStock);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!chip.isChecked()) {
                        chip.setChecked(true);
                        return;
                    }
                    variant = option;
                    bindPrice();
                }
            });
            group.addView(chip);
        }

        if (shown.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }

        // Start on the first one that can actually be bought. Walking the chips rather
        // than the variants keeps the two in step even when some were skipped.
        for (int i = 0; i < group.getChildCount() && i < shown.size(); i++) {
            final View child = group.getChildAt(i);
            if (child instanceof Chip && child.isEnabled()) {
                ((Chip) child).setChecked(true);
                variant = shown.get(i);
                break;
            }
        }
    }

    /** What the shop will ask for after the buyer gets there. */
    private void bindPersonalisation() {
        final View section = findViewById(R.id.linear_layout_product_personalisation);
        final LinearLayout list = findViewById(R.id.linear_layout_personalisation_items);

        if (product.personalisation == null || product.personalisation.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }

        final LayoutInflater inflater = LayoutInflater.from(this);
        for (ShopProduct.Personalisation item : product.personalisation) {
            if (item == null || TextUtils.isEmpty(item.label)) {
                continue;
            }
            final View row = inflater.inflate(R.layout.item_shop_personalisation,
                    (ViewGroup) list, false);

            final TextView label = row.findViewById(R.id.text_view_personalisation_label);
            final TextView help = row.findViewById(R.id.text_view_personalisation_help);

            label.setText(item.required
                    ? getString(R.string.shop_required, item.label)
                    : item.label);

            if (TextUtils.isEmpty(item.help)) {
                help.setVisibility(View.GONE);
            } else {
                help.setText(item.help);
            }
            list.addView(row);
        }
    }

    private void bindDescription() {
        final View section = findViewById(R.id.linear_layout_product_description);
        final TextView body = findViewById(R.id.text_view_product_description);

        // The plain text first: the shop's HTML carries editor attributes that add
        // nothing on a phone, and a blank description should hide the whole section.
        if (!TextUtils.isEmpty(product.descriptionText)) {
            body.setText(product.descriptionText.trim());
        } else if (!TextUtils.isEmpty(product.descriptionHtml)) {
            body.setText(fromHtml(product.descriptionHtml));
        } else if (!TextUtils.isEmpty(product.shortDescription)) {
            body.setText(fromHtml(product.shortDescription));
        } else {
            section.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("deprecation")
    private CharSequence fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim();
        }
        return Html.fromHtml(html).toString().trim();
    }

    /** Brand, code and how long it takes to make - the small print under the fold. */
    private void bindMeta() {
        final TextView meta = findViewById(R.id.text_view_product_meta);
        final StringBuilder text = new StringBuilder();

        if (!TextUtils.isEmpty(product.brand)) {
            text.append(product.brand);
        }
        final String code = variant == null || TextUtils.isEmpty(variant.sku)
                ? product.sku : variant.sku;
        if (!TextUtils.isEmpty(code)) {
            append(text, code);
        }
        if (product.productionDays != null && product.productionDays > 0) {
            append(text, getResources().getQuantityString(R.plurals.shop_production_days,
                    product.productionDays, product.productionDays));
        }

        if (text.length() == 0) {
            meta.setVisibility(View.GONE);
        } else {
            meta.setText(text.toString());
        }
    }

    private void append(StringBuilder text, String part) {
        if (text.length() > 0) {
            text.append("  ·  ");
        }
        text.append(part);
    }

    /**
     * Over to the shop. The variant's own address when one is picked, so the buyer
     * lands on the pack size they chose rather than the default.
     */
    private void openOnWebsite() {
        String url = variant != null && !TextUtils.isEmpty(variant.url)
                ? variant.url : product.url;
        if (TextUtils.isEmpty(url)) {
            url = com.stickersanimated.kissing.Shop.url(this);
        }
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, R.string.error_shop, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_shop, Toast.LENGTH_SHORT).show();
        }
    }
}
