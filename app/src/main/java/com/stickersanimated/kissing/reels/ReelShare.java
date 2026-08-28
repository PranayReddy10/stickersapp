package com.stickersanimated.kissing.reels;

import android.content.Context;
import android.content.Intent;

import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.config.Config;
import com.stickersanimated.kissing.entity.ReelApi;

/**
 * Sharing a reel.
 *
 * <p>What goes out is a page on the site, never the file on storage: a storage link
 * exposes where the media is kept, shows a bare video with no way back to the app, and
 * breaks the moment the reel is deleted or the bucket is moved. The page plays the reel
 * and carries the app's own install link, the same way a pack is shared.
 */
public final class ReelShare {

    private ReelShare() {
    }

    /** The reel's page on the site, matching the pack share links. */
    public static String link(ReelApi reel) {
        return Config.API_URL.replace("api", "share") + "reel/" + reel.getId() + ".html";
    }

    /** Caption first, then the link: what somebody reads before deciding to tap it. */
    public static String text(Context context, ReelApi reel) {
        final String caption = reel.getCaption();
        return (caption.isEmpty()
                ? context.getString(R.string.reel_share_subject, reel.getUser())
                : caption) + "\n\n" + link(reel);
    }

    /** The chooser, with the reel's page as the shared text. */
    public static void start(Context context, ReelApi reel) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.reel_share_subject, reel.getUser()));
        intent.putExtra(Intent.EXTRA_TEXT, text(context, reel));
        context.startActivity(Intent.createChooser(intent,
                context.getString(R.string.reel_share)));
    }
}
