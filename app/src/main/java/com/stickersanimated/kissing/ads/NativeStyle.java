package com.stickersanimated.kissing.ads;

/** How much room a native placement has, which decides the layout it is rendered into. */
public enum NativeStyle {

    /** A card in a list: icon, text and the creative below it. */
    INLINE,

    /** The whole page, for the ad pages between reels. */
    FULLSCREEN,

    /** A strip along the bottom of a screen, where a creative would be in the way. */
    BAR
}
