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
    private final ViewGroup container;
    private final AdsConfig config;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<AdNetwork> waterfall = Collections.emptyList();
    private int index;
    private int attemptId;
    private boolean destroyed;
    private Runnable timeoutRunnable;

    private NativeAd admobAd;
    private MaxNativeAdLoader maxLoader;
    private MaxAd maxAd;
    private NativeBannerAd facebookAd;

    private NativeAdManager(Activity activity, ViewGroup container) {
        this.activity = activity;
        this.container = container;
        this.config = new AdsConfig(activity);
    }

    @Nullable
    public static NativeAdManager into(@Nullable Activity activity, @Nullable ViewGroup container) {
        if (activity == null || container == null) {
            return null;
        }
        return new NativeAdManager(activity, container);
    }

    public void load() {
        if (destroyed || !config.isEnabled(AdFormat.NATIVE)) {
            return;
        }
        waterfall = config.waterfall(AdFormat.NATIVE);
        index = 0;
        loadNext();
    }

    public void destroy() {
        destroyed = true;
        cancelTimeout();
        release();
    }

    private void loadNext() {
        if (destroyed || activity.isFinishing() || index >= waterfall.size()) {
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
                    show(NativeAdRenderer.renderAdmob(activity, nativeAd));
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
        loader.loadAd(NativeAdRenderer.createMaxAdView(activity));
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

    private void show(View adView) {
        container.removeAllViews();
        container.addView(adView);
        container.setVisibility(View.VISIBLE);
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
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release native ad", t);
        } finally {
            admobAd = null;
            maxLoader = null;
            maxAd = null;
            facebookAd = null;
        }
    }
}
