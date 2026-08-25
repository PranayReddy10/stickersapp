package com.stickersanimated.kissing.reels;

import java.util.Locale;

/** Small display helpers shared by the reels feed and the player. */
final class ReelFormat {

    private ReelFormat() {
    }

    /** 1200 -> 1.2K, so a long count cannot push a row around. */
    static String count(int value) {
        if (value < 1000) {
            return String.valueOf(value);
        }
        if (value < 1000000) {
            return String.format(Locale.US, "%.1fK", value / 1000f).replace(".0", "");
        }
        return String.format(Locale.US, "%.1fM", value / 1000000f).replace(".0", "");
    }
}
