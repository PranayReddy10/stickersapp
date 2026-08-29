package com.stickersanimated.kissing.ads;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.adview.AppLovinAdView;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxAdView;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinSdkUtils;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.inmobi.ads.AdMetaInfo;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiBanner;
import com.inmobi.ads.listeners.BannerAdEventListener;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;
import com.vungle.ads.BannerAd;
import com.vungle.ads.BannerAdListener;
import com.vungle.ads.BannerAdSize;
import com.vungle.ads.BaseAd;
import com.vungle.ads.VungleError;

import java.util.Collections;
import java.util.List;

/**
 * Shows a banner in {@code container}, walking down the configured waterfall until one
 * network fills. A network that errors out, or that never answers within
 * {@link AdsConfig#timeoutMillis()}, hands over to the next one instead of leaving an
 * empty banner slot behind.
 */
public final class BannerAdManager {

    private static final String TAG = "BannerAds";

    /** How long to wait before asking again whether a network's SDK has come up. */
    private static final long SDK_RETRY_MS = 400L;

    private static final int BANNER_WIDTH_DP = 320;
    private static final int BANNER_HEIGHT_DP = 50;
    private static final int MREC_WIDTH_DP = 300;
    private static final int MREC_HEIGHT_DP = 250;

    private final Activity activity;
    private final ViewGroup container;
    private final AdsConfig config;
    private final BannerSize size;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<AdNetwork> waterfall = Collections.emptyList();
    private int index;
    private int attemptId;
    private boolean destroyed;

    private View currentView;
    /** Run when the format is off or no network filled, so the caller can try something else. */
    private Runnable onEmpty;
    private Runnable timeoutRunnable;
    /** Vungle hands back a view but keeps teardown on the ad object itself. */
    private BannerAd vungleBannerAd;

    private BannerAdManager(Activity activity, ViewGroup container, BannerSize size) {
        this.activity = activity;
        this.container = container;
        this.size = size;
        this.config = new AdsConfig(activity);
    }

    private boolean isMrec() {
        return size == BannerSize.MREC;
    }

    /** What this slot calls itself in the log, so the two shapes can be told apart. */
    private String label() {
        return isMrec() ? "MREC" : "Banner";
    }

    /** Creates a manager for the given container. Returns {@code null} if there is none. */
    @Nullable
    public static BannerAdManager into(@Nullable Activity activity, @Nullable ViewGroup container) {
        if (activity == null || container == null) {
            return null;
        }
        return new BannerAdManager(activity, container, BannerSize.STANDARD);
    }

    /**
     * A 300x250 block rather than a strip, for a slot with room for a card - an in-feed
     * row, or the ad page between reels. Every network sells this shape, so it fills where
     * a native ad has no demand.
     */
    @Nullable
    public static BannerAdManager mrec(@Nullable Activity activity,
                                       @Nullable ViewGroup container) {
        if (activity == null || container == null) {
            return null;
        }
        return new BannerAdManager(activity, container, BannerSize.MREC);
    }

    /**
     * Called once when this slot ends up with no banner: banners are switched off, or every
     * network passed. Lets the caller fall back to another format rather than leave a gap.
     */
    public BannerAdManager onEmpty(@Nullable Runnable action) {
        this.onEmpty = action;
        return this;
    }

    /** Starts the waterfall. Does nothing for subscribers or when banners are switched off. */
    public void load() {
        if (destroyed) {
            return;
        }
        if (!config.isEnabled(AdFormat.BANNER)) {
            Log.d(TAG, label() + ": banners are off or have no configured network");
            reportEmpty();
            return;
        }
        waterfall = config.waterfall(AdFormat.BANNER);
        Log.d(TAG, label() + " waterfall " + waterfall + ", " + config.timeoutMillis()
                + "ms per network");
        index = 0;
        loadNext();
    }

    public void destroy() {
        destroyed = true;
        cancelTimeout();
        releaseCurrent();
    }

