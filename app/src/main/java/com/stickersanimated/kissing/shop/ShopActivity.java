package com.stickersanimated.kissing.shop;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.Shop;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The shop's products, inside the app.
 *
 * <p>Nothing here knows the shop's API key. Every call goes to our own server, which
 * holds the key and asks the shop, so the key is never in the APK and can be changed
 * from the panel without shipping a build.
 */
public class ShopActivity extends AppCompatActivity implements ShopAdapter.OnProductTapped {

    /** How many products to ask for at a time. The server clamps this to 50. */
    private static final int PER_PAGE = 20;

    /** How long to wait after the last keystroke before searching. */
    private static final long SEARCH_DELAY = 450;

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyView;
    private TextView emptyText;
    private ChipGroup chipGroup;
    private EditText searchBox;

    private ShopAdapter adapter;
    private final List<ShopProduct> products = new ArrayList<>();

    private int page = 1;
    private boolean loading = false;

    /**
     * Bumped every time the filter changes. A reply carrying an older token belongs to
     * a search the buyer has already typed past, and is dropped rather than shown.
     */
    private int requestToken = 0;
    private boolean lastPageReached = false;
    private String query = null;
    private String category = null;

    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shop);

        final Toolbar toolbar = findViewById(R.id.toolbar_shop);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(Shop.label(this, getString(R.string.nav_shop)));
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        recyclerView = findViewById(R.id.recycler_view_shop);
        swipeRefresh = findViewById(R.id.swipe_refresh_shop);
        progressBar = findViewById(R.id.progress_bar_shop);
        emptyView = findViewById(R.id.linear_layout_shop_empty);
        emptyText = findViewById(R.id.text_view_shop_empty);
        chipGroup = findViewById(R.id.chip_group_shop);
        searchBox = findViewById(R.id.edit_text_shop_search);

        final GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ShopAdapter(this, products, this);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy <= 0 || loading || lastPageReached) {
                    return;
                }
                // Start the next page a screen early, so the grid does not stall.
                if (layoutManager.findLastVisibleItemPosition() >= products.size() - 4) {
                    page++;
                    load(false);
                }
            }
        });

        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reload();
            }
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String typed = s.toString().trim();
                query = typed.isEmpty() ? null : typed;
                // One search when the typing stops, not one per letter.
                searchBox.removeCallbacks(searchRunnable);
                searchBox.postDelayed(searchRunnable, SEARCH_DELAY);
            }
        });

        loadCategories();
        load(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_shop, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_shop_website) {
            final String url = Shop.url(this);
            if (url != null) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_shop, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Back to page one with the filter as it now stands. */
    private void reload() {
        requestToken++;
        page = 1;
        lastPageReached = false;
        products.clear();
        adapter.notifyDataSetChanged();
        load(true);
    }

    private void load(final boolean first) {
        // A new filter always goes out; only another page of the same list waits.
        if (loading && !first) {
            return;
        }
        loading = true;
        final int token = requestToken;
        emptyView.setVisibility(View.GONE);
        if (first && !swipeRefresh.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }

        final apiRest api = apiClient.getClient().create(apiRest.class);
        api.shopProducts(page, PER_PAGE, query, category).enqueue(new Callback<ShopPage>() {
            @Override
            public void onResponse(Call<ShopPage> call, Response<ShopPage> response) {
                if (token != requestToken) {
                    return;
                }
                loading = false;
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                final ShopPage body = response.body();
                if (!response.isSuccessful() || body == null || body.data == null) {
                    // The server answers with a code and a message when the shop is off
                    // or unreachable, which Retrofit cannot map onto a page of products.
                    showEmpty(getString(R.string.shop_unavailable));
                    return;
                }

                final int before = products.size();
                products.addAll(body.data);
                if (before == 0) {
                    adapter.notifyDataSetChanged();
                } else {
                    adapter.notifyItemRangeInserted(before, body.data.size());
                }

                if (!body.hasMoreAfter(page)) {
                    lastPageReached = true;
                }
                if (products.isEmpty()) {
                    showEmpty(query == null
                            ? getString(R.string.shop_empty)
                            : getString(R.string.shop_no_results, query));
                }
            }

            @Override
            public void onFailure(Call<ShopPage> call, Throwable t) {
                if (token != requestToken) {
                    return;
                }
                loading = false;
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                if (products.isEmpty()) {
                    showEmpty(getString(R.string.shop_unavailable));
                } else {
                    // Keep what is already on screen; only the next page failed.
                    page = Math.max(1, page - 1);
                }
            }
        });
    }

    /**
     * The filter row. A shop with no categories endpoint simply has no row - this
     * never blocks the products, which is why it is fetched separately.
     */
    private void loadCategories() {
        final apiRest api = apiClient.getClient().create(apiRest.class);
        api.shopCategories().enqueue(new Callback<ShopCategories>() {
            @Override
            public void onResponse(Call<ShopCategories> call, Response<ShopCategories> response) {
                final ShopCategories body = response.body();
                if (!response.isSuccessful() || body == null || body.data == null
                        || body.data.isEmpty()) {
                    return;
                }
                buildChips(body.data);
            }

            @Override
            public void onFailure(Call<ShopCategories> call, Throwable t) {
            }
        });
    }

    private void buildChips(List<ShopProduct.Category> categories) {
        chipGroup.removeAllViews();
        chipGroup.setVisibility(View.VISIBLE);

        addChip(getString(R.string.shop_all), null, true);
        for (ShopProduct.Category entry : categories) {
            if (entry == null || entry.slug == null || entry.name == null) {
                continue;
            }
            // Only the top level: the children would make a row nobody can read.
            if (entry.parentId != null) {
                continue;
            }
            if (entry.products != null && entry.products == 0) {
                continue;
            }
            addChip(entry.name, entry.slug, false);
        }
    }

    private void addChip(final String label, final String slug, boolean checked) {
        // Inflated, not new Chip(): a chip built in code does not pick up the style
        // and would come out as a stock Material chip among the shop's own.
        final Chip chip = (Chip) LayoutInflater.from(this)
                .inflate(R.layout.item_shop_chip, chipGroup, false);
        chip.setText(label);
        chip.setChecked(checked);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!chip.isChecked()) {
                    // Tapping the chip that is already on would otherwise clear the
                    // filter row and leave nothing selected.
                    chip.setChecked(true);
                    return;
                }
                category = slug;
                reload();
            }
        });
        chipGroup.addView(chip);
    }

    private void showEmpty(String message) {
        emptyText.setText(message);
        emptyView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onProductTapped(ShopProduct product) {
        final Intent intent = new Intent(this, ShopProductActivity.class);
        intent.putExtra(ShopProductActivity.EXTRA_PRODUCT, product);
        startActivity(intent);
        overridePendingTransition(R.anim.enter, R.anim.exit);
    }
}
