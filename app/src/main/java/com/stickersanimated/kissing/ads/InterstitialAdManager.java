package com.stickersanimated.kissing.ads;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinSdk;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.inmobi.ads.AdMetaInfo;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiInterstitial;
import com.inmobi.ads.listeners.InterstitialAdEventListener;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;
import com.vungle.ads.AdConfig;
import com.vungle.ads.BaseAd;
import com.vungle.ads.InterstitialAdListener;
import com.vungle.ads.VungleError;

import java.util.Collections;
import java.util.List;

/**
 * Keeps one interstitial warm across the whole waterfall and shows it between screens.
 *
 * <p>The caller never has to know which network answered: {@link #showThen(Runnable)} either
 * shows whatever is loaded and runs {@code onDone} when the ad is dismissed, or - if every
 * network came back empty - runs {@code onDone} straight away, so navigation is never
 * blocked by a failing ad network.
 */
public final class InterstitialAdManager {

    private static final String TAG = "InterstitialAds";

    /** How long to wait before asking again whether a network's SDK has come up. */
    private static final long SDK_RETRY_MS = 400L;
    private static final String KEY_CLICK_COUNT = "ADMOB_INTERSTITIAL_COUNT_CLICKS";

    private final Activity activity;
    private final AdsConfig config;
    private final PrefManager pref;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<AdNetwork> waterfall = Collections.emptyList();
    private int index;
    private int attemptId;
    private boolean destroyed;
    private boolean loading;
    private Runnable timeoutRunnable;

    private AdNetwork readyNetwork;
    private InterstitialAd admobAd;
    private MaxInterstitialAd maxAd;
    private AppLovinInterstitialAdDialog applovinDialog;
    private AppLovinAd applovinAd;
    private com.facebook.ads.InterstitialAd facebookAd;
    private String unityPlacementId;
    private com.vungle.ads.InterstitialAd vungleAd;
    private InMobiInterstitial inmobiAd;

    private Runnable pendingOnDone;

    public InterstitialAdManager(Activity activity) {
        this.activity = activity;
        this.config = new AdsConfig(activity);
        this.pref = new PrefManager(activity.getApplicationContext());
    }

    /** Starts warming up an ad. Safe to call as often as you like. */
    public void preload() {
        if (destroyed || loading || readyNetwork != null || !config.isEnabled(AdFormat.INTERSTITIAL)) {
            return;
        }
        waterfall = config.waterfall(AdFormat.INTERSTITIAL);
        index = 0;
        loading = true;
        loadNext();
    }

    /**
     * Shows an interstitial if one is ready and the click counter allows it, then runs
     * {@code onDone}. {@code onDone} always runs exactly once.
     */
    public void showThen(@Nullable Runnable onDone) {
        final Runnable done = onDone == null ? NO_OP : onDone;
        if (destroyed || !config.isEnabled(AdFormat.INTERSTITIAL)) {
            done.run();
            return;
        }

        final int clicks = pref.getInt(KEY_CLICK_COUNT);
        if (config.interstitialClicks() > clicks) {
            pref.setInt(KEY_CLICK_COUNT, clicks + 1);
            preload();
            done.run();
            return;
        }

        if (readyNetwork == null) {
            preload();
            done.run();
            return;
        }

        pref.setInt(KEY_CLICK_COUNT, 0);
        pendingOnDone = done;
        final AdNetwork network = readyNetwork;
        try {
            if (!show(network)) {
                finishShow();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Showing interstitial on " + network + " threw", t);
            finishShow();
        }
    }

    public void destroy() {
        destroyed = true;
        cancelTimeout();
        release();
    }

    // ---------------------------------------------------------------- loading

    private void loadNext() {
        if (destroyed || index >= waterfall.size()) {
            loading = false;
            return;
        }
        final AdNetwork network = waterfall.get(index++);
        final String unitId = config.unitId(AdFormat.INTERSTITIAL, network);
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
                default:
                    onFailed(attempt, network, "unsupported network");
                    break;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Interstitial request to " + network + " threw", t);
            onFailed(attempt, network, String.valueOf(t.getMessage()));
        }
    }

