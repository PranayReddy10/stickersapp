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
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

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

    private final Activity activity;
    private final ViewGroup container;
    private final AdsConfig config;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<AdNetwork> waterfall = Collections.emptyList();
    private int index;
    private int attemptId;
    private boolean destroyed;

    private View currentView;
    private Runnable timeoutRunnable;

    private BannerAdManager(Activity activity, ViewGroup container) {
        this.activity = activity;
        this.container = container;
        this.config = new AdsConfig(activity);
    }

    /** Creates a manager for the given container. Returns {@code null} if there is none. */
    @Nullable
    public static BannerAdManager into(@Nullable Activity activity, @Nullable ViewGroup container) {
        if (activity == null || container == null) {
            return null;
        }
        return new BannerAdManager(activity, container);
    }

    /** Starts the waterfall. Does nothing for subscribers or when banners are switched off. */
    public void load() {
        if (destroyed || !config.isEnabled(AdFormat.BANNER)) {
            return;
        }
        waterfall = config.waterfall(AdFormat.BANNER);
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
                Log.d(TAG, "No banner network filled");
            }
            return;
        }
        final AdNetwork network = waterfall.get(index++);
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
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, 360));
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
        final MaxAdView adView = new MaxAdView(unitId, activity);
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
        final int heightPx = AppLovinSdkUtils.dpToPx(activity,
                MaxAdFormat.BANNER.getAdaptiveSize(activity).getHeight());
        adView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        attach(adView);
        adView.loadAd();
    }

    private void loadAppLovin(int attempt) {
        final AppLovinAdView adView = new AppLovinAdView(AppLovinAdSize.BANNER, activity);
        final int heightPx = AppLovinSdkUtils.dpToPx(activity,
                AppLovinSdkUtils.isTablet(activity) ? 90 : 50);
        adView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
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
        final com.facebook.ads.AdView adView = new com.facebook.ads.AdView(
                activity, placementId, com.facebook.ads.AdSize.BANNER_HEIGHT_50);
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
        AdsInitializer.initializeUnity(activity);
        if (!AdsInitializer.isUnityReady()) {
            onFailed(attempt, AdNetwork.UNITY, "sdk not initialized");
            return;
        }
        final BannerView bannerView =
                new BannerView(activity, placementId, new UnityBannerSize(320, 50));
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
        Log.d(TAG, "Banner filled by " + network);
        if (currentView != null) {
            currentView.setVisibility(View.VISIBLE);
        }
    }

    private void onFailed(int attempt, AdNetwork network, String reason) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        Log.d(TAG, "Banner on " + network + " failed (" + reason + "), trying next network");
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
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release banner view", t);
        }
    }
}