    private void loadNext() {
        releaseCurrent();
        if (destroyed || activity.isFinishing() || index >= waterfall.size()) {
            if (index >= waterfall.size()) {
                Log.d(TAG, "No network filled the " + label().toLowerCase() + " slot");
                reportEmpty();
            }
            return;
        }
        final AdNetwork network = waterfall.get(index++);
        if (AdCooldown.waiting(AdFormat.BANNER, network)) {
            loadNext();
            return;
        }
        final String unitId = config.unitId(AdFormat.BANNER, network);
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
                case APPLOVIN:
                    loadAppLovin(attempt);
                    break;
                case FACEBOOK:
                    loadFacebook(attempt, unitId);
                    break;
                case UNITY:
                    loadUnity(attempt, unitId);
                    break;
                case VUNGLE:
                    loadVungle(attempt, unitId);
                    break;
                case INMOBI:
                    loadInmobi(attempt, unitId);
                    break;
                case STARTIO:
                    loadStartIo(attempt);
                    break;
                default:
                    onFailed(attempt, network, "unsupported network");
                    break;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Banner request to " + network + " threw", t);
            onFailed(attempt, network, String.valueOf(t.getMessage()));
        }
    }

    private void loadAdmob(int attempt, String unitId) {
        final AdView adView = new AdView(activity);
        adView.setAdSize(isMrec() ? AdSize.MEDIUM_RECTANGLE
                : AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, 360));
        adView.setAdUnitId(unitId);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                onLoaded(attempt, AdNetwork.ADMOB);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                onFailed(attempt, AdNetwork.ADMOB, adError.getMessage());
            }
        });
        attach(adView);
        adView.loadAd(new AdRequest.Builder().build());
    }

    private void loadMax(int attempt, String unitId) {
        final MaxAdView adView = isMrec()
                ? new MaxAdView(unitId, MaxAdFormat.MREC, activity)
                : new MaxAdView(unitId, activity);
        adView.setListener(new MaxAdViewAdListener() {
            @Override
            public void onAdLoaded(MaxAd ad) {
                onLoaded(attempt, AdNetwork.MAX);
            }

            @Override
            public void onAdLoadFailed(String adUnitId, MaxError error) {
                onFailed(attempt, AdNetwork.MAX, error == null ? "" : error.getMessage());
            }

            @Override
            public void onAdDisplayFailed(MaxAd ad, MaxError error) {
                onFailed(attempt, AdNetwork.MAX, error == null ? "" : error.getMessage());
            }

            @Override
            public void onAdExpanded(MaxAd ad) {
            }

            @Override
            public void onAdCollapsed(MaxAd ad) {
            }

            @Override
            public void onAdDisplayed(MaxAd ad) {
            }

            @Override
            public void onAdHidden(MaxAd ad) {
            }

            @Override
            public void onAdClicked(MaxAd ad) {
            }
        });
        if (isMrec()) {
            adView.setLayoutParams(new FrameLayout.LayoutParams(
                    AppLovinSdkUtils.dpToPx(activity, MREC_WIDTH_DP),
                    AppLovinSdkUtils.dpToPx(activity, MREC_HEIGHT_DP),
                    android.view.Gravity.CENTER));
        } else {
            final int heightPx = AppLovinSdkUtils.dpToPx(activity,
                    MaxAdFormat.BANNER.getAdaptiveSize(activity).getHeight());
            adView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        }
        attach(adView);
        adView.loadAd();
    }

    private void loadAppLovin(int attempt) {
        final AppLovinAdView adView = new AppLovinAdView(
                isMrec() ? AppLovinAdSize.MREC : AppLovinAdSize.BANNER, activity);
        if (isMrec()) {
            adView.setLayoutParams(new FrameLayout.LayoutParams(
                    AppLovinSdkUtils.dpToPx(activity, MREC_WIDTH_DP),
                    AppLovinSdkUtils.dpToPx(activity, MREC_HEIGHT_DP),
                    android.view.Gravity.CENTER));
        } else {
            final int heightPx = AppLovinSdkUtils.dpToPx(activity,
                    AppLovinSdkUtils.isTablet(activity) ? 90 : 50);
            adView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        }
        adView.setAdLoadListener(new AppLovinAdLoadListener() {
            @Override
            public void adReceived(AppLovinAd ad) {
                handler.post(() -> onLoaded(attempt, AdNetwork.APPLOVIN));
            }

            @Override
            public void failedToReceiveAd(int errorCode) {
                handler.post(() -> onFailed(attempt, AdNetwork.APPLOVIN, "code " + errorCode));
            }
        });
        attach(adView);
        adView.loadNextAd();
    }

    private void loadFacebook(int attempt, String placementId) {
        final com.facebook.ads.AdView adView = new com.facebook.ads.AdView(activity, placementId,
                isMrec() ? com.facebook.ads.AdSize.RECTANGLE_HEIGHT_250
                        : com.facebook.ads.AdSize.BANNER_HEIGHT_50);
        final com.facebook.ads.AdListener listener = new com.facebook.ads.AdListener() {
            @Override
            public void onError(com.facebook.ads.Ad ad, com.facebook.ads.AdError adError) {
                onFailed(attempt, AdNetwork.FACEBOOK, adError == null ? "" : adError.getErrorMessage());
            }

            @Override
            public void onAdLoaded(com.facebook.ads.Ad ad) {
                onLoaded(attempt, AdNetwork.FACEBOOK);
            }

            @Override
            public void onAdClicked(com.facebook.ads.Ad ad) {
            }

            @Override
            public void onLoggingImpression(com.facebook.ads.Ad ad) {
            }
        };
        attach(adView);
        adView.loadAd(adView.buildLoadAdConfig().withAdListener(listener).build());
    }

    private void loadUnity(int attempt, String placementId) {
        if (!sdkReady(attempt, AdNetwork.UNITY, () -> loadUnity(attempt, placementId))) {
            return;
        }
        final BannerView bannerView = new BannerView(activity, placementId,
                isMrec() ? new UnityBannerSize(MREC_WIDTH_DP, MREC_HEIGHT_DP)
                        : new UnityBannerSize(BANNER_WIDTH_DP, BANNER_HEIGHT_DP));
        bannerView.setListener(new BannerView.Listener() {
            @Override
            public void onBannerLoaded(BannerView view) {
                onLoaded(attempt, AdNetwork.UNITY);
            }

            @Override
            public void onBannerFailedToLoad(BannerView view, BannerErrorInfo errorInfo) {
                onFailed(attempt, AdNetwork.UNITY, String.valueOf(errorInfo));
            }
        });
        attach(bannerView);
        bannerView.load();
    }

    private void loadVungle(int attempt, String placementId) {
        if (!sdkReady(attempt, AdNetwork.VUNGLE, () -> loadVungle(attempt, placementId))) {
            return;
        }
        final BannerAd bannerAd = new BannerAd(activity, placementId,
                isMrec() ? BannerAdSize.VUNGLE_MREC : BannerAdSize.BANNER);
        bannerAd.setAdListener(new BannerAdListener() {
            @Override
            public void onAdLoaded(@NonNull BaseAd baseAd) {
                if (destroyed || attempt != attemptId) {
                    return;
                }
                // Vungle only hands over the view once the ad is in, so unlike the other
                // networks it is attached here rather than before the request.
                final com.vungle.ads.BannerView view = bannerAd.getBannerView();
                if (view == null) {
                    onFailed(attempt, AdNetwork.VUNGLE, "no banner view");
                    return;
                }
                view.setLayoutParams(isMrec()
                        ? new FrameLayout.LayoutParams(
                                AppLovinSdkUtils.dpToPx(activity, MREC_WIDTH_DP),
                                AppLovinSdkUtils.dpToPx(activity, MREC_HEIGHT_DP),
                                android.view.Gravity.CENTER)
                        : new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                AppLovinSdkUtils.dpToPx(activity, BANNER_HEIGHT_DP)));
                vungleBannerAd = bannerAd;
                attach(view);
                onLoaded(attempt, AdNetwork.VUNGLE);
            }

            @Override
            public void onAdFailedToLoad(@NonNull BaseAd baseAd, @NonNull VungleError error) {
                onFailed(attempt, AdNetwork.VUNGLE, error.getMessage());
            }

            @Override
            public void onAdFailedToPlay(@NonNull BaseAd baseAd, @NonNull VungleError error) {
                onFailed(attempt, AdNetwork.VUNGLE, error.getMessage());
            }

            @Override
            public void onAdStart(@NonNull BaseAd baseAd) {
            }

            @Override
            public void onAdImpression(@NonNull BaseAd baseAd) {
            }

            @Override
            public void onAdEnd(@NonNull BaseAd baseAd) {
            }

            @Override
            public void onAdClicked(@NonNull BaseAd baseAd) {
            }

            @Override
            public void onAdLeftApplication(@NonNull BaseAd baseAd) {
            }
        });
        bannerAd.load(null);
    }

    private void loadInmobi(int attempt, String placementId) {
        if (!sdkReady(attempt, AdNetwork.INMOBI, () -> loadInmobi(attempt, placementId))) {
            return;
        }
        final long placement = parsePlacementId(placementId);
        if (placement == 0L) {
            onFailed(attempt, AdNetwork.INMOBI, "placement id is not a number");
            return;
        }
        final InMobiBanner banner = new InMobiBanner(activity, placement);
        banner.setListener(new BannerAdEventListener() {
            @Override
            public void onAdLoadSucceeded(@NonNull InMobiBanner ad, @NonNull AdMetaInfo info) {
                onLoaded(attempt, AdNetwork.INMOBI);
            }

            @Override
            public void onAdLoadFailed(@NonNull InMobiBanner ad, @NonNull InMobiAdRequestStatus status) {
                onFailed(attempt, AdNetwork.INMOBI, status.getMessage());
            }
        });
        final int widthDp = isMrec() ? MREC_WIDTH_DP : BANNER_WIDTH_DP;
        final int heightDp = isMrec() ? MREC_HEIGHT_DP : BANNER_HEIGHT_DP;
        banner.setEnableAutoRefresh(false);
        banner.setBannerSize(widthDp, heightDp);
        // InMobi sizes its banner from the layout params, which it reads in pixels.
        banner.setLayoutParams(new FrameLayout.LayoutParams(
                AppLovinSdkUtils.dpToPx(activity, widthDp),
                AppLovinSdkUtils.dpToPx(activity, heightDp),
                android.view.Gravity.CENTER));
        attach(banner);
        banner.load();
    }

    /**
     * Start.io. Its banner and MREC views load themselves as soon as they are built, so
     * there is nothing to request: the listener says whether anything came back.
     */
    private void loadStartIo(int attempt) {
        if (!sdkReady(attempt, AdNetwork.STARTIO, () -> loadStartIo(attempt))) {
            return;
        }
        final com.startapp.sdk.ads.banner.BannerListener listener =
                new com.startapp.sdk.ads.banner.BannerListener() {
                    @Override
                    public void onReceiveAd(View view) {
                        onLoaded(attempt, AdNetwork.STARTIO);
                    }

                    @Override
                    public void onFailedToReceiveAd(View view) {
                        onFailed(attempt, AdNetwork.STARTIO, "no ad");
                    }

                    @Override
                    public void onImpression(View view) {
                    }

                    @Override
                    public void onClick(View view) {
                    }
                };

        final View adView = isMrec()
                ? new com.startapp.sdk.ads.banner.Mrec(activity, listener)
                : new com.startapp.sdk.ads.banner.Banner(activity, listener);
        adView.setLayoutParams(isMrec()
                ? new FrameLayout.LayoutParams(
                        AppLovinSdkUtils.dpToPx(activity, MREC_WIDTH_DP),
                        AppLovinSdkUtils.dpToPx(activity, MREC_HEIGHT_DP),
                        android.view.Gravity.CENTER)
                : new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        attach(adView);
    }

    /** InMobi placements are numeric; anything else means the panel value is wrong. */
    private static long parsePlacementId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /** Fires the empty callback once, and only once. */
    private void reportEmpty() {
        final Runnable action = onEmpty;
        onEmpty = null;
        if (action != null && !destroyed) {
            action.run();
        }
    }

    /**
     * True when {@code network} can be asked right now. When its SDK is still starting the
     * request is tried again in a moment instead of being written off - the SDKs the panel
     * configures are not up yet when the first ad of a session is asked for. The timeout
     * already running for this network is what eventually moves the waterfall on.
     */
    private boolean sdkReady(int attempt, AdNetwork network, Runnable retry) {
        if (AdsSdks.isReady(network)) {
            return true;
        }
        AdsSdks.start(activity, network);
        Log.d(TAG, network + " SDK is still starting, retrying shortly");
        handler.postDelayed(() -> {
            if (!destroyed && attempt == attemptId) {
                retry.run();
            }
        }, SDK_RETRY_MS);
        return false;
    }

    private void attach(View view) {
        view.setVisibility(View.GONE);
        currentView = view;
        container.addView(view);
    }

    private void onLoaded(int attempt, AdNetwork network) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        AdCooldown.filled(AdFormat.BANNER, network);
        Log.d(TAG, label() + " filled by " + network);
        if (currentView != null) {
            currentView.setVisibility(View.VISIBLE);
        }
    }

    private void onFailed(int attempt, AdNetwork network, String reason) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        AdCooldown.failed(AdFormat.BANNER, network, reason);
        Log.d(TAG, label() + " on " + network + " failed (" + reason
                + "), trying next network");
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

    private void releaseCurrent() {
        // Vungle's teardown lives on BannerAd, not on the BannerView it returns, so it
        // is released here rather than in the view switch below.
        if (vungleBannerAd != null) {
            try {
                vungleBannerAd.finishAd();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to release Vungle banner", t);
            }
            vungleBannerAd = null;
        }
        if (currentView == null) {
            return;
        }
        final View view = currentView;
        currentView = null;
        container.removeView(view);
        try {
            if (view instanceof AdView) {
                ((AdView) view).destroy();
            } else if (view instanceof MaxAdView) {
                ((MaxAdView) view).destroy();
            } else if (view instanceof com.facebook.ads.AdView) {
                ((com.facebook.ads.AdView) view).destroy();
            } else if (view instanceof BannerView) {
                ((BannerView) view).destroy();
            } else if (view instanceof InMobiBanner) {
                ((InMobiBanner) view).destroy();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release banner view", t);
        }
    }
}
