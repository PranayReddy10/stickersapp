package com.stickersanimated.kissing.reels;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.stickersanimated.kissing.R;

/**
 * The hairline across the top of the player: how far through the reel that is playing.
 *
 * <p>One bar for the clip on screen, not for the feed - the feed never ends, so a bar per
 * reel would say nothing. It is hidden on photos and ad pages, which have no clock.
 */
public class ReelProgressBar extends FrameLayout {

    private final View fill;

    public ReelProgressBar(Context context) {
        this(context, null);
    }

    public ReelProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.bg_reel_segment_track);
        setMinimumHeight(Math.round(2.5f * getResources().getDisplayMetrics().density));

        fill = new View(context);
        fill.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        fill.setBackgroundResource(R.drawable.bg_reel_segment_fill);
        // Scaling from the left edge is what makes a bar grow instead of stretch.
        fill.setPivotX(0f);
        fill.setScaleX(0f);
        addView(fill);
    }

    /** Fills the bar to {@code fraction} of the reel's length. */
    public void setProgress(float fraction) {
        fill.setScaleX(Math.max(0f, Math.min(1f, fraction)));
    }

    /** Back to empty, for a reel that is just starting. */
    public void reset() {
        fill.setScaleX(0f);
    }
}
