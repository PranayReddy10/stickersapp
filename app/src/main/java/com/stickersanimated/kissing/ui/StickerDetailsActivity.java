// REPLACE YOUR ENTIRE StickerDetailsActivity.java WITH THIS SINGLE, COMPLETE FILE

package com.stickersanimated.kissing.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatRatingBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.siyamed.shapeimageview.CircularImageView;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import com.stickersanimated.kissing.BuildConfig;
import com.stickersanimated.kissing.MainActivity;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.Sticker;
import com.stickersanimated.kissing.StickerPack;
import com.stickersanimated.kissing.adapter.StickerDetailsAdapter;
import com.stickersanimated.kissing.ads.BannerAdManager;
import com.stickersanimated.kissing.ads.NativeAdManager;
import com.stickersanimated.kissing.ads.RewardedAdManager;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.config.Config;
import com.stickersanimated.kissing.entity.ApiResponse;
import com.stickersanimated.kissing.entity.PackApi;
import com.stickersanimated.kissing.services.BillingSubs;
import com.stickersanimated.kissing.services.CallBackBilling;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import static com.stickersanimated.kissing.MainActivity.EXTRA_STICKER_PACK_AUTHORITY;
import static com.stickersanimated.kissing.MainActivity.EXTRA_STICKER_PACK_ID;
import static com.stickersanimated.kissing.MainActivity.EXTRA_STICKER_PACK_NAME;

public class StickerDetailsActivity extends AppCompatActivity {

    // Ad properties - every format goes through a waterfall so a network that fails to
    // fill simply hands over to the next configured one.
    private BannerAdManager bannerAdManager;
    private NativeAdManager detailsNativeAdManager;
    private RewardedAdManager rewardedAdManager;
    private boolean autoDisplay = false;
    private TextView text_view_watch_ads;

    // Sticker Pack properties
    private static final int ADD_PACK = 200;
    private static final String TAG = "STICKER_DEBUG";
    private StickerPack stickerPack;
    private PackApi packApi;
    private BillingSubs billingSubs;
    private Dialog dialog;

    private  Boolean DialogOpened = false;
    private  Boolean fromLoad = false;

    private String sanitizeImageUrl(String raw) {

        if (raw == null) return "";

        raw = raw.trim();

        // ✅ Generic double-URL fix (NO hardcoding)
        if (raw.startsWith("http")) {
            int secondHttp = raw.indexOf("http", 4);
            if (secondHttp > 0) {
                raw = raw.substring(secondHttp);
            }
        }

        // Full URL → use as-is
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }

