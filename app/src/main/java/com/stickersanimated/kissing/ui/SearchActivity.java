package com.stickersanimated.kissing.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.orhanobut.hawk.Hawk;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.Sticker;
import com.stickersanimated.kissing.StickerPack;
import com.stickersanimated.kissing.adapter.StickerAdapter;
import com.stickersanimated.kissing.ads.BannerAdManager;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.PackApi;
import com.stickersanimated.kissing.entity.StickerApi;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout errorLayout;
    private ImageView emptyListImage;
    private Button tryAgainButton;
    private RelativeLayout loadMoreLayout;

    private StickerAdapter adapter;
    private LinearLayoutManager layoutManager;

    private final ArrayList<StickerPack> stickerPacks = new ArrayList<>();

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean isAllDataLoaded = false;
    private int adItemCounter = 0;
    private int linesBetweenAds = 8;
    private boolean areNativeAdsEnabled = false;
    private BannerAdManager bannerAdManager;

    private String searchQuery;

    private static final String TAG = "SearchActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            this.searchQuery = bundle.getString("query");
        } else {
            finish();
            return;
        }

        initViews();
        initConfig();
        initListeners();
        showAdsBanner();
        loadInitialData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(searchQuery);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        loadMoreLayout = findViewById(R.id.relative_layout_load_more);
        tryAgainButton = findViewById(R.id.button_try_again);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout_list);
        emptyListImage = findViewById(R.id.image_view_empty_list);
        errorLayout = findViewById(R.id.linear_layout_layout_error);
        recyclerView = findViewById(R.id.recycler_view_list);

        layoutManager = new LinearLayoutManager(this);
        adapter = new StickerAdapter(this, stickerPacks);

        //recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    private void initConfig() {
        PrefManager prefManager = new PrefManager(getApplicationContext());
        if (!"FALSE".equals(prefManager.getString("ADMIN_NATIVE_TYPE"))) {
            areNativeAdsEnabled = true;
            linesBetweenAds = Integer.parseInt(prefManager.getString("ADMIN_NATIVE_LINES"));
        }
        if ("TRUE".equals(prefManager.getString("SUBSCRIBED"))) {
            areNativeAdsEnabled = false;
        }
    }

    private void initListeners() {
        swipeRefreshLayout.setOnRefreshListener(this::loadInitialData);
        tryAgainButton.setOnClickListener(v -> loadInitialData());
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if (!isLoading && !isAllDataLoaded) {
                        if ((visibleItemCount + pastVisiblesItems) >= totalItemCount) {
                            fetchPacks(false);
                        }
                    }
                }
            }
        });
    }

    private void loadInitialData() {
        currentPage = 0;
        adItemCounter = 0;
        isLoading = false;
        isAllDataLoaded = false;

        int oldSize = stickerPacks.size();
        stickerPacks.clear();
        adapter.notifyItemRangeRemoved(0, oldSize);

        showLoadingLayout();
        fetchPacks(true);
    }

    private void fetchPacks(final boolean isInitialLoad) {
        if (isLoading) return;

        isLoading = true;
        if (isInitialLoad) {
            swipeRefreshLayout.setRefreshing(true);
        } else {
            loadMoreLayout.setVisibility(View.VISIBLE);
        }

        apiClient.getClient().create(apiRest.class).packsByQuery(currentPage, searchQuery).enqueue(new Callback<List<PackApi>>() {
            @Override
            public void onResponse(@NonNull Call<List<PackApi>> call, @NonNull Response<List<PackApi>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PackApi> newPacks = response.body();

                    if (newPacks.isEmpty()) {
                        isAllDataLoaded = true;
                        if (isInitialLoad && stickerPacks.isEmpty()) {
                            showEmptyLayout();
                        }
                    } else {
                        processPacks(newPacks);
                        currentPage++;
                        showContentLayout();
                    }
                } else {
                    if (isInitialLoad) {
                        showErrorLayout();
                    }
                }
                finishLoading();
            }

            @Override
            public void onFailure(@NonNull Call<List<PackApi>> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to fetch packs by query", t);
                if (isInitialLoad) {
                    showErrorLayout();
                }
                finishLoading();
            }
        });
    }

    private void processPacks(List<PackApi> newPacks) {
        int initialSize = stickerPacks.size();
        ArrayList<StickerPack> packsToAdd = new ArrayList<>();

        for (PackApi packApi : newPacks) {
            if (packApi != null && isNotBlocked(packApi)) {
                StickerPack newStickerPack = createStickerPackFromApi(packApi);
                packsToAdd.add(newStickerPack);

                if (areNativeAdsEnabled && ++adItemCounter >= linesBetweenAds) {
                    adItemCounter = 0;
                    packsToAdd.add(createAdStickerPack());
                }
            }
        }

        if (!packsToAdd.isEmpty()) {
            stickerPacks.addAll(packsToAdd);
            adapter.notifyItemRangeInserted(initialSize, packsToAdd.size());
        }
    }

    private StickerPack createStickerPackFromApi(PackApi packApi) {
        ArrayList<Sticker> stickersForThisPack = new ArrayList<>();
        List<StickerApi> stickerApiList = packApi.getStickers();

        if (stickerApiList != null) {
            for (StickerApi stickerApi : stickerApiList) {
                stickersForThisPack.add(new Sticker(
                        stickerApi.getImageFileThum(),
                        stickerApi.getImageFile(),
                        getLastBitFromUrl(stickerApi.getImageFile()).replace(".png", ".webp"),
                        new ArrayList<>()
                ));
            }
        }

        StickerPack newStickerPack = new StickerPack(
                String.valueOf(packApi.getIdentifier()), packApi.getName(), packApi.getPublisher(),
                getLastBitFromUrl(packApi.getTrayImageFile()).replace(" ", "_"),
                packApi.getTrayImageFile(), packApi.getSize(), packApi.getDownloads(),
                packApi.getPremium(), packApi.getTrusted(), packApi.getCreated(),
                packApi.getUser(), packApi.getUserimage(), packApi.getUserid(),
                packApi.getPublisherEmail(), packApi.getPublisherWebsite(), packApi.getPrivacyPolicyWebsite(),
                packApi.getLicenseAgreementWebsite(), packApi.getAnimated(), packApi.getTelegram(),
                packApi.getSignal(), packApi.getWhatsapp(), packApi.getSignalurl(),
                packApi.getTelegramurl()
        );

        Hawk.put(String.valueOf(packApi.getIdentifier()), stickersForThisPack);
        newStickerPack.setStickers(stickersForThisPack);
        newStickerPack.packApi = packApi;
        return newStickerPack;
    }

    private StickerPack createAdStickerPack() {
        // One in-feed ad row for every network: the adapter runs the waterfall and picks
        // whichever network fills first.
        return new StickerPack().setViewType(StickerAdapter.VIEW_TYPE_NATIVE_AD);
    }

    private void finishLoading() {
        isLoading = false;
        swipeRefreshLayout.setRefreshing(false);
        loadMoreLayout.setVisibility(View.GONE);
    }

    private void showLoadingLayout() {
        recyclerView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
        emptyListImage.setVisibility(View.GONE);
    }

    private void showContentLayout() {
        recyclerView.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
        emptyListImage.setVisibility(View.GONE);
    }

    private void showErrorLayout() {
        recyclerView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        emptyListImage.setVisibility(View.GONE);
    }

    private void showEmptyLayout() {
        recyclerView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
        emptyListImage.setVisibility(View.VISIBLE);
    }

    private static String getLastBitFromUrl(final String url) {
        if (url == null || url.isEmpty()) return "";
        return url.replaceFirst(".*/([^/?]+).*", "$1");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.slide_enter, R.anim.slide_exit);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_enter, R.anim.slide_exit);
    }

    public boolean isNotBlocked(PackApi pack) {
        if (pack == null) return false;
        final PrefManager prefManager = new PrefManager(getApplicationContext());
        return !"TRUE".equals(prefManager.getString("user_reported_" + pack.getUserid()));
    }

    public boolean checkSUBSCRIBED() {
        PrefManager prefManager = new PrefManager(getApplicationContext());
        return "TRUE".equals(prefManager.getString("SUBSCRIBED"));
    }

    public void showAdsBanner() {
        if (checkSUBSCRIBED()) {
            return;
        }
        bannerAdManager = BannerAdManager.into(this, findViewById(R.id.linear_layout_ads));
        if (bannerAdManager != null) {
            bannerAdManager.load();
        }
    }

    @Override
    protected void onDestroy() {
        if (bannerAdManager != null) {
            bannerAdManager.destroy();
        }
        super.onDestroy();
    }
}
