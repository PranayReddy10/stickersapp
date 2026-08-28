package com.stickersanimated.kissing.reels;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ReelApi;
import com.stickersanimated.kissing.ui.LoginActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The reels grid: the same feed as the Reels tab, seen all at once.
 *
 * <p>The card feed shows who posted a reel and what they said about it, which is worth a
 * screen each; this is for finding something to watch. A tile hands the whole loaded list
 * to the player, opened at the tile that was tapped, so both routes end in the same place.
 */
public class ReelsGridActivity extends AppCompatActivity implements ReelTileAdapter.Listener {

    /** Reels are three to a row here, so pages of three keep the last row full. */
    private static final int COLUMNS = 3;

    private final List<ReelApi> reels = new ArrayList<>();

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyView;
    private ReelTileAdapter adapter;
    private PrefManager prefManager;

    private int page = 0;
    private boolean loading;
    private boolean reachedEnd;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels_grid);

        prefManager = new PrefManager(getApplicationContext());

        final Toolbar toolbar = findViewById(R.id.toolbar_reels_grid);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.reels_browse);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_view_reels_grid);
        swipeRefresh = findViewById(R.id.swipe_refresh_reels_grid);
        progressBar = findViewById(R.id.progress_bar_reels_grid);
        emptyView = findViewById(R.id.linear_layout_grid_empty);

        adapter = new ReelTileAdapter(this, reels, this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::reload);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy <= 0 || loading || reachedEnd) {
                    return;
                }
                final GridLayoutManager manager = (GridLayoutManager) view.getLayoutManager();
                if (manager != null
                        && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - COLUMNS) {
                    load();
                }
            }
        });

        load();
    }

    private void reload() {
        page = 0;
        reachedEnd = false;
        reels.clear();
        adapter.notifyDataSetChanged();
        load();
    }

    private void load() {
        if (loading || reachedEnd) {
            return;
        }
        loading = true;
        progressBar.setVisibility(reels.isEmpty() ? View.VISIBLE : View.GONE);

        apiClient.getClient().create(apiRest.class)
                .reelFeed(page, ReelsFragment.viewerId(prefManager))
                .enqueue(new Callback<List<ReelApi>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ReelApi>> call,
                                           @NonNull Response<List<ReelApi>> response) {
                        finishLoading();
                        final List<ReelApi> batch = response.body();
                        if (batch == null || batch.isEmpty()) {
                            reachedEnd = true;
                            emptyView.setVisibility(reels.isEmpty() ? View.VISIBLE : View.GONE);
                            return;
                        }
                        final int from = reels.size();
                        reels.addAll(batch);
                        page++;
                        adapter.notifyItemRangeInserted(from + 1, batch.size());
                        emptyView.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ReelApi>> call,
                                          @NonNull Throwable t) {
                        finishLoading();
                        emptyView.setVisibility(reels.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void finishLoading() {
        loading = false;
        progressBar.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onOpen(ReelApi reel) {
        final Intent intent = new Intent(this, ReelPlayerActivity.class);
        intent.putExtra(ReelPlayerActivity.EXTRA_REELS, new ArrayList<>(reels));
        intent.putExtra(ReelPlayerActivity.EXTRA_START, Math.max(0, reels.indexOf(reel)));
        intent.putExtra(ReelPlayerActivity.EXTRA_PAGE, page);
        startActivity(intent);
    }

    @Override
    public void onNewReel() {
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        startActivity(new Intent(this, UploadReelActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A reel posted or deleted while this screen was away should not still be here.
        if (!reels.isEmpty()) {
            reload();
        }
    }
}
