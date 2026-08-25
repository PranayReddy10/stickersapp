package com.stickersanimated.kissing.reels;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ReelApi;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The Reels tab: a grid of covers. Tapping one opens {@link ReelPlayerActivity},
 * which is where the full screen vertical swiping happens.
 *
 * The grid rather than an immediately full screen feed is deliberate: this tab
 * lives inside the home pager, under a toolbar and above a bottom bar, and a
 * full bleed video feed fighting that chrome looks broken.
 */
public class ReelsFragment extends Fragment {

    private static final int COLUMNS = 3;

    private final List<ReelApi> reels = new ArrayList<>();

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyView;
    private ReelGridAdapter adapter;

    private int page = 0;
    private boolean loading = false;
    private boolean reachedEnd = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_reels, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_reels);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_reels);
        progressBar = view.findViewById(R.id.progress_bar_reels);
        emptyView = view.findViewById(R.id.linear_layout_reels_empty);

        adapter = new ReelGridAdapter(requireActivity(), reels, COLUMNS, this::openPlayer);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::reload);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || reachedEnd) {
                    return;
                }
                final GridLayoutManager manager = (GridLayoutManager) rv.getLayoutManager();
                if (manager == null) {
                    return;
                }
                // Fetch the next page a row early so scrolling does not stall.
                if (manager.findLastVisibleItemPosition() >= reels.size() - COLUMNS) {
                    load();
                }
            }
        });

        load();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // A like or a delete in the player should be reflected when coming back.
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void reload() {
        page = 0;
        reachedEnd = false;
        reels.clear();
        adapter.notifyDataSetChanged();
        load();
    }

    private void openPlayer(int position) {
        final Intent intent = new Intent(requireContext(), ReelPlayerActivity.class);
        intent.putExtra(ReelPlayerActivity.EXTRA_REELS, new ArrayList<>(reels));
        intent.putExtra(ReelPlayerActivity.EXTRA_START, position);
        intent.putExtra(ReelPlayerActivity.EXTRA_PAGE, page);
        startActivity(intent);
    }

    private void load() {
        if (loading || reachedEnd) {
            return;
        }
        loading = true;
        progressBar.setVisibility(reels.isEmpty() ? View.VISIBLE : View.GONE);

        final PrefManager prefManager = new PrefManager(requireContext().getApplicationContext());
        final apiRest service = apiClient.getClient().create(apiRest.class);

        service.reelFeed(page, viewerId(prefManager)).enqueue(new Callback<List<ReelApi>>() {
            @Override
            public void onResponse(@NonNull Call<List<ReelApi>> call,
                                   @NonNull Response<List<ReelApi>> response) {
                if (!isAdded()) {
                    return;
                }
                finishLoading();
                if (!response.isSuccessful() || response.body() == null) {
                    showEmptyIfNeeded();
                    return;
                }
                final List<ReelApi> batch = response.body();
                if (batch.isEmpty()) {
                    reachedEnd = true;
                } else {
                    final int from = reels.size();
                    reels.addAll(batch);
                    adapter.notifyItemRangeInserted(from, batch.size());
                    page++;
                }
                showEmptyIfNeeded();
            }

            @Override
            public void onFailure(@NonNull Call<List<ReelApi>> call, @NonNull Throwable t) {
                if (!isAdded()) {
                    return;
                }
                finishLoading();
                showEmptyIfNeeded();
            }
        });
    }

    /** 0 when signed out; it only decides whether reels come back marked as liked. */
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

    private void finishLoading() {
        loading = false;
        progressBar.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private void showEmptyIfNeeded() {
        emptyView.setVisibility(reels.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