        // Relative path → prepend base domain
        return Config.API_URL.replace("/api/", "/") + raw;
    }



    // Views
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private ImageView pack_try_image;
    private TextView item_pack_name;
    private TextView item_pack_publisher;
    private TextView text_view_create_pack;
    private TextView text_view_downloads_pack;
    private TextView text_view_size_pack;
    private LinearLayout linear_layout_add_to_whatsapp;
    private LinearLayout linear_layout_progress;
    private CircularImageView circle_image_view_user_image;
    private ImageView image_view_trusted_user;
    private TextView text_view_user_name;
    private AppCompatRatingBar rating_bar_guide_main_pack_activity;
    private AppCompatRatingBar rating_bar_guide_value_pack_activity;
    private Button button_follow_user;
    private RelativeLayout linear_layout_pack_screen_shot;
    private LinearLayout linear_layout_share;
    private ImageView image_view_fav;
    private ProgressBar progress_bar_pack;
    private TextView text_view_rate_main_pack_activity;
    private ProgressBar progress_bar_rate_1_pack_activity;
    private ProgressBar progress_bar_rate_2_pack_activity;
    private ProgressBar progress_bar_rate_3_pack_activity;
    private ProgressBar progress_bar_rate_4_pack_activity;
    private ProgressBar progress_bar_rate_5_pack_activity;
    private TextView text_view_rate_1_pack_activity;
    private TextView text_view_rate_2_pack_activity;
    private TextView text_view_rate_3_pack_activity;
    private TextView text_view_rate_4_pack_activity;
    private TextView text_view_rate_5_pack_activity;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_details);

        stickerPack = getIntent().getParcelableExtra(MainActivity.EXTRA_STICKERPACK);
        if (stickerPack == null || stickerPack.getStickers() == null || stickerPack.getStickers().isEmpty()) {
            Toasty.error(this, "Sticker pack is invalid.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fromLoad = getIntent().getBooleanExtra("from", false);
        initViews();
        initData();
        initListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        pack_try_image = findViewById(R.id.pack_try_image);
        item_pack_name = findViewById(R.id.item_pack_name);
        item_pack_publisher = findViewById(R.id.item_pack_publisher);
        text_view_create_pack = findViewById(R.id.text_view_create_pack);
        text_view_downloads_pack = findViewById(R.id.text_view_downloads_pack);
        text_view_size_pack = findViewById(R.id.text_view_size_pack);
        linear_layout_add_to_whatsapp = findViewById(R.id.linear_layout_add_to_whatsapp);
        linear_layout_progress = findViewById(R.id.linear_layout_progress);
        circle_image_view_user_image = findViewById(R.id.circle_image_view_user_image);
        image_view_trusted_user = findViewById(R.id.image_view_trusted_user);
        text_view_user_name = findViewById(R.id.text_view_user_name);
        rating_bar_guide_main_pack_activity = findViewById(R.id.rating_bar_guide_main_pack_activity);
        rating_bar_guide_value_pack_activity = findViewById(R.id.rating_bar_guide_value_pack_activity);
        button_follow_user = findViewById(R.id.button_follow_user);
        linear_layout_pack_screen_shot = findViewById(R.id.linear_layout_pack_screen_shot);
        linear_layout_share = findViewById(R.id.linear_layout_share);
        image_view_fav = findViewById(R.id.image_view_fav);
        progress_bar_pack = findViewById(R.id.progress_bar_pack);
        text_view_rate_main_pack_activity = findViewById(R.id.text_view_rate_main_pack_activity);
        progress_bar_rate_1_pack_activity = findViewById(R.id.progress_bar_rate_1_pack_activity);
        progress_bar_rate_2_pack_activity = findViewById(R.id.progress_bar_rate_2_pack_activity);
        progress_bar_rate_3_pack_activity = findViewById(R.id.progress_bar_rate_3_pack_activity);
        progress_bar_rate_4_pack_activity = findViewById(R.id.progress_bar_rate_4_pack_activity);
        progress_bar_rate_5_pack_activity = findViewById(R.id.progress_bar_rate_5_pack_activity);
        text_view_rate_1_pack_activity = findViewById(R.id.text_view_rate_1_pack_activity);
        text_view_rate_2_pack_activity = findViewById(R.id.text_view_rate_2_pack_activity);
        text_view_rate_3_pack_activity = findViewById(R.id.text_view_rate_3_pack_activity);
        text_view_rate_4_pack_activity = findViewById(R.id.text_view_rate_4_pack_activity);
        text_view_rate_5_pack_activity = findViewById(R.id.text_view_rate_5_pack_activity);
    }

    private void initData() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(stickerPack.name);
        }

        Picasso.get().load(sanitizeImageUrl(stickerPack.trayImageUrl)).placeholder(R.drawable.sticker_error).error(R.drawable.sticker_error).into(pack_try_image);
        item_pack_name.setText(stickerPack.name);
        item_pack_publisher.setText(stickerPack.publisher);
        text_view_create_pack.setText(stickerPack.created);
        text_view_downloads_pack.setText(stickerPack.downloads);
        text_view_size_pack.setText(stickerPack.size);

        ArrayList<String> stickerPreviews = new ArrayList<>();
        for (Sticker s : stickerPack.getStickers()) {
            stickerPreviews.add(sanitizeImageUrl(s.imageFileUrlThum));
        }
        StickerDetailsAdapter adapter = new StickerDetailsAdapter(stickerPreviews, this);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);

        showAdsBanner();
        setUser();
        getRate();
        getUser();
        packApi = new PackApi(stickerPack);
        checkFavorite();
        initBuy();
        initAds();

        initRewardedAds();
    }

    private void initListeners() {
        linear_layout_add_to_whatsapp.setOnClickListener(view -> addPack(this::startStickerPackDownload));

        findViewById(R.id.image_view_menu_item).setOnClickListener(v->{
            PopupMenu popup = new PopupMenu(this, v);
            popup.setOnMenuItemClickListener(item -> {
                final PrefManager prefManager = new PrefManager(getApplicationContext());

                int itemId = item.getItemId();

                if (itemId == R.id.report_user) {
                    Intent intent_user = new Intent(StickerDetailsActivity.this, SupportActivity.class);
                    intent_user.putExtra("message", "Hi Admin, Please check this user " + stickerPack.username + " i think should be removed user id : " + stickerPack.userid);
                    startActivity(intent_user);
                } else if (itemId == R.id.block_user) {
                    prefManager.setString("user_reported_" + stickerPack.userid, "TRUE");
                    Toasty.warning(getApplicationContext(), "User : " + stickerPack.username + " has been blocked !").show();
                }

                return  false;
            });
            popup.inflate(R.menu.menu_user);
            popup.show();
        });

        image_view_fav.setOnClickListener(v -> addFavotite());
        linear_layout_share.setOnClickListener(v -> share());
        button_follow_user.setOnClickListener(v -> follow());
        rating_bar_guide_main_pack_activity.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (fromUser) addRate(rating);
        });
        text_view_user_name.setOnClickListener(this::openUserActivity);
        circle_image_view_user_image.setOnClickListener(this::openUserActivity);

        LinearLayout linear_layout_add_to_telegram = findViewById(R.id.linear_layout_add_to_telegram);
        linear_layout_add_to_telegram.setOnClickListener(v -> addPack(this::AddToTelegram));

        LinearLayout linear_layout_add_to_signal = findViewById(R.id.linear_layout_add_to_signal);
        linear_layout_add_to_signal.setOnClickListener(v -> addPack(this::AddToSignal));
    }

    /**
     * Runs {@code action} - adding the pack to WhatsApp, Telegram or Signal.
     *
     * <p>A premium pack goes through the unlock dialog, where the user either watches a
     * rewarded video or subscribes. That rewarded video is served by the whole network
     * waterfall, so a single network failing to fill no longer blocks the unlock. A free
     * pack is added straight away.
     */
    private void addPack(Runnable action) {
        if ("true".equals(stickerPack.premium) && !checkSUBSCRIBED()) {
            showDialog();
            return;
        }
        action.run();
    }

    private void startStickerPackDownload() {
        Log.d(TAG, "Starting download for pack: " + stickerPack.identifier);
        linear_layout_add_to_whatsapp.setVisibility(View.GONE);
        linear_layout_progress.setVisibility(View.VISIBLE);
        progress_bar_pack.setProgress(0);
        new DownloadAndInstallStickerPack().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @SuppressLint("StaticFieldLeak")
    private class DownloadAndInstallStickerPack extends AsyncTask<Void, String, StickerPack> {
        private final int TRAY_ICON_MAX_KB = 50;
        private final int STICKER_MAX_KB = 100;

        @Override
        protected StickerPack doInBackground(Void... voids) {
            StickerPack downloadedPack = new StickerPack(
                    stickerPack.identifier,
                    stickerPack.name,
                    stickerPack.publisher,
                    "tray.png",
                    stickerPack.publisherEmail,
                    stickerPack.publisherWebsite,
                    stickerPack.privacyPolicyWebsite,
                    stickerPack.licenseAgreementWebsite,
                    stickerPack.animatedStickerPack
            );

            ArrayList<Sticker> validStickers = new ArrayList<>();

            try {
                publishProgress("Downloading tray icon...");
                URL urlTry = new URL(sanitizeImageUrl(stickerPack.trayImageUrl));
                Bitmap bitmapTry = BitmapFactory.decodeStream(urlTry.openStream());
                if (bitmapTry == null) {
                    Log.e(TAG, "Tray icon download failed, bitmap is null.");
                    return null;
                }
                Bitmap scaledTray = Bitmap.createScaledBitmap(bitmapTry, 96, 96, true);
                saveFile(scaledTray, downloadedPack.trayImageFile, true, downloadedPack.identifier);
            } catch (Exception e) {
                Log.e(TAG, "Error downloading or saving tray icon", e);
                return null;
            }

            for (int i = 0; i < stickerPack.getStickers().size(); i++) {
                Sticker initialSticker = stickerPack.getStickers().get(i);
                String simpleFileName = i + ".webp";
                publishProgress("Downloading sticker " + (i + 1) + "/" + stickerPack.getStickers().size());

                try {
                    URL urlSticker = new URL(sanitizeImageUrl(initialSticker.imageFileUrl));
                    URLConnection connection = urlSticker.openConnection();
                    connection.connect();
                    try (InputStream inputSticker = new BufferedInputStream(urlSticker.openStream(), 8192)) {
                        saveFile(inputSticker, simpleFileName, false, downloadedPack.identifier);

                        // **FIX #1: Using the CORRECT Sticker constructor**
                        // Based on your Sticker.java file.
                        Sticker downloadedSticker = new Sticker(
                                initialSticker.imageFileUrlThum,
                                initialSticker.imageFileUrl,
                                simpleFileName,
                                initialSticker.emojis
                        );
                        validStickers.add(downloadedSticker);
                    }
                  /*  if (connection.getContentLength() / 1024 <= STICKER_MAX_KB) {

                    } else {
                        Log.w(TAG, "Skipping sticker (too large): " + initialSticker.imageFileUrl);
                    }*/
                } catch (Exception e) {
                    Log.e(TAG, "Failed to download sticker: " + initialSticker.imageFileUrl, e);
                }
            }

            if (validStickers.size() < 3) {
                Log.e(TAG, "Download failed, not enough valid stickers. Found " + validStickers.size() + ", need at least 3.");
                return null;
            }

            downloadedPack.setStickers(validStickers);
            return downloadedPack;
        }

        // In StickerDetailsActivity.java -> DownloadAndInstallStickerPack

        @Override
        protected void onPostExecute(StickerPack resultPack) {
            linear_layout_progress.setVisibility(View.GONE);
            linear_layout_add_to_whatsapp.setVisibility(View.VISIBLE);

            if (resultPack != null) {
                Log.d(TAG, "Download complete. " + resultPack.getStickers().size() + " stickers ready.");

                Log.d(TAG, "Correcting publisher to: " + resultPack.publisher);
                Log.d(TAG, "Tray file name to be saved: " + resultPack.trayImageFile);

                ArrayList<StickerPack> allStickerPacks = Hawk.get("whatsapp_sticker_packs", new ArrayList<>());

                Iterator<StickerPack> iterator = allStickerPacks.iterator();
                while (iterator.hasNext()) {
                    StickerPack p = iterator.next();
                    if (p.identifier.equals(resultPack.identifier)) {
                        iterator.remove();
                        break;
                    }
                }

                allStickerPacks.add(resultPack);
                Hawk.put("whatsapp_sticker_packs", allStickerPacks);
                Log.d(TAG, "Clean pack saved to Hawk. Triggering 'Add to WhatsApp'.");

                addDownload();
                addPackToWhatsApp(resultPack.identifier, resultPack.name);
            } else {
                Toasty.error(StickerDetailsActivity.this, "Download failed. Please check connection and try again.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "onPostExecute: resultPack was null. Download failed.");
            }
        }
    }

    // REPLACE the saveFile method in StickerDetailsActivity.java with this version

    private void saveFile(Object data, String fileName, boolean isTray, String packIdentifier) throws IOException {
        // **DEFINITIVE PATH FIX**: Use getExternalFilesDir(null) to get the root /files/ directory
        File rootFilesDir = Objects.requireNonNull(getExternalFilesDir(null));

        // Create our own subdirectory, ensuring the name is exact.
        File contentDir = new File(rootFilesDir, "stickers_asset");

        File packDir;
        if (isTray) {
            packDir = new File(contentDir, packIdentifier + File.separator + "try");
        } else {
            packDir = new File(contentDir, packIdentifier);
        }

        if (!packDir.exists()) {
            packDir.mkdirs();
        }

        File file = new File(packDir, fileName);
        if (file.exists()) {
            file.delete();
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            if (data instanceof Bitmap) {
                ((Bitmap) data).compress(Bitmap.CompressFormat.PNG, 90, out);
            } else if (data instanceof InputStream) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = ((InputStream) data).read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
        Log.d("STICKER_DEBUG", "WRITER saved file to: " + file.getAbsolutePath());
    }


    private void addPackToWhatsApp(String identifier, String packName) {
        Intent intent = new Intent();
        intent.setAction("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra(EXTRA_STICKER_PACK_ID, identifier);
        intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        intent.putExtra(EXTRA_STICKER_PACK_NAME, packName);
        try {
            startActivityForResult(intent, ADD_PACK);
        } catch (ActivityNotFoundException e) {
            Toasty.info(this, "WhatsApp is not installed on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void resetWatchAdsLabel() {
        if (text_view_watch_ads != null) {
            text_view_watch_ads.setText("WATCH AD TO DOWNLOAD");
        }
    }

    /** Warms up a rewarded video across every configured network. */
    private void initRewardedAds() {
        if (checkSUBSCRIBED()) {
            return;
        }
        rewardedAdManager = new RewardedAdManager(this, new RewardedAdManager.Listener() {
            @Override
            public void onAdReady() {
                if (!autoDisplay) {
                    return;
                }
                autoDisplay = false;
                resetWatchAdsLabel();
                rewardedAdManager.show();
            }

            @Override
            public void onAdUnavailable() {
                if (!autoDisplay) {
                    return;
                }
                autoDisplay = false;
                resetWatchAdsLabel();
                Toasty.warning(StickerDetailsActivity.this,
                        "No ad available right now, please try again later.").show();
            }

            @Override
            public void onUserRewarded() {
                stickerPack.premium = "false";
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                Toasty.success(getApplicationContext(),
                        "Now you can use this premium stickers for free").show();
            }

            @Override
            public void onAdClosed() {
                if (dialog != null && dialog.isShowing() && "false".equals(stickerPack.premium)) {
                    dialog.dismiss();
                }
            }
        });
        rewardedAdManager.load();
    }

    public void showDialog(){
        this.dialog = new Dialog(this,R.style.Theme_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        Window window = dialog.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        wlp.gravity = Gravity.BOTTOM;
        wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(wlp);
        final   PrefManager prf= new PrefManager(getApplicationContext());
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dialog_subscribe);

        text_view_watch_ads = (TextView) dialog.findViewById(R.id.text_view_watch_ads);
        text_view_watch_ads.setText("WATCH AD TO DOWNLOAD");

        RelativeLayout relative_layout_watch_ads = (RelativeLayout) dialog.findViewById(R.id.relative_layout_watch_ads);
        relative_layout_watch_ads.setVisibility(View.VISIBLE);
        relative_layout_watch_ads.setOnClickListener(view -> {
            if (rewardedAdManager == null) {
                initRewardedAds();
            }
            if (rewardedAdManager == null) {
                return;
            }
            if (rewardedAdManager.show()) {
                return;
            }
            // Nothing warm yet: keep loading down the waterfall and show the first fill.
            autoDisplay = true;
            text_view_watch_ads.setText("LOADING AD...");
            rewardedAdManager.load();
        });

        TextView text_view_go_pro=(TextView) dialog.findViewById(R.id.text_view_go_pro);
        text_view_go_pro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                subscribe();
            }
        });
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {

            @Override
            public boolean onKey(DialogInterface arg0, int keyCode,
                                 KeyEvent event) {
                // TODO Auto-generated method stub
                if (keyCode == KeyEvent.KEYCODE_BACK) {

                    dialog.dismiss();
                }
                return true;
            }
        });
        dialog.show();
        DialogOpened=true;

    }


    private void openUserActivity(View view) {
        Intent intent  =  new Intent(getApplicationContext(), UserActivity.class);
        intent.putExtra("id",Integer.parseInt(stickerPack.userid));
        intent.putExtra("image",stickerPack.userimage);
        intent.putExtra("name",stickerPack.username);

        if (stickerPack.trused.equals("true"))
            intent.putExtra("trusted",true);
        else
            intent.putExtra("trusted",false);

        startActivity(intent,
                ActivityOptionsCompat.makeScaleUpAnimation(view, (int) view.getX(), (int) view.getY(), view.getWidth(),
                        view.getHeight()).toBundle());
    }

    private void addFavotite() {
        List<PackApi> favorites_list = Hawk.get("favorite", new ArrayList<>());
        int fav_position = -1;
        for (int i = 0; i < favorites_list.size(); i++) {
            if (favorites_list.get(i).getIdentifier().equals(packApi.getIdentifier())) {
                fav_position = i;
                break;
            }
        }
        if (fav_position == -1) {
            favorites_list.add(packApi);
            image_view_fav.setImageResource(R.drawable.ic_favorite_black);
        } else {
            favorites_list.remove(fav_position);
            image_view_fav.setImageResource(R.drawable.ic_favorite_border);
        }
        Hawk.put("favorite", favorites_list);
    }

    public void follow(){

        PrefManager prf= new PrefManager(getApplicationContext());
        if (prf.getString("LOGGED").toString().equals("TRUE")) {
            button_follow_user.setText(getResources().getString(R.string.loading));
            button_follow_user.setEnabled(false);
            String follower = prf.getString("ID_USER");
            String key = prf.getString("TOKEN_USER");
            Retrofit retrofit = apiClient.getClient();
            apiRest service = retrofit.create(apiRest.class);
            Call<ApiResponse> call = service.follow(Integer.parseInt(stickerPack.userid), Integer.parseInt(follower), key);
            call.enqueue(new Callback<ApiResponse>() {
                @Override
                public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                    if (response.isSuccessful()) {
                        if (response.body().getCode().equals(200)){
                            button_follow_user.setText("UnFollow");
                        }else if (response.body().getCode().equals(202)) {
                            button_follow_user.setText("Follow");

                        }
                    }
                    button_follow_user.setEnabled(true);

                }

                @Override
                public void onFailure(Call<ApiResponse> call, Throwable t) {
                    button_follow_user.setEnabled(true);
                }
            });
        }else{
            Intent intent = new Intent(StickerDetailsActivity.this, LoginActivity.class);
            startActivity(intent);
        }
    }
    private void getUser() {
        PrefManager prf = new PrefManager(getApplicationContext());
        if (prf.getString("LOGGED").toString().equals("TRUE")) {
            button_follow_user.setEnabled(false);
            Integer follower = -1;
            follower = Integer.parseInt(prf.getString("ID_USER"));
            if (follower != Integer.parseInt(stickerPack.userid)) {
                button_follow_user.setVisibility(View.VISIBLE);
            }
            Retrofit retrofit = apiClient.getClient();
            apiRest service = retrofit.create(apiRest.class);
            Call<ApiResponse> call = service.getUser(Integer.parseInt(stickerPack.userid), follower);
            call.enqueue(new Callback<ApiResponse>() {
                @Override
                public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                    if (response.isSuccessful()) {

                        for (int i = 0; i < response.body().getValues().size(); i++) {

                            if (response.body().getValues().get(i).getName().equals("follow")) {
                                if (response.body().getValues().get(i).getValue().equals("true"))
                                    button_follow_user.setText("UnFollow");
                                else
                                    button_follow_user.setText("Follow");
                            }
                        }

                    } else {


                    }
                    button_follow_user.setEnabled(true);
                }

                @Override
                public void onFailure(Call<ApiResponse> call, Throwable t) {
                    button_follow_user.setEnabled(true);
                }
            });
        }
    }


    public void addRate(final float value) {
        PrefManager prf = new PrefManager(getApplicationContext());
        if ("TRUE".equals(prf.getString("LOGGED"))) {
            Retrofit retrofit = apiClient.getClient();
            apiRest service = retrofit.create(apiRest.class);
            Call<ApiResponse> call = service.addRate(prf.getString("ID_USER"), Integer.parseInt(stickerPack.identifier), value);
            call.enqueue(new Callback<ApiResponse>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Toasty.success(StickerDetailsActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                        getRate();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                }
            });
        } else {
            Intent intent = new Intent(StickerDetailsActivity.this, LoginActivity.class);
            startActivity(intent);
        }
    }

    // YOUR ORIGINAL getRate() METHOD, RESTORED
    public void getRate() {
        PrefManager prf = new PrefManager(getApplicationContext());
        String user_id = "0";
        if (prf.getString("LOGGED").toString().equals("TRUE")) {
            user_id = prf.getString("ID_USER").toString();
        }
        Retrofit retrofit = apiClient.getClient();
        apiRest service = retrofit.create(apiRest.class);
        Call<ApiResponse> call = service.getRate(user_id, Integer.parseInt(stickerPack.identifier));
        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                if (response.isSuccessful()) {
                    if (response.body().getCode() == 200) {
                        rating_bar_guide_main_pack_activity.setRating(Integer.parseInt(response.body().getMessage()));
                    } else if (response.body().getCode() == 202) {
                        rating_bar_guide_main_pack_activity.setRating(0);
                    } else {
                        rating_bar_guide_main_pack_activity.setRating(0);
                    }
                    if (response.body().getCode() != 500) {
                        Integer rate_1 = 0;
                        Integer rate_2 = 0;
                        Integer rate_3 = 0;
                        Integer rate_4 = 0;
                        Integer rate_5 = 0;
                        float rate = 0;
                        for (int i = 0; i < response.body().getValues().size(); i++) {

                            if (response.body().getValues().get(i).getName().equals("1")) {
                                rate_1 = Integer.parseInt(response.body().getValues().get(i).getValue());
                            }
                            if (response.body().getValues().get(i).getName().equals("2")) {
                                rate_2 = Integer.parseInt(response.body().getValues().get(i).getValue());
                            }
                            if (response.body().getValues().get(i).getName().equals("3")) {
                                rate_3 = Integer.parseInt(response.body().getValues().get(i).getValue());
                            }
                            if (response.body().getValues().get(i).getName().equals("4")) {
                                rate_4 = Integer.parseInt(response.body().getValues().get(i).getValue());
                            }
                            if (response.body().getValues().get(i).getName().equals("5")) {
                                rate_5 = Integer.parseInt(response.body().getValues().get(i).getValue());
                            }
                            if (response.body().getValues().get(i).getName().equals("rate")) {
                                rate = Float.parseFloat(response.body().getValues().get(i).getValue());
                            }
                        }
                        rating_bar_guide_value_pack_activity.setRating(rate);
                        String formattedString = rate + "";


                        text_view_rate_main_pack_activity.setText(formattedString);
                        text_view_rate_1_pack_activity.setText(rate_1 + "");
                        text_view_rate_2_pack_activity.setText(rate_2 + "");
                        text_view_rate_3_pack_activity.setText(rate_3 + "");
                        text_view_rate_4_pack_activity.setText(rate_4 + "");
                        text_view_rate_5_pack_activity.setText(rate_5 + "");
                        Integer total = rate_1 + rate_2 + rate_3 + rate_4 + rate_5;
                        if (total == 0) {
                            total = 1;
                        }
                        progress_bar_rate_1_pack_activity.setProgress((int) ((rate_1 * 100) / total));
                        progress_bar_rate_2_pack_activity.setProgress((int) ((rate_2 * 100) / total));
                        progress_bar_rate_3_pack_activity.setProgress((int) ((rate_3 * 100) / total));
                        progress_bar_rate_4_pack_activity.setProgress((int) ((rate_4 * 100) / total));
                        progress_bar_rate_5_pack_activity.setProgress((int) ((rate_5 * 100) / total));
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
            }
        });
    }


    public void addDownload() {
        Retrofit retrofit = apiClient.getClient();
        apiRest service = retrofit.create(apiRest.class);
        Call<Integer> call = service.addDownload(Integer.parseInt(stickerPack.identifier));
        call.enqueue(new Callback<Integer>() {
            @Override
            public void onResponse(@NonNull Call<Integer> call, @NonNull Response<Integer> response) {
                if (response.isSuccessful() && response.body() != null) {
                    text_view_downloads_pack.setText(String.valueOf(response.body()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Integer> call, @NonNull Throwable t) {
            }
        });
    }

    public void setUser(){
        text_view_user_name.setText(stickerPack.username);
        Picasso.get().load(sanitizeImageUrl(stickerPack.userimage)).placeholder(getResources().getDrawable(R.drawable.profile)).placeholder(getResources().getDrawable(R.drawable.profile)).into(circle_image_view_user_image);
        if (stickerPack.trused.equals("true")){
            image_view_trusted_user.setVisibility(View.VISIBLE);
        }else{
            image_view_trusted_user.setVisibility(View.GONE);
        }
    }

    public void AddToTelegram() {
        if (!"true".equals(stickerPack.premium) || checkSUBSCRIBED()) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(stickerPack.telegramurl));
            startActivity(i);
        } else {
            showDialog();
        }
    }

    public void AddToSignal() {
        if (!"true".equals(stickerPack.premium) || checkSUBSCRIBED()) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(stickerPack.signalurl));
            startActivity(i);
        } else {
            showDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_PACK) {
            if (resultCode == Activity.RESULT_CANCELED) {
                if (data != null) {
                    final String validationError = data.getStringExtra("validation_error");
                    if (validationError != null) {
                        Log.e(TAG, "Validation failed:" + validationError);
                    }
                } else {
                    Log.e(TAG, "Activity cancelled.");
                }
            }
        }
    }

    public void share() {
        Bitmap bitmap = getBitmapFromView(linear_layout_pack_screen_shot);
        if (bitmap != null) {
            try {
                File cachePath = new File(getCacheDir(), "images");
                cachePath.mkdirs();
                File imageFile = new File(cachePath, "image.png");
                FileOutputStream stream = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();
                Uri contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", imageFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                String shareBody = stickerPack.name + "\n\n" + getResources().getString(R.string.download_pack_from) + "\n" + Config.API_URL.replace("api", "share") + stickerPack.identifier + ".html";
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                startActivity(Intent.createChooser(shareIntent, "Share via"));
            } catch (Exception e) {
                e.printStackTrace();
                Toasty.error(this, "Failed to share image.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toasty.error(this, "Could not create image for sharing.", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap getBitmapFromView(View view) {
        Bitmap returnedBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(returnedBitmap);
        Drawable bgDrawable = view.getBackground();
        if (bgDrawable != null) {
            bgDrawable.draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }
        view.draw(canvas);
        return returnedBitmap;
    }

    public boolean checkSUBSCRIBED() {
        PrefManager prefManager = new PrefManager(getApplicationContext());
        return "TRUE".equals(prefManager.getString("SUBSCRIBED"));
    }

    public void showAdsBanner() {
        if (checkSUBSCRIBED()) {
            return;
        }
        bannerAdManager = BannerAdManager.into(this, findViewById(R.id.linear_layout_ads));
        if (bannerAdManager != null) {
            bannerAdManager.load();
        }
    }

    public void initAds() {
        if (checkSUBSCRIBED()) {
            return;
        }
        // A single native slot on the pack page, between the author card and the rating
        // card. It runs the same waterfall as every other placement.
        detailsNativeAdManager = NativeAdManager.into(this,
                findViewById(R.id.frame_layout_details_native));
        if (detailsNativeAdManager != null) {
            detailsNativeAdManager.load();
        }
    }

    @Override
    protected void onDestroy() {
        if (bannerAdManager != null) {
            bannerAdManager.destroy();
        }
        if (detailsNativeAdManager != null) {
            detailsNativeAdManager.destroy();
        }
        if (rewardedAdManager != null) {
            rewardedAdManager.destroy();
        }
        super.onDestroy();
    }

    public void initBuy() {
        billingSubs = new BillingSubs(this, List.of(Config.SUBSCRIPTION_ID), new CallBackBilling() {
            @Override public void onPurchase() {
                new PrefManager(getApplicationContext()).setString("SUBSCRIBED", "TRUE");
                Toasty.success(StickerDetailsActivity.this, "You have successfully subscribed!", Toast.LENGTH_SHORT).show();
            }
            @Override public void onNotPurchase() { Toasty.warning(StickerDetailsActivity.this, "Operation has been cancelled.", Toast.LENGTH_SHORT).show(); }
            @Override public void onNotLogin() { Toasty.warning(StickerDetailsActivity.this, "Operation has been cancelled.", Toast.LENGTH_SHORT).show(); }
        });
    }

    public void subscribe() {
        billingSubs.purchase(Config.SUBSCRIPTION_ID);
    }

    private void checkFavorite() {
        List<PackApi> favorites_list = Hawk.get("favorite", new ArrayList<>());
        boolean exist = false;
        for (PackApi favPack : favorites_list) {
            if (favPack.getIdentifier().equals(packApi.getIdentifier())) {
                exist = true;
                break;
            }
        }
        image_view_fav.setImageResource(exist ? R.drawable.ic_favorite_black : R.drawable.ic_favorite_border);
    }

    // This is the continuation of the StickerDetailsActivity.java file

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_infos) {
            Intent intent = new Intent(getApplicationContext(), InfosActivity.class);
            intent.putExtra("name", packApi.getName());
            intent.putExtra("publisher", packApi.getPublisher());
            intent.putExtra("publisherEmail", packApi.getPublisherEmail());
            intent.putExtra("publisherWebsite", packApi.getPublisherWebsite());
            intent.putExtra("privacyPolicyWebsite", packApi.getPrivacyPolicyWebsite());
            intent.putExtra("licenseAgreementWebsite", packApi.getLicenseAgreementWebsite());
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (fromLoad) {
            startActivity(new Intent(getApplicationContext(), HomeActivity.class));
        } else {
            super.onBackPressed();
        }
        overridePendingTransition(R.anim.slide_enter, R.anim.slide_exit);
    }
}
