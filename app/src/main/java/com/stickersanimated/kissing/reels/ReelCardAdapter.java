package com.stickersanimated.kissing.reels;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.ads.NativeAdManager;
import com.stickersanimated.kissing.entity.ReelApi;

import java.util.List;

/**
 * The Reels tab feed: a card per reel, with a native ad card slotted in every few
 * reels. Tapping the media opens the full screen player at that reel.
 *
 * Ads are rows in the same list rather than a separate view, so they scroll with
 * the feed and inherit its spacing.
 */
public class ReelCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_REEL = 1;
    private static final int TYPE_AD = 2;

    /** Used when the reel did not report its own size. */
    private static final float DEFAULT_ASPECT = 5f / 4f;
    /** Nothing taller than this, or one card fills the screen and the feed stops reading as a feed. */
    private static final float MAX_ASPECT = 16f / 9f;
    private static final float MIN_ASPECT = 3f / 4f;

    public interface Listener {
        void onOpen(ReelApi reel);

        void onToggleLike(ReelApi reel);

        void onShare(ReelApi reel);

        void onMore(ReelApi reel);

        void onAuthor(ReelApi reel);
    }

    /** A row in the feed: either a reel or an ad slot. */
    public static class Row {
        final ReelApi reel;

        Row(ReelApi reel) {
            this.reel = reel;
        }

        boolean isAd() {
            return reel == null;
        }
    }

    private final Activity activity;
    private final List<Row> rows;
    private final Listener listener;

    public ReelCardAdapter(Activity activity, List<Row> rows, Listener listener) {
        this.activity = activity;
        this.rows = rows;
        this.listener = listener;
    }

    public static Row reelRow(ReelApi reel) {
        return new Row(reel);
    }

    public static Row adRow() {
        return new Row(null);
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isAd() ? TYPE_AD : TYPE_REEL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_AD) {
            return new AdHolder(inflater.inflate(R.layout.item_reel_ad_card, parent, false));
        }
        return new CardHolder(inflater.inflate(R.layout.item_reel_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AdHolder) {
            return; // the ad loads once, in the holder's constructor
        }
        final CardHolder card = (CardHolder) holder;
        final ReelApi reel = rows.get(position).reel;

        card.author.setText(reel.getUser());
        card.time.setText(reel.getCreated());
        card.verified.setVisibility(reel.isTrusted() ? View.VISIBLE : View.GONE);
        card.caption.setText(reel.getCaption());
        card.caption.setVisibility(reel.getCaption().isEmpty() ? View.GONE : View.VISIBLE);
        card.views.setText(ReelFormat.count(reel.getViews()));
        card.play.setVisibility(reel.isVideo() ? View.VISIBLE : View.GONE);

        // Size the frame to the reel's own shape so a portrait video is not cropped
        // and a landscape one is not blown up. Clamped so a very tall or very wide
        // reel cannot take over the feed.
        float aspect = DEFAULT_ASPECT;
        if (reel.getWidth() > 0 && reel.getHeight() > 0) {
            aspect = (float) reel.getHeight() / (float) reel.getWidth();
        }
        aspect = Math.max(MIN_ASPECT, Math.min(MAX_ASPECT, aspect));

        resize(card, aspect);

        final boolean knownSize = reel.getWidth() > 0 && reel.getHeight() > 0;
        Glide.with(activity).load(reel.getThumb())
                .placeholder(R.drawable.sticker_error)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                   Target<Drawable> target, DataSource source,
                                                   boolean isFirstResource) {
                        // Reels uploaded before the app started recording width and
                        // height report zeros. Take the shape from the thumbnail itself
                        // so those cards fit too instead of falling back to a guess.
                        if (!knownSize && resource.getIntrinsicWidth() > 0) {
                            resize(card, (float) resource.getIntrinsicHeight()
                                    / (float) resource.getIntrinsicWidth());
                        }
                        return false;
                    }
                })
                .into(card.thumb);
        Glide.with(activity).load(reel.getUserimage()).placeholder(R.drawable.profile)
                .into(card.avatar);

        bindLike(card, reel);
        bindFollow(card, reel);

        card.media.setOnClickListener(v -> listener.onOpen(reel));
        card.like.setOnClickListener(v -> listener.onToggleLike(reel));
        card.share.setOnClickListener(v -> listener.onShare(reel));
        card.more.setOnClickListener(v -> listener.onMore(reel));
        card.avatar.setOnClickListener(v -> listener.onAuthor(reel));
        card.author.setOnClickListener(v -> listener.onAuthor(reel));
        card.follow.setOnClickListener(v ->
                ReelFollow.toggle(activity, reel, following -> bindFollow(card, reel)));
    }

    /** Sets the media frame's height from an aspect ratio, clamped to sane bounds. */
    private void resize(CardHolder card, float aspect) {
        final float clamped = Math.max(MIN_ASPECT, Math.min(MAX_ASPECT, aspect));
        final int width = activity.getResources().getDisplayMetrics().widthPixels;
        final ViewGroup.LayoutParams params = card.media.getLayoutParams();
        final int height = (int) (width * clamped);
        if (params.height != height) {
            params.height = height;
            card.media.setLayoutParams(params);
        }
    }

    private void bindLike(CardHolder card, ReelApi reel) {
        card.like.setImageResource(reel.isLiked()
                ? R.drawable.ic_reel_heart_filled : R.drawable.ic_reel_heart_outline);
        card.likes.setText(ReelFormat.count(reel.getLikes()));
    }

    private void bindFollow(CardHolder card, ReelApi reel) {
        final boolean following = reel.isFollowing();
        card.follow.setText(following ? R.string.reel_following : R.string.reel_follow);
        card.follow.setBackgroundResource(following
                ? R.drawable.bg_following_button : R.drawable.bg_follow_button);
        card.follow.setTextColor(activity.getResources().getColor(following
                ? R.color.primary_text : android.R.color.white));
        // Nothing to follow when the reel is the viewer's own or has no author.
        card.follow.setVisibility(ReelsFragment.isSelf(activity, reel) ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class CardHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final ImageView verified;
        final ImageView thumb;
        final ImageView play;
        final ImageView like;
        final ImageView share;
        final ImageView more;
        final TextView author;
        final TextView time;
        final TextView follow;
        final TextView likes;
        final TextView views;
        final TextView caption;
        final FrameLayout media;

        CardHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.image_view_card_avatar);
            verified = itemView.findViewById(R.id.image_view_card_verified);
            thumb = itemView.findViewById(R.id.image_view_card_media);
            play = itemView.findViewById(R.id.image_view_card_play);
            like = itemView.findViewById(R.id.image_view_card_like);
            share = itemView.findViewById(R.id.image_view_card_share);
            more = itemView.findViewById(R.id.image_view_card_more);
            author = itemView.findViewById(R.id.text_view_card_author);
            time = itemView.findViewById(R.id.text_view_card_time);
            follow = itemView.findViewById(R.id.text_view_card_follow);
            likes = itemView.findViewById(R.id.text_view_card_likes);
            views = itemView.findViewById(R.id.text_view_card_views);
            caption = itemView.findViewById(R.id.text_view_card_caption);
            media = itemView.findViewById(R.id.frame_layout_card_media);
        }
    }

    /** Runs the ad waterfall once per created slot, like the pack list does. */
    class AdHolder extends RecyclerView.ViewHolder {
        AdHolder(@NonNull View itemView) {
            super(itemView);
            final NativeAdManager manager = NativeAdManager.into(activity,
                    itemView.findViewById(R.id.frame_layout_reel_ad));
            if (manager != null) {
                manager.load();
            }
        }
    }
}
