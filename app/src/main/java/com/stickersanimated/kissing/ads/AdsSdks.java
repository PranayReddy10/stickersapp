package com.stickersanimated.kissing.ads;

import android.content.Context;

/**
 * Whether a network's SDK is up yet, and how to start it.
 *
 * <p>Unity, Vungle and InMobi are started with credentials the panel sends, which are not in
 * preferences until the settings call has come back - so on a fresh session the first ad
 * request usually arrives before those SDKs are ready. The managers use this to start the
 * SDK and come back in a moment rather than writing the network off.
 */
public final class AdsSdks {

    private AdsSdks() {
    }

    /** Starts the SDK for {@code network} if it has credentials and is not running. */
    public static void start(Context context, AdNetwork network) {
        switch (network) {
            case UNITY:
                AdsInitializer.initializeUnity(context);
                break;
            case VUNGLE:
                AdsInitializer.initializeVungle(context);
                break;
            case INMOBI:
                AdsInitializer.initializeInmobi(context);
                break;
            case STARTIO:
                AdsInitializer.initializeStartIo(context);
                break;
            default:
                break;
        }
    }

    /** True when the network can be asked for an ad right now. */
    public static boolean isReady(AdNetwork network) {
        switch (network) {
            case UNITY:
                return AdsInitializer.isUnityReady();
            case VUNGLE:
                return AdsInitializer.isVungleReady();
            case INMOBI:
                return AdsInitializer.isInmobiReady();
            case STARTIO:
                return AdsInitializer.isStartIoReady();
            default:
                // AdMob, Meta and AppLovin queue requests made before they finish starting.
                return true;
        }
    }
}
