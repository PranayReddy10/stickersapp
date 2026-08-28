package com.stickersanimated.kissing.ads;

/**
 * The two banner shapes the app asks for.
 *
 * <p>{@link #MREC} is the 300x250 block. It matters because it is the one shape every
 * network sells: a slot that no network will fill with a native ad can still be filled
 * with an MREC, which is why the in-feed and full screen slots fall back to it.
 */
public enum BannerSize {

    /** The strip along the bottom of a screen. */
    STANDARD,

    /** The 300x250 block, for a slot that has room for a card. */
    MREC
}
