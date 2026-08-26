package com.stickersanimated.kissing.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;



import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.config.Config;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ApiResponse;
import com.stickersanimated.kissing.entity.CategoryApi;
import com.stickersanimated.kissing.services.BillingSubs;
import com.stickersanimated.kissing.services.CallBackCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class SplashActivity extends AppCompatActivity {

    private PrefManager prf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        check();
        loadLang();
        setContentView(R.layout.activity_splash);
        prf= new PrefManager(getApplicationContext());

        Timer myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // If you want to modify a view in your Activity
                SplashActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                       checkUser();

                    }
                });
            }
        }, 3000);
        prf.setString("ADMIN_REWARDED_ADMOB_ID","");
        prf.setString("ADMIN_NATIVE_BANNER_FACEBOOK_ID","");

        prf.setString("ADMIN_INTERSTITIAL_ADMOB_ID","");
        prf.setString("ADMIN_INTERSTITIAL_FACEBOOK_ID","");
        prf.setString("ADMIN_INTERSTITIAL_TYPE","FALSE");
        prf.setInt("ADMIN_INTERSTITIAL_CLICKS",3);

        prf.setString("ADMIN_BANNER_ADMOB_ID","");
        prf.setString("ADMIN_BANNER_FACEBOOK_ID","");
        prf.setString("ADMIN_BANNER_TYPE","FALSE");

        prf.setString("ADMIN_NATIVE_FACEBOOK_ID","");
        prf.setString("ADMIN_NATIVE_ADMOB_ID","");
        prf.setString("ADMIN_NATIVE_LINES","6");
        prf.setString("ADMIN_NATIVE_TYPE","FALSE");
        prf.setString("ADMIN_REWARDED_AD_TYPE","FALSE");

        resetAdWaterfallSettings();
    }

    /**
     * Clears the per network unit ids and the waterfall order, so a placement removed from
     * the panel stops being requested instead of lingering from the previous run.
     */
    private void resetAdWaterfallSettings() {
        for (String format : new String[]{"BANNER", "NATIVE", "INTERSTITIAL", "REWARDED"}) {
            prf.setString("ADMIN_" + format + "_ORDER", "");
            for (String network : new String[]{"MAX", "APPLOVIN", "UNITY"}) {
                prf.setString("ADMIN_" + format + "_" + network + "_ID", "");
            }
        }
        prf.setString("ADMIN_REWARDED_FACEBOOK_ID", "");
        prf.setString("ADMIN_UNITY_GAME_ID", "");
    }

    /**
     * Stores any ADMIN_* setting the panel sends, including keys this build does not know
     * about yet. That is what lets a new ad network or a new fallback order be rolled out
     * from the backend without shipping a new APK.
     */
    private void storeAdminSetting(String name, String value) {
        if (name == null || value == null || !name.startsWith("ADMIN_")) {
            return;
        }
        if (INT_SETTINGS.contains(name)) {
            try {
                prf.setInt(name, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                // Leave the previous value in place rather than corrupting the type.
            }
            return;
        }
        prf.setString(name, value);
    }

    /** Settings the app reads back with getInt(), so they must never be stored as strings. */
    private static final java.util.Set<String> INT_SETTINGS =
            java.util.Collections.singleton("ADMIN_INTERSTITIAL_CLICKS");

    private void checkUser() {
        if (prf.getString("LOGGED").toString().equals("TRUE")){
            Integer user_id =  Integer.parseInt(prf.getString("ID_USER"));
            String user_key = prf.getString("TOKEN_USER");
            Retrofit retrofit = apiClient.getClient();
            apiRest service = retrofit.create(apiRest.class);
            Call<ApiResponse> call = service.checkUser(user_id,user_key);
            call.enqueue(new Callback<ApiResponse>() {
                @Override
                public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                    if (response.isSuccessful()){
                        if (response.body().getCode() == 500){
                            logout();
                        }
                    }
                    checkAccount();
                }

                @Override
                public void onFailure(Call<ApiResponse> call, Throwable t) {
                    checkAccount();
                }
            });
        }else{
            checkAccount();
        }
    }
    public void logout() {
        PrefManager prf = new PrefManager(getApplicationContext());
        prf.remove("ID_USER");
        prf.remove("SALT_USER");
        prf.remove("TOKEN_USER");
        prf.remove("NAME_USER");
        prf.remove("TYPE_USER");
        prf.remove("USERN_USER");
        prf.remove("IMAGE_USER");
        prf.remove("LOGGED");
        Toasty.warning(getApplicationContext(),getString(R.string.message_logout),Toast.LENGTH_LONG).show();

    }
    private void checkAccount() {

        Integer version = -1;
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

            Retrofit retrofit = apiClient.getClient();
            apiRest service = retrofit.create(apiRest.class);
            Call<ApiResponse> call = service.check(version);
            call.enqueue(new Callback<ApiResponse>() {
                @Override
                public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {


                    if (response.isSuccessful()){

                        for (int i = 0; i < response.body().getValues().size(); i++) {
                            storeAdminSetting(response.body().getValues().get(i).getName(),
                                    response.body().getValues().get(i).getValue());
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_APP_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_APP_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_PUBLISHER_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_PUBLISHER_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_REWARDED_ADMOB_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_REWARDED_ADMOB_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_REWARDED_AD_TYPE") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_REWARDED_AD_TYPE",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_NATIVE_BANNER_FACEBOOK_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_NATIVE_BANNER_FACEBOOK_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_INTERSTITIAL_ADMOB_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_INTERSTITIAL_ADMOB_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_INTERSTITIAL_FACEBOOK_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_INTERSTITIAL_FACEBOOK_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_INTERSTITIAL_TYPE") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_INTERSTITIAL_TYPE",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_INTERSTITIAL_CLICKS") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setInt("ADMIN_INTERSTITIAL_CLICKS",Integer.parseInt(response.body().getValues().get(i).getValue()));
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_BANNER_ADMOB_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_BANNER_ADMOB_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_BANNER_FACEBOOK_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_BANNER_FACEBOOK_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_BANNER_TYPE") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_BANNER_TYPE",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_NATIVE_FACEBOOK_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_NATIVE_FACEBOOK_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_NATIVE_ADMOB_ID") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_NATIVE_ADMOB_ID",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_NATIVE_LINES") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_NATIVE_LINES",response.body().getValues().get(i).getValue());
                            }
                            if ( response.body().getValues().get(i).getName().equals("ADMIN_NATIVE_TYPE") ){
                                if (response.body().getValues().get(i).getValue()!=null)
                                    prf.setString("ADMIN_NATIVE_TYPE",response.body().getValues().get(i).getValue());
                            }
                        }

                        if (response.body().getCode().equals(200)) {
                            // Straight to the home screen: the welcome slides are gone.
                                Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
                                startActivity(intent);
                                overridePendingTransition(R.anim.enter, R.anim.exit);
                                finish();
                        }else if (response.body().getCode().equals(202)) {
                            String title_update=response.body().getValues().get(0).getValue();
                            String featurs_update=response.body().getMessage();
                            View v = (View)  getLayoutInflater().inflate(R.layout.update_message,null);
                            TextView update_text_view_title=(TextView) v.findViewById(R.id.update_text_view_title);
                            TextView update_text_view_updates=(TextView) v.findViewById(R.id.update_text_view_updates);
                            update_text_view_title.setText(title_update);
                            update_text_view_updates.setText(featurs_update);
                            AlertDialog.Builder builder;
                            builder = new AlertDialog.Builder(SplashActivity.this);
                            builder.setTitle("New Update")
                                    //.setMessage(response.body().getValue())
                                    .setView(v)
                                    .setPositiveButton(getResources().getString(R.string.update_now), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            final String appPackageName=getApplication().getPackageName();
                                            try {
                                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                                            } catch (android.content.ActivityNotFoundException anfe) {
                                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + appPackageName)));
                                            }
                                            finish();
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.skip), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // Straight to the home screen: the welcome slides are gone.
                                                Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
                                                startActivity(intent);
                                                overridePendingTransition(R.anim.enter, R.anim.exit);
                                                finish();
                                        }
                                    })
                                    .setCancelable(false)
                                    .setIcon(R.drawable.ic_update)
                                    .show();
                        } else {
                            // Straight to the home screen: the welcome slides are gone.
                                Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
                                startActivity(intent);
                                overridePendingTransition(R.anim.enter, R.anim.exit);
                                finish();
                        }
                    }else {
                        // Straight to the home screen: the welcome slides are gone.
                            Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
                            startActivity(intent);
                            overridePendingTransition(R.anim.enter, R.anim.exit);
                            finish();
                    }
                }
                @Override
                public void onFailure(Call<ApiResponse> call, Throwable t) {

                    // Straight to the home screen: the welcome slides are gone.
                        Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
                        startActivity(intent);
                        overridePendingTransition(R.anim.enter, R.anim.exit);
                        finish();

                }
            });


    }


    public void check(){
        List<String> listSkuStoreSubs = new ArrayList<>();
        listSkuStoreSubs.add(Config.SUBSCRIPTION_ID);

        new BillingSubs(SplashActivity.this, listSkuStoreSubs, new CallBackCheck() {
            @Override
            public void onPurchase() {
                PrefManager prefManager= new PrefManager(getApplicationContext());
                prefManager.setString("SUBSCRIBED","TRUE");
            }

            @Override
            public void onNotPurchase() {
                PrefManager prefManager= new PrefManager(getApplicationContext());
                prefManager.setString("SUBSCRIBED","FALSE");
            }
        }).checkPurchase();
    }
    public void loadLang(){
        Retrofit retrofit = apiClient.getClient();
        apiRest service = retrofit.create(apiRest.class);
        Call<List<CategoryApi>> call = service.AllCategories();
        call.enqueue(new Callback<List<CategoryApi>>() {
            @Override
            public void onResponse(Call<List<CategoryApi>> call, Response<List<CategoryApi>> response) {

            }

            @Override
            public void onFailure(Call<List<CategoryApi>> call, Throwable t) {

            }
        });

    }


}
