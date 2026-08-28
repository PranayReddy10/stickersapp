package com.stickersanimated.kissing.ads;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.facebook.ads.NativeAdListener;
import com.facebook.ads.NativeBannerAd;
import com.inmobi.ads.AdMetaInfo;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiNative;
import com.inmobi.ads.listeners.NativeAdEventListener;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;

import java.util.Collections;
import java.util.List;

/**
 * Fills a container with a native ad, trying each configured network in turn. Used both by
 * the in-feed ad rows and by the single native slot on the pack details screen.
 */
public final class NativeAdManager {

    private static final String TAG = "NativeAds";

    private final Activity activity;
    /** Null while the ad is being loaded ahead of the row that will show it. */
    @Nullable
    private ViewGroup container;
    private final AdsConfig config;
    /** How much room this placement has, which decides the layout used. */
    private final NativeStyle style;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<AdNetwork> waterfall = Collections.emptyList();
    private int index;
    private int attemptId;
    private boolean destroyed;
    private Runnable timeoutRunnable;
    /** The rendered ad, kept so a recycled row gets it back instead of loading again. */
    private View adView;

    private NativeAd admobAd;
    private MaxNativeAdLoader maxLoader;
    private MaxAd maxAd;
    private NativeBannerAd facebookAd;
    private com.vungle.ads.NativeAd vungleAd;
    private InMobiNative inmobiAd;

    private NativeAdManager(Activity activity, @Nullable ViewGroup container, NativeStyle style) {
        this.activity = activity;
        this.container = container;
        this.style = style;
        this.config = new AdsConfig(activity);
    }

    @Nullable
    public static NativeAdManager into(@Nullable Activity activity, @Nullable ViewGroup container) {
        if (activity == null || container == null) {
            return null;
        }
        return new NativeAdManager(activity, container, NativeStyle.INLINE);
    }

    /**
     * A native ad that fills its container edge to edge, for the ad page between reels.
     * Same waterfall, different layout: the creative gets the whole screen and the
     * headline and call to action sit on a panel over the bottom of it.
     */
    @Nullable
    public static NativeAdManager fullscreen(@Nullable Activity activity,
                                             @Nullable ViewGroup container) {
        if (activity == null || container == null) {
            return null;
        }
        return new NativeAdManager(activity, container, NativeStyle.FULLSCREEN);
    }

    /**
     * A native ad laid out as a strip, for the bottom of a screen a creative would
     * otherwise cover - the reel player's ad bar. Same waterfall, compact layout.
     */
    @Nullable
    public static NativeAdManager bar(@Nullable Activity activity,
                                      @Nullable ViewGroup container) {
        if (activity == null || container == null) {
            return null;
        }
        return new NativeAdManager(activity, container, NativeStyle.BAR);
    }

    /**
     * Loads an ad with nowhere to put it yet, for a list row that has not scrolled into
     * view. Call {@link #attachTo(ViewGroup)} when the row appears: a network that has
     * already answered by then shows straight away instead of after a round trip.
     */
    @Nullable
    public static NativeAdManager preload(@Nullable Activity activity, NativeStyle style) {
        if (activity == null) {
            return null;
        }
        return new NativeAdManager(activity, null, style);
    }

    /** True once a network has filled this slot. */
    public boolean isFilled() {
        return adView != null;
    }

    /**
     * Moves this ad into {@code newContainer}, which is what a recycled row calls. The ad
     * itself is not reloaded: the same view is taken out of the row it was in and put in
     * the new one.
     */
    public void attachTo(@Nullable ViewGroup newContainer) {
        if (destroyed || newContainer == null || container == newContainer) {
            return;
        }
        container = newContainer;
        if (adView != null) {
            show(adView);
        } else {
            collapse();
        }
    }

    public void load() {
        if (destroyed || !config.isEnabled(AdFormat.NATIVE)) {
            collapse();
            return;
        }
        // Nothing is shown until a network fills: an empty slot would otherwise leave a
        // blank card sitting in the layout with its margins and elevation.
        collapse();
        waterfall = config.waterfall(AdFormat.NATIVE);
        // Worth having in the log: a network that never fills still costs the slot a
        // timeout before the next one is tried, which is what makes an ad look slow.
        Log.d(TAG, "Native waterfall " + waterfall + ", " + config.timeoutMillis()
                + "ms per network");
        index = 0;
        loadNext();
    }

    public void destroy() {
        destroyed = true;
        cancelTimeout();
        release();
    }

