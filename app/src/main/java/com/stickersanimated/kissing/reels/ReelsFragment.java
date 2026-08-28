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

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
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
@OptIn(markerClass = UnstableApi.class)
public class ReelsFragment extends Fragment implements ReelCardAdapter.Listener {

    /** Argument: show only this author's reels, as on a profile page. */
    private static final String ARG_AUTHOR = "author";

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
    /**
     * One player for the whole feed, moved onto whichever card is most on screen. A
     * player per card would run the device out of video decoders in a few scrolls.
     */
    private ExoPlayer feedPlayer;
    private int playingPosition = RecyclerView.NO_POSITION;
    /**
     * Feed sound. It starts off - a list that talks the moment it is opened is the
     * fastest way to make somebody close an app - and stays however the viewer last
     * set it while the tab is open.
     */
    private boolean feedMuted = true;

    /** 0 for the main feed, otherwise the profile whose reels are being shown. */
    private int author;
    private String filter = FILTER_ALL;
    private int page = 0;
    private int reelsBetweenAds = 3;
    private boolean adsEnabled;
    private boolean loading;
    private boolean reachedEnd;

    /**
     * The same feed, narrowed to one author, for the Reels tab of a profile page.
     * The filter chips and the upload button are left off there: the page is already
     * about one person, and posting belongs on the Reels tab.
     */
    public static ReelsFragment forUser(int authorId) {
        final ReelsFragment fragment = new ReelsFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_AUTHOR, authorId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_reels, container, false);
        author = getArguments() == null ? 0 : getArguments().getInt(ARG_AUTHOR, 0);

