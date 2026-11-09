package com.stickersanimated.kissing.ads;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The ads of your own, kept on the phone.
 *
 * <p>Held in preferences rather than fetched when a slot needs filling. This is
 * the last network in the waterfall, reached precisely when the others could not
 * be: a phone on a bad connection, or one the networks have nothing for. An
 * answer that needs its own request would fail in exactly those moments, so the
 * list is downloaded ahead of time and read from disk afterwards.
 */
public final class HouseAds {

    private static final String TAG = "HouseAds";
    private static final String KEY_JSON = "HOUSE_ADS_JSON";
    private static final String KEY_FETCHED = "HOUSE_ADS_FETCHED";

    /** How long before the list is asked for again, if the server says nothing. */
    private static final long DEFAULT_CACHE_MS = 30 * 60 * 1000L;

    private static final Random RANDOM = new Random();

    /** Parsed once per process rather than on every slot. */
    private static volatile List<HouseAd> cached;
    private static volatile boolean fetching;

    private HouseAds() {
    }

    /** The server's answer: the ads, and how long to keep them. */
    public static class Payload {
        @SerializedName("cache") public int cache;
        @SerializedName("ads") public List<HouseAd> ads;
    }

    /**
     * Downloads the list if it is missing or stale. Safe to call often - it does
     * nothing when the copy on disk is still good.
     */
    public static void refresh(Context context) {
        final Context app = context.getApplicationContext();
        final PrefManager prf = new PrefManager(app);

        long fetched = 0L;
        try {
            final String raw = prf.getString(KEY_FETCHED);
            fetched = TextUtils.isEmpty(raw) ? 0L : Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
        }

        if (fetching || System.currentTimeMillis() - fetched < DEFAULT_CACHE_MS) {
            return;
        }
        fetching = true;

        apiClient.getClient().create(apiRest.class).houseAds().enqueue(new Callback<Payload>() {
            @Override
            public void onResponse(Call<Payload> call, Response<Payload> response) {
                fetching = false;
                final Payload body = response.body();
                if (!response.isSuccessful() || body == null || body.ads == null) {
                    return;
                }
                final List<HouseAd> usable = new ArrayList<>();
                for (HouseAd ad : body.ads) {
                    if (ad != null && ad.usable()) {
                        usable.add(ad);
                    }
                }
                cached = usable;
                prf.setString(KEY_JSON, new Gson().toJson(usable));
                prf.setString(KEY_FETCHED, String.valueOf(System.currentTimeMillis()));
                if (usable.isEmpty()) {
                    // Said out loud: the server answering with nothing looks
                    // exactly like the app never asking.
                    Log.d(TAG, "The server has no ads of your own, or none complete"
                            + " enough to show (every one needs an address, and"
                            + " everything but a native one needs a picture)");
                } else {
                    Log.d(TAG, "Kept " + usable.size() + " of your own ads");
                }
            }

            @Override
            public void onFailure(Call<Payload> call, Throwable t) {
                fetching = false;
                Log.d(TAG, "Could not fetch your own ads", t);
                // Whatever is already on disk stays. An old house ad is worth more
                // than an empty slot, which is the alternative here.
            }
        });
    }

    /** Everything on disk, parsed once. */
    private static List<HouseAd> all(Context context) {
        if (cached != null) {
            return cached;
        }
        final String json = new PrefManager(context.getApplicationContext()).getString(KEY_JSON);
        if (TextUtils.isEmpty(json)) {
            cached = new ArrayList<>();
            return cached;
        }
        try {
            final List<HouseAd> parsed = new Gson().fromJson(json,
                    new TypeToken<List<HouseAd>>() { }.getType());
            cached = parsed == null ? new ArrayList<HouseAd>() : parsed;
        } catch (Exception e) {
            // Saved by an older build, or half written. Start again rather than
            // let one bad string stop every slot from filling.
            Log.w(TAG, "Could not read the saved ads", e);
            cached = new ArrayList<>();
        }
        return cached;
    }

    /**
     * What is on the phone right now, for the log.
     *
     * <p>"Not loading" is almost always one of three things: nothing saved yet,
     * nothing of that shape, or the list never downloaded. Guessing between them
     * from an empty slot is impossible, so the slot says which.
     */
    public static String describe(Context context) {
        final List<HouseAd> ads = all(context);
        if (ads.isEmpty()) {
            // Ask again now. The first fetch runs at launch and can lose to a
            // slow start; without this the next try is the next launch.
            refresh(context);
            return "none saved - asking the server again";
        }
        final StringBuilder shapes = new StringBuilder();
        for (HouseAd ad : ads) {
            if (shapes.length() > 0) {
                shapes.append(", ");
            }
            shapes.append(ad.format);
        }

        return ads.size() + " saved: " + shapes;
    }

    /** Whether there is anything at all for this shape. */
    public static boolean has(Context context, AdFormat format) {
        return pick(context, format) != null;
    }

    /** The MREC shape, which AdFormat has no constant for. */
    public static boolean hasMrec(Context context) {
        return pick(context, MREC) != null;
    }

    public static HouseAd pick(Context context, AdFormat format) {
        return pick(context, format == null ? AdFormat.BANNER.name() : format.name());
    }

    /** An MREC is a banner slot in the app but its own shape on the panel. */
    public static final String MREC = "MREC";

    /**
     * One ad for this shape, chosen by its share.
     *
     * <p>A weighted draw rather than the first match: two ads at 1 and 3 should
     * come up in that proportion, which is what the panel's "share" promises.
     */
    public static HouseAd pick(Context context, String format) {
        int total = 0;
        final List<HouseAd> fitting = new ArrayList<>();
        for (HouseAd ad : all(context)) {
            if (ad != null && ad.format != null && ad.format.equals(format) && ad.usable()) {
                fitting.add(ad);
                total += ad.share();
            }
        }
        if (fitting.isEmpty()) {
            return null;
        }

        int draw = RANDOM.nextInt(total);
        for (HouseAd ad : fitting) {
            draw -= ad.share();
            if (draw < 0) {
                return ad;
            }
        }
        return fitting.get(fitting.size() - 1);
    }
}
