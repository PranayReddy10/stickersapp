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
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.entity.ReelApi;

import java.util.List;
import java.util.Locale;

/** The Reels tab grid: a thumbnail per reel, tapping one opens the full screen player. */
public class ReelGridAdapter extends RecyclerView.Adapter<ReelGridAdapter.GridHolder> {

    /** Portrait cells, the shape reels are actually shot in. */
    private static final float CELL_ASPECT = 16f / 9f;

    public interface OnReelClick {
        void onReelClick(int position);
    }

    private final Activity activity;
    private final List<ReelApi> reels;
    private final OnReelClick listener;
    private final int columns;

    public ReelGridAdapter(Activity activity, List<ReelApi> reels, int columns, OnReelClick listener) {
        this.activity = activity;
        this.reels = reels;
        this.columns = columns;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GridHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new GridHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reel_grid, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull GridHolder holder, int position) {
        final ReelApi reel = reels.get(position);

        // A FrameLayout cannot derive its height from a child's aspect ratio, so the
        // cell is sized here from the column width.
        final int cellWidth = activity.getResources().getDisplayMetrics().widthPixels / columns;
        final ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
        params.height = (int) (cellWidth * CELL_ASPECT);
        holder.itemView.setLayoutParams(params);

        Glide.with(activity)
                .load(reel.getThumb())
                .placeholder(R.drawable.sticker_error)
                .into(holder.thumb);

        holder.views.setText(formatCount(reel.getViews()));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReelClick(holder.getBindingAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return reels.size();
    }

    /** 1200 -> 1.2K, so a long count cannot push the cell around. */
    static String formatCount(int value) {
        if (value < 1000) {
            return String.valueOf(value);
        }
        if (value < 1000000) {
            return String.format(Locale.US, "%.1fK", value / 1000f).replace(".0", "");
        }
        return String.format(Locale.US, "%.1fM", value / 1000000f).replace(".0", "");
    }

    static class GridHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView views;

        GridHolder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.image_view_grid_thumb);
            views = itemView.findViewById(R.id.text_view_grid_views);
        }
    }
}
