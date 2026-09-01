package com.stickersanimated.kissing.reels;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ReelApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * Uploads a reel: ask the panel for a short lived URL, PUT the bytes straight to
 * DigitalOcean Spaces, then tell the panel the file has landed.
 *
 * The file never passes through the web server, so a long video is not bound by
 * PHP's upload limits. Nothing is written to the database until the PUT succeeds,
 * so an abandoned upload leaves an orphan object rather than a broken reel.
 */
public class ReelUploader {

    private static final String TAG = "ReelUploader";

    public interface Callbacks {
        void onProgress(int percent);

        void onDone(String message);

        void onError(String message);
    }

    private final Context context;
    private final Callbacks callbacks;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient();

    public ReelUploader(Context context, Callbacks callbacks) {
        this.context = context.getApplicationContext();
        this.callbacks = callbacks;
    }

    public void upload(final Uri uri, final String type, final String extension,
                       final String caption, final int width, final int height,
                       final int durationSeconds) {
        final PrefManager prefManager = new PrefManager(context);
        final String userId = prefManager.getString("ID_USER");
        final String key = prefManager.getString("TOKEN_USER");

        apiClient.getClient().create(apiRest.class)
                .reelUploadUrl(userId, key, type, extension, "jpg")
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonObject> call,
                                           @NonNull retrofit2.Response<JsonObject> response) {
                        final JsonObject body = response.body();
                        if (body == null || !body.has("media")) {
                            fail(body != null && body.has("message")
                                    ? body.get("message").getAsString()
                                    : "The server would not hand out an upload slot.");
                            return;
                        }
                        new Thread(() -> putThenCreate(uri, body, userId, key, type, caption,
                                width, height, durationSeconds)).start();
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                        fail("Could not reach the server: " + t.getMessage());
                    }
                });
    }

    private void putThenCreate(Uri uri, JsonObject slots, String userId, String key,
                               String type, String caption,
                               int width, int height, int durationSeconds) {
        File temp = null;
        File poster = null;
        try {
            temp = copyToCache(uri);
            progress(5);

            final JsonObject media = slots.getAsJsonObject("media");
            final String objectKey = media.get("object_key").getAsString();

            if (!put(media, temp)) {
                return;
            }
            progress(75);

            // A video has nothing to show until it is playing, so the feed and the
            // grid need a still. The server signs a second slot for it in the same
            // round trip; a frame the phone cannot decode is not worth failing over,
            // so the reel is posted either way.
            String thumbKey = null;
            if (ReelApi.TYPE_VIDEO.equals(type) && slots.has("thumb")) {
                poster = posterFrame(temp);
                if (poster != null) {
                    final JsonObject thumb = slots.getAsJsonObject("thumb");
                    if (put(thumb, poster)) {
                        thumbKey = thumb.get("object_key").getAsString();
                    }
                }
            }
            progress(85);

            createReel(userId, key, objectKey, thumbKey, type, caption,
                    width, height, durationSeconds);
        } catch (IOException e) {
            Log.w(TAG, "Reel upload failed", e);
            fail("Upload failed: " + e.getMessage());
        } finally {
            remove(temp);
            remove(poster);
        }
    }

    /**
     * PUTs one file into a signed slot.
     *
     * <p>The signed headers are not advisory: sending a different Content-Type or
     * dropping the ACL makes Spaces reject the upload.
     */
    private boolean put(JsonObject slot, File file) throws IOException {
        final Request.Builder request = new Request.Builder().url(slot.get("url").getAsString());
        String contentType = "application/octet-stream";
        if (slot.has("headers")) {
            final JsonObject headers = slot.getAsJsonObject("headers");
            for (Iterator<Map.Entry<String, com.google.gson.JsonElement>> it =
                 headers.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<String, com.google.gson.JsonElement> entry = it.next();
                final String value = entry.getValue().getAsString();
                if ("content-type".equalsIgnoreCase(entry.getKey())) {
                    contentType = value;
                } else {
                    request.header(entry.getKey(), value);
                }
            }
        }
        request.put(RequestBody.create(file, MediaType.parse(contentType)));

        final Response response = client.newCall(request.build()).execute();
        final boolean ok = response.isSuccessful();
        final int code = response.code();
        response.close();
        if (!ok) {
            fail("Storage refused the upload (HTTP " + code + ").");
        }
        return ok;
    }

    /** A frame from the video as a JPEG, or null if it cannot be read. */
    private File posterFrame(File video) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(video.getAbsolutePath());
            // Half a second in: the first frame of a phone recording is often black.
            Bitmap frame = retriever.getFrameAtTime(500000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime();
            }
            if (frame == null) {
                return null;
            }
            final File out = new File(context.getCacheDir(),
                    "reel_poster_" + System.currentTimeMillis() + ".jpg");
            try (OutputStream os = new FileOutputStream(out)) {
                frame.compress(Bitmap.CompressFormat.JPEG, 80, os);
            }
            frame.recycle();
            return out;
        } catch (Exception e) {
            Log.w(TAG, "Could not take a poster frame", e);
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void remove(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "Could not remove " + file.getName());
        }
    }

    private void createReel(String userId, String key, String objectKey, String thumbKey,
                            String type, String caption,
                            int width, int height, int durationSeconds) {
        apiClient.getClient().create(apiRest.class)
                .reelCreate(userId, key, objectKey, thumbKey, type, caption,
                        width, height, durationSeconds)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonObject> call,
                                           @NonNull retrofit2.Response<JsonObject> response) {
                        final JsonObject body = response.body();
                        if (body != null && body.has("code") && body.get("code").getAsInt() == 200) {
                            progress(100);
                            main.post(() -> callbacks.onDone(body.has("message")
                                    ? body.get("message").getAsString() : "Your reel is up."));
                        } else {
                            fail(body != null && body.has("message")
                                    ? body.get("message").getAsString()
                                    : "The reel could not be saved.");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                        // The file is already in the bucket at this point, so the user
                        // should be told rather than left thinking it worked.
                        fail("Uploaded, but the reel could not be saved: " + t.getMessage());
                    }
                });
    }

    /** OkHttp needs a real file to stream; content:// uris are not files. */
    private File copyToCache(Uri uri) throws IOException {
        final File out = new File(context.getCacheDir(), "reel_upload_" + System.currentTimeMillis());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                throw new IOException("Could not open the selected file");
            }
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
        return out;
    }

    private void progress(final int percent) {
        main.post(() -> callbacks.onProgress(percent));
    }

    private void fail(final String message) {
        main.post(() -> callbacks.onError(message));
    }
}
