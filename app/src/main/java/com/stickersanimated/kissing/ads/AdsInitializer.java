package com.stickersanimated.kissing.ads;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.facebook.ads.AdSettings;
import com.facebook.ads.AudienceNetworkAds;
import com.google.android.gms.ads.MobileAds;
import com.inmobi.sdk.InMobiSdk;
import com.inmobi.sdk.SdkInitializationListener;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.stickersanimated.kissing.R;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;
import com.vungle.ads.InitializationListener;
import com.vungle.ads.VungleAds;
import com.vungle.ads.VungleError;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Starts every ad SDK the app can fall back to.
 *
 * <p>Each SDK is initialised inside its own guard: a network whose SDK blows up at startup
 * (missing key, incompatible device, stripped class) must not take the app down with it,
 * it simply drops out of the waterfall.
 */
public final class AdsInitializer {

    private static final String TAG = "AdsInitializer";

    /**
     * Ad SDKs are started here, off whichever thread asked for them.
     *
     * <p>This used to run inline, and the first caller is Application.onCreate - so
     * seven SDKs opened files and sockets inside the window Android gives a process
     * to finish starting. On a slow device, or an emulator, that window closes first
     * and the system kills the app with "failed to complete startup". Nothing here
     * is needed before the first screen draws: an ad that is not ready yet simply
     * is not asked for.
     */
    private static final Executor BACKGROUND = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "ads-init");
        thread.setPriority(Thread.MIN_PRIORITY + 2);
        return thread;
    });

    /** For the two SDKs that expect to be started from the main thread. */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean unityRequested;
    private static volatile boolean vungleRequested;
    private static volatile boolean inmobiRequested;
    private static volatile boolean inmobiReady;
    private static volatile boolean startIoRequested;

    private AdsInitializer() {
    }

    public static void initialize(Context context) {
        final Context app = context.getApplicationContext();

        // Whatever thread the caller is on, the app carries on immediately.
        BACKGROUND.execute(() -> {
            initializeAdMob(app);
            initializeMeta(app);
        });

        // AppLovin and InMobi both want the main thread. Posting them puts them
        // after startup rather than inside it, which is the part that matters.
        MAIN.post(() -> initializeAppLovin(app));

        startUnityLater(app);
        startVungleLater(app);
        startInmobiLater(app);
        startStartIoLater(app);
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
        startUnityLater(context.getApplicationContext());
    }

    private static void startUnityLater(Context context) {
        BACKGROUND.execute(() -> startUnity(context));
    }

    private static void startUnity(Context context) {
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

    /**
     * Liftoff Monetize (Vungle). Only started once the panel has sent an app id.
     */
    public static void initializeVungle(Context context) {
        startVungleLater(context.getApplicationContext());
    }

    private static void startVungleLater(Context context) {
        BACKGROUND.execute(() -> startVungle(context));
    }

    private static void startVungle(Context context) {
        final String appId = new AdsConfig(context).vungleAppId();
        if (TextUtils.isEmpty(appId) || vungleRequested) {
            return;
        }
        vungleRequested = true;
        try {
            if (VungleAds.Companion.isInitialized()) {
                return;
            }
            VungleAds.Companion.init(context.getApplicationContext(), appId,
                    new InitializationListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Vungle initialized");
                        }

                        @Override
                        public void onError(@NonNull VungleError error) {
                            vungleRequested = false;
                            Log.w(TAG, "Vungle failed to initialize: " + error.getMessage());
                        }
                    });
        } catch (Throwable t) {
            vungleRequested = false;
            Log.w(TAG, "Vungle failed to initialize", t);
        }
    }

    /** True when Vungle can currently be asked for an ad. */
    public static boolean isVungleReady() {
        try {
            return VungleAds.Companion.isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * InMobi. Only started once the panel has sent an account id.
     */
    public static void initializeInmobi(Context context) {
        startInmobiLater(context.getApplicationContext());
    }

    private static void startInmobiLater(Context context) {
        MAIN.post(() -> startInmobi(context));
    }

    private static void startInmobi(Context context) {
        final String accountId = new AdsConfig(context).inmobiAccountId();
        if (TextUtils.isEmpty(accountId) || inmobiRequested) {
            return;
        }
        inmobiRequested = true;
        try {
            final JSONObject consent = new JSONObject();
            consent.put(InMobiSdk.IM_GDPR_CONSENT_AVAILABLE, true);
            consent.put("gdpr", "0");
            InMobiSdk.init(context.getApplicationContext(), accountId, consent,
                    new SdkInitializationListener() {
                        @Override
                        public void onInitializationComplete(@Nullable Error error) {
                            if (error == null) {
                                inmobiReady = true;
                                Log.d(TAG, "InMobi initialized");
                            } else {
                                inmobiRequested = false;
                                Log.w(TAG, "InMobi failed to initialize: " + error.getMessage());
                            }
                        }
                    });
        } catch (Throwable t) {
            inmobiRequested = false;
            Log.w(TAG, "InMobi failed to initialize", t);
        }
    }

    /** True when InMobi can currently be asked for an ad. */
    public static boolean isInmobiReady() {
        return inmobiReady;
    }

    /**
     * Start.io. One app id covers every format, so there is nothing else to configure -
     * which is the point of having it at the end of the waterfall.
     */
    public static void initializeStartIo(Context context) {
        startStartIoLater(context.getApplicationContext());
    }

    private static void startStartIoLater(Context context) {
        BACKGROUND.execute(() -> startStartIo(context));
    }

    private static void startStartIo(Context context) {
        final String appId = new AdsConfig(context).startIoAppId();
        if (TextUtils.isEmpty(appId) || startIoRequested) {
            return;
        }
        startIoRequested = true;
        try {
            // The splash Start.io can show on its own is not wanted: this app has its own,
            // and an ad on launch is not what the waterfall is for.
            StartAppAd.disableSplash();
            StartAppSDK.init(context.getApplicationContext(), appId, false);
            StartAppSDK.setUserConsent(context.getApplicationContext(),
                    "pas", System.currentTimeMillis(), true);
            Log.d(TAG, "Start.io initialized");
        } catch (Throwable t) {
            startIoRequested = false;
            Log.w(TAG, "Start.io failed to initialize", t);
        }
    }

    /** True once Start.io has been started with an app id. */
    public static boolean isStartIoReady() {
        return startIoRequested;
    }
}
