package com.stickersanimated.kissing.reels;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
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
@OptIn(markerClass = UnstableApi.class)
public class ReelCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_REEL = 1;
    private static final int TYPE_AD = 2;

    /**
     * Every card is 4:5, so two of them fit a screen and the next reel is always part
     * way into view. Portrait media is cropped to the frame instead of being letterboxed;
     * the full shape is what the player is for.
     */
    private static final float CARD_ASPECT = 5f / 4f;
    /** Card side margins in item_reel_card.xml, used before the card has been measured. */
    private static final int CARD_MARGIN_DP = 10;

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
    private final List<NativeAdManager> nativeAdManagers = new java.util.ArrayList<>();

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

        // Every card the same 4:5 frame, decided before the picture arrives so nothing
        // jumps while it loads.
        resize(card);
        // A card that was playing before it was recycled starts again as a still.
        card.playerView.setVisibility(View.GONE);
        card.playerView.setPlayer(null);
        card.muted.setVisibility(View.GONE);
        card.thumb.setVisibility(View.VISIBLE);
        // A picture that is wider than the frame is fitted rather than cropped to a
        // sliver of itself; anything taller fills it.
        card.thumb.setScaleType(isWide(reel)
                ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);

        Glide.with(activity).load(reel.getThumb())
                .placeholder(R.drawable.sticker_error)
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

    /** Gives the media frame the feed's one shape. */
    private void resize(CardHolder card) {
        final ViewGroup.LayoutParams params = card.media.getLayoutParams();
        final int height = Math.round(mediaWidth(card) * CARD_ASPECT);
        if (height > 0 && params.height != height) {
            params.height = height;
            card.media.setLayoutParams(params);
        }
    }

    /** True when the reel is wider than the card's frame, so cropping would gut it. */
    private static boolean isWide(ReelApi reel) {
        return reel.getWidth() > 0 && reel.getHeight() > 0
                && (float) reel.getHeight() / (float) reel.getWidth() < 1f;
    }

    /**
     * The width the picture actually gets. Measured once the card is on screen; until
     * then the screen width less the card margins, which is the same number.
     */
    private int mediaWidth(CardHolder card) {
        if (card.media.getWidth() > 0) {
            return card.media.getWidth();
        }
        final DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        final int margins = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                CARD_MARGIN_DP * 2, metrics));
        return Math.max(1, metrics.widthPixels - margins);
    }

    private void bindLike(CardHolder card, ReelApi reel) {
        card.like.setImageResource(reel.isLiked()
                ? R.drawable.ic_reel_heart_filled : R.drawable.ic_reel_heart_outline);
        card.likes.setText(ReelFormat.count(reel.getLikes()));
    }

    private void bindFollow(CardHolder card, ReelApi reel) {
        final boolean following = reel.isFollowing();
        card.follow.setText(following ? R.string.reel_following : R.string.reel_follow);
        card.follow.setTextColor(activity.getResources().getColor(following
                ? R.color.primary_text_light : R.color.green));
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
        final PlayerView playerView;
        final ImageView muted;

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
            playerView = itemView.findViewById(R.id.player_view_card);
            muted = itemView.findViewById(R.id.image_view_card_muted);
        }
    }

    /** Runs the ad waterfall once per created slot, like the pack list does. */
    class AdHolder extends RecyclerView.ViewHolder {
        AdHolder(@NonNull View itemView) {
            super(itemView);
            final NativeAdManager manager = NativeAdManager.into(activity,
                    itemView.findViewById(R.id.frame_layout_reel_ad));
            if (manager != null) {
                nativeAdManagers.add(manager);
                manager.load();
            }
        }
    }

    /** Releases the ad slots, which hold the activity and the network's ad object. */
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        for (NativeAdManager manager : nativeAdManagers) {
            manager.destroy();
        }
        nativeAdManagers.clear();
    }
}