    private void loadNext() {
        if (destroyed || activity.isFinishing()) {
            return;
        }
        if (index >= waterfall.size()) {
            // Every network passed on this slot: keep it collapsed.
            collapse();
            return;
        }
        final AdNetwork network = waterfall.get(index++);
        final String unitId = config.unitId(AdFormat.NATIVE, network);
        final int attempt = ++attemptId;
        scheduleTimeout(attempt, network);

        try {
            switch (network) {
                case ADMOB:
                    loadAdmob(attempt, unitId);
                    break;
                case MAX:
                    loadMax(attempt, unitId);
                    break;
                case FACEBOOK:
                    loadFacebook(attempt, unitId);
                    break;
                case VUNGLE:
                    loadVungle(attempt, unitId);
                    break;
                case INMOBI:
                    loadInMobi(attempt, unitId);
                    break;
                default:
                    onFailed(attempt, network, "format not supported");
                    break;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Native request to " + network + " threw", t);
            onFailed(attempt, network, String.valueOf(t.getMessage()));
        }
    }

    private void loadAdmob(int attempt, String unitId) {
        final AdLoader adLoader = new AdLoader.Builder(activity, unitId)
                .forNativeAd(nativeAd -> {
                    if (destroyed || attempt != attemptId) {
                        nativeAd.destroy();
                        return;
                    }
                    release();
                    admobAd = nativeAd;
                    show(NativeAdRenderer.renderAdmob(activity, nativeAd, style));
                    onLoaded(attempt, AdNetwork.ADMOB);
                })
                .withNativeAdOptions(new NativeAdOptions.Builder()
                        .setVideoOptions(new VideoOptions.Builder().setStartMuted(true).build())
                        .build())
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        onFailed(attempt, AdNetwork.ADMOB, loadAdError.getMessage());
                    }
                })
                .build();
        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void loadMax(int attempt, String unitId) {
        final MaxNativeAdLoader loader = new MaxNativeAdLoader(unitId, activity);
        loader.setNativeAdListener(new MaxNativeAdListener() {
            @Override
            public void onNativeAdLoaded(@Nullable MaxNativeAdView nativeAdView, @NonNull MaxAd ad) {
                if (destroyed || attempt != attemptId) {
                    loader.destroy(ad);
                    return;
                }
                release();
                maxLoader = loader;
                maxAd = ad;
                if (nativeAdView != null) {
                    show(nativeAdView);
                }
                onLoaded(attempt, AdNetwork.MAX);
            }

            @Override
            public void onNativeAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) {
                onFailed(attempt, AdNetwork.MAX, error.getMessage());
            }

            @Override
            public void onNativeAdClicked(@NonNull MaxAd ad) {
            }
        });
        loader.loadAd(NativeAdRenderer.createMaxAdView(activity, style));
    }

    private void loadFacebook(int attempt, String placementId) {
        final NativeBannerAd nativeBannerAd = new NativeBannerAd(activity, placementId);
        final NativeAdListener listener = new NativeAdListener() {
            @Override
            public void onMediaDownloaded(com.facebook.ads.Ad ad) {
            }

            @Override
            public void onError(com.facebook.ads.Ad ad, com.facebook.ads.AdError adError) {
                onFailed(attempt, AdNetwork.FACEBOOK,
                        adError == null ? "" : adError.getErrorMessage());
            }

            @Override
            public void onAdLoaded(com.facebook.ads.Ad ad) {
                if (destroyed || attempt != attemptId || ad != nativeBannerAd) {
                    return;
                }
                release();
                facebookAd = nativeBannerAd;
                show(NativeAdRenderer.renderFacebook(activity, nativeBannerAd));
                onLoaded(attempt, AdNetwork.FACEBOOK);
            }

            @Override
            public void onAdClicked(com.facebook.ads.Ad ad) {
            }

            @Override
            public void onLoggingImpression(com.facebook.ads.Ad ad) {
            }
        };
        nativeBannerAd.loadAd(nativeBannerAd.buildLoadAdConfig().withAdListener(listener).build());
    }

    private void loadVungle(int attempt, String placementId) {
        final com.vungle.ads.NativeAd nativeAd = new com.vungle.ads.NativeAd(activity, placementId);
        nativeAd.setAdListener(new com.vungle.ads.NativeAdListener() {
            @Override
            public void onAdLoaded(@NonNull com.vungle.ads.BaseAd baseAd) {
                if (destroyed || attempt != attemptId) {
                    return;
                }
                release();
                vungleAd = nativeAd;
                show(NativeAdRenderer.renderVungle(activity, nativeAd, style));
                onLoaded(attempt, AdNetwork.VUNGLE);
            }

            @Override
            public void onAdFailedToLoad(@NonNull com.vungle.ads.BaseAd baseAd,
                                         @NonNull com.vungle.ads.VungleError error) {
                onFailed(attempt, AdNetwork.VUNGLE, error.getLocalizedMessage());
            }

            @Override
            public void onAdFailedToPlay(@NonNull com.vungle.ads.BaseAd baseAd,
                                         @NonNull com.vungle.ads.VungleError error) {
            }

            @Override public void onAdStart(@NonNull com.vungle.ads.BaseAd baseAd) { }
            @Override public void onAdImpression(@NonNull com.vungle.ads.BaseAd baseAd) { }
            @Override public void onAdEnd(@NonNull com.vungle.ads.BaseAd baseAd) { }
            @Override public void onAdClicked(@NonNull com.vungle.ads.BaseAd baseAd) { }
            @Override public void onAdLeftApplication(@NonNull com.vungle.ads.BaseAd baseAd) { }
        });
        nativeAd.load(null);
    }

    private void loadInMobi(int attempt, String placementId) {
        final long placement;
        try {
            placement = Long.parseLong(placementId.trim());
        } catch (NumberFormatException e) {
            // InMobi placements are numeric; anything else is a mistyped panel field.
            onFailed(attempt, AdNetwork.INMOBI, "placement id is not a number");
            return;
        }
        final InMobiNative nativeAd = new InMobiNative(activity, placement,
                new NativeAdEventListener() {
                    @Override
                    public void onAdLoadSucceeded(@NonNull InMobiNative ad,
                                                  @NonNull AdMetaInfo info) {
                        if (destroyed || attempt != attemptId) {
                            ad.destroy();
                            return;
                        }
                        release();
                        inmobiAd = ad;
                        show(NativeAdRenderer.renderInMobi(activity, ad, style));
                        onLoaded(attempt, AdNetwork.INMOBI);
                    }

                    @Override
                    public void onAdLoadFailed(@NonNull InMobiNative ad,
                                               @NonNull InMobiAdRequestStatus status) {
                        onFailed(attempt, AdNetwork.INMOBI, status.getMessage());
                    }
                });
        nativeAd.load();
    }

    private void show(View view) {
        adView = view;
        if (container == null) {
            return; // preloading: it is shown when a row asks for it
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        container.removeAllViews();
        if (style == NativeStyle.FULLSCREEN) {
            // Meta's native banner has no big creative, so it keeps its own height and
            // is centred; the AdMob and MAX full screen layouts fill the page.
            container.addView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (style == NativeStyle.BAR) {
            // A bar spans the screen; inflating with no parent leaves the view without the
            // layout params its own root asked for, so it would otherwise shrink to fit.
            container.addView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            container.addView(view);
        }
        container.setVisibility(View.VISIBLE);
        final ViewGroup frame = cardParent();
        if (frame != null) {
            frame.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hides the slot while it is empty. The container usually sits alone inside a card, and
     * that card carries the margins and the background, so it has to go too.
     */
    private void collapse() {
        if (container == null) {
            return;
        }
        container.setVisibility(View.GONE);
        final ViewGroup frame = cardParent();
        if (frame != null) {
            frame.setVisibility(View.GONE);
        }
    }

    /** The card wrapping this slot, when the container is the only thing inside it. */
    @Nullable
    private ViewGroup cardParent() {
        if (style != NativeStyle.INLINE || container == null) {
            return null;
        }
        final View parent = container.getParent() instanceof View
                ? (View) container.getParent() : null;
        if (parent instanceof androidx.cardview.widget.CardView
                && ((ViewGroup) parent).getChildCount() == 1) {
            return (ViewGroup) parent;
        }
        return null;
    }

    private void onLoaded(int attempt, AdNetwork network) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        Log.d(TAG, "Native filled by " + network);
    }

    private void onFailed(int attempt, AdNetwork network, String reason) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        Log.d(TAG, "Native on " + network + " failed (" + reason + "), trying next network");
        handler.post(this::loadNext);
    }

    private void scheduleTimeout(int attempt, AdNetwork network) {
        cancelTimeout();
        timeoutRunnable = () -> onFailed(attempt, network, "timeout");
        handler.postDelayed(timeoutRunnable, config.timeoutMillis());
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void release() {
        try {
            if (admobAd != null) {
                admobAd.destroy();
            }
            if (maxLoader != null && maxAd != null) {
                maxLoader.destroy(maxAd);
            }
            if (facebookAd != null) {
                facebookAd.destroy();
            }
            if (vungleAd != null) {
                vungleAd.unregisterView();
            }
            if (inmobiAd != null) {
                inmobiAd.unTrackViews();
                inmobiAd.destroy();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release native ad", t);
        } finally {
            admobAd = null;
            maxLoader = null;
            maxAd = null;
            facebookAd = null;
            vungleAd = null;
            inmobiAd = null;
            if (adView != null && adView.getParent() instanceof ViewGroup) {
                ((ViewGroup) adView.getParent()).removeView(adView);
            }
            adView = null;
        }
    }
}
