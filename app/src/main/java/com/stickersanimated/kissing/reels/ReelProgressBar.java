package com.stickersanimated.kissing.reels;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.stickersanimated.kissing.R;

/**
 * The story style bar across the top of the player: one segment per reel, the current one
 * filling as the clip plays.
 *
 * <p>The feed is endless, so segments cover a block of {@link #SEGMENTS} pages at a time
 * rather than the whole list - a hundred hairlines would say nothing. Swiping past the end
 * of a block starts the next one, which is what the viewer sees anyway: how far through
 * this handful of reels they are.
 */
public class ReelProgressBar extends LinearLayout {

    /** Pages covered by one block of segments. */
    private static final int SEGMENTS = 8;

    private int blockStart = -1;
    private int blockSize;

    public ReelProgressBar(Context context) {
        this(context, null);
    }

    public ReelProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setClipChildren(false);
    }

    /** Draws the block holding {@code position}, out of {@code pageCount} pages. */
    public void showBlockFor(int position, int pageCount) {
        if (position < 0 || pageCount <= 0) {
            return;
        }
        final int start = (position / SEGMENTS) * SEGMENTS;
        final int size = Math.min(SEGMENTS, pageCount - start);
        if (start != blockStart || size != blockSize) {
            blockStart = start;
            blockSize = size;
            build(size);
        }
        setPlayed(position - start);
    }

    /** Fills the segment for {@code position} to {@code fraction} of its width. */
    public void setProgress(int position, float fraction) {
        final int index = position - blockStart;
        if (index < 0 || index >= getChildCount()) {
            return;
        }
        fill(index).setScaleX(Math.max(0f, Math.min(1f, fraction)));
    }

    /** Everything before the current segment is full, everything after it is empty. */
    private void setPlayed(int index) {
        for (int i = 0; i < getChildCount(); i++) {
            fill(i).setScaleX(i < index ? 1f : 0f);
        }
    }

    private void build(int count) {
        removeAllViews();
        final int gap = Math.round(3 * getResources().getDisplayMetrics().density);
        final int height = Math.round(2.5f * getResources().getDisplayMetrics().density);
        for (int i = 0; i < count; i++) {
            final FrameLayout segment = new FrameLayout(getContext());
            final LayoutParams params = new LayoutParams(0, height, 1f);
            params.setMarginStart(i == 0 ? 0 : gap);
            segment.setLayoutParams(params);
            segment.setBackgroundResource(R.drawable.bg_reel_segment_track);

            final View fill = new View(getContext());
            fill.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            fill.setBackgroundResource(R.drawable.bg_reel_segment_fill);
            // Scaling from the left edge is what makes a bar grow instead of stretch.
            fill.setPivotX(0f);
            fill.setScaleX(0f);
            segment.addView(fill);
            addView(segment);
        }
    }

    @NonNull
    private View fill(int index) {
        return ((FrameLayout) getChildAt(index)).getChildAt(0);
    }
}
