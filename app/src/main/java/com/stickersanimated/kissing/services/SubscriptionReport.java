package com.stickersanimated.kissing.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.Purchase;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ApiResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Tells the panel what Google Play said about this device's subscription.
 *
 * <p>Play knows who is paying; it has no idea which of your users that is, which
 * device they use, or how many times they have subscribed before. The app is the
 * only place those facts meet, so it reports them.
 *
 * <p>Nothing here changes what the app does: the subscription is granted by Play
 * and checked by Play, and a report that fails to send is simply not sent. It is
 * a record, never a gate.
 */
public final class SubscriptionReport {

    private static final String TAG = "SubscriptionReport";

    /**
     * What this device last told the panel: the purchase token and the state it was
     * in. The state is part of it on purpose - a subscription that stops renewing
     * is the same purchase saying something new, and the panel should hear it that
     * day rather than the next one.
     */
    private static final String LAST_TOKEN = "SUB_REPORTED_TOKEN";
    /** The day it last said so: the app is opened far more often than this changes. */
    private static final String LAST_DAY = "SUB_REPORTED_DAY";

    private static final String PLATFORM = "google_play";

    private SubscriptionReport() {
    }

    /** A subscription Play says is live, seen on this device. */
    public static void active(Context context, Purchase purchase) {
        if (context == null || purchase == null) {
            return;
        }
        final PrefManager prefManager = new PrefManager(context.getApplicationContext());
        final String token = purchase.getPurchaseToken();
        if (token == null || token.isEmpty()) {
            return;
        }

        final int renewing = purchase.isAutoRenewing() ? 1 : 0;
        final String signature = token + "|" + renewing;

        // Nothing has changed and the panel already heard it today. Play is asked at
        // every launch; it does not need the same answer a dozen times a day. A new
        // purchase, or the same one no longer renewing, goes through at once.
        if (signature.equals(prefManager.getString(LAST_TOKEN))
                && today().equals(prefManager.getString(LAST_DAY))) {
            return;
        }

        send(context, prefManager, "active", token, signature,
                purchase.getProducts().isEmpty() ? null : purchase.getProducts().get(0),
                purchase.getOrderId(), purchase.getPurchaseTime(), renewing);
    }

    /**
     * Play returned no live subscription for this device.
     *
     * <p>Only worth saying when this device previously reported one - otherwise it
     * would be every launch of every install that has never subscribed.
     */
    public static void none(Context context) {
        if (context == null) {
            return;
        }
        final PrefManager prefManager = new PrefManager(context.getApplicationContext());
        final String reported = prefManager.getString(LAST_TOKEN);
        if (reported == null || reported.isEmpty()) {
            return;
        }
        send(context, prefManager, "expired", "", "", null, null, 0, 0);
    }

    private static void send(Context context, final PrefManager prefManager, final String state,
                             final String token, final String signature, String product,
                             String order, long startedMillis, int renewing) {
        final String userId = prefManager.getString("ID_USER");

        final Call<ApiResponse> call = apiClient.getClient().create(apiRest.class)
                .reportSubscription(
                        deviceId(context),
                        number(userId),
                        state,
                        product,
                        token,
                        order,
                        startedMillis > 0 ? startedMillis : null,
                        renewing,
                        PLATFORM);

        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call,
                                   @NonNull Response<ApiResponse> response) {
                if (!response.isSuccessful()) {
                    return;
                }
                // Remembered only once the panel has it, so a failed report is retried
                // at the next launch rather than quietly forgotten.
                if ("expired".equals(state)) {
                    prefManager.setString(LAST_TOKEN, "");
                    prefManager.setString(LAST_DAY, "");
                } else {
                    prefManager.setString(LAST_TOKEN, signature);
                    prefManager.setString(LAST_DAY, today());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "Could not report the subscription: " + t.getMessage());
            }
        });
    }

    /** The same id the app already registers for notifications, so devices line up. */
    @SuppressLint("HardwareIds")
    private static String deviceId(Context context) {
        try {
            return Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            return "";
        }
    }

    private static Integer number(String value) {
        try {
            return value == null || value.isEmpty() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
