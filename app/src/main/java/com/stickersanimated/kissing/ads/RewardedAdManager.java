package com.stickersanimated.kissing.ads;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.adview.AppLovinIncentivizedInterstitial;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdRewardListener;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.inmobi.ads.AdMetaInfo;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiInterstitial;
import com.inmobi.ads.listeners.InterstitialAdEventListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;
import com.vungle.ads.AdConfig;
import com.vungle.ads.BaseAd;
import com.vungle.ads.RewardedAdListener;
import com.vungle.ads.VungleError;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rewarded video with the same failover behaviour as the other formats: every configured
 * network is tried in turn, and the caller is told once - through {@link Listener} - whether
 * an ad is available, whether the user earned the reward, and when the ad went away.
 */
public final class RewardedAdManager {

    private static final String TAG = "RewardedAds";

    /** Callbacks for the screen that asked for a rewarded ad. */
    public interface Listener {
        /** An ad is warm and {@link #show()} will work. */
        void onAdReady();

        /** No network could supply an ad. */
        void onAdUnavailable();

        /** The user watched far enough to earn the reward. */
        void onUserRewarded();

        /** The ad was dismissed, rewarded or not. */
        void onAdClosed();
    }

    private final Activity activity;
    private final AdsConfig config;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private List<AdNetwork> waterfall = Collections.emptyList();
    private int index;
    private int attemptId;
    private boolean destroyed;
    private boolean loading;
    private boolean rewarded;
    private Runnable timeoutRunnable;

    private AdNetwork readyNetwork;
    private RewardedAd admobAd;
    private MaxRewardedAd maxAd;
    private AppLovinIncentivizedInterstitial applovinAd;
    private com.facebook.ads.RewardedVideoAd facebookAd;
    private String unityPlacementId;
    private com.vungle.ads.RewardedAd vungleAd;
    private InMobiInterstitial inmobiAd;

    public RewardedAdManager(Activity activity, Listener listener) {
        this.activity = activity;
        this.config = new AdsConfig(activity);
        this.listener = listener;
    }

    public boolean isEnabled() {
        return config.isEnabled(AdFormat.REWARDED);
    }

    public boolean isReady() {
        return readyNetwork != null;
    }

    /** Starts (or restarts) the waterfall. */
    public void load() {
        if (destroyed || loading || readyNetwork != null || !isEnabled()) {
            return;
        }
        waterfall = config.waterfall(AdFormat.REWARDED);
        index = 0;
        loading = true;
        loadNext();
    }

