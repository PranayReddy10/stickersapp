package com.stickersanimated.kissing.reels;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.widget.ImageViewCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.ads.BannerAdManager;
import com.stickersanimated.kissing.ads.NativeAdManager;
import com.stickersanimated.kissing.entity.ReelApi;

import java.util.List;

/**
 * One page per reel. The adapter owns no player: {@link ReelPlayerActivity} keeps a
 * single ExoPlayer and attaches it to whichever page is on screen, because holding a
 * player per page runs the device out of codecs after a handful of swipes.
 */
@OptIn(markerClass = UnstableApi.class)
public class ReelPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_REEL = 1;
    private static final int TYPE_AD = 2;

    public interface Listener {
        void onToggleLike(int position);

        void onShare(int position);

        void onMore(int position);

        void onAuthor(int position);

        void onToggleFollow(int position);

        /** The page's views are on screen and can hold the player now. */
        void onPageReady(int position);

        /** A single tap on the video: pause it, or start it again. */
        void onTogglePlayback(int position);
    }

    private final Activity activity;
    /** Null entries are ad pages, so page indexes line up with what is on screen. */
    private final List<ReelApi> reels;
    private final Listener listener;
    /** Room the banner takes at the bottom of the player, in pixels. */
    private int bottomInset;
    private RecyclerView recyclerView;
    /** The ad pages this feed has built, released with the list. */
    private final java.util.List<NativeAdManager> managers = new java.util.ArrayList<>();
    private final java.util.List<BannerAdManager> banners = new java.util.ArrayList<>();

    public ReelPagerAdapter(Activity activity, List<ReelApi> reels, Listener listener) {
        this.activity = activity;
        this.reels = reels;
        this.listener = listener;
    }

    /**
     * Lifts the page's controls clear of the banner across the bottom of the player.
     * Pages already on screen are moved straight away.
     */
    public void setBottomInset(int pixels) {
        if (bottomInset == pixels) {
            return;
        }
        bottomInset = pixels;
        if (recyclerView == null) {
            return;
        }
        // Moved on the pages themselves rather than through a rebind: rebinding the page
        // that is playing would drop its surface and black out the video.
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            final RecyclerView.ViewHolder holder =
                    recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (holder instanceof ReelHolder) {
                ((ReelHolder) holder).applyBottomInset(bottomInset);
            } else {
                holder.itemView.setPadding(0, 0, 0, bottomInset);
            }
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView view) {
        super.onAttachedToRecyclerView(view);
        recyclerView = view;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView view) {
        super.onDetachedFromRecyclerView(view);
        recyclerView = null;
        for (NativeAdManager manager : managers) {
            manager.destroy();
        }
        managers.clear();
        for (BannerAdManager banner : banners) {
            banner.destroy();
        }
        banners.clear();
    }

    @Override
    public int getItemViewType(int position) {
        return reels.get(position) == null ? TYPE_AD : TYPE_REEL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_AD) {
            return new AdHolder(inflater.inflate(R.layout.item_reel_fullscreen_ad, parent, false));
        }
        return new ReelHolder(inflater.inflate(R.layout.item_reel, parent, false));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (!(viewHolder instanceof ReelHolder)) {
            // The ad page loads once, in its holder; only the banner gap is per bind.
            viewHolder.itemView.setPadding(0, 0, 0, bottomInset);
            return;
        }
        final ReelHolder holder = (ReelHolder) viewHolder;
        holder.applyBottomInset(bottomInset);
        final ReelApi reel = reels.get(position);

        Glide.with(activity).load(reel.getThumb()).into(holder.poster);
        if (!reel.isVideo()) {
            // A photo is its own poster; load the full size over the thumbnail.
            Glide.with(activity).load(reel.getUrl()).into(holder.poster);
        }

        holder.poster.setVisibility(View.VISIBLE);
        holder.playerView.setVisibility(View.GONE);
        holder.progressBar.setVisibility(reel.isVideo() ? View.VISIBLE : View.GONE);

        holder.author.setText(reel.getUser());
        holder.verified.setVisibility(reel.isTrusted() ? View.VISIBLE : View.GONE);
        holder.views.setText(ReelFormat.count(reel.getViews()));
        holder.caption.setText(reel.getCaption());
        holder.caption.setVisibility(reel.getCaption().isEmpty() ? View.GONE : View.VISIBLE);
        Glide.with(activity).load(reel.getUserimage()).placeholder(R.drawable.profile)
                .into(holder.authorImage);

        bindLike(holder, reel);
        bindFollow(holder, reel);
        holder.follow.setOnClickListener(v ->
                listener.onToggleFollow(holder.getBindingAdapterPosition()));

        holder.like.setOnClickListener(v -> listener.onToggleLike(holder.getBindingAdapterPosition()));
        holder.share.setOnClickListener(v -> listener.onShare(holder.getBindingAdapterPosition()));
        holder.more.setOnClickListener(v -> listener.onMore(holder.getBindingAdapterPosition()));
        holder.authorImage.setOnClickListener(v -> listener.onAuthor(holder.getBindingAdapterPosition()));
        holder.author.setOnClickListener(v -> listener.onAuthor(holder.getBindingAdapterPosition()));

        // Double tap to like, the gesture everyone already expects here; a single tap
        // pauses, which is the other one.
        final GestureDetector detector = new GestureDetector(activity,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        final int at = holder.getBindingAdapterPosition();
                        if (at != RecyclerView.NO_POSITION) {
                            listener.onTogglePlayback(at);
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        final int at = holder.getBindingAdapterPosition();
                        if (at == RecyclerView.NO_POSITION) {
                            return true;
                        }
                        if (!reels.get(at).isLiked()) {
                            listener.onToggleLike(at);
                        }
                        holder.playBurst();
                        return true;
                    }
                });
        holder.itemView.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    /** Refreshes just the like control, so a tap does not rebind the whole page. */
    public void bindLike(@NonNull ReelHolder holder, @NonNull ReelApi reel) {
        // A liked heart keeps its own red; an unliked one is tinted white so it reads
        // against whatever the reel is showing behind it.
        holder.like.setImageResource(reel.isLiked()
                ? R.drawable.ic_reel_heart_filled : R.drawable.ic_reel_heart_outline);
        ImageViewCompat.setImageTintList(holder.like, reel.isLiked()
                ? null : ColorStateList.valueOf(Color.WHITE));
        holder.likes.setText(ReelFormat.count(reel.getLikes()));
    }

    /** Follow reads as a pill on the dark player rather than the feed's solid button. */
    public void bindFollow(@NonNull ReelHolder holder, @NonNull ReelApi reel) {
        holder.follow.setText(reel.isFollowing()
                ? R.string.reel_following : R.string.reel_follow);
        holder.follow.setVisibility(ReelsFragment.isSelf(activity, reel)
                ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        final int position = holder.getBindingAdapterPosition();
        if (holder instanceof ReelHolder) {
            ((ReelHolder) holder).applyBottomInset(bottomInset);
        }
        if (holder instanceof ReelHolder && position != RecyclerView.NO_POSITION) {
            // The very first page is attached after the pager has already been told to
            // play it, so the player had nowhere to draw: sound, no picture.
            listener.onPageReady(position);
        }
    }

    @Override
    public int getItemCount() {
        return reels.size();
    }

    /** Runs the ad waterfall once per created page. */
    class AdHolder extends RecyclerView.ViewHolder {
        AdHolder(@NonNull View itemView) {
            super(itemView);
            final ViewGroup slot = itemView.findViewById(R.id.frame_layout_fullscreen_ad);
            // Full bleed: the creative gets the whole page, like the reel either side.
            final NativeAdManager manager = NativeAdManager.fullscreen(activity, slot);
            if (manager == null) {
                return;
            }
            managers.add(manager);
            // A page with nothing on it is worse than a smaller ad: when no network has a
            // native ad, the 300x250 block sits in the middle of the page instead.
            manager.onEmpty(() -> {
                final BannerAdManager banner = BannerAdManager.mrec(activity, slot);
                if (banner != null) {
                    banners.add(banner);
                    banner.load();
                }
            }).load();
        }
    }

    public static class ReelHolder extends RecyclerView.ViewHolder {
        public final PlayerView playerView;
        public final ImageView poster;
        public final ProgressBar progressBar;
        public final ImageView pause;
        final ImageView burst;
        final ImageView like;
        final ImageView share;
        final ImageView more;
        final ImageView authorImage;
        final ImageView verified;
        final TextView author;
        final TextView caption;
        final TextView likes;
        final TextView views;
        final TextView follow;
        final View rail;
        final View bottomBlock;
        /** Margins the layout asks for, before the banner is taken into account. */
        final int railMargin;
        final int bottomMargin;

        ReelHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view_reel);
            poster = itemView.findViewById(R.id.image_view_reel);
            progressBar = itemView.findViewById(R.id.progress_bar_reel);
            pause = itemView.findViewById(R.id.image_view_reel_pause);
            burst = itemView.findViewById(R.id.image_view_burst);
            like = itemView.findViewById(R.id.image_view_like);
            share = itemView.findViewById(R.id.image_view_share_reel);
            more = itemView.findViewById(R.id.image_view_more_reel);
            authorImage = itemView.findViewById(R.id.image_view_reel_author);
            verified = itemView.findViewById(R.id.image_view_reel_verified);
            author = itemView.findViewById(R.id.text_view_reel_author);
            caption = itemView.findViewById(R.id.text_view_reel_caption);
            likes = itemView.findViewById(R.id.text_view_likes);
            views = itemView.findViewById(R.id.text_view_reel_views);
            follow = itemView.findViewById(R.id.text_view_reel_follow);
            rail = itemView.findViewById(R.id.layout_reel_rail);
            bottomBlock = itemView.findViewById(R.id.layout_reel_bottom);
            railMargin = marginBottom(rail);
            bottomMargin = marginBottom(bottomBlock);
        }

        void applyBottomInset(int inset) {
            setMarginBottom(rail, railMargin + inset);
            setMarginBottom(bottomBlock, bottomMargin + inset);
        }

        private static int marginBottom(View view) {
            return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin;
        }

        private static void setMarginBottom(View view, int value) {
            final ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (params.bottomMargin != value) {
                params.bottomMargin = value;
                view.setLayoutParams(params);
            }
        }

        void playBurst() {
            burst.setAlpha(1f);
            burst.setScaleX(0.6f);
            burst.setScaleY(0.6f);
            burst.animate().scaleX(1.15f).scaleY(1.15f).setDuration(180)
                    .withEndAction(() -> burst.animate().alpha(0f).setDuration(320).start())
                    .start();
        }
    }
}
