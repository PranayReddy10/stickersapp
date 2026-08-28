package com.stickersanimated.kissing.reels;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.entity.ReelApi;

import java.util.List;
import java.util.Locale;

/**
 * The grid behind the reels tab's grid button: nine reels a screen instead of one and a
 * half. Nothing plays here - the tiles are still pictures, and a tap hands the whole list
 * to the full screen player, opened at the tile that was tapped.
 */
public class ReelTileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_NEW = 1;
    private static final int TYPE_REEL = 2;

    /** The shape a phone shoots in, so rows line up whatever was uploaded. */
    private static final float TILE_ASPECT = 14f / 9f;

    public interface Listener {
        void onOpen(ReelApi reel);

        void onNewReel();
    }

    private final Activity activity;
    private final List<ReelApi> reels;
    private final Listener listener;

    public ReelTileAdapter(Activity activity, List<ReelApi> reels, Listener listener) {
        this.activity = activity;
        this.reels = reels;
        this.listener = listener;
    }

    /** The first cell is the empty slot that starts an upload. */
    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_NEW : TYPE_REEL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View view = inflater.inflate(viewType == TYPE_NEW
                ? R.layout.item_reel_tile_new : R.layout.item_reel_tile, parent, false);
        return viewType == TYPE_NEW ? new NewHolder(view) : new TileHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        sizeTile(holder.itemView);
        if (holder instanceof NewHolder) {
            holder.itemView.setOnClickListener(v -> listener.onNewReel());
            return;
        }
        final TileHolder tile = (TileHolder) holder;
        final ReelApi reel = reels.get(position - 1);

        // A tile is a third of the screen wide: decoding the full picture for it is the
        // difference between a grid that scrolls and one that stutters.
        Glide.with(activity).load(reel.getThumb())
                .override(tileWidth(), Math.round(tileWidth() * TILE_ASPECT))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .placeholder(R.drawable.sticker_error)
                .into(tile.image);
        tile.views.setText(ReelFormat.count(reel.getViews()));
        tile.duration.setText(duration(reel));
        tile.duration.setVisibility(reel.isVideo() ? View.VISIBLE : View.GONE);
        tile.itemView.setOnClickListener(v -> listener.onOpen(reel));
    }

    private int tileWidth() {
        return activity.getResources().getDisplayMetrics().widthPixels / 3;
    }

    /** A tile is a third of the grid wide, and taller than it is wide. */
    private void sizeTile(View view) {
        final int width = tileWidth();
        final int height = Math.round(width * TILE_ASPECT);
        final ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != height) {
            params.height = height;
            view.setLayoutParams(params);
        }
    }

    private String duration(ReelApi reel) {
        final int seconds = reel.getDuration();
        if (seconds <= 0) {
            return "";
        }
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public int getItemCount() {
        return reels.size() + 1;
    }

    static class TileHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView views;
        final TextView duration;

        TileHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image_view_tile);
            views = itemView.findViewById(R.id.text_view_tile_views);
            duration = itemView.findViewById(R.id.text_view_tile_duration);
        }
    }

    static class NewHolder extends RecyclerView.ViewHolder {
        NewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
