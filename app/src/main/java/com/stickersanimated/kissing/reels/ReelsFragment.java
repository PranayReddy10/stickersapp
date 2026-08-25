package com.stickersanimated.kissing.reels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.JsonObject;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.ads.AdsConfig;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ReelApi;
import com.stickersanimated.kissing.ui.LoginActivity;
import com.stickersanimated.kissing.ui.SupportActivity;
import com.stickersanimated.kissing.ui.UserActivity;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The Reels tab: a card feed with All / Photos / Videos filters and a native ad
 * card every few reels. Tapping a card's media opens the full screen player.
 */
public class ReelsFragment extends Fragment implements ReelCardAdapter.Listener {

    private static final String FILTER_ALL = "all";
    private static final String FILTER_PHOTO = "photo";
    private static final String FILTER_VIDEO = "video";

    /** Everything the feed has fetched, before filtering. */
    private final List<ReelApi> loaded = new ArrayList<>();
    /** What is on screen, ads included. */
    private final List<ReelCardAdapter.Row> rows = new ArrayList<>();

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyView;
    private TextView chipAll;
    private TextView chipPhotos;
    private TextView chipVideos;
    private ReelCardAdapter adapter;

    private String filter = FILTER_ALL;
    private int page = 0;
    private int reelsBetweenAds = 3;
    private boolean adsEnabled;
    private boolean loading;
    private boolean reachedEnd;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_reels, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_reels);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_reels);
        progressBar = view.findViewById(R.id.progress_bar_reels);
        emptyView = view.findViewById(R.id.linear_layout_reels_empty);
        chipAll = view.findViewById(R.id.chip_reels_all);
        chipPhotos = view.findViewById(R.id.chip_reels_photos);
        chipVideos = view.findViewById(R.id.chip_reels_videos);

        final AdsConfig adsConfig = new AdsConfig(requireContext());
        adsEnabled = adsConfig.isEnabled(com.stickersanimated.kissing.ads.AdFormat.NATIVE);
        reelsBetweenAds = adsConfig.packsBetweenNativeAds();

        adapter = new ReelCardAdapter(requireActivity(), rows, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.fab_upload_reel).setOnClickListener(v -> {
            final PrefManager prefs = new PrefManager(requireContext().getApplicationContext());
            startActivity("TRUE".equals(prefs.getString("LOGGED"))
                    ? new Intent(requireContext(), UploadReelActivity.class)
                    : new Intent(requireContext(), LoginActivity.class));
        });

        chipAll.setOnClickListener(v -> applyFilter(FILTER_ALL));
        chipPhotos.setOnClickListener(v -> applyFilter(FILTER_PHOTO));
        chipVideos.setOnClickListener(v -> applyFilter(FILTER_VIDEO));
        highlightChips();

        swipeRefresh.setOnRefreshListener(this::reload);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || reachedEnd) {
                    return;
                }
                final LinearLayoutManager manager = (LinearLayoutManager) rv.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition() >= rows.size() - 2) {
                    load();
                }
            }
        });

        load();
        return view;
    }

    // ------------------------------------------------------------------ filters

    private void applyFilter(String next) {
        if (filter.equals(next)) {
            return;
        }
        filter = next;
        highlightChips();
        // Filtering is local to what has been fetched; the feed keeps paging as normal.
        rebuildRows();
        recyclerView.scrollToPosition(0);
    }

    private void highlightChips() {
        style(chipAll, FILTER_ALL.equals(filter));
        style(chipPhotos, FILTER_PHOTO.equals(filter));
        style(chipVideos, FILTER_VIDEO.equals(filter));
    }

    private void style(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_chip_selected : R.drawable.bg_chip_normal);
        chip.setTextColor(getResources().getColor(selected
                ? android.R.color.white : R.color.primary_text));
    }

    /** Rebuilds the visible rows from {@link #loaded}, dropping in the ad cards. */
    private void rebuildRows() {
        rows.clear();
        int sinceAd = 0;
        for (ReelApi reel : loaded) {
            if (!matchesFilter(reel)) {
                continue;
            }
            rows.add(ReelCardAdapter.reelRow(reel));
            if (adsEnabled && ++sinceAd >= reelsBetweenAds) {
                sinceAd = 0;
                rows.add(ReelCardAdapter.adRow());
            }
        }
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesFilter(ReelApi reel) {
        if (FILTER_ALL.equals(filter)) {
            return true;
        }
        return FILTER_VIDEO.equals(filter) == reel.isVideo();
    }

    // ------------------------------------------------------------------ loading

    private void reload() {
        page = 0;
        reachedEnd = false;
        loaded.clear();
        rebuildRows();
        load();
    }

    private void load() {
        if (loading || reachedEnd) {
            return;
        }
        loading = true;
        progressBar.setVisibility(page == 0 && loaded.isEmpty() ? View.VISIBLE : View.GONE);

        final PrefManager prefManager = new PrefManager(requireContext().getApplicationContext());
        apiClient.getClient().create(apiRest.class)
                .reelFeed(page, viewerId(prefManager))
                .enqueue(new Callback<List<ReelApi>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ReelApi>> call,
                                           @NonNull Response<List<ReelApi>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        finishLoading();
                        final List<ReelApi> batch = response.body();
                        if (batch == null || batch.isEmpty()) {
                            reachedEnd = true;
                            emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                            return;
                        }
                        loaded.addAll(batch);
                        page++;
                        rebuildRows();
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ReelApi>> call, @NonNull Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        finishLoading();
                        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void finishLoading() {
        loading = false;
        progressBar.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    /** 0 when signed out; it only decides the liked and following flags. */
    static Integer viewerId(PrefManager prefManager) {
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            return 0;
        }
        try {
            return Integer.parseInt(prefManager.getString("ID_USER"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** True when the reel belongs to the signed-in user, who cannot follow themselves. */
    static boolean isSelf(Context context, ReelApi reel) {
        final PrefManager prefManager = new PrefManager(context.getApplicationContext());
        return "TRUE".equals(prefManager.getString("LOGGED"))
                && reel.getUserid().equals(prefManager.getString("ID_USER"));
    }

    // ------------------------------------------------------------- card actions

    @Override
    public void onOpen(ReelApi reel) {
        // Only the reels matching the current filter, so the player swipes through
        // what the user can actually see.
        final ArrayList<ReelApi> visible = new ArrayList<>();
        for (ReelCardAdapter.Row row : rows) {
            if (!row.isAd()) {
                visible.add(row.reel);
            }
        }
        final Intent intent = new Intent(requireContext(), ReelPlayerActivity.class);
        intent.putExtra(ReelPlayerActivity.EXTRA_REELS, visible);
        intent.putExtra(ReelPlayerActivity.EXTRA_START, Math.max(0, visible.indexOf(reel)));
        intent.putExtra(ReelPlayerActivity.EXTRA_PAGE, page);
        startActivity(intent);
    }

    @Override
    public void onToggleLike(ReelApi reel) {
        final PrefManager prefManager = new PrefManager(requireContext().getApplicationContext());
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            startActivity(new Intent(requireContext(), LoginActivity.class));
            return;
        }
        final boolean wasLiked = reel.isLiked();
        reel.setLiked(!wasLiked);
        reel.setLikes(reel.getLikes() + (wasLiked ? -1 : 1));
        adapter.notifyDataSetChanged();

        apiClient.getClient().create(apiRest.class)
                .reelLike(reel.getId(), prefManager.getString("ID_USER"),
                        prefManager.getString("TOKEN_USER"))
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonObject> call,
                                           @NonNull Response<JsonObject> response) {
                        final JsonObject body = response.body();
                        if (body == null || !body.has("liked") || !isAdded()) {
                            return;
                        }
                        reel.setLiked("true".equals(body.get("liked").getAsString()));
                        if (body.has("likes")) {
                            reel.setLikes(body.get("likes").getAsInt());
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        reel.setLiked(wasLiked);
                        reel.setLikes(reel.getLikes() + (wasLiked ? 1 : -1));
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public void onShare(ReelApi reel) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, reel.getCaption().isEmpty()
                ? reel.getUrl() : reel.getCaption() + "\n\n" + reel.getUrl());
        startActivity(Intent.createChooser(intent, getString(R.string.reel_share)));
    }

    @Override
    public void onMore(ReelApi reel) {
        final Intent intent = new Intent(requireContext(), SupportActivity.class);
        intent.putExtra("message", "Hi Admin, please check this reel, id : " + reel.getId());
        startActivity(intent);
    }

    @Override
    public void onAuthor(ReelApi reel) {
        try {
            final Intent intent = new Intent(requireContext(), UserActivity.class);
            intent.putExtra("id", Integer.parseInt(reel.getUserid()));
            intent.putExtra("image", reel.getUserimage());
            intent.putExtra("name", reel.getUser());
            intent.putExtra("trusted", reel.isTrusted());
            startActivity(intent);
        } catch (NumberFormatException e) {
            Toasty.warning(requireContext(), "This reel has no author", Toast.LENGTH_SHORT).show();
        }
    }
}
