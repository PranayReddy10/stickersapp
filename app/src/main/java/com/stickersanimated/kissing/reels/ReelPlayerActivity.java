package com.stickersanimated.kissing.reels;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.JsonObject;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.ads.AdFormat;
import com.stickersanimated.kissing.ads.AdsConfig;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ReelApi;
import com.stickersanimated.kissing.ui.LoginActivity;
import com.stickersanimated.kissing.ui.SupportActivity;
import com.stickersanimated.kissing.ui.UserActivity;
import com.stickersanimated.kissing.utils.EdgeToEdgeHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Full screen vertical reel feed.
 *
 * One ExoPlayer is kept for the whole activity and moved onto whichever page is
 * showing. A player per page would exhaust the device's video decoders after a
 * few swipes, which on most phones means playback silently stops working.
 */
@OptIn(markerClass = UnstableApi.class)
public class ReelPlayerActivity extends AppCompatActivity
        implements ReelPagerAdapter.Listener, EdgeToEdgeHelper.FullBleed {

    public static final String EXTRA_REELS = "reels";
    public static final String EXTRA_START = "start";
    public static final String EXTRA_PAGE = "page";

    private final List<ReelApi> reels = new ArrayList<>();
    /** Reels already counted this session, so a swipe back does not inflate views. */
    private final Set<String> counted = new HashSet<>();

    private ViewPager2 viewPager;
    private ReelPagerAdapter adapter;
    private ExoPlayer player;
    private PrefManager prefManager;

    private int page;
    private int currentPosition = -1;
    private int reelsBetweenAds = 3;
    private boolean adsEnabled;
    private boolean loading;
    private boolean reachedEnd;

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reel_player);

        prefManager = new PrefManager(getApplicationContext());

        final AdsConfig adsConfig = new AdsConfig(this);
        adsEnabled = adsConfig.isEnabled(AdFormat.NATIVE);
        reelsBetweenAds = adsConfig.packsBetweenNativeAds();

        final ArrayList<ReelApi> initial =
                (ArrayList<ReelApi>) getIntent().getSerializableExtra(EXTRA_REELS);
        page = getIntent().getIntExtra(EXTRA_PAGE, 0);
        final int startReel = getIntent().getIntExtra(EXTRA_START, 0);

        // Ad pages are null entries, so a page index is also a list index and the
        // player never has to translate between the two.
        int start = startReel;
        if (initial != null) {
            int sinceAd = 0;
            for (int i = 0; i < initial.size(); i++) {
                if (i == startReel) {
                    start = reels.size();
                }
                reels.add(initial.get(i));
                if (adsEnabled && ++sinceAd >= reelsBetweenAds && i < initial.size() - 1) {
                    sinceAd = 0;
                    reels.add(null);
                }
            }
        }

        if (reels.isEmpty()) {
            finish();
            return;
        }

        viewPager = findViewById(R.id.view_pager_reels);
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        adapter = new ReelPagerAdapter(this, reels, this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(1);

        final View close = findViewById(R.id.image_view_close_reels);
        close.setOnClickListener(v -> finish());
        // This screen draws under the bars, so the close button moves itself clear of
        // the status bar rather than the whole page being padded away from it.
        ViewCompat.setOnApplyWindowInsetsListener(close, (v, insets) -> {
            final int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            final ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.topMargin = top;
            v.setLayoutParams(params);
            return insets;
        });

        player = new ExoPlayer.Builder(this).build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                final ReelPagerAdapter.ReelHolder holder = holderAt(currentPosition);
                if (holder == null) {
                    return;
                }
                holder.progressBar.setVisibility(state == Player.STATE_BUFFERING
                        ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) {
                    // Only drop the poster once there is a real frame behind it.
                    holder.playerView.setVisibility(View.VISIBLE);
                    holder.poster.setVisibility(View.GONE);
                }
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                playAt(position);
                if (position >= reels.size() - 3) {
                    loadMore();
                }
            }
        });

        // start is reassigned while the ad pages are folded in, so it cannot be
        // captured by the lambda directly.
        final int startPage = start;
        viewPager.post(() -> {
            viewPager.setCurrentItem(startPage, false);
            playAt(startPage);
        });
    }

    /** Moves the single player onto {@code position} and starts it. */
    private void playAt(int position) {
        if (position < 0 || position >= reels.size() || player == null) {
            return;
        }
        currentPosition = position;
        final ReelApi reel = reels.get(position);
        if (reel == null) {
            player.stop();
            return;
        }

        // Detach from every attached page first, or the previous page keeps the
        // surface and the new one renders nothing.
        for (int i = 0; i < reels.size(); i++) {
            final ReelPagerAdapter.ReelHolder other = holderAt(i);
            if (other != null && i != position) {
                other.playerView.setPlayer(null);
                other.playerView.setVisibility(View.GONE);
                other.poster.setVisibility(View.VISIBLE);
            }
        }

        final ReelPagerAdapter.ReelHolder holder = holderAt(position);
        if (!reel.isVideo()) {
            player.stop();
            if (holder != null) {
                holder.playerView.setPlayer(null);
                holder.playerView.setVisibility(View.GONE);
                holder.poster.setVisibility(View.VISIBLE);
                holder.progressBar.setVisibility(View.GONE);
            }
            countView(reel);
            return;
        }

        if (holder != null) {
            holder.playerView.setPlayer(player);
            holder.progressBar.setVisibility(View.VISIBLE);
        }
        player.setMediaItem(MediaItem.fromUri(reel.getUrl()));
        player.prepare();
        player.setPlayWhenReady(true);
        countView(reel);
    }

    private ReelPagerAdapter.ReelHolder holderAt(int position) {
        if (position < 0 || viewPager == null) {
            return null;
        }
        final View child = viewPager.getChildAt(0);
        if (!(child instanceof RecyclerView)) {
            return null;
        }
        final RecyclerView.ViewHolder holder =
                ((RecyclerView) child).findViewHolderForAdapterPosition(position);
        return holder instanceof ReelPagerAdapter.ReelHolder
                ? (ReelPagerAdapter.ReelHolder) holder : null;
    }

    private void countView(ReelApi reel) {
        if (!counted.add(reel.getId())) {
            return;
        }
        apiClient.getClient().create(apiRest.class).reelView(reel.getId())
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonObject> call,
                                           @NonNull Response<JsonObject> response) {
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                        // A missed view count is not worth telling the user about.
                    }
                });
    }

    // ------------------------------------------------------------- interactions

    @Override
    public void onToggleLike(int position) {
        if (reelAt(position) == null) {
            return;
        }
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        final ReelApi reel = reels.get(position);

        // Flip straight away and correct from the reply; waiting on the network for
        // a like makes the whole feed feel broken.
        final boolean wasLiked = reel.isLiked();
        reel.setLiked(!wasLiked);
        reel.setLikes(reel.getLikes() + (wasLiked ? -1 : 1));
        refreshLike(position, reel);

        apiClient.getClient().create(apiRest.class)
                .reelLike(reel.getId(), prefManager.getString("ID_USER"),
                        prefManager.getString("TOKEN_USER"))
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonObject> call,
                                           @NonNull Response<JsonObject> response) {
                        final JsonObject body = response.body();
                        if (body == null || !body.has("liked")) {
                            return;
                        }
                        reel.setLiked("true".equals(body.get("liked").getAsString()));
                        if (body.has("likes")) {
                            reel.setLikes(body.get("likes").getAsInt());
                        }
                        refreshLike(position, reel);
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                        // Put it back the way it was rather than showing a like that
                        // did not happen.
                        reel.setLiked(wasLiked);
                        reel.setLikes(reel.getLikes() + (wasLiked ? 1 : -1));
                        refreshLike(position, reel);
                    }
                });
    }

    @Override
    public void onToggleFollow(int position) {
        final ReelApi reel = reelAt(position);
        if (reel == null) {
            return;
        }
        ReelFollow.toggle(this, reel, following -> {
            final ReelPagerAdapter.ReelHolder holder = holderAt(position);
            if (holder != null) {
                adapter.bindFollow(holder, reel);
            }
        });
    }

    /** Null for an ad page or an index that has scrolled out of range. */
    private ReelApi reelAt(int position) {
        if (position < 0 || position >= reels.size()) {
            return null;
        }
        return reels.get(position);
    }

    private void refreshLike(int position, ReelApi reel) {
        final ReelPagerAdapter.ReelHolder holder = holderAt(position);
        if (holder != null) {
            adapter.bindLike(holder, reel);
        }
    }

    @Override
    public void onShare(int position) {
        final ReelApi reel = reelAt(position);
        if (reel == null) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, reel.getCaption().isEmpty()
                ? reel.getUrl()
                : reel.getCaption() + "\n\n" + reel.getUrl());
        startActivity(Intent.createChooser(intent, getString(R.string.reel_share)));
    }

    @Override
    public void onMore(int position) {
        final ReelApi reel = reelAt(position);
        if (reel == null) {
            return;
        }
        final Intent intent = new Intent(this, SupportActivity.class);
        intent.putExtra("message", "Hi Admin, please check this reel, id : " + reel.getId());
        startActivity(intent);
    }

    @Override
    public void onAuthor(int position) {
        final ReelApi reel = reelAt(position);
        if (reel == null) {
            return;
        }
        try {
            final Intent intent = new Intent(this, UserActivity.class);
            intent.putExtra("id", Integer.parseInt(reel.getUserid()));
            intent.putExtra("image", reel.getUserimage());
            intent.putExtra("name", reel.getUser());
            intent.putExtra("trusted", reel.isTrusted());
            startActivity(intent);
        } catch (NumberFormatException e) {
            Toasty.warning(this, "This reel has no author", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ paging

    private void loadMore() {
        if (loading || reachedEnd) {
            return;
        }
        loading = true;
        apiClient.getClient().create(apiRest.class)
                .reelFeed(page + 1, ReelsFragment.viewerId(prefManager))
                .enqueue(new Callback<List<ReelApi>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ReelApi>> call,
                                           @NonNull Response<List<ReelApi>> response) {
                        loading = false;
                        final List<ReelApi> batch = response.body();
                        if (batch == null || batch.isEmpty()) {
                            reachedEnd = true;
                            return;
                        }
                        page++;
                        final int from = reels.size();
                        int sinceAd = 0;
                        for (ReelApi fetched : batch) {
                            reels.add(fetched);
                            if (adsEnabled && ++sinceAd >= reelsBetweenAds) {
                                sinceAd = 0;
                                reels.add(null);
                            }
                        }
                        adapter.notifyItemRangeInserted(from, reels.size() - from);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ReelApi>> call, @NonNull Throwable t) {
                        loading = false;
                    }
                });
    }

    // ----------------------------------------------------------------- lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        final ReelApi current = reelAt(currentPosition);
        if (player != null && current != null && current.isVideo()) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
