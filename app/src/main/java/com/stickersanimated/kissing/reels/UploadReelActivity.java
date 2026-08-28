package com.stickersanimated.kissing.reels;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.FrameLayout;
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
    private TextView post;
    private ProgressBar progressBar;
    private TextView status;
    private TextView badge;
    private TextView change;
    private TextView captionCount;
    private TextView mediaName;
    private View stepPick;
    private View stepDetails;
    private View stepOneMark;
    private View stepTwoMark;
    /** True once media has been picked, which is what step two is about. */
    private boolean onDetails;
    private boolean uploading;

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
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        preview = findViewById(R.id.image_view_reel_preview);
        pickPrompt = findViewById(R.id.linear_layout_pick);
        caption = findViewById(R.id.edit_text_reel_caption);
        post = findViewById(R.id.button_post_reel);
        progressBar = findViewById(R.id.progress_bar_upload_reel);
        status = findViewById(R.id.text_view_upload_status);
        badge = findViewById(R.id.text_view_media_badge);
        change = findViewById(R.id.text_view_change_media);
        captionCount = findViewById(R.id.text_view_caption_count);
        mediaName = findViewById(R.id.text_view_media_name);
        stepPick = findViewById(R.id.layout_step_pick);
        stepDetails = findViewById(R.id.layout_step_details);
        stepOneMark = findViewById(R.id.view_step_one);
        stepTwoMark = findViewById(R.id.view_step_two);

        pickPrompt.setOnClickListener(v -> pickMedia());
        findViewById(R.id.frame_layout_preview).setOnClickListener(v -> pickMedia());
        change.setOnClickListener(v -> pickMedia());
        post.setOnClickListener(v -> onPrimaryAction());
        showStep(false);

        captionCount.setText(getString(R.string.reel_caption_counter, 0));
        caption.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                captionCount.setText(getString(R.string.reel_caption_counter, s.length()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * Step one is the picker, step two everything about the reel. Only one is on screen,
     * and the button at the bottom says what it does on each.
     */
    private void showStep(boolean details) {
        onDetails = details;
        stepPick.setVisibility(details ? View.GONE : View.VISIBLE);
        stepDetails.setVisibility(details ? View.VISIBLE : View.GONE);
        stepOneMark.setAlpha(1f);
        stepTwoMark.setAlpha(details ? 1f : 0.25f);
        post.setText(details ? R.string.reel_post : R.string.reel_pick);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(details
                    ? R.string.reel_details_title : R.string.reel_new_title);
        }
    }

    private void onPrimaryAction() {
        if (onDetails) {
            startUpload();
        } else {
            pickMedia();
        }
    }

    @Override
    public void onBackPressed() {
        // Back off step two returns to the picker instead of throwing the caption away.
        if (onDetails && !uploading) {
            showStep(false);
            return;
        }
        super.onBackPressed();
    }

    /**
     * The file's own name, which is what somebody recognises their clip by. A picker hands
     * over a content uri whose last segment is a document id, so the name is asked for.
     */
    private String fileName() {
        if (picked == null) {
            return getString(R.string.reel_new_title);
        }
        try (android.database.Cursor cursor = getContentResolver().query(picked,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getColumnCount() > 0) {
                final String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // A provider that will not answer leaves the fallback below.
        }
        final String path = picked.getLastPathSegment();
        return path == null || path.isEmpty() ? getString(R.string.reel_new_title) : path;
    }

    private String describeMedia() {
        final StringBuilder text = new StringBuilder(
                ReelApi.TYPE_VIDEO.equals(type) ? "Video" : "Photo");
        text.append("  ·  ").append(extension.toUpperCase(Locale.US));
        if (duration > 0) {
            text.append("  ·  ").append(String.format(Locale.US, "%d:%02d",
                    duration / 60, duration % 60));
        }
        if (width > 0 && height > 0) {
            text.append("  ·  ").append(String.format(Locale.US, "%d x %d", width, height));
        }
        return text.toString();
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

        badge.setVisibility(View.VISIBLE);
        change.setVisibility(View.VISIBLE);
        Glide.with(this).load(picked).into(preview);

        badge.setText(ReelApi.TYPE_VIDEO.equals(type)
                ? getString(R.string.reel_badge_video) : getString(R.string.reel_badge_photo));
        mediaName.setText(fileName());
        status.setText(describeMedia());
        showStep(true);
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
        uploading = true;
        post.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        status.setText(getString(R.string.reel_uploading, 0));

        final String text = TextUtils.isEmpty(caption.getText()) ? "" : caption.getText().toString();

        new ReelUploader(this, new ReelUploader.Callbacks() {
            @Override
            public void onProgress(int percent) {
                progressBar.setProgress(percent);
                status.setText(getString(R.string.reel_uploading, percent));
            }

            @Override
            public void onDone(String message) {
                Toasty.success(UploadReelActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                uploading = false;
                post.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                status.setText(message);
                Toasty.error(UploadReelActivity.this, message, Toast.LENGTH_LONG).show();
            }
        }).upload(picked, type, extension, text, width, height, duration);
    }
}
