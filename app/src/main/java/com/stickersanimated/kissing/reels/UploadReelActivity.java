package com.stickersanimated.kissing.reels;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.entity.ReelApi;
import com.stickersanimated.kissing.ui.LoginActivity;

import java.util.Locale;

import es.dmoral.toasty.Toasty;

/** Pick a video or photo, caption it, and post it as a reel. */
public class UploadReelActivity extends AppCompatActivity {

    private static final int PICK_MEDIA = 4211;

    private ImageView preview;
    private LinearLayout pickPrompt;
    private EditText caption;
    private Button post;
    private ProgressBar progressBar;
    private TextView status;

    private Uri picked;
    private String type = ReelApi.TYPE_VIDEO;
    private String extension = "mp4";
    private int width;
    private int height;
    private int duration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_reel);

        final PrefManager prefManager = new PrefManager(getApplicationContext());
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        final Toolbar toolbar = findViewById(R.id.toolbar_upload_reel);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.reels);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        preview = findViewById(R.id.image_view_reel_preview);
        pickPrompt = findViewById(R.id.linear_layout_pick);
        caption = findViewById(R.id.edit_text_reel_caption);
        post = findViewById(R.id.button_post_reel);
        progressBar = findViewById(R.id.progress_bar_upload_reel);
        status = findViewById(R.id.text_view_upload_status);

        preview.setOnClickListener(v -> pickMedia());
        pickPrompt.setOnClickListener(v -> pickMedia());
        post.setOnClickListener(v -> startUpload());
    }

    private void pickMedia() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.reel_pick)), PICK_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MEDIA || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        picked = data.getData();
        if (picked == null) {
            return;
        }

        final String mime = getContentResolver().getType(picked);
        type = mime != null && mime.startsWith("image/") ? "photo" : ReelApi.TYPE_VIDEO;
        extension = resolveExtension(mime);
        readDimensions();

        pickPrompt.setVisibility(View.GONE);
        Glide.with(this).load(picked).into(preview);
        status.setText(String.format(Locale.US, "%s · %dx%d%s",
                type, width, height, duration > 0 ? " · " + duration + "s" : ""));
    }

    /**
     * The server only signs a known set of extensions, so map the mime type onto one
     * of those rather than trusting whatever the file happens to be called.
     */
    private String resolveExtension(String mime) {
        if (mime == null) {
            return ReelApi.TYPE_VIDEO.equals(type) ? "mp4" : "jpg";
        }
        switch (mime) {
            case "image/png":
                return "png";
            case "image/webp":
                return "webp";
            case "image/jpeg":
                return "jpg";
            case "video/webm":
                return "webm";
            case "video/quicktime":
                return "mov";
            default:
                return ReelApi.TYPE_VIDEO.equals(type) ? "mp4" : "jpg";
        }
    }

    private void readDimensions() {
        width = 0;
        height = 0;
        duration = 0;
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, picked);
            width = parse(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            height = parse(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            final int millis = parse(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            duration = millis / 1000;
        } catch (Exception e) {
            // Photos have no video metadata; the server treats zeros as unknown.
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int parse(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void startUpload() {
        if (picked == null) {
            Toasty.warning(this, getString(R.string.reel_pick), Toast.LENGTH_SHORT).show();
            return;
        }
        post.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        status.setText("Uploading…");

        final String text = TextUtils.isEmpty(caption.getText()) ? "" : caption.getText().toString();

        new ReelUploader(this, new ReelUploader.Callbacks() {
            @Override
            public void onProgress(int percent) {
                progressBar.setProgress(percent);
            }

            @Override
            public void onDone(String message) {
                Toasty.success(UploadReelActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                post.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                status.setText(message);
                Toasty.error(UploadReelActivity.this, message, Toast.LENGTH_LONG).show();
            }
        }).upload(picked, type, extension, text, width, height, duration);
    }
}
