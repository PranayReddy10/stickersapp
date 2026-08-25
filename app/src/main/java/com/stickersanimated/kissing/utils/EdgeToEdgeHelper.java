package com.stickersanimated.kissing.utils;

import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Android 15 (API 35) started drawing every window edge to edge and Android 16 (API 36)
 * removed the {@code windowOptOutEdgeToEdgeEnforcement} escape hatch, so an app targeting
 * API 36 always gets a transparent status bar and navigation bar.
 *
 * <p>This helper restores the pre-Android-15 look for the whole app without touching each
 * layout:
 * <ul>
 *     <li>screens whose layout does not already declare {@code android:fitsSystemWindows}
 *         get the system bar (and keyboard) insets applied as padding, so nothing is
 *         hidden underneath the bars;</li>
 *     <li>the strips behind the status bar and the navigation bar are repainted with the
 *         theme's {@code statusBarColor} / {@code navigationBarColor}, which the platform
 *         itself now ignores;</li>
 *     <li>the system bar icons are switched to dark or light to stay readable on those
 *         colours.</li>
 * </ul>
 */
public final class EdgeToEdgeHelper {

    /**
     * Marks a screen that wants to paint under the system bars, such as the full
     * screen reel player. Padding a video feed away from the bars would letterbox it,
     * so these screens are left alone and position their own controls.
     */
    public interface FullBleed {
    }

    private EdgeToEdgeHelper() {
    }

    /** Applies the compatibility treatment to every activity of the app. */
    public static void install(@NonNull android.app.Application application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Below Android 15 the platform still lays windows out inside the system bars.
            return;
        }
        application.registerActivityLifecycleCallbacks(
                new android.app.Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                    }

                    @Override
                    public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                        apply(activity);
                    }

                    @Override
                    public void onActivityStarted(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityPaused(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityStopped(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                    }
                });
    }

    /**
     * Handles a single activity. Safe to call more than once, later calls are ignored.
     */
    public static void apply(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        if (activity instanceof FullBleed) {
            // Light icons, because these screens are dark behind the bars.
            final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                    activity.getWindow(), activity.getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            return;
        }
        final View contentRoot = activity.findViewById(android.R.id.content);
        if (!(contentRoot instanceof ViewGroup)) {
            return;
        }
        final ViewGroup content = (ViewGroup) contentRoot;
        if (content.getChildCount() == 0) {
            // setContentView() has not run - nothing to fix up.
            return;
        }
        if (Boolean.TRUE.equals(content.getTag(TAG_KEY))) {
            return;
        }
        content.setTag(TAG_KEY, Boolean.TRUE);

        final View layoutRoot = content.getChildAt(0);
        final boolean layoutHandlesInsets = declaresFitsSystemWindows(layoutRoot, 0);

        final int statusBarColor = themeColor(activity, android.R.attr.statusBarColor, Color.TRANSPARENT);
        final int navigationBarColor = themeColor(activity, android.R.attr.navigationBarColor, Color.TRANSPARENT);

        final View statusScrim = addScrim(content, Gravity.TOP, statusBarColor);
        final View navigationScrim = addScrim(content, Gravity.BOTTOM, navigationBarColor);

        final int padLeft = layoutRoot.getPaddingLeft();
        final int padTop = layoutRoot.getPaddingTop();
        final int padRight = layoutRoot.getPaddingRight();
        final int padBottom = layoutRoot.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            final Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            final Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            resize(statusScrim, bars.top);
            resize(navigationScrim, bars.bottom);

            if (!layoutHandlesInsets) {
                layoutRoot.setPadding(
                        padLeft + bars.left,
                        padTop + bars.top,
                        padRight + bars.right,
                        padBottom + Math.max(bars.bottom, ime.bottom));
            }
            return insets;
        });

        applyBarIconAppearance(activity, statusBarColor, navigationBarColor);
        ViewCompat.requestApplyInsets(content);
    }

    private static View addScrim(ViewGroup content, int gravity, int color) {
        final View scrim = new View(content.getContext());
        scrim.setBackgroundColor(color);
        final FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, gravity);
        scrim.setLayoutParams(params);
        // Added last so it paints over the layout's own background, which fills the
        // padding reserved for the system bars.
        content.addView(scrim);
        return scrim;
    }

    private static void resize(View scrim, int height) {
        final ViewGroup.LayoutParams params = scrim.getLayoutParams();
        if (params != null && params.height != height) {
            params.height = height;
            scrim.setLayoutParams(params);
        }
    }

    private static void applyBarIconAppearance(Activity activity, int statusBarColor, int navigationBarColor) {
        final View decor = activity.getWindow().getDecorView();
        final WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), decor);
        controller.setAppearanceLightStatusBars(isLight(statusBarColor));
        controller.setAppearanceLightNavigationBars(isLight(navigationBarColor));
    }

    private static boolean isLight(int color) {
        if (Color.alpha(color) < 128) {
            // A transparent bar shows the window background, which is light in this app.
            return true;
        }
        final double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255d;
        return luminance > 0.5d;
    }

    private static int themeColor(Activity activity, int attr, int fallback) {
        final TypedArray a = activity.getTheme().obtainStyledAttributes(new int[]{attr});
        try {
            return a.getColor(0, fallback);
        } finally {
            a.recycle();
        }
    }

    /**
     * Layouts that already declare {@code fitsSystemWindows} keep their own inset handling,
     * otherwise the padding would be applied twice.
     */
    private static boolean declaresFitsSystemWindows(View view, int depth) {
        if (view == null || depth > MAX_FITS_SYSTEM_WINDOWS_DEPTH) {
            return false;
        }
        if (view.getFitsSystemWindows()) {
            return true;
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (declaresFitsSystemWindows(group.getChildAt(i), depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final int MAX_FITS_SYSTEM_WINDOWS_DEPTH = 2;
    private static final int TAG_KEY = com.stickersanimated.kissing.R.id.tag_edge_to_edge;
}
