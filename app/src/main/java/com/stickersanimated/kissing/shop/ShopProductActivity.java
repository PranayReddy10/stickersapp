package com.stickersanimated.kissing.shop;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

    /** How many neighbours to show. More than this and nobody scrolls that far. */
    private static final int RELATED_COUNT = 10;

    private ShopProduct product;

    /** The variant the buyer has picked, if the product has any. */
    private ShopProduct.Variant variant;

    private TextView priceView;
    private TextView wasPriceView;
    private TextView stockView;
    private TextView counterView;
    private TextView saveView;
    private TextView buyButton;

    /** The other things from the same category, once they arrive. */
    private final List<ShopProduct> related = new ArrayList<>();

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
        saveView = findViewById(R.id.text_view_product_save);
        counterView = findViewById(R.id.text_view_product_counter);

        // Found before the binding runs: bindPrice() turns this button on and off.
        buyButton = findViewById(R.id.button_product_buy);
        buyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openOnWebsite();
            }
        });

        bindImages();
        bindHeader();
        bindVariants();
        bindPersonalisation();
        bindDescription();
        bindDetails();
        bindPrice();
        loadRelated();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_shop_product, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_product_share) {
            share();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** The product's own address, so whoever receives it lands on the same page. */
    private void share() {
        final String url = address();
        if (TextUtils.isEmpty(url)) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.shop_share_subject, product.name == null ? "" : product.name));
        intent.putExtra(Intent.EXTRA_TEXT,
                (product.name == null ? "" : product.name + "\n") + url);
        startActivity(Intent.createChooser(intent, getString(R.string.shop_share)));
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

        if (regular > price) {
            saveView.setVisibility(View.VISIBLE);
            saveView.setText(getString(R.string.shop_save,
                    ShopPrice.format(regular - price, product.currency)));
        } else {
            saveView.setVisibility(View.GONE);
        }

        stockView.setText(inStock ? R.string.shop_in_stock : R.string.shop_out_of_stock);
        stockView.setTextColor(getResources().getColor(
                inStock ? R.color.shop_mint_dark : R.color.shop_peach_dark));
        stockView.setBackgroundResource(
                inStock ? R.drawable.shop_pill_mint : R.drawable.shop_pill_peach);

        buyButton.setEnabled(inStock);
        buyButton.setText(inStock ? R.string.shop_buy : R.string.shop_out_of_stock);
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
            final Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.item_shop_chip, group, false);
            chip.setText(option.label());
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
                    // The code and availability belong to the variant, not the product.
                    bindDetails();
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

    /**
     * The description, which the shop writes in HTML - mostly bulleted lists. It is laid
     * out block by block rather than flattened; see {@link ShopDescription}.
     */
    private void bindDescription() {
        final View section = findViewById(R.id.linear_layout_product_description);
        final LinearLayout body = findViewById(R.id.linear_layout_description_body);

        String html = product.descriptionHtml;
        if (TextUtils.isEmpty(html)) {
            html = product.shortDescription;
        }

        if (ShopDescription.render(body, html)) {
            return;
        }

        // No markup to lay out: whatever plain text there is, or nothing at all.
        if (TextUtils.isEmpty(product.descriptionText)) {
            section.setVisibility(View.GONE);
            return;
        }
        final TextView plain = new TextView(this);
        plain.setText(product.descriptionText.trim());
        plain.setTextSize(14f);
        body.removeAllViews();
        body.addView(plain);
    }

    /**
     * The details card: brand, code, category, making time and availability.
     *
     * <p>A row is only added when the shop actually has that field, so a sparse
     * product shows a short card rather than a column of dashes.
     */
    private void bindDetails() {
        final View section = findViewById(R.id.linear_layout_product_details);
        final LinearLayout rows = findViewById(R.id.linear_layout_details_rows);
        rows.removeAllViews();

        addDetail(rows, getString(R.string.shop_detail_brand), product.brand);
        addDetail(rows, getString(R.string.shop_detail_code),
                variant == null || TextUtils.isEmpty(variant.sku) ? product.sku : variant.sku);
        addDetail(rows, getString(R.string.shop_detail_category), categoryNames());
        if (product.productionDays != null && product.productionDays > 0) {
            addDetail(rows, getString(R.string.shop_detail_made),
                    getResources().getQuantityString(R.plurals.shop_production_days,
                            product.productionDays, product.productionDays));
        }
        addDetail(rows, getString(R.string.shop_detail_availability),
                getString(inStock() ? R.string.shop_in_stock : R.string.shop_out_of_stock));

        section.setVisibility(rows.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void addDetail(LinearLayout rows, String label, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        final View row = LayoutInflater.from(this)
                .inflate(R.layout.item_shop_detail_row, (ViewGroup) rows, false);
        ((TextView) row.findViewById(R.id.text_view_detail_label)).setText(label);
        ((TextView) row.findViewById(R.id.text_view_detail_value)).setText(value);
        rows.addView(row);
    }

    /** "Magnets, Round Magnets" - every category the shop files this under. */
    private String categoryNames() {
        if (product.categories == null || product.categories.isEmpty()) {
            return product.category;
        }
        final StringBuilder names = new StringBuilder();
        for (ShopProduct.Category category : product.categories) {
            if (category == null || TextUtils.isEmpty(category.name)) {
                continue;
            }
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(category.name);
        }
        return names.length() == 0 ? product.category : names.toString();
    }

    private boolean inStock() {
        return variant == null ? product.inStock : variant.inStock;
    }

    /**
     * Other things from the same category.
     *
     * <p>The shop's API has no "related" of its own, so this asks for the category
     * this product sits in and drops the product itself. A product with no category,
     * or a category holding nothing else, simply has no row - it is never an error
     * worth telling the buyer about.
     */
    private void loadRelated() {
        final String slug = categorySlug();
        if (slug == null) {
            return;
        }

        final View section = findViewById(R.id.linear_layout_product_related);
        final RecyclerView list = findViewById(R.id.recycler_view_product_related);
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        list.setAdapter(new ShopRelatedAdapter(this, related,
                new ShopAdapter.OnProductTapped() {
                    @Override
                    public void onProductTapped(ShopProduct tapped) {
                        final Intent intent = new Intent(ShopProductActivity.this,
                                ShopProductActivity.class);
                        intent.putExtra(EXTRA_PRODUCT, tapped);
                        startActivity(intent);
                        overridePendingTransition(R.anim.enter, R.anim.exit);
                    }
                }));

        final apiRest api = apiClient.getClient().create(apiRest.class);
        api.shopProducts(1, RELATED_COUNT + 1, null, slug).enqueue(new Callback<ShopPage>() {
            @Override
            public void onResponse(Call<ShopPage> call, Response<ShopPage> response) {
                final ShopPage body = response.body();
                if (!response.isSuccessful() || body == null || body.data == null) {
                    return;
                }
                for (ShopProduct other : body.data) {
                    if (other == null || other.id == product.id) {
                        continue;
                    }
                    if (related.size() >= RELATED_COUNT) {
                        break;
                    }
                    related.add(other);
                }
                if (related.isEmpty()) {
                    return;
                }
                section.setVisibility(View.VISIBLE);
                list.getAdapter().notifyDataSetChanged();
            }

            @Override
            public void onFailure(Call<ShopPage> call, Throwable t) {
            }
        });
    }

    /** The narrowest category this product is in, which makes the closest neighbours. */
    private String categorySlug() {
        if (product.categories == null) {
            return null;
        }
        String fallback = null;
        for (ShopProduct.Category category : product.categories) {
            if (category == null || TextUtils.isEmpty(category.slug)) {
                continue;
            }
            // A child category is more specific than its parent, so prefer one.
            if (category.parentId != null) {
                return category.slug;
            }
            if (fallback == null) {
                fallback = category.slug;
            }
        }
        return fallback;
    }

    /** The variant's own address when one is picked, otherwise the product's. */
    private String address() {
        if (variant != null && !TextUtils.isEmpty(variant.url)) {
            return variant.url;
        }
        if (!TextUtils.isEmpty(product.url)) {
            return product.url;
        }
        return com.stickersanimated.kissing.Shop.url(this);
    }

    /**
     * Over to the shop. The variant's own address when one is picked, so the buyer
     * lands on the pack size they chose rather than the default.
     */
    private void openOnWebsite() {
        final String url = address();
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
