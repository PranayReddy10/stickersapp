package com.stickersanimated.kissing.ads;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

/**
 * Drawing one of your own ads.
 *
 * <p>Built in code rather than from a layout file. These are three views deep at
 * most, every shape needs slightly different measurements, and a layout per shape
 * would be four files that have to be kept in step with this one.
 *
 * <p>Every view made here reports its own impression when it is built and its own
 * click when it is tapped, so no caller can forget to.
 */
final class HouseAdViews {

    private static final String TAG = "HouseAdViews";

    private HouseAdViews() {
    }

    /** The strip, or the box between packs. */
    static View banner(final Activity activity, final HouseAd ad, boolean mrec) {
        final ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                mrec ? dp(activity, 300) : ViewGroup.LayoutParams.MATCH_PARENT,
                mrec ? dp(activity, 250) : dp(activity, 50),
                Gravity.CENTER));

        load(activity, ad.image, image);
        onTap(activity, ad, image);
        HouseAdReport.impression(ad);

        return image;
    }

    /**
     * The card in a list, built from the words rather than a picture - which is
     * what makes it a native ad rather than a banner in a list's clothing.
     */
    static View native_(final Activity activity, final HouseAd ad, NativeStyle style) {
        final LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));

        if (!TextUtils.isEmpty(ad.image)) {
            final ImageView picture = new ImageView(activity);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 160)));
            load(activity, ad.image, picture);
            card.addView(picture);
        }

        card.addView(text(activity, ad.title, 16f, true, dp(activity, 10)));
        if (!TextUtils.isEmpty(ad.body)) {
            card.addView(text(activity, ad.body, 13.5f, false, dp(activity, 4)));
        }

        final TextView button = text(activity, ad.buttonText(), 14f, true, dp(activity, 12));
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10));
        final GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.parseColor("#25D366"));
        pill.setCornerRadius(dp(activity, 22));
        button.setBackground(pill);
        card.addView(button);

        onTap(activity, ad, card);
        HouseAdReport.impression(ad);

        return card;
    }

    /**
     * The full page, for an interstitial or a rewarded slot.
     *
     * <p>A dialog rather than an activity: an activity would need a manifest entry
     * and a way back into whatever was underneath, and this has neither to gain
     * from it. {@code onClosed} runs exactly once, however it was dismissed.
     *
     * @param holdSeconds how long before the close button appears; 0 shows it at once
     */
    static Dialog fullScreen(final Activity activity, final HouseAd ad,
                             int holdSeconds, final Runnable onClosed) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
        }

        final FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);

        final ImageView picture = new ImageView(activity);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        load(activity, ad.image, picture);
        root.addView(picture);

        final TextView close = new TextView(activity);
        close.setTextColor(Color.WHITE);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        close.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
        final GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.parseColor("#99000000"));
        pill.setCornerRadius(dp(activity, 20));
        close.setBackground(pill);
        final FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        closeParams.topMargin = dp(activity, 18);
        closeParams.rightMargin = dp(activity, 14);
        close.setLayoutParams(closeParams);
        root.addView(close);

        dialog.setContentView(root);
        dialog.setCancelable(false);

        // Runs once whatever happened: tapped away, closed, or the activity went.
        final boolean[] finished = { false };
        final Runnable once = new Runnable() {
            @Override
            public void run() {
                if (finished[0]) {
                    return;
                }
                finished[0] = true;
                if (onClosed != null) {
                    onClosed.run();
                }
            }
        };
        dialog.setOnDismissListener(d -> once.run());

        picture.setOnClickListener(v -> {
            open(activity, ad);
            dismiss(dialog);
        });

        if (holdSeconds <= 0) {
            close.setText("Close");
            close.setOnClickListener(v -> dismiss(dialog));
        } else {
            countdown(activity, close, dialog, holdSeconds);
        }

        HouseAdReport.impression(ad);
        return dialog;
    }

    /** Counts down in the corner, then turns into a close button. */
    private static void countdown(final Activity activity, final TextView close,
                                  final Dialog dialog, final int seconds) {
        close.setText(seconds + "s");
        close.postDelayed(new Runnable() {
            int left = seconds;

            @Override
            public void run() {
                if (activity.isFinishing() || !dialog.isShowing()) {
                    return;
                }
                left--;
                if (left > 0) {
                    close.setText(left + "s");
                    close.postDelayed(this, 1000);
                    return;
                }
                close.setText("Close");
                close.setOnClickListener(v -> dismiss(dialog));
            }
        }, 1000);
    }

    private static void dismiss(Dialog dialog) {
        try {
            dialog.dismiss();
        } catch (Throwable t) {
            // The activity went while the ad was up. The dismiss listener has
            // already run, so the caller has been told either way.
            Log.d(TAG, "Could not dismiss", t);
        }
    }

    private static TextView text(Activity activity, String value, float size,
                                 boolean bold, int topMargin) {
        final TextView view = new TextView(activity);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(bold ? Color.parseColor("#212121") : Color.parseColor("#606060"));
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        view.setLayoutParams(params);
        return view;
    }

    private static void load(Activity activity, String url, ImageView into) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        try {
            Picasso.get().load(url.trim()).into(into);
        } catch (Throwable t) {
            Log.w(TAG, "Could not load " + url, t);
        }
    }

    private static void onTap(final Activity activity, final HouseAd ad, View view) {
        view.setOnClickListener(v -> open(activity, ad));
    }

    /** Opens wherever the ad points, and says it was tapped. */
    private static void open(Activity activity, HouseAd ad) {
        HouseAdReport.click(ad);
        try {
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ad.url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable t) {
            // A bad address typed into the panel. The click still counted, which
            // is how you would find out.
            Log.w(TAG, "Could not open " + ad.url, t);
        }
    }

    private static int dp(Activity activity, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics());
    }
}