        recyclerView = view.findViewById(R.id.recycler_view_reels);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_reels);
        progressBar = view.findViewById(R.id.progress_bar_reels);
        emptyView = view.findViewById(R.id.linear_layout_reels_empty);
        chipAll = view.findViewById(R.id.chip_reels_all);
        chipPhotos = view.findViewById(R.id.chip_reels_photos);
        chipVideos = view.findViewById(R.id.chip_reels_videos);

        final AdsConfig adsConfig = new AdsConfig(requireContext());
        adsEnabled = adsConfig.isEnabled(com.stickersanimated.kissing.ads.AdFormat.NATIVE);
        reelsBetweenAds = adsConfig.reelsBetweenNativeAds();

        adapter = new ReelCardAdapter(requireActivity(), rows, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // The upload button belongs to HomeActivity - see app_bar_home.xml - so it
        // cannot slide away with the pager.
        final View filters = view.findViewById(R.id.layout_reels_filters);
        final View header = view.findViewById(R.id.layout_reels_header);
        if (isProfileFeed()) {
            filters.setVisibility(View.GONE);
            // A profile page already says whose reels these are, and has its own tabs.
            header.setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.image_view_reels_grid).setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), ReelsGridActivity.class)));
            chipAll.setOnClickListener(v -> applyFilter(FILTER_ALL));
            chipPhotos.setOnClickListener(v -> applyFilter(FILTER_PHOTO));
            chipVideos.setOnClickListener(v -> applyFilter(FILTER_VIDEO));
            highlightChips();
        }

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

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                // Only once the scroll settles: starting a clip mid-flick would mean
                // preparing a file per card the finger passes.
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    playMostVisible();
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
        stopPlayback();
        recyclerView.post(this::playMostVisible);
    }

    /**
     * What actually went wrong, rather than "could not save your like".
     *
     * The server answers a refusal with its own message in the body; a rejected sign
     * in key comes back as a 404 page with no body at all. Both are worth seeing,
     * along with the reel the app asked about - a reel id of 0 means the database it
     * came from has no working primary key.
     */
    static String likeError(Response<JsonObject> response, JsonObject body, ReelApi reel) {
        if (body != null && body.has("message")) {
            return body.get("message").getAsString();
        }
        return "Like failed: server said " + response.code() + " for reel " + reel.getId();
    }

    /** True when this is a profile's Reels tab rather than the main feed. */
    private boolean isProfileFeed() {
        return author > 0;
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
        final apiRest service = apiClient.getClient().create(apiRest.class);
        final Integer viewer = viewerId(prefManager);
        final Call<List<ReelApi>> call = isProfileFeed()
                ? service.reelByUser(page, author, viewer)
                : service.reelFeed(page, viewer);
        call.enqueue(new Callback<List<ReelApi>>() {
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

    // ---------------------------------------------------------------- autoplay

    /**
     * Plays the video card that is most on screen, muted, and stops whichever was
     * playing before. Sound belongs to the full screen player; a feed that starts
     * talking on its own is the fastest way to make somebody close an app.
     */
    private void playMostVisible() {
        if (!isAdded() || recyclerView == null) {
            return;
        }
        final int position = mostVisibleVideo();
        if (position == RecyclerView.NO_POSITION) {
            stopPlayback();
            return;
        }
        if (position == playingPosition && feedPlayer != null) {
            feedPlayer.setPlayWhenReady(true);
            return;
        }
        final ReelCardAdapter.CardHolder holder = cardAt(position);
        if (holder == null) {
            return;
        }
        stopPlayback();

        if (feedPlayer == null) {
            feedPlayer = new ExoPlayer.Builder(requireContext()).build();
            feedPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        }
        feedPlayer.setVolume(feedMuted ? 0f : 1f);
        playingPosition = position;
        holder.playerView.setPlayer(feedPlayer);
        holder.playerView.setVisibility(View.VISIBLE);
        holder.play.setVisibility(View.GONE);
        holder.muted.setVisibility(View.VISIBLE);
        showSoundState(holder);
        feedPlayer.setMediaItem(MediaItem.fromUri(rows.get(position).reel.getUrl()));
        feedPlayer.prepare();
        feedPlayer.setPlayWhenReady(true);
    }

    /**
     * Sound on, or off again, for the card that is playing. The speaker only appears on
     * that card, so this is always about the reel the viewer is looking at.
     */
    @Override
    public void onToggleSound() {
        feedMuted = !feedMuted;
        if (feedPlayer != null) {
            feedPlayer.setVolume(feedMuted ? 0f : 1f);
        }
        final ReelCardAdapter.CardHolder holder = cardAt(playingPosition);
        if (holder != null) {
            showSoundState(holder);
        }
    }

    /** The speaker shows what a tap will do: crossed out while the sound is off. */
    private void showSoundState(ReelCardAdapter.CardHolder holder) {
        holder.muted.setImageResource(feedMuted
                ? R.drawable.ic_reel_volume_off : R.drawable.ic_reel_volume_on);
        holder.muted.setContentDescription(getString(feedMuted
                ? R.string.reel_sound_off : R.string.reel_sound_on));
    }

    /** Puts the playing card back to its still picture. */
    private void stopPlayback() {
        if (feedPlayer != null) {
            feedPlayer.stop();
        }
        final ReelCardAdapter.CardHolder holder = cardAt(playingPosition);
        if (holder != null) {
            holder.playerView.setPlayer(null);
            holder.playerView.setVisibility(View.GONE);
            holder.play.setVisibility(View.VISIBLE);
            holder.muted.setVisibility(View.GONE);
        }
        playingPosition = RecyclerView.NO_POSITION;
    }

    /** The video row showing the most of itself, or NO_POSITION when none is. */
    private int mostVisibleVideo() {
        final LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager == null) {
            return RecyclerView.NO_POSITION;
        }
        final int first = manager.findFirstVisibleItemPosition();
        final int last = manager.findLastVisibleItemPosition();
        int best = RecyclerView.NO_POSITION;
        int bestVisible = 0;
        for (int position = first; position <= last && position >= 0; position++) {
            if (position >= rows.size()) {
                break;
            }
            final ReelApi reel = rows.get(position).reel;
            if (reel == null || !reel.isVideo()
                    || reel.getUrl() == null || reel.getUrl().isEmpty()) {
                continue;
            }
            final ReelCardAdapter.CardHolder holder = cardAt(position);
            if (holder == null) {
                continue;
            }
            final int visible = visibleHeight(holder.media);
            // Half of the frame has to be on screen before it is worth playing.
            if (visible > bestVisible && visible * 2 >= holder.media.getHeight()) {
                bestVisible = visible;
                best = position;
            }
        }
        return best;
    }

    private int visibleHeight(View view) {
        final android.graphics.Rect bounds = new android.graphics.Rect();
        if (!view.getGlobalVisibleRect(bounds)) {
            return 0;
        }
        return bounds.height();
    }

    @Nullable
    private ReelCardAdapter.CardHolder cardAt(int position) {
        if (position == RecyclerView.NO_POSITION || recyclerView == null) {
            return null;
        }
        final RecyclerView.ViewHolder holder =
                recyclerView.findViewHolderForAdapterPosition(position);
        return holder instanceof ReelCardAdapter.CardHolder
                ? (ReelCardAdapter.CardHolder) holder : null;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (feedPlayer != null) {
            feedPlayer.setPlayWhenReady(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from the full screen player, the card under the finger picks up.
        if (recyclerView != null) {
            recyclerView.post(this::playMostVisible);
        }
    }

    @Override
    public void onDestroyView() {
        stopPlayback();
        if (feedPlayer != null) {
            feedPlayer.release();
            feedPlayer = null;
        }
        super.onDestroyView();
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
        intent.putExtra(ReelPlayerActivity.EXTRA_AUTHOR, author);
        startActivity(intent);
    }

    @Override
    public void onToggleLike(ReelApi reel) {
        final PrefManager prefManager = new PrefManager(requireContext().getApplicationContext());
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            // Say why before the login screen appears - a tap that silently swaps the
            // screen reads as a broken button.
            Toasty.info(requireContext(), getString(R.string.reel_sign_in_to_like),
                    Toast.LENGTH_SHORT).show();
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
                        if (!isAdded()) {
                            return;
                        }
                        final JsonObject body = response.body();
                        if (!response.isSuccessful() || body == null || !body.has("liked")) {
                            // The server refused it - a rejected sign in key comes back
                            // as a 404 page, not JSON. Put the heart back rather than
                            // leaving a like that was never saved.
                            reel.setLiked(wasLiked);
                            reel.setLikes(reel.getLikes() + (wasLiked ? 1 : -1));
                            adapter.notifyDataSetChanged();
                            Toasty.error(requireContext(), likeError(response, body, reel),
                                    Toast.LENGTH_LONG).show();
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
        ReelShare.start(requireContext(), reel);
    }

    @Override
    public void onMore(ReelApi reel) {
        // Your own reel is yours to remove; anybody else's can only be reported.
        if (!isSelf(requireContext(), reel)) {
            report(reel);
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setItems(new CharSequence[]{
                        getString(R.string.reel_delete), getString(R.string.reel_report)},
                        (dialog, which) -> {
                            if (which == 0) {
                                confirmDelete(reel);
                            } else {
                                report(reel);
                            }
                        })
                .show();
    }

    private void report(ReelApi reel) {
        final Intent intent = new Intent(requireContext(), SupportActivity.class);
        intent.putExtra("message", "Hi Admin, please check this reel, id : " + reel.getId());
        startActivity(intent);
    }

    private void confirmDelete(ReelApi reel) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.reel_delete)
                .setMessage(R.string.reel_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reel_delete, (dialog, which) -> delete(reel))
                .show();
    }

    private void delete(ReelApi reel) {
        final PrefManager prefManager = new PrefManager(requireContext().getApplicationContext());
        apiClient.getClient().create(apiRest.class)
                .reelDelete(reel.getId(), prefManager.getString("ID_USER"),
                        prefManager.getString("TOKEN_USER"))
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonObject> call,
                                           @NonNull Response<JsonObject> response) {
                        if (!isAdded()) {
                            return;
                        }
                        final JsonObject body = response.body();
                        final boolean deleted = body != null && body.has("code")
                                && body.get("code").getAsInt() == 200;
                        if (!deleted) {
                            Toasty.error(requireContext(), body != null && body.has("message")
                                    ? body.get("message").getAsString()
                                    : getString(R.string.reel_delete_failed),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        loaded.remove(reel);
                        rebuildRows();
                        Toasty.success(requireContext(), getString(R.string.reel_deleted),
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                        if (isAdded()) {
                            Toasty.error(requireContext(), getString(R.string.reel_delete_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
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
