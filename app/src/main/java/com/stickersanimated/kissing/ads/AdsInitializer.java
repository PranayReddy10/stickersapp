package com.stickersanimated.kissing.ads;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.facebook.ads.AdSettings;
import com.facebook.ads.AudienceNetworkAds;
import com.google.android.gms.ads.MobileAds;
import com.stickersanimated.kissing.R;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;

import java.util.Arrays;

/**
 * Starts every ad SDK the app can fall back to.
 *
 * <p>Each SDK is initialised inside its own guard: a network whose SDK blows up at startup
 * (missing key, incompatible device, stripped class) must not take the app down with it,
 * it simply drops out of the waterfall.
 */
public final class AdsInitializer {

    private static final String TAG = "AdsInitializer";

    private static volatile boolean unityRequested;

    private AdsInitializer() {
    }

    public static void initialize(Context context) {
        final Context app = context.getApplicationContext();
        initializeAdMob(app);
        initializeMeta(app);
        initializeAppLovin(app);
        initializeUnity(app);
    }

    private static void initializeAdMob(Context context) {
        try {
            MobileAds.initialize(context, initializationStatus -> {
            });
        } catch (Throwable t) {
            Log.w(TAG, "AdMob failed to initialize", t);
        }
    }

    private static void initializeMeta(Context context) {
        try {
            AudienceNetworkAds.initialize(context);
            AdSettings.setDataProcessingOptions(new String[]{});
        } catch (Throwable t) {
            Log.w(TAG, "Meta Audience Network failed to initialize", t);
        }
    }

    private static void initializeAppLovin(Context context) {
        try {
            final AppLovinSdkInitializationConfiguration initConfig =
                    AppLovinSdkInitializationConfiguration
                            .builder(context.getString(R.string.applovin_sdk_key))
                            .setMediationProvider(AppLovinMediationProvider.MAX)
                            .setTestDeviceAdvertisingIds(Arrays.asList(
                                    "4d66b2b0-f348-4360-b3d8-9b5da7dee910",
                                    "cae04a9c-e0a6-49a9-bb7d-68a4d04ab50d"))
                            .build();
            AppLovinSdk.getInstance(context).initialize(initConfig,
                    sdkConfig -> Log.d(TAG, "AppLovin SDK initialized"));
        } catch (Throwable t) {
            Log.w(TAG, "AppLovin failed to initialize", t);
        }
    }

    /**
     * Unity is only started when the panel has sent a game id. Safe to call repeatedly -
     * the first call wins and {@link UnityAds#isInitialized()} guards the rest.
     */
    public static void initializeUnity(Context context) {
        final String gameId = new AdsConfig(context).unityGameId();
        if (TextUtils.isEmpty(gameId) || unityRequested) {
            return;
        }
        unityRequested = true;
        try {
            if (UnityAds.isInitialized()) {
                return;
            }
            UnityAds.initialize(context.getApplicationContext(), gameId, false,
                    new IUnityAdsInitializationListener() {
                        @Override
                        public void onInitializationComplete() {
                            Log.d(TAG, "Unity Ads initialized");
                        }

                        @Override
                        public void onInitializationFailed(UnityAds.UnityAdsInitializationError error,
                                                           String message) {
                            unityRequested = false;
                            Log.w(TAG, "Unity Ads failed to initialize: " + message);
                        }
                    });
        } catch (Throwable t) {
            unityRequested = false;
            Log.w(TAG, "Unity Ads failed to initialize", t);
        }
    }

    /** True when Unity can currently be asked for an ad. */
    public static boolean isUnityReady() {
        try {
            return UnityAds.isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }
}
