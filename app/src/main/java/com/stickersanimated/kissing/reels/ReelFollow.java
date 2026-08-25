package com.stickersanimated.kissing.reels;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ApiResponse;
import com.stickersanimated.kissing.entity.ReelApi;
import com.stickersanimated.kissing.ui.LoginActivity;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Follow / unfollow the author of a reel, shared by the card feed and the player.
 *
 * The existing endpoint answers 200 when the viewer now follows and 202 when they
 * no longer do, so the reply is authoritative and the optimistic flip is corrected
 * from it rather than assumed.
 */
public final class ReelFollow {

    public interface Callback0 {
        void onState(boolean following);
    }

    private ReelFollow() {
    }

    /** Returns false when the user had to be sent to sign in first. */
    public static boolean toggle(@NonNull Context context, @NonNull ReelApi reel,
                                 @NonNull Callback0 callback) {
        final PrefManager prefManager = new PrefManager(context.getApplicationContext());
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            final Intent intent = new Intent(context, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return false;
        }

        final int author;
        final int follower;
        try {
            author = Integer.parseInt(reel.getUserid());
            follower = Integer.parseInt(prefManager.getString("ID_USER"));
        } catch (NumberFormatException e) {
            return false;
        }
        if (author == follower) {
            return false; // following yourself is not a thing
        }

        final boolean wasFollowing = reel.isFollowing();
        reel.setFollowing(!wasFollowing);
        callback.onState(!wasFollowing);

        apiClient.getClient().create(apiRest.class)
                .follow(author, follower, prefManager.getString("TOKEN_USER"))
                .enqueue(new retrofit2.Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call,
                                           @NonNull Response<ApiResponse> response) {
                        final ApiResponse body = response.body();
                        if (body == null || body.getCode() == null) {
                            return;
                        }
                        // 200 = now following, 202 = no longer following.
                        // getCode() is an Integer; == would compare references.
                        final boolean following = body.getCode().intValue() == 200;
                        reel.setFollowing(following);
                        callback.onState(following);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        reel.setFollowing(wasFollowing);
                        callback.onState(wasFollowing);
                    }
                });
        return true;
    }
}
