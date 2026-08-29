package com.stickersanimated.kissing.ads;

import android.os.SystemClock;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How long to leave a network alone after it has just turned a request down.
 *
 * <p>A list can ask for a dozen ad slots in a minute, and each one used to walk the whole
 * waterfall again: the same networks were asked the same question over and over, every
 * dead one costing the slot its timeout before the next was tried. Meta answers that with
 * "Ad was re-loaded too frequently", and the viewer waits half a minute for an ad that was
 * never going to come.
 *
 * <p>So a refusal is remembered for a short while. The network is skipped until then, the
 * waterfall reaches whatever can fill much sooner, and a fill clears the note at once.
 */
final class AdCooldown {

    private static final String TAG = "AdCooldown";

    /** After an ordinary "no ad for you". */
    private static final long NO_FILL_MS = 60_000L;
    /** After the network says it is being asked too often. */
    private static final long RATE_LIMIT_MS = 300_000L;

    private static final Map<String, Long> until = new ConcurrentHashMap<>();

    private AdCooldown() {
    }

    /** True while this network should not be asked for this format. */
    static boolean waiting(AdFormat format, AdNetwork network) {
        final Long when = until.get(key(format, network));
        if (when == null) {
            return false;
        }
        if (SystemClock.elapsedRealtime() >= when) {
            until.remove(key(format, network));
            return false;
        }
        return true;
    }

    /** Notes a refusal, for longer when the network is complaining about the pace. */
    static void failed(AdFormat format, AdNetwork network, String reason) {
        final boolean rateLimited = reason != null
                && (reason.toLowerCase().contains("too frequently")
                || reason.toLowerCase().contains("rate limit")
                || reason.toLowerCase().contains("too many"));
        final long wait = rateLimited ? RATE_LIMIT_MS : NO_FILL_MS;
        until.put(key(format, network), SystemClock.elapsedRealtime() + wait);
        Log.d(TAG, network + " is out of the " + format + " waterfall for "
                + (wait / 1000) + "s");
    }

    /** A fill means whatever was wrong has passed. */
    static void filled(AdFormat format, AdNetwork network) {
        until.remove(key(format, network));
    }

    private static String key(AdFormat format, AdNetwork network) {
        return format.name() + ':' + network.name();
    }
}
