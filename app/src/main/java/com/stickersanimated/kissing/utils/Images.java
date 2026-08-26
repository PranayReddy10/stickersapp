package com.stickersanimated.kissing.utils;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import com.stickersanimated.kissing.R;

/**
 * Picasso, with the one case it refuses to handle taken care of.
 *
 * <p>{@code Picasso.load(String)} throws on an empty string - it only tolerates null -
 * and a profile picture is empty whenever an account was created without one, which is
 * every account made with an email address. That threw the moment the drawer header was
 * drawn, right after signing up.
 */
public final class Images {

    private Images() {
    }

    /** A profile picture, falling back to the default avatar when there is no URL. */
    public static RequestCreator profile(String url) {
        if (url == null || url.trim().isEmpty()) {
            return Picasso.get().load(R.drawable.profile);
        }
        return Picasso.get().load(url.trim());
    }
}
