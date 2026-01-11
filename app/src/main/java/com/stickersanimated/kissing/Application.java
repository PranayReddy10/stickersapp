package com.stickersanimated.kissing;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.facebook.ads.AdSettings;
import com.facebook.ads.AudienceNetworkAds;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.orhanobut.hawk.Hawk;

import java.util.Arrays;

public class Application extends MultiDexApplication {

    private static Application instance;

    @Override
    public void onCreate()
    {
        super.onCreate();
        Hawk.init(this).build();

        instance = this;

        AudienceNetworkAds.initialize(instance);
        MobileAds.initialize(this, initializationStatus -> {
        });
        new RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("B078212DB5EAB81998E2EBF273B2A90E"));
        //FacebookSdk.sdkInitialize(instance);

        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(getString(R.string.applovin_sdk_key))
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .setTestDeviceAdvertisingIds(Arrays.asList("4d66b2b0-f348-4360-b3d8-9b5da7dee910","cae04a9c-e0a6-49a9-bb7d-68a4d04ab50d"))
                .build();

        AppLovinSdk.getInstance(instance).initialize(initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig) {
                // Start loading ads
                Log.d("applovin_sdk", "Applovin Sdk Initialized Done");
            }
        });
        AdSettings.setDataProcessingOptions( new String[] {} );

    }

    public static Application getInstance ()
    {
        return instance;
    }

    public static boolean hasNetwork ()
    {
        return instance.checkIfHasNetwork();
    }

    public boolean checkIfHasNetwork()
    {
        ConnectivityManager cm = (ConnectivityManager) getSystemService( Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
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
