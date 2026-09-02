package com.stickersanimated.kissing;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.MobileAds;
import com.orhanobut.hawk.Hawk;
import com.stickersanimated.kissing.ads.AdsInitializer;
import com.stickersanimated.kissing.utils.EdgeToEdgeHelper;
import com.stickersanimated.kissing.utils.Notifications;

import java.util.Arrays;

public class Application extends MultiDexApplication {

    private static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        Hawk.init(this).build();

        instance = this;

        MobileAds.setRequestConfiguration(new RequestConfiguration.Builder()
                .setTestDeviceIds(Arrays.asList("B078212DB5EAB81998E2EBF273B2A90E"))
                .build());

        // Asks every ad network to start. It returns at once: the SDKs themselves are
        // started off this thread, because Android gives a process a fixed window to
        // finish starting and seven SDKs opening files and sockets does not fit in it.
        AdsInitializer.initialize(this);

        // Android 16 always draws windows edge to edge; this keeps the system bars from
        // covering the app's own content.
        EdgeToEdgeHelper.install(this);

        // The notification channel has to exist before the first message arrives, since
        // a notification payload is posted by Android without the app's code running.
        Notifications.ensureChannel(this);
    }

    public static Application getInstance() {
        return instance;
    }

    public static boolean hasNetwork() {
        return instance != null && instance.checkIfHasNetwork();
    }

    public boolean checkIfHasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            final NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
