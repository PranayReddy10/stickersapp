package com.stickersanimated.kissing.reels;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.stickersanimated.kissing.R;
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
    }

    private final Activity activity;
    /** Null entries are ad pages, so page indexes line up with what is on screen. */
    private final List<ReelApi> reels;
    private final Listener listener;

    public ReelPagerAdapter(Activity activity, List<ReelApi> reels, Listener listener) {
        this.activity = activity;
        this.reels = reels;
        this.listener = listener;
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
            return; // the ad page loads once, in its holder
        }
        final ReelHolder holder = (ReelHolder) viewHolder;
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

        // Double tap to like, the gesture everyone already expects here.
        final GestureDetector detector = new GestureDetector(activity,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
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
        holder.like.setImageResource(reel.isLiked()
                ? R.drawable.ic_favorite_black : R.drawable.ic_favorite_border);
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
    public int getItemCount() {
        return reels.size();
    }

    /** Runs the ad waterfall once per created page. */
    class AdHolder extends RecyclerView.ViewHolder {
        AdHolder(@NonNull View itemView) {
            super(itemView);
            final NativeAdManager manager = NativeAdManager.into(activity,
                    itemView.findViewById(R.id.frame_layout_fullscreen_ad));
            if (manager != null) {
                manager.load();
            }
        }
    }

    public static class ReelHolder extends RecyclerView.ViewHolder {
        public final PlayerView playerView;
        public final ImageView poster;
        public final ProgressBar progressBar;
        final ImageView burst;
        final ImageView like;
        final ImageView share;
        final ImageView more;
        final ImageView authorImage;
        final ImageView verified;
        final TextView author;
        final TextView caption;
        final TextView likes;
        final TextView follow;

        ReelHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view_reel);
            poster = itemView.findViewById(R.id.image_view_reel);
            progressBar = itemView.findViewById(R.id.progress_bar_reel);
            burst = itemView.findViewById(R.id.image_view_burst);
            like = itemView.findViewById(R.id.image_view_like);
            share = itemView.findViewById(R.id.image_view_share_reel);
            more = itemView.findViewById(R.id.image_view_more_reel);
            authorImage = itemView.findViewById(R.id.image_view_reel_author);
            verified = itemView.findViewById(R.id.image_view_reel_verified);
            author = itemView.findViewById(R.id.text_view_reel_author);
            caption = itemView.findViewById(R.id.text_view_reel_caption);
            likes = itemView.findViewById(R.id.text_view_likes);
            follow = itemView.findViewById(R.id.text_view_reel_follow);
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