    /**
     * Shows the warm ad. Returns false when nothing is ready, in which case a fresh load is
     * kicked off and the listener hears about the outcome.
     */
    public boolean show() {
        if (destroyed || readyNetwork == null) {
            load();
            return false;
        }
        rewarded = false;
        final AdNetwork network = readyNetwork;
        readyNetwork = null;
        try {
            switch (network) {
                case ADMOB:
                    if (admobAd == null) {
                        return false;
                    }
                    admobAd.show(activity, rewardItem -> onRewarded());
                    return true;
                case MAX:
                    if (maxAd == null || !maxAd.isReady()) {
                        return false;
                    }
                    maxAd.showAd(activity);
                    return true;
                case APPLOVIN:
                    if (applovinAd == null || !applovinAd.isAdReadyToDisplay()) {
                        return false;
                    }
                    applovinAd.show(activity, rewardListener, null, displayListener);
                    return true;
                case FACEBOOK:
                    return facebookAd != null && facebookAd.isAdLoaded() && facebookAd.show();
                case VUNGLE:
                    if (vungleAd == null || !Boolean.TRUE.equals(vungleAd.canPlayAd())) {
                        return false;
                    }
                    vungleAd.play(activity);
                    return true;
                case INMOBI:
                    if (inmobiAd == null || !inmobiAd.isReady()) {
                        return false;
                    }
                    inmobiAd.show();
                    return true;
                case UNITY:
                    if (unityPlacementId == null) {
                        return false;
                    }
                    UnityAds.show(activity, unityPlacementId, new UnityAdsShowOptions(),
                            unityShowListener);
                    return true;
                default:
                    return false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Showing rewarded ad on " + network + " threw", t);
            onClosed();
            return false;
        }
    }

    public void destroy() {
        destroyed = true;
        cancelTimeout();
        try {
            if (maxAd != null) {
                maxAd.destroy();
            }
            if (facebookAd != null) {
                facebookAd.destroy();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release rewarded ad", t);
        }
        admobAd = null;
        maxAd = null;
        applovinAd = null;
        facebookAd = null;
        unityPlacementId = null;
        vungleAd = null;
        inmobiAd = null;
        readyNetwork = null;
    }

    // ---------------------------------------------------------------- loading

    private void loadNext() {
        if (destroyed || index >= waterfall.size()) {
            loading = false;
            if (!destroyed && readyNetwork == null) {
                notifyUnavailable();
            }
            return;
        }
        final AdNetwork network = waterfall.get(index++);
        final String unitId = config.unitId(AdFormat.REWARDED, network);
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
            Log.w(TAG, "Rewarded request to " + network + " threw", t);
            onFailed(attempt, network, String.valueOf(t.getMessage()));
        }
    }

    private void loadAdmob(int attempt, String unitId) {
        RewardedAd.load(activity, unitId, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        if (destroyed || attempt != attemptId) {
                            return;
                        }
                        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                admobAd = null;
                                onClosed();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                admobAd = null;
                                onClosed();
                            }
                        });
                        admobAd = rewardedAd;
                        onLoaded(attempt, AdNetwork.ADMOB);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        onFailed(attempt, AdNetwork.ADMOB, loadAdError.getMessage());
                    }
                });
    }

    private void loadMax(int attempt, String unitId) {
        final MaxRewardedAd ad = MaxRewardedAd.getInstance(unitId, activity);
        ad.setListener(new MaxRewardedAdListener() {
            @Override
            public void onUserRewarded(MaxAd maxAdInfo, MaxReward reward) {
                onRewarded();
            }

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
                onClosed();
            }

            @Override
            public void onAdHidden(MaxAd maxAdInfo) {
                onClosed();
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
        if (applovinAd == null) {
            applovinAd = AppLovinIncentivizedInterstitial.create(activity);
        }
        applovinAd.preload(new AppLovinAdLoadListener() {
            @Override
            public void adReceived(AppLovinAd ad) {
                handler.post(() -> onLoaded(attempt, AdNetwork.APPLOVIN));
            }

            @Override
            public void failedToReceiveAd(int errorCode) {
                handler.post(() -> onFailed(attempt, AdNetwork.APPLOVIN, "code " + errorCode));
            }
        });
    }

    private void loadFacebook(int attempt, String placementId) {
        final com.facebook.ads.RewardedVideoAd ad =
                new com.facebook.ads.RewardedVideoAd(activity, placementId);
        final com.facebook.ads.RewardedVideoAdListener adListener =
                new com.facebook.ads.RewardedVideoAdListener() {
                    @Override
                    public void onRewardedVideoCompleted() {
                        onRewarded();
                    }

                    @Override
                    public void onRewardedVideoClosed() {
                        facebookAd = null;
                        onClosed();
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
        ad.loadAd(ad.buildLoadAdConfig().withAdListener(adListener).build());
    }

    private void loadUnity(int attempt, String placementId) {
        AdsInitializer.initializeUnity(activity);
        if (!AdsInitializer.isUnityReady()) {
            onFailed(attempt, AdNetwork.UNITY, "sdk not initialized");
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
        AdsInitializer.initializeVungle(activity);
        if (!AdsInitializer.isVungleReady()) {
            onFailed(attempt, AdNetwork.VUNGLE, "sdk not initialized");
            return;
        }
        final com.vungle.ads.RewardedAd ad =
                new com.vungle.ads.RewardedAd(activity, placementId, new AdConfig());
        ad.setAdListener(new RewardedAdListener() {
            @Override
            public void onAdLoaded(@NonNull BaseAd baseAd) {
                if (destroyed || attempt != attemptId) {
                    return;
                }
                vungleAd = ad;
                onLoaded(attempt, AdNetwork.VUNGLE);
            }

            @Override
            public void onAdRewarded(@NonNull BaseAd baseAd) {
                onRewarded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull BaseAd baseAd, @NonNull VungleError error) {
                onFailed(attempt, AdNetwork.VUNGLE, error.getMessage());
            }

            @Override
            public void onAdFailedToPlay(@NonNull BaseAd baseAd, @NonNull VungleError error) {
                vungleAd = null;
                onClosed();
            }

            @Override
            public void onAdEnd(@NonNull BaseAd baseAd) {
                vungleAd = null;
                onClosed();
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
        AdsInitializer.initializeInmobi(activity);
        if (!AdsInitializer.isInmobiReady()) {
            onFailed(attempt, AdNetwork.INMOBI, "sdk not initialized");
            return;
        }
        final long placement = parsePlacementId(placementId);
        if (placement == 0L) {
            onFailed(attempt, AdNetwork.INMOBI, "placement id is not a number");
            return;
        }
        // InMobi serves rewarded video through the interstitial class; the placement
        // itself is what makes it rewarded, and onRewardsUnlocked marks the payout.
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
                    public void onRewardsUnlocked(@NonNull InMobiInterstitial interstitial,
                                                  Map<Object, Object> rewards) {
                        onRewarded();
                    }

                    @Override
                    public void onAdDismissed(@NonNull InMobiInterstitial interstitial) {
                        inmobiAd = null;
                        onClosed();
                    }

                    @Override
                    public void onAdDisplayFailed(@NonNull InMobiInterstitial interstitial) {
                        inmobiAd = null;
                        onClosed();
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

    // ------------------------------------------------------- display listeners

    private final AppLovinAdRewardListener rewardListener = new AppLovinAdRewardListener() {
        @Override
        public void userRewardVerified(AppLovinAd ad, Map map) {
            onRewarded();
        }

        @Override
        public void userOverQuota(AppLovinAd ad, Map map) {
        }

        @Override
        public void userRewardRejected(AppLovinAd ad, Map map) {
        }

        @Override
        public void validationRequestFailed(AppLovinAd ad, int errorCode) {
        }

        @Override
        public void userDeclinedToViewAd(AppLovinAd ad) {
        }
    };

    private final AppLovinAdDisplayListener displayListener = new AppLovinAdDisplayListener() {
        @Override
        public void adDisplayed(AppLovinAd ad) {
        }

        @Override
        public void adHidden(AppLovinAd ad) {
            onClosed();
        }
    };

    private final IUnityAdsShowListener unityShowListener = new IUnityAdsShowListener() {
        @Override
        public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error,
                                          String message) {
            unityPlacementId = null;
            onClosed();
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
            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                onRewarded();
            }
            onClosed();
        }
    };

    // ---------------------------------------------------------------- plumbing

    private void onLoaded(int attempt, AdNetwork network) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        loading = false;
        readyNetwork = network;
        Log.d(TAG, "Rewarded ready on " + network);
        if (listener != null) {
            handler.post(listener::onAdReady);
        }
    }

    private void onFailed(int attempt, AdNetwork network, String reason) {
        if (destroyed || attempt != attemptId) {
            return;
        }
        cancelTimeout();
        Log.d(TAG, "Rewarded on " + network + " failed (" + reason + "), trying next network");
        handler.post(this::loadNext);
    }

    private void notifyUnavailable() {
        if (listener != null) {
            handler.post(listener::onAdUnavailable);
        }
    }

    private void onRewarded() {
        if (rewarded || listener == null) {
            return;
        }
        rewarded = true;
        handler.post(listener::onUserRewarded);
    }

    private void onClosed() {
        if (listener != null) {
            handler.post(listener::onAdClosed);
        }
        handler.post(this::load);
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

    @Nullable
    AdNetwork readyNetwork() {
        return readyNetwork;
    }
}
