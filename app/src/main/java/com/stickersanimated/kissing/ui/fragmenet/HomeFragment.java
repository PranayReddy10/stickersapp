package com.stickersanimated.kissing.ui.fragmenet;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.orhanobut.hawk.Hawk;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.Sticker;
import com.stickersanimated.kissing.StickerPack;
import com.stickersanimated.kissing.adapter.StickerAdapter;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.CategoryApi;
import com.stickersanimated.kissing.entity.PackApi;
import com.stickersanimated.kissing.entity.SlideApi;
import com.stickersanimated.kissing.entity.StickerApi;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    // --- Views ---
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout errorLayout;
    private ImageView emptyListImage;
    private Button tryAgainButton;
    private RelativeLayout loadMoreLayout;

    // --- Adapters & Layouts ---
    private StickerAdapter adapter;
    private LinearLayoutManager layoutManager;

    // --- Data Lists ---
    private final ArrayList<StickerPack> stickerPacks = new ArrayList<>();
    private final ArrayList<CategoryApi> categoryList = new ArrayList<>();
    private final List<SlideApi> slideList = new ArrayList<>();

    // --- State Variables ---
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean isAllDataLoaded = false;
    private int adItemCounter = 0;
    private int linesBetweenAds = 8;
    private boolean areNativeAdsEnabled = false;

    private static final String TAG = "HomeFragment";

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initViews(view);
        initConfig();
        initListeners();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Start loading data only after the view is created
        loadInitialData();
    }

    private void initViews(View view) {
        loadMoreLayout = view.findViewById(R.id.relative_layout_load_more);
        tryAgainButton = view.findViewById(R.id.button_try_again);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_list);
        emptyListImage = view.findViewById(R.id.image_view_empty_list);
        errorLayout = view.findViewById(R.id.linear_layout_layout_error);
        recyclerView = view.findViewById(R.id.recycler_view_list);

        // Setup RecyclerView
        layoutManager = new LinearLayoutManager(requireContext());
        // **FIX:** Passing a copy of the lists to the adapter for better state separation if needed.
        adapter = new StickerAdapter(getActivity(), stickerPacks, slideList, categoryList, false);
       // recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    private void initConfig() {
        PrefManager prefManager = new PrefManager(requireContext());
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
                if (dy > 0) { // Check for scroll down
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    // **FIX:** Simplified and safer scroll listener logic
                    if (!isLoading && !isAllDataLoaded) {
                        if ((visibleItemCount + pastVisiblesItems) >= totalItemCount) {
                            fetchPacks(false); // Fetch next page, not initial load
                        }
                    }
                }
            }
        });
    }

    /**
     * Resets state and loads all initial data. Triggered by swipe-to-refresh.
     */
    private void loadInitialData() {
        swipeRefreshLayout.setRefreshing(true);
        // Reset all state variables
        currentPage = 0;
        adItemCounter = 0;
        isLoading = false;
        isAllDataLoaded = false;

        // Clear all data lists
        stickerPacks.clear();
        slideList.clear();
        categoryList.clear();
        adapter.notifyDataSetChanged(); // Clear the UI immediately

        showLoadingLayout();
        fetchSlidesAndCategories();
    }

    /**
     * **REFACTORED:** Fetches both slides and categories. Once both are done, fetches the first page of packs.
     */
    private void fetchSlidesAndCategories() {
        apiClient.getClient().create(apiRest.class).slideAll().enqueue(new Callback<List<SlideApi>>() {
            @Override
            public void onResponse(@NonNull Call<List<SlideApi>> call, @NonNull Response<List<SlideApi>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    slideList.addAll(response.body());
                    // Add a placeholder for the slider view type
                    boolean sliderViewExists = false;
                    for (StickerPack pack : stickerPacks) {
                        if (pack.getViewType() == 2) {
                            sliderViewExists = true;
                            break; // Exit the loop once found
                        }
                    }

                    if (!sliderViewExists) {
                        stickerPacks.add(new StickerPack().setViewType(2));
                    }
                }
                // Always fetch categories, even if slides fail
                fetchCategories();
            }

            @Override
            public void onFailure(@NonNull Call<List<SlideApi>> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to fetch slides", t);
                fetchCategories(); // Attempt to load the rest of the app
            }
        });
    }

    private void fetchCategories() {
        apiClient.getClient().create(apiRest.class).PopularCategories().enqueue(new Callback<List<CategoryApi>>() {
            @Override
            public void onResponse(@NonNull Call<List<CategoryApi>> call, @NonNull Response<List<CategoryApi>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    for(CategoryApi category : response.body()){
                        categoryList.add(category.setViewType(3));
                    }
                    // Add a placeholder for the categories view type
                    boolean sliderViewExists = false;
                    for (StickerPack pack : stickerPacks) {
                        if (pack.getViewType() == 2) {
                            sliderViewExists = true;
                            break; // Exit the loop once found
                        }
                    }

                    if (!sliderViewExists) {
                        stickerPacks.add(new StickerPack().setViewType(2));
                    }
                }
                // Now fetch the first page of actual sticker packs
                fetchPacks(true);
            }

            @Override
            public void onFailure(@NonNull Call<List<CategoryApi>> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to fetch categories", t);
                fetchPacks(true); // Attempt to load packs anyway
            }
        });
    }


    /**
     * **REFACTORED & UNIFIED:** A single method to fetch sticker packs for both initial load and "load more".
     * @param isInitialLoad True if this is the first page of packs after a refresh.
     */
    private void fetchPacks(final boolean isInitialLoad) {
        if (isLoading) return; // Prevent simultaneous requests

        isLoading = true;
        if (!isInitialLoad) {
            loadMoreLayout.setVisibility(View.VISIBLE);
        }

        apiClient.getClient().create(apiRest.class).packsAll(currentPage, "created").enqueue(new Callback<List<PackApi>>() {
            @Override
            public void onResponse(@NonNull Call<List<PackApi>> call, @NonNull Response<List<PackApi>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PackApi> newPacks = response.body();

                    if (newPacks.isEmpty()) {
                        isAllDataLoaded = true; // No more pages to load
                        if (isInitialLoad && stickerPacks.isEmpty()) {
                            showEmptyLayout(); // Nothing loaded at all
                        }
                    } else {
                        processPacks(newPacks);
                        currentPage++; // Increment page only on success with data
                        showContentLayout();
                    }
                } else {
                    // Handle server errors (404, 500, etc.)
                    if (isInitialLoad) {
                        showErrorLayout();
                    }
                }
                finishLoading();
            }

            @Override
            public void onFailure(@NonNull Call<List<PackApi>> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to fetch packs", t);
                if (isInitialLoad) {
                    showErrorLayout();
                }
                finishLoading();
            }
        });
    }

    /**
     * **NEW:** Processes the list of packs from the API and adds them to the main list.
     */
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
            // **FIX:** Use more efficient notifyItemRangeInserted
            adapter.notifyItemRangeInserted(initialSize, packsToAdd.size());
        }
    }

    /**
     * **NEW & REFACTORED:** Centralized logic for creating a StickerPack object from API data.
     */
    private StickerPack createStickerPackFromApi(PackApi packApi) {
        // **FIX:** Use a LOCAL list for stickers, not a member variable.
        ArrayList<Sticker> stickersForThisPack = new ArrayList<>();
        List<StickerApi> stickerApiList = packApi.getStickers();

        if (stickerApiList != null) {
            for (StickerApi stickerApi : stickerApiList) {
                stickersForThisPack.add(new Sticker(
                        stickerApi.getImageFileThum(),
                        stickerApi.getImageFile(),
                        getLastBitFromUrl(stickerApi.getImageFile()).replace(".png", ".webp"),
                        new ArrayList<>() // Emojis should be part of the sticker data, not a shared list
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

        // Save stickers to Hawk and associate them with the pack
        Hawk.put(String.valueOf(packApi.getIdentifier()), stickersForThisPack);
        newStickerPack.setStickers(stickersForThisPack);
        newStickerPack.packApi = packApi;
        return newStickerPack;
    }

    /**
     * **NEW:** Centralized logic for creating an Ad placeholder.
     */
    private StickerPack createAdStickerPack() {
        PrefManager prefManager = new PrefManager(requireContext());
        String adType = prefManager.getString("ADMIN_NATIVE_TYPE");
        if ("ADMOB".equals(adType)) {
            return new StickerPack().setViewType(6);
        } else if ("MAX".equals(adType)) {
            return new StickerPack().setViewType(7);
        }
        return null; // Should not happen if areNativeAdsEnabled is true
    }

    /**
     * **NEW:** Centralized method to finish a loading cycle.
     */
    private void finishLoading() {
        isLoading = false;
        swipeRefreshLayout.setRefreshing(false);
        loadMoreLayout.setVisibility(View.GONE);
    }

    // --- UI State Management Methods ---
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

    // --- Utility Methods ---
    private static String getLastBitFromUrl(final String url) {
        if (url == null || url.isEmpty()) return "";
        return url.replaceFirst(".*/([^/?]+).*", "$1");
    }

    public boolean isNotBlocked(PackApi pack) {
        if (getActivity() == null) return false;
        final PrefManager prefManager = new PrefManager(getActivity());
        // **FIX:** Your original logic was inverted. It returned true (not blocked) if the user WAS reported.
        // This returns false if the user is reported, which seems correct.
        return !"TRUE".equals(prefManager.getString("user_reported_" + pack.getUserid()));
    }
}
