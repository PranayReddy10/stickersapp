package com.stickersanimated.kissing.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import com.stickersanimated.kissing.R;

/**
 * The app's one notification channel.
 *
 * <p>It used to be created inline by every send, under the id {@code my_channel_01} and
 * named "my_channel", which is what the user saw in Android's notification settings. It
 * is created once at startup now, so a message that arrives as a notification payload -
 * which Android posts on its own, without the app's code running - has a channel to land
 * in as well. The id is also declared in the manifest as the FCM default.
 */
public final class Notifications {

    /** Must match {@code default_notification_channel_id} in the manifest. */
    public static final String CHANNEL_ID = "stickers_updates";

    /** The old inline channel, deleted on upgrade so it stops showing in settings. */
    private static final String LEGACY_CHANNEL_ID = "my_channel_01";

    private Notifications() {
    }

    /** Creates the channel if it is not there yet. Safe to call from anywhere. */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        final NotificationManager manager = (NotificationManager)
                context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.enableLights(true);
        channel.setLightColor(Color.parseColor("#25D366"));
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
        } catch (Throwable ignored) {
            // Nothing to delete on a fresh install.
        }
    }
}
