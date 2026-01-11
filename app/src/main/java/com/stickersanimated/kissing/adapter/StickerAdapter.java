package com.stickersanimated.kissing.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityOptionsCompat;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.widget.PopupMenu;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinAdVideoPlaybackListener;
import com.applovin.sdk.AppLovinSdk;
import com.bumptech.glide.Glide;

import com.github.siyamed.shapeimageview.CircularImageView;
import com.github.vivchar.viewpagerindicator.ViewPagerIndicator;
import com.stickersanimated.kissing.config.Config;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VideoController;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.orhanobut.hawk.Hawk;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.StickerPack;
import com.stickersanimated.kissing.entity.CategoryApi;
import com.stickersanimated.kissing.ui.StickerDetailsActivity;
import com.stickersanimated.kissing.ui.SupportActivity;
import com.stickersanimated.kissing.ui.UserActivity;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ApiResponse;
import com.stickersanimated.kissing.entity.PackApi;
import com.stickersanimated.kissing.entity.SlideApi;
import com.stickersanimated.kissing.entity.UserApi;
import com.stickersanimated.kissing.ui.views.ClickableViewPager;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import static com.stickersanimated.kissing.MainActivity.EXTRA_STICKERPACK;


public class StickerAdapter extends  RecyclerView.Adapter<RecyclerView.ViewHolder>{


    private View selected_view = null;
    private int selected_position = -1;
    private Intent selected_intent = null;

    private  List<CategoryApi> categoryList = new ArrayList<>();
    public  Boolean favorite =  false;
    // lists
    List<StickerPack> StickerPack;
    private List<SlideApi> slideList= new ArrayList<>();
    private  List<UserApi> userList = new ArrayList<>();
    private Dialog dialog_progress;

    // objects
    private Activity activity;
    private SlideAdapter slide_adapter;
    private FollowAdapter followAdapter;

    private InterstitialAd admobInterstitialAd;
    private MaxInterstitialAd maxInterstitialAd;
    private AppLovinInterstitialAdDialog applovinInterstitialAd;
    private AppLovinAd applovinInterstitialAdBlock;

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


    private LinearLayoutManager linearLayoutManager;
    public StickerAdapter(Activity activity, ArrayList<StickerPack> StickerPack) {
        this.activity = activity;
        this.StickerPack = StickerPack;
    }
    public StickerAdapter(Activity activity, ArrayList<StickerPack> StickerPack,List<SlideApi> slideList) {
        this.activity = activity;
        this.StickerPack = StickerPack;
        this.slideList = slideList;
    }
    public StickerAdapter(Activity activity, ArrayList<StickerPack> StickerPack,List<SlideApi> slideList,List<CategoryApi> categoryList,Boolean b) {
        this.activity = activity;
        this.StickerPack = StickerPack;
        this.slideList = slideList;
        this.categoryList = categoryList;
    }
    public StickerAdapter(Activity activity, ArrayList<StickerPack> StickerPack,List<SlideApi> slideList,List<UserApi> userList){
        this.activity = activity;
        this.StickerPack = StickerPack;
        this.slideList = slideList;
        this.userList = userList;
    }
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder viewHolder = null;
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case 1: {
                View v1 = inflater.inflate(R.layout.item_pack, parent, false);
                viewHolder = new PackViewHolder(v1);
                break;
            }
            case 2: {
                View v2 = inflater.inflate(R.layout.item_slide, parent, false);
                viewHolder = new SlideHolder(v2);
                break;
            }
            case 3: {
                View v3 = inflater.inflate(R.layout.item_followings, parent, false);
                viewHolder = new FollowHolder(v3);
                break;
            }

