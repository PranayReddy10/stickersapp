package com.stickersanimated.kissing.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Assuming EditorActivity is where the editing happens.
// Import the contract used by the modern cropper.


public class CutOut {

    // These request codes are now deprecated but kept for compatibility if needed elsewhere.
    public static final short CUTOUT_ACTIVITY_REQUEST_CODE = 368;
    public static final short CUTOUT_ACTIVITY_RESULT_ERROR_CODE = 3680;

    static final String CUTOUT_EXTRA_SOURCE = "CUTOUT_EXTRA_SOURCE";
    static final String CUTOUT_EXTRA_RESULT = "CUTOUT_EXTRA_RESULT";
    static final String CUTOUT_EXTRA_BORDER_COLOR = "CUTOUT_EXTRA_BORDER_COLOR";
    static final String CUTOUT_EXTRA_CROP = "CUTOUT_EXTRA_CROP";
    static final String CUTOUT_EXTRA_INTRO = "CUTOUT_EXTRA_INTRO";

    public static ActivityBuilder activity() {
        return new ActivityBuilder();
    }

    /**
     * Builder used for creating CutOut Activity by user request.
     */
    public static final class ActivityBuilder {

        @Nullable
        private Uri source;
        private boolean bordered;
        private boolean crop = true;
        private boolean intro;
        private int borderColor = Color.WHITE;

        private ActivityBuilder() {}

        /**
         * Get {@link EditorActivity} intent to start the activity.
         */
        private Intent getIntent(@NonNull Context context) {
            Intent intent = new Intent();
            intent.setClass(context, EditorActivity.class);

            if (source != null) {
                // We pass the source Uri directly to the EditorActivity.
                intent.putExtra(CUTOUT_EXTRA_SOURCE, source);
            }

            if (bordered) {
                intent.putExtra(CUTOUT_EXTRA_BORDER_COLOR, borderColor);
            }

            // The EditorActivity will decide whether to launch the cropper.
            intent.putExtra(CUTOUT_EXTRA_CROP, crop);

            if (intro) {
                intent.putExtra(CUTOUT_EXTRA_INTRO, true);
            }
            return intent;
        }

        public ActivityBuilder src(Uri source) {
            this.source = source;
            return this;
        }

        public ActivityBuilder bordered() {
            this.bordered = true;
            return this;
        }

        public ActivityBuilder bordered(int borderColor) {
            this.borderColor = borderColor;
            return bordered();
        }

        public ActivityBuilder noCrop() {
            this.crop = false;
            return this;
        }

        public ActivityBuilder intro() {
            this.intro = true;
            return this;
        }

        // ======================= THE FIX =======================
        // The old 'start' method is replaced with this new one.
        // It now takes an ActivityResultLauncher as an argument.
        // This is the modern, correct way to start an activity for a result.
        /**
         * Start {@link EditorActivity} for a result.
         *
         * @param context Context to create the intent.
         * @param launcher The ActivityResultLauncher that will receive the final result.
         */
        public void start(@NonNull Context context, @NonNull ActivityResultLauncher<Intent> launcher) {
            launcher.launch(getIntent(context));
        }
        // =======================================================
    }

    /**
     * Reads the {@link Uri} from the result data.
     */
    public static Uri getUri(@Nullable Intent data) {
        return data != null ? data.getParcelableExtra(CUTOUT_EXTRA_RESULT) : null;
    }

    /**
     * Gets an Exception from the result data.
     */
    public static Exception getError(@Nullable Intent data) {
        return data != null ? (Exception) data.getSerializableExtra(CUTOUT_EXTRA_RESULT) : null;
    }
}
