package com.stickersanimated.kissing.ads;

import android.util.Log;

import com.google.gson.JsonObject;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Telling the panel that one of your own ads was shown or tapped.
 *
 * <p>Fire and forget: nothing on screen waits for it and a failure is not worth
 * telling anybody about. The counting itself happens on the server, which adds
 * in SQL rather than reading a number back first, so a lost report costs one
 * count and nothing else.
 */
public final class HouseAdReport {

    private static final String TAG = "HouseAdReport";

    /**
     * Ads already counted as seen this run.
     *
     * <p>A banner is re-attached whenever its screen is rebuilt - a rotation, a
     * tab change - and counting each of those as a fresh impression would make
     * the numbers in the panel mean nothing.
     */
    private static final Set<Integer> seen =
            Collections.synchronizedSet(new HashSet<Integer>());

    private HouseAdReport() {
    }

    public static void impression(HouseAd ad) {
        if (ad == null || !seen.add(ad.id)) {
            return;
        }
        send(apiClient.getClient().create(apiRest.class).houseAdImpression(ad.id), "impression");
    }

    /** Every tap counts: a second tap is a second visit to wherever it goes. */
    public static void click(HouseAd ad) {
        if (ad == null) {
            return;
        }
        send(apiClient.getClient().create(apiRest.class).houseAdClick(ad.id), "click");
    }

    private static void send(Call<JsonObject> call, final String what) {
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.d(TAG, "Could not report an " + what, t);
            }
        });
    }
}