    private void loadAdmob(int attempt, String unitId) {
        InterstitialAd.load(activity, unitId, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        if (destroyed || attempt != attemptId) {
                            return;
                        }
                        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                admobAd = null;
                                finishShow();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                admobAd = null;
                                finishShow();
                            }
                        });
                        admobAd = interstitialAd;
                        onLoaded(attempt, AdNetwork.ADMOB);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        onFailed(attempt, AdNetwork.ADMOB, loadAdError.getMessage());
                    }
                });
    }

    private void loadMax(int attempt, String unitId) {
        final MaxInterstitialAd ad = new MaxInterstitialAd(unitId, activity);
        ad.setListener(new MaxAdListener() {
            @Override
            public void onAdLoaded(MaxAd maxAdInfo) {
                if (destroyed || attempt != attemptId) {
                    return;
                }
                maxAd = ad;
                onLoaded(attempt, AdNetwork.MAX);
            }

            @Override
            public void onAdLoadFailed(String adUnitId, MaxError error) {
                onFailed(attempt, AdNetwork.MAX, error == null ? "" : error.getMessage());
            }

            @Override
            public void onAdDisplayFailed(MaxAd maxAdInfo, MaxError error) {
                finishShow();
            }

            @Override
            public void onAdHidden(MaxAd maxAdInfo) {
                finishShow();
            }

            @Override
            public void onAdDisplayed(MaxAd maxAdInfo) {
            }

            @Override
            public void onAdClicked(MaxAd maxAdInfo) {
            }
        });
        ad.loadAd();
    }

    private void loadAppLovin(int attempt) {
        AppLovinSdk.getInstance(activity).getAdService().loadNextAd(AppLovinAdSize.INTERSTITIAL,
                new AppLovinAdLoadListener() {
                    @Override
                    public void adReceived(AppLovinAd ad) {
                        handler.post(() -> {
                            if (destroyed || attempt != attemptId) {
                                return;
                            }
                            applovinAd = ad;
                            applovinDialog = AppLovinInterstitialAd.create(
                                    AppLovinSdk.getInstance(activity), activity);
                            applovinDialog.setAdDisplayListener(new AppLovinAdDisplayListener() {
                                @Override
                                public void adDisplayed(AppLovinAd displayed) {
                                }

                                @Override
                                public void adHidden(AppLovinAd hidden) {
                                    applovinAd = null;
                                    finishShow();
                                }
                            });
                            onLoaded(attempt, AdNetwork.APPLOVIN);
                        });
                    }

                    @Override
                    public void failedToReceiveAd(int errorCode) {
                        handler.post(() -> onFailed(attempt, AdNetwork.APPLOVIN, "code " + errorCode));
                    }
                });
    }

    private void loadFacebook(int attempt, String placementId) {
        final com.facebook.ads.InterstitialAd ad =
                new com.facebook.ads.InterstitialAd(activity, placementId);
        final com.facebook.ads.InterstitialAdListener listener =
                new com.facebook.ads.InterstitialAdListener() {
                    @Override
                    public void onInterstitialDisplayed(com.facebook.ads.Ad displayed) {
                    }

                    @Override
                    public void onInterstitialDismissed(com.facebook.ads.Ad dismissed) {
                        facebookAd = null;
                        finishShow();
                    }

                    @Override
                    public void onError(com.facebook.ads.Ad errored, com.facebook.ads.AdError adError) {
                        onFailed(attempt, AdNetwork.FACEBOOK,
                                adError == null ? "" : adError.getErrorMessage());
                    }

                    @Override
                    public void onAdLoaded(com.facebook.ads.Ad loaded) {
                        if (destroyed || attempt != attemptId) {
                            return;
                        }
                        facebookAd = ad;
                        onLoaded(attempt, AdNetwork.FACEBOOK);
                    }

                    @Override
                    public void onAdClicked(com.facebook.ads.Ad clicked) {
                    }

                    @Override
                    public void onLoggingImpression(com.facebook.ads.Ad impression) {
                    }
                };
        ad.loadAd(ad.buildLoadAdConfig().withAdListener(listener).build());
    }

    private void loadUnity(int attempt, String placementId) {
        if (!sdkReady(attempt, AdNetwork.UNITY, () -> loadUnity(attempt, placementId))) {
            return;
        }
        UnityAds.load(placementId, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String loadedPlacementId) {
                if (destroyed || attempt != attemptId) {
                    return;
                }
                unityPlacementId = loadedPlacementId;
                onLoaded(attempt, AdNetwork.UNITY);
            }

            @Override
            public void onUnityAdsFailedToLoad(String failedPlacementId,
                                               UnityAds.UnityAdsLoadError error,
                                               String message) {
                onFailed(attempt, AdNetwork.UNITY, message);
            }
        });
    }

    private void loadVungle(int attempt, String placementId) {
        if (!sdkReady(attempt, AdNetwork.VUNGLE, () -> loadVungle(attempt, placementId))) {
            return;
        }
        final com.vungle.ads.InterstitialAd ad =
                new com.vungle.ads.InterstitialAd(activity, placementId, new AdConfig());
        ad.setAdListener(new InterstitialAdListener() {
            @Override
            public void onAdLoaded(@NonNull BaseAd baseAd) {
                if (destroyed || attempt != attemptId) {
                    return;
                }
                vungleAd = ad;
                onLoaded(attempt, AdNetwork.VUNGLE);
            }

            @Override
            public void onAdFailedToLoad(@NonNull BaseAd baseAd, @NonNull VungleError error) {
                onFailed(attempt, AdNetwork.VUNGLE, error.getMessage());
            }

            @Override
            public void onAdFailedToPlay(@NonNull BaseAd baseAd, @NonNull VungleError error) {
                vungleAd = null;
                finishShow();
            }

            @Override
            public void onAdEnd(@NonNull BaseAd baseAd) {
                vungleAd = null;
                finishShow();
            }

            @Override
            public void onAdStart(@NonNull BaseAd baseAd) {
            }

            @Override
            public void onAdImpression(@NonNull BaseAd baseAd) {
            }

            @Override
            public void onAdClicked(@NonNull BaseAd baseAd) {
            }

            @Override
            public void onAdLeftApplication(@NonNull BaseAd baseAd) {
            }
        });
        ad.load(null);
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
        final InMobiInterstitial ad = new InMobiInterstitial(activity, placement,
                new InterstitialAdEventListener() {
                    @Override
                    public void onAdLoadSucceeded(@NonNull InMobiInterstitial interstitial,
                                                  @NonNull AdMetaInfo info) {
                        if (destroyed || attempt != attemptId) {
                            return;
                        }
                        inmobiAd = interstitial;
                        onLoaded(attempt, AdNetwork.INMOBI);
                    }

                    @Override
                    public void onAdLoadFailed(@NonNull InMobiInterstitial interstitial,
                                               @NonNull InMobiAdRequestStatus status) {
                        onFailed(attempt, AdNetwork.INMOBI, status.getMessage());
                    }

                    @Override
                    public void onAdDismissed(@NonNull InMobiInterstitial interstitial) {
                        inmobiAd = null;
                        finishShow();
                    }

                    @Override
                    public void onAdDisplayFailed(@NonNull InMobiInterstitial interstitial) {
                        inmobiAd = null;
                        finishShow();
                    }
                });
        ad.load();
    }

    /** InMobi placements are numeric; anything else means the panel value is wrong. */
    private static long parsePlacementId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    // ---------------------------------------------------------------- showing

    private boolean show(AdNetwork network) {
        switch (network) {
            case ADMOB:
                if (admobAd == null) {
                    return false;
                }
                readyNetwork = null;
                admobAd.show(activity);
                return true;
            case MAX:
                if (maxAd == null || !maxAd.isReady()) {
                    return false;
                }
                readyNetwork = null;
                maxAd.showAd(activity);
                return true;
            case APPLOVIN:
                if (applovinDialog == null || applovinAd == null) {
                    return false;
                }
                readyNetwork = null;
                applovinDialog.showAndRender(applovinAd);
                return true;
            case FACEBOOK:
                if (facebookAd == null || !facebookAd.isAdLoaded()) {
                    return false;
                }
                readyNetwork = null;
                facebookAd.show();
                return true;
            case VUNGLE:
                if (vungleAd == null || !Boolean.TRUE.equals(vungleAd.canPlayAd())) {
                    return false;
                }
                readyNetwork = null;
                vungleAd.play(activity);
                return true;
            case INMOBI:
                if (inmobiAd == null || !inmobiAd.isReady()) {
                    return false;
                }
                readyNetwork = null;
                inmobiAd.show();
                return true;
            case UNITY:
                if (unityPlacementId == null) {
                    return false;
                }
                readyNetwork = null;
                UnityAds.show(activity, unityPlacementId, new UnityAdsShowOptions(),
                        new IUnityAdsShowListener() {
                            @Override
                            public void onUnityAdsShowFailure(String placementId,
                                                              UnityAds.UnityAdsShowError error,
                                                              String message) {
                                unityPlacementId = null;
                                finishShow();
                            }

                            @Override
                            public void onUnityAdsShowStart(String placementId) {
                            }

                            @Override
                            public void onUnityAdsShowClick(String placementId) {
                            }

                            @Override
                            public void onUnityAdsShowComplete(String placementId,
                                                               UnityAds.UnityAdsShowCompletionState state) {
                                unityPlacementId = null;
                                finishShow();
                            }
                        });
                return true;
            default:
                return false;
        }
    }

    /** Runs the caller's continuation once, then warms up the next ad. */
    private void finishShow() {
        final Runnable done = pendingOnDone;
        pendingOnDone = null;
        readyNetwork = null;
        if (done != null) {
            handler.post(done);
        }
        handler.post(this::preload);
    }

    // ---------------------------------------------------------------- plumbing

    private void onLoaded(int attempt, AdNetwork network) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        loading = false;
        readyNetwork = network;
        Log.d(TAG, "Interstitial ready on " + network);
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

    private void onFailed(int attempt, AdNetwork network, String reason) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        Log.d(TAG, "Interstitial on " + network + " failed (" + reason + "), trying next network");
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
            if (facebookAd != null) {
                facebookAd.destroy();
            }
            if (maxAd != null) {
                maxAd.destroy();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release interstitial", t);
        } finally {
            admobAd = null;
            maxAd = null;
            facebookAd = null;
            applovinAd = null;
            applovinDialog = null;
            unityPlacementId = null;
            vungleAd = null;
            inmobiAd = null;
            readyNetwork = null;
        }
    }

    private static final Runnable NO_OP = () -> {
    };
}