            case 5: {
                View v5 = inflater.inflate(R.layout.item_categories, parent, false);
                viewHolder = new CategoriesHolder(v5 );
                break;
            }
            case 6: {
                View v6 = inflater.inflate(R.layout.item_admob_native_ads, parent, false);
                viewHolder = new AdmobNativeHolder(v6);
                break;
            }
            case 7: {
                View v7 = inflater.inflate(R.layout.item_max_native_ads, parent, false);
                viewHolder = new MaxNativeHolder(v7);
                break;
            }
        }
        return viewHolder;
    }
    @Override
    public int getItemViewType(int position) {
        return StickerPack.get(position).getViewType();
    }
    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder_parent, @SuppressLint("RecyclerView") final int position) {

        switch (StickerPack.get(position).getViewType()) {
            case 1: {

                final PackViewHolder viewHolder = (PackViewHolder) holder_parent;


                viewHolder.image_view_whatsapp.setVisibility((StickerPack.get(position).whatsapp.equals("false"))? View.GONE:View.VISIBLE);
                viewHolder.image_view_telegram.setVisibility((StickerPack.get(position).telegram.equals("false"))? View.GONE:View.VISIBLE);
                viewHolder.image_view_signal.setVisibility((StickerPack.get(position).signal.equals("false"))? View.GONE:View.VISIBLE);


                viewHolder.item_pack_name.setText(StickerPack.get(position).name);
                viewHolder.item_pack_publisher.setText(StickerPack.get(position).publisher);
                viewHolder.item_pack_downloads.setText(StickerPack.get(position).downloads);
                viewHolder.item_pack_size.setText(StickerPack.get(position).size);
                viewHolder.item_pack_created.setText(StickerPack.get(position).created);
                viewHolder.item_pack_username.setText(StickerPack.get(position).username);

                if (StickerPack.get(position).premium.equals("true")) {
                    viewHolder.item_pack_premium.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.item_pack_premium.setVisibility(View.GONE);
                }
                if (StickerPack.get(position).review.equals("true")) {
                    viewHolder.item_pack_review.setVisibility(View.VISIBLE);
                    viewHolder.item_pack_delete.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.item_pack_delete.setVisibility(View.GONE);
                }



                // helper (local method inside adapter OR just inline usage)
                String baseUrl = Config.API_URL.replace("/api/", "/");

                String userImg = StickerPack.get(position).userimage;
                String trayImg = StickerPack.get(position).trayImageUrl;
                String img1 = StickerPack.get(position).getStickers().get(0).imageFileUrlThum;
                String img2 = StickerPack.get(position).getStickers().get(1).imageFileUrlThum;
                String img3 = StickerPack.get(position).getStickers().get(2).imageFileUrlThum;

                Glide.with(activity.getApplicationContext())
                        .load(sanitizeImageUrl(StickerPack.get(position).userimage))
                        .into(viewHolder.pack_item_image_view_userimage);

                Glide.with(activity.getApplicationContext())
                        .load(sanitizeImageUrl(StickerPack.get(position).trayImageUrl))
                        .into(viewHolder.pack_try_image);

                Glide.with(activity.getApplicationContext())
                        .load(sanitizeImageUrl(
                                StickerPack.get(position).getStickers().get(0).imageFileUrlThum))
                        .into(viewHolder.imone);

                Glide.with(activity.getApplicationContext())
                        .load(sanitizeImageUrl(
                                StickerPack.get(position).getStickers().get(1).imageFileUrlThum))
                        .into(viewHolder.imtwo);

                Glide.with(activity.getApplicationContext())
                        .load(sanitizeImageUrl(
                                StickerPack.get(position).getStickers().get(2).imageFileUrlThum))
                        .into(viewHolder.imthree);

                if (StickerPack.get(position).getStickers().size() > 3) {
                    Glide.with(activity.getApplicationContext())
                            .load(sanitizeImageUrl(
                                    StickerPack.get(position).getStickers().get(3).imageFileUrlThum))
                            .into(viewHolder.imfour);
                } else {
                    viewHolder.imfour.setVisibility(View.INVISIBLE);
                }

                if (StickerPack.get(position).getStickers().size() > 4) {
                    Glide.with(activity.getApplicationContext())
                            .load(sanitizeImageUrl(
                                    StickerPack.get(position).getStickers().get(4).imageFileUrlThum))
                            .into(viewHolder.imfive);
                } else {
                    viewHolder.imfive.setVisibility(View.INVISIBLE);
                }

                Log.e("trayImageUrl", StickerPack.get(position).trayImageUrl);
                Log.e("trayImageUrl", StickerPack.get(position).trayImageFile);
                viewHolder.image_view_menu_item.setOnClickListener(v->{
                    PopupMenu popup = new PopupMenu(activity, v);
                    popup.setOnMenuItemClickListener(item -> {
                        Report(StickerPack.get(position),item);
                        return true;
                    });
                    popup.inflate(R.menu.report_menu);
                    popup.show();
                });
                viewHolder.cardView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent((activity), StickerDetailsActivity.class).putExtra(EXTRA_STICKERPACK, StickerPack.get(viewHolder.getAdapterPosition()));
                        selected_position = position;
                        selected_intent = intent;
                        selected_view = v;
                        Operation();
                    }
                });
                List<PackApi> favorites_list =Hawk.get("favorite");
                Boolean exist = false;
                if (favorites_list == null) {
                    favorites_list = new ArrayList<>();
                }

                for (int i = 0; i < favorites_list.size(); i++) {
                    if (favorites_list.get(i).getIdentifier().equals(StickerPack.get(position).packApi.getIdentifier())) {
                        exist = true;
                    }
                }
                if (exist){
                    viewHolder.image_view_item_pack_fav.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_favorite_black));
                }else{
                    viewHolder.image_view_item_pack_fav.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_favorite_border));
                }

                viewHolder.image_view_item_pack_fav.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        List<PackApi> favorites_list =Hawk.get("favorite");
                        Boolean exist = false;
                        if (favorites_list == null) {
                            favorites_list = new ArrayList<>();
                        }
                        int fav_position = -1;
                        for (int i = 0; i < favorites_list.size(); i++) {
                            if (favorites_list.get(i).getIdentifier().equals(StickerPack.get(position).packApi.getIdentifier())) {
                                exist = true;
                                fav_position = i;
                            }
                        }
                        if (exist == false) {
                            favorites_list.add(StickerPack.get(position).packApi);
                            Hawk.put("favorite",favorites_list);
                            viewHolder.image_view_item_pack_fav.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_favorite_black));

                        }else{
                            favorites_list.remove(fav_position);
                            Hawk.put("favorite",favorites_list);
                            viewHolder.image_view_item_pack_fav.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_favorite_border));
                            if (favorite) {
                                StickerPack.remove(position);
                                notifyItemRemoved(position);
                                notifyDataSetChanged();
                            }

                        }

                    }
                });
                viewHolder.image_view_delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog_progress= ProgressDialog.show(activity, null,activity.getResources().getString(R.string.operation_progress), true);
                        final PrefManager prf= new PrefManager(activity.getApplicationContext());
                        String user_id = prf.getString("ID_USER");
                        String user_key = prf.getString("TOKEN_USER");
                        Retrofit retrofit = apiClient.getClient();
                        apiRest service = retrofit.create(apiRest.class);
                        Call<ApiResponse> call = service.deletePack(Integer.parseInt(user_id),user_key,Integer.parseInt(StickerPack.get(position).identifier));
                        call.enqueue(new Callback<ApiResponse>() {
                            @Override
                            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                                if(response.isSuccessful()) {
                                    if (response.body().getCode() ==  200){
                                        Toasty.success(activity,response.body().getMessage(),Toast.LENGTH_LONG).show();
                                        Intent intent  =  new Intent(activity.getApplicationContext(), UserActivity.class);
                                        intent.putExtra("id", Integer.parseInt(prf.getString("ID_USER")));
                                        intent.putExtra("image",prf.getString("IMAGE_USER").toString());
                                        intent.putExtra("name",prf.getString("NAME_USER").toString());
                                        activity.startActivity(intent);
                                        activity.overridePendingTransition(R.anim.enter, R.anim.exit);
                                        activity.overridePendingTransition(R.anim.enter, R.anim.exit);
                                        activity.finish();
                                    }else{
                                        Toasty.error(activity,response.body().getMessage(),Toast.LENGTH_LONG).show();
                                    }
                                }else{
                                    Toasty.error(activity,activity.getResources().getString(R.string.error_server),Toast.LENGTH_LONG).show();
                                }
                                if (dialog_progress!=null){
                                    dialog_progress.dismiss();
                                }
                            }
                            @Override
                            public void onFailure(Call<ApiResponse> call, Throwable t) {
                                Toasty.error(activity,activity.getResources().getString(R.string.error_server),Toast.LENGTH_LONG).show();
                                if (dialog_progress!=null){
                                    dialog_progress.dismiss();
                                }
                            }
                        });
                    }
                });
            }
            break;
            case 2: {
                final SlideHolder holder = (SlideHolder) holder_parent;

                slide_adapter = new SlideAdapter(activity, slideList);
                holder.view_pager_slide.setAdapter(slide_adapter);
                holder.view_pager_slide.setOffscreenPageLimit(1);

                holder.view_pager_slide.setClipToPadding(false);
                holder.view_pager_slide.setPageMargin(0);
                holder.view_pager_indicator.setupWithViewPager(holder.view_pager_slide);

                holder.view_pager_slide.setCurrentItem(slideList.size() / 2);
            }
            break;
            case 3: {
                final FollowHolder holder = (FollowHolder) holder_parent;
                this.linearLayoutManager=  new LinearLayoutManager((activity.getApplicationContext()),LinearLayoutManager.HORIZONTAL,false);
                this.followAdapter =new FollowAdapter(userList,activity);
                holder.recycle_view_follow_items.setAdapter(followAdapter);
                holder.recycle_view_follow_items.setLayoutManager(linearLayoutManager);
                followAdapter.notifyDataSetChanged();
                Log.v("WE ARE ONE","FollowHolder");
            }
            break;

        }

    }

    @Override
    public int getItemCount() {
        return StickerPack.size();
    }

    public class PackViewHolder extends RecyclerView.ViewHolder {

        private final AppCompatImageView image_view_menu_item;
        TextView item_pack_name,
                item_pack_publisher,
                item_pack_size,
                item_pack_created,
                item_pack_downloads,
                item_pack_username
                        ;
        CircularImageView pack_item_image_view_userimage;
        ImageView imone, imtwo, imthree, imfour,imfive,imsix,pack_try_image,image_view_item_pack_fav,image_view_delete;
        CardView cardView;
        RelativeLayout item_pack_premium;
        RelativeLayout item_pack_review;
        RelativeLayout item_pack_delete;

        ImageView image_view_telegram;
        ImageView image_view_whatsapp;
        ImageView image_view_signal;

        public PackViewHolder(@NonNull View itemView) {
            super(itemView);

            image_view_menu_item      = itemView.findViewById(R.id.image_view_menu_item);
            item_pack_size      = itemView.findViewById(R.id.item_pack_size);
            item_pack_publisher = itemView.findViewById(R.id.item_pack_publisher);
            item_pack_name      = itemView.findViewById(R.id.item_pack_name);
            item_pack_created   = itemView.findViewById(R.id.item_pack_created);
            item_pack_downloads = itemView.findViewById(R.id.item_pack_downloads);
            item_pack_username  = itemView.findViewById(R.id.item_pack_username);

            pack_item_image_view_userimage  = itemView.findViewById(R.id.pack_item_image_view_userimage);
            item_pack_premium               = itemView.findViewById(R.id.item_pack_premium);
            item_pack_review               = itemView.findViewById(R.id.item_pack_review);
            item_pack_delete               = itemView.findViewById(R.id.item_pack_delete);
            image_view_item_pack_fav        = itemView.findViewById(R.id.image_view_item_pack_fav);
            image_view_whatsapp        = itemView.findViewById(R.id.image_view_whatsapp);
            image_view_telegram        = itemView.findViewById(R.id.image_view_telegram);
            image_view_signal        = itemView.findViewById(R.id.image_view_signal);

            image_view_delete = itemView.findViewById(R.id.image_view_delete);

            imone = itemView.findViewById(R.id.sticker_one);
            imtwo = itemView.findViewById(R.id.sticker_two);
            imthree = itemView.findViewById(R.id.sticker_three);
            imfour = itemView.findViewById(R.id.sticker_four);
            imfive = itemView.findViewById(R.id.sticker_five);
            imsix = itemView.findViewById(R.id.sticker_six);
            pack_try_image = itemView.findViewById(R.id.pack_try_image);
            cardView = itemView.findViewById(R.id.card_view);
        }
    }
    private class SlideHolder extends RecyclerView.ViewHolder {
        private final ViewPagerIndicator view_pager_indicator;
        private final ClickableViewPager view_pager_slide;
        public SlideHolder(View itemView) {
            super(itemView);
            this.view_pager_indicator=(ViewPagerIndicator) itemView.findViewById(R.id.view_pager_indicator);
            this.view_pager_slide=(ClickableViewPager) itemView.findViewById(R.id.view_pager_slide);


        }

    }
    public class CategoriesHolder extends RecyclerView.ViewHolder {
        private final LinearLayoutManager linearLayoutManager;
        private final CategoryAdapter categoryVideoAdapter;
        public RecyclerView recycler_view_item_categories;

        public CategoriesHolder(View view) {
            super(view);
            this.recycler_view_item_categories = (RecyclerView) itemView.findViewById(R.id.recycler_view_item_categories);
            this.linearLayoutManager = new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false);
            this.categoryVideoAdapter = new CategoryAdapter(categoryList, activity);
            //recycler_view_item_categories.setHasFixedSize(true);
            recycler_view_item_categories.setAdapter(categoryVideoAdapter);
            recycler_view_item_categories.setLayoutManager(linearLayoutManager);
        }
    }
    public static class FollowHolder extends  RecyclerView.ViewHolder {
        private final RecyclerView recycle_view_follow_items;
        public FollowHolder(View view) {
            super(view);
            recycle_view_follow_items = (RecyclerView) itemView.findViewById(R.id.recycle_view_follow_items);
        }
    }
    public class MaxNativeHolder extends RecyclerView.ViewHolder {
        private MaxNativeAdLoader nativeAdLoader;
        private MaxAd             loadedNativeAd;
        private FrameLayout         native_ad_layout;

        public MaxNativeHolder(@NonNull View itemView) {
            super(itemView);
            this.native_ad_layout = itemView.findViewById(R.id.native_ad_layout);
            PrefManager prefManager= new PrefManager(activity);
            nativeAdLoader = new MaxNativeAdLoader( prefManager.getString("ADMIN_NATIVE_ADMOB_ID"), activity );
            nativeAdLoader.setNativeAdListener(new MaxNativeAdListener() {
                @Override
                public void onNativeAdLoaded(MaxNativeAdView nativeAdView, MaxAd nativeAd) {
                    if ( loadedNativeAd != null )
                    {
                        nativeAdLoader.destroy( loadedNativeAd );
                    }

                    // Save ad for cleanup.
                    loadedNativeAd = nativeAd;

                    native_ad_layout.removeAllViews();
                    native_ad_layout.addView( nativeAdView );
                }
            });

            nativeAdLoader.loadAd(createNativeAdView());
        }
    }

    private MaxNativeAdView createNativeAdView()
    {
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder( R.layout.native_max_ad_view )
                .setTitleTextViewId( R.id.title_text_view )
                .setBodyTextViewId( R.id.body_text_view )
                .setAdvertiserTextViewId( R.id.advertiser_textView )
                .setIconImageViewId( R.id.icon_image_view )
                .setMediaContentViewGroupId( R.id.media_view_container )
                .setCallToActionButtonId( R.id.cta_button )
                .build();

        return new MaxNativeAdView( binder, activity );
    }
    public class AdmobNativeHolder extends RecyclerView.ViewHolder {
        private final AdLoader adLoader;
        private com.google.android.gms.ads.nativead.NativeAd nativeAd;
        private FrameLayout frameLayout;

        public AdmobNativeHolder(@NonNull View itemView) {
            super(itemView);

            PrefManager prefManager= new PrefManager(activity);
            frameLayout = (FrameLayout) itemView.findViewById(R.id.fl_adplaceholder);
            AdLoader.Builder builder = new AdLoader.Builder(activity, prefManager.getString("ADMIN_NATIVE_ADMOB_ID"));

            builder.forNativeAd(
                    nativeAd -> {
                        // If this callback occurs after the activity is destroyed, you must call
                        // destroy and return or you may get a memory leak.

                        if (nativeAd == null) {
                            nativeAd.destroy();
                            return;
                        }

                        Bundle extras = nativeAd.getExtras();

                        AdmobNativeHolder.this.nativeAd = nativeAd;
                        FrameLayout frameLayout = activity.findViewById(R.id.fl_adplaceholder);
                        NativeAdView adView = (NativeAdView) activity.getLayoutInflater().inflate(R.layout.ad_unified, null);

                        populateNativeAdView(nativeAd, adView);
                        if(frameLayout != null){
                            frameLayout.removeAllViews();
                            frameLayout.addView(adView);
                        }

                    });

            VideoOptions videoOptions =
                    new VideoOptions.Builder().setStartMuted(true).build();

            com.google.android.gms.ads.nativead.NativeAdOptions adOptions =
                    new NativeAdOptions.Builder().setVideoOptions(videoOptions).build();

            builder.withNativeAdOptions(adOptions);

            adLoader =
                    builder
                            .withAdListener(
                                    new AdListener() {
                                        @Override
                                        public void onAdFailedToLoad(LoadAdError loadAdError) {
                                            String error =
                                                    String.format(
                                                            "domain: %s, code: %d, message: %s",
                                                            loadAdError.getDomain(),
                                                            loadAdError.getCode(),
                                                            loadAdError.getMessage());

                                            Log.d("ADMOB_TES", error);

                                        }
                                    })
                            .build();

            adLoader.loadAd(new AdRequest.Builder().build());

        }
    }

    /**
     * Populates a {@link NativeAdView} object with data from a given {@link com.google.android.gms.ads.nativead.NativeAd}.
     *
     * @param nativeAd the object containing the ad's assets
     * @param adView the view to be populated
     */
    private void populateNativeAdView(com.google.android.gms.ads.nativead.NativeAd nativeAd, NativeAdView adView) {
        // Set the media view.
        adView.setMediaView((com.google.android.gms.ads.nativead.MediaView) adView.findViewById(R.id.ad_media));

        // Set other ad assets.
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setPriceView(adView.findViewById(R.id.ad_price));
        adView.setStarRatingView(adView.findViewById(R.id.ad_stars));
        adView.setStoreView(adView.findViewById(R.id.ad_store));
        adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));

        // The headline and mediaContent are guaranteed to be in every NativeAd.
        ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        adView.getMediaView().setMediaContent(nativeAd.getMediaContent());

        // These assets aren't guaranteed to be in every NativeAd, so it's important to
        // check before trying to display them.
        if (nativeAd.getBody() == null) {
            adView.getBodyView().setVisibility(View.INVISIBLE);
        } else {
            adView.getBodyView().setVisibility(View.VISIBLE);
            ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
        }

        if (nativeAd.getCallToAction() == null) {
            adView.getCallToActionView().setVisibility(View.INVISIBLE);
        } else {
            adView.getCallToActionView().setVisibility(View.VISIBLE);
            ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
        }

        if (nativeAd.getIcon() == null) {
            adView.getIconView().setVisibility(View.GONE);
        } else {
            ((ImageView) adView.getIconView()).setImageDrawable(
                    nativeAd.getIcon().getDrawable());
            adView.getIconView().setVisibility(View.VISIBLE);
        }

        if (nativeAd.getPrice() == null) {
            adView.getPriceView().setVisibility(View.INVISIBLE);
        } else {
            adView.getPriceView().setVisibility(View.VISIBLE);
            ((TextView) adView.getPriceView()).setText(nativeAd.getPrice());
        }

        if (nativeAd.getStore() == null) {
            adView.getStoreView().setVisibility(View.INVISIBLE);
        } else {
            adView.getStoreView().setVisibility(View.VISIBLE);
            ((TextView) adView.getStoreView()).setText(nativeAd.getStore());
        }

        if (nativeAd.getStarRating() == null) {
            adView.getStarRatingView().setVisibility(View.INVISIBLE);
        } else {
            ((RatingBar) adView.getStarRatingView())
                    .setRating(nativeAd.getStarRating().floatValue());
            adView.getStarRatingView().setVisibility(View.VISIBLE);
        }

        if (nativeAd.getAdvertiser() == null) {
            adView.getAdvertiserView().setVisibility(View.INVISIBLE);
        } else {
            ((TextView) adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
            adView.getAdvertiserView().setVisibility(View.VISIBLE);
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad.
        adView.setNativeAd(nativeAd);

        // Get the video controller for the ad. One will always be provided, even if the ad doesn't
        // have a video asset.
        VideoController vc = nativeAd.getMediaContent().getVideoController();

        // Updates the UI to say whether or not this ad has a video asset.
        if (vc.hasVideoContent()) {


            // Create a new VideoLifecycleCallbacks object and pass it to the VideoController. The
            // VideoController will call methods on this object when events occur in the video
            // lifecycle.
            vc.setVideoLifecycleCallbacks(new VideoController.VideoLifecycleCallbacks() {
                @Override
                public void onVideoEnd() {
                    // Publishers should allow native ads to complete video playback before
                    // refreshing or replacing them with another ad in the same UI location.

                    super.onVideoEnd();
                }
            });
        } else {

        }
    }
    private void requestAppLovinInterstitial() {
        if (applovinInterstitialAdBlock==null){
            applovinInterstitialAd = AppLovinInterstitialAd.create( AppLovinSdk.getInstance( activity ), activity );
            applovinInterstitialAd.setAdLoadListener(new AppLovinAdLoadListener() {
                @Override
                public void adReceived(AppLovinAd ad) {
                    applovinInterstitialAdBlock = ad;
                }

                @Override
                public void failedToReceiveAd(int errorCode) {

                }
            });
            applovinInterstitialAd.setAdDisplayListener(new AppLovinAdDisplayListener() {
                @Override
                public void adDisplayed(AppLovinAd ad) {

                }

                @Override
                public void adHidden(AppLovinAd ad) {
                    selectOperation();
                    requestAppLovinInterstitial();
                }
            });
            applovinInterstitialAd.setAdClickListener(ad -> {

            });
            applovinInterstitialAd.setAdVideoPlaybackListener(new AppLovinAdVideoPlaybackListener() {
                @Override
                public void videoPlaybackBegan(AppLovinAd ad) {

                }

                @Override
                public void videoPlaybackEnded(AppLovinAd ad, double percentViewed, boolean fullyWatched) {

                }
            });
            AppLovinSdk.getInstance( activity.getApplicationContext() ).getAdService().loadNextAd(AppLovinAdSize.INTERSTITIAL, new AppLovinAdLoadListener() {
                @Override
                public void adReceived(AppLovinAd ad) {
                    applovinInterstitialAdBlock = ad;
                }

                @Override
                public void failedToReceiveAd(int errorCode) {

                }
            });
        }
    }


    private void requestMaxInterstitial() {
        if (maxInterstitialAd==null) {
            PrefManager prefManager= new PrefManager(activity);
            maxInterstitialAd = new MaxInterstitialAd(prefManager.getString("ADMIN_INTERSTITIAL_ADMOB_ID"), activity);
            maxInterstitialAd.setListener(new MaxAdListener() {
                @Override
                public void onAdLoaded(MaxAd ad) {

                }

                @Override
                public void onAdDisplayed(MaxAd ad) {

                    Log.d("TAG", "The ad was shown.");
                }

                @Override
                public void onAdHidden(MaxAd ad) {
                    selectOperation();
                }

                @Override
                public void onAdClicked(MaxAd ad) {

                }

                @Override
                public void onAdLoadFailed(String adUnitId, MaxError error) {

                }

                @Override
                public void onAdDisplayFailed(MaxAd ad, MaxError error) {

                }
            });

            // Load the first ad
            maxInterstitialAd.loadAd();
        }
    }
    /* private void requestISInterstitial() {
         if(!IronSource.isInterstitialReady()){
             PrefManager prefManager= new PrefManager(activity);
             IronSource.init(activity, prefManager.getString("ADMIN_INTERSTITIAL_ADMOB_ID"), IronSource.AD_UNIT.INTERSTITIAL);
             IronSource.setInterstitialListener(new InterstitialListener() {
                 @Override
                 public void onInterstitialAdReady() {
                     Log.v("IROUNSOURCE","onInterstitialAdReady");

                 }
                 @Override
                 public void onInterstitialAdLoadFailed(IronSourceError error) {
                     Log.v("IROUNSOURCE",error.getErrorMessage());

                 }
                 @Override
                 public void onInterstitialAdOpened() {
                     Log.v("IROUNSOURCE","onInterstitialAdOpened");

                 }
                 @Override
                 public void onInterstitialAdClosed() {

                     selectOperation();
                     requestISInterstitial();


                 }
                 @Override
                 public void onInterstitialAdShowFailed(IronSourceError error) {
                     Log.v("IROUNSOURCE",error.getErrorMessage());

                 }
                 @Override
                 public void onInterstitialAdClicked() {
                     Log.v("IROUNSOURCE","onInterstitialAdClicked");

                 }
                 @Override
                 public void onInterstitialAdShowSucceeded() {
                     Log.v("IROUNSOURCE","onInterstitialAdShowSucceeded");

                 }
             });
             IronSource.loadInterstitial();
         }

     }*/
    private void requestAdmobInterstitial() {
        if (admobInterstitialAd==null){
            PrefManager prefManager= new PrefManager(activity);
            AdRequest adRequest = new AdRequest.Builder().build();
            admobInterstitialAd.load(activity.getApplicationContext(), prefManager.getString("ADMIN_INTERSTITIAL_ADMOB_ID"), adRequest, new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    admobInterstitialAd = interstitialAd;


                    admobInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback(){
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            selectOperation();

                            Log.d("TAG", "The ad was dismissed.");
                        }


                        @Override
                        public void onAdShowedFullScreenContent() {
                            admobInterstitialAd = null;
                            Log.d("TAG", "The ad was shown.");
                        }
                    });

                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    admobInterstitialAd = null;
                    Log.d("TAG_ADS", "onAdFailedToLoad: "+loadAdError.getMessage());

                }
            });

        }


    }
    public boolean checkSUBSCRIBED(){
        PrefManager prefManager= new PrefManager(activity);
        if (!prefManager.getString("SUBSCRIBED").equals("TRUE")) {
            return false;
        }
        return true;
    }
    public void selectOperation() {
        if(selected_view !=  null && selected_position != -1){
            (activity).startActivity(selected_intent, ActivityOptionsCompat.makeScaleUpAnimation(selected_view, (int) selected_view.getX(), (int) selected_view.getY(), selected_view.getWidth(), selected_view.getHeight()).toBundle());
        }
    }

    public void Operation(){

        PrefManager prefManager= new PrefManager(activity);
        if(checkSUBSCRIBED()) {
            selectOperation();
        }else{
            if(prefManager.getString("ADMIN_INTERSTITIAL_TYPE").equals("ADMOB")) {
                requestAdmobInterstitial();
                if(prefManager.getInt("ADMIN_INTERSTITIAL_CLICKS") <= prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")){
                    if (admobInterstitialAd != null) {
                        prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS",0);
                        admobInterstitialAd.show(activity);
                    }else{
                        selectOperation();
                    }
                }else{
                    selectOperation();
                    prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS",prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")+1);
                }
            }else  if(prefManager.getString("ADMIN_INTERSTITIAL_TYPE").equals("MAX")) {
                requestMaxInterstitial();
                if(prefManager.getInt("ADMIN_INTERSTITIAL_CLICKS") <= prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")){
                    if (maxInterstitialAd != null) {
                        if (maxInterstitialAd.isReady()) {
                            prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS", 0);
                            maxInterstitialAd.showAd();

                        } else {
                            selectOperation();
                        }
                    } else {
                        selectOperation();
                    }
                }else{
                    selectOperation();
                    prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS",prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")+1);
                }
            }else  if(prefManager.getString("ADMIN_INTERSTITIAL_TYPE").equals("APPLOVIN")) {
                requestAppLovinInterstitial();
                if(prefManager.getInt("ADMIN_INTERSTITIAL_CLICKS") <= prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")){
                    if (applovinInterstitialAd != null) {
                        if (applovinInterstitialAdBlock!=null) {
                            prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS", 0);
                            applovinInterstitialAd.showAndRender(applovinInterstitialAdBlock);
                        } else {
                            selectOperation();
                        }
                    } else {
                        selectOperation();
                    }
                }else{
                    selectOperation();
                    prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS",prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")+1);
                }
            }/*else  if(prefManager.getString("ADMIN_INTERSTITIAL_TYPE").equals("IS")) {
                requestISInterstitial();
                if(prefManager.getInt("ADMIN_INTERSTITIAL_CLICKS") <= prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")) {
                    if(IronSource.isInterstitialReady()){
                        prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS", 0);
                        IronSource.showInterstitial();
                    }else{
                        selectOperation();
                    }
                }else{
                    selectOperation();
                    prefManager.setInt("ADMOB_INTERSTITIAL_COUNT_CLICKS",prefManager.getInt("ADMOB_INTERSTITIAL_COUNT_CLICKS")+1);
                }
            }*/
            else{
                selectOperation();
            }
        }
    }
    private void Report(StickerPack packApi, MenuItem item) {
        final PrefManager prefManager = new PrefManager(activity);

        switch (item.getItemId()){
            case R.id.report:
                Intent intent = new Intent(activity, SupportActivity.class);
                intent.putExtra("message","Hi Admin, Please check this status i think should be removed status id : "+packApi.identifier );
                activity.startActivity(intent);
                break;
            case R.id.report_user:
                Intent intent_user = new Intent(activity, SupportActivity.class);
                intent_user.putExtra("message","Hi Admin, Please check this user i think should be removed user id : "+packApi.userid );
                activity.startActivity(intent_user);
                break;
            case R.id.block_user:

                prefManager.setString("user_reported_"+packApi.userid,"TRUE");
                Toasty.warning(activity,"User : "+ packApi.username+" has been blocked !").show();

                break;

        };
    }
}
