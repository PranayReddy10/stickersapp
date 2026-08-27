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

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import com.github.siyamed.shapeimageview.CircularImageView;
import com.github.vivchar.viewpagerindicator.ViewPagerIndicator;
import com.stickersanimated.kissing.config.Config;
import com.orhanobut.hawk.Hawk;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.ads.AdsConfig;
import com.stickersanimated.kissing.ads.InterstitialAdManager;
import com.stickersanimated.kissing.ads.NativeAdManager;
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

    /** View type of the in-feed native ad row, filled by whichever network answers first. */
    public static final int VIEW_TYPE_NATIVE_AD = 6;
    /** Kept so lists built by older code still render an ad row. */
    private static final int VIEW_TYPE_NATIVE_AD_LEGACY = 7;

    private InterstitialAdManager interstitialAdManager;

    private final java.util.List<NativeAdManager> nativeAdManagers = new java.util.ArrayList<>();

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
        preloadInterstitial();
    }
    public StickerAdapter(Activity activity, ArrayList<StickerPack> StickerPack,List<SlideApi> slideList) {
        this.activity = activity;
        this.StickerPack = StickerPack;
        this.slideList = slideList;
        preloadInterstitial();
    }
    public StickerAdapter(Activity activity, ArrayList<StickerPack> StickerPack,List<SlideApi> slideList,List<CategoryApi> categoryList,Boolean b) {
        this.activity = activity;
        this.StickerPack = StickerPack;
        this.slideList = slideList;
        this.categoryList = categoryList;
        preloadInterstitial();
    }
    public StickerAdapter(Activity activity, ArrayList<StickerPack> StickerPack,List<SlideApi> slideList,List<UserApi> userList){
        this.activity = activity;
        this.StickerPack = StickerPack;
        this.slideList = slideList;
        this.userList = userList;
        preloadInterstitial();
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
            case VIEW_TYPE_NATIVE_AD:
            case VIEW_TYPE_NATIVE_AD_LEGACY: {
                View v6 = inflater.inflate(R.layout.item_admob_native_ads, parent, false);
                viewHolder = new NativeAdHolder(v6);
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
    /**
     * In-feed ad row. The container is handed to {@link NativeAdManager}, which walks the
     * configured waterfall until a network fills it.
     */
    public class NativeAdHolder extends RecyclerView.ViewHolder {

        NativeAdHolder(@NonNull View itemView) {
            super(itemView);
            final NativeAdManager manager =
                    NativeAdManager.into(activity, itemView.findViewById(R.id.fl_adplaceholder));
            if (manager != null) {
                nativeAdManagers.add(manager);
                manager.load();
            }
        }
    }

    /**
     * Every in-feed ad slot this adapter has filled. They hold on to the activity and to the
     * network's ad object, so they are released when the list goes away.
     */
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        for (NativeAdManager manager : nativeAdManagers) {
            manager.destroy();
        }
        nativeAdManagers.clear();
    }

    public boolean checkSUBSCRIBED() {
        return new AdsConfig(activity).isSubscribed();
    }

    public void selectOperation() {
        if (selected_view != null && selected_position != -1) {
            activity.startActivity(selected_intent,
                    ActivityOptionsCompat.makeScaleUpAnimation(selected_view,
                            (int) selected_view.getX(), (int) selected_view.getY(),
                            selected_view.getWidth(), selected_view.getHeight()).toBundle());
        }
    }

    /**
     * Opens the pack the user tapped, showing an interstitial first when one is due.
     *
     * <p>Every configured network is tried in turn, and navigation happens either way: a
     * network that fails to fill can never leave the user stuck on the list.
     */
    public void Operation() {
        if (checkSUBSCRIBED()) {
            selectOperation();
            return;
        }
        if (interstitialAdManager == null) {
            interstitialAdManager = new InterstitialAdManager(activity);
        }
        interstitialAdManager.showThen(this::selectOperation);
    }

    /** Warms up the next interstitial. Call it when the list becomes visible. */
    public void preloadInterstitial() {
        if (checkSUBSCRIBED()) {
            return;
        }
        if (interstitialAdManager == null) {
            interstitialAdManager = new InterstitialAdManager(activity);
        }
        interstitialAdManager.preload();
    }

    /** Releases the interstitial held by this adapter. */
    public void destroyAds() {
        if (interstitialAdManager != null) {
            interstitialAdManager.destroy();
            interstitialAdManager = null;
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
