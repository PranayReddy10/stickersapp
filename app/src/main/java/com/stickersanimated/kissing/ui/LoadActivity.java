package com.stickersanimated.kissing.ui;

import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.Sticker;
import com.stickersanimated.kissing.StickerPack;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.PackApi;
import com.stickersanimated.kissing.entity.StickerApi;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import static com.stickersanimated.kissing.MainActivity.EXTRA_STICKERPACK;

public class LoadActivity extends AppCompatActivity {
    ArrayList<StickerPack> stickerPacks = new ArrayList<>();
    List<Sticker> mStickers;
    List<String> mEmojis,mDownloadFiles;
    private Integer position = 0;

    private  Integer id;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load);
        Uri data = this.getIntent().getData();
        if (data==null){
            Bundle bundle = getIntent().getExtras() ;
            this.id =  bundle.getInt("id");
        }else{
            // This activity answers for every link to the site, because the intent
            // filter matches the whole host rather than one path. Most of those
            // links are not a pack, and the old code parsed the path with two
            // string replacements and Integer.parseInt - so a reel link, or the
            // site's front page, crashed the app before it drew anything.
            final Integer packId = packIdFrom(data);
            if (packId == null) {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
                return;
            }
            this.id = packId;
        }

        stickerPacks = new ArrayList<>();
        mStickers = new ArrayList<>();
        mEmojis = new ArrayList<>();
        mDownloadFiles = new ArrayList<>();
        mEmojis.add("");

        getArticle();
    }

    /**
     * The pack id in a link to this site, or null when the link is not a pack.
     *
     * <p>Matches the address a pack is shared at and the one its page lives at,
     * and nothing else: a reel, the front page and every other address on the
     * host belong to the browser or to the home screen, not here.
     */
    private static Integer packIdFrom(Uri data) {
        final String path = data.getPath() == null ? "" : data.getPath();
        final Matcher matcher =
                Pattern.compile("^/(?:share|stickers)/(\\d+)\\.html$").matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getLastBitFromUrl(final String url) {
        return url.replaceFirst(".*/([^/?]+).*", "$1");
    }

    public void getArticle(){

        Retrofit retrofit = apiClient.getClient();
        apiRest service = retrofit.create(apiRest.class);
        Call<PackApi> call = service.packById(id);
        call.enqueue(new retrofit2.Callback<PackApi>() {
            @Override
            public void onResponse(Call<PackApi> call, Response<PackApi> response) {
                if(response.isSuccessful()) {

                    position = 0 ;
                    stickerPacks.clear();
                    mStickers.clear();
                    mEmojis.clear();
                    mDownloadFiles.clear();
                    mEmojis.add("");
                    position = 0;

                    PackApi packApi= response.body();
                    stickerPacks.add(new StickerPack(
                            packApi.getIdentifier()+"",
                            packApi.getName(),
                            packApi.getPublisher(),
                            getLastBitFromUrl(packApi.getTrayImageFile()).replace(" ","_"),
                            packApi.getTrayImageFile(),
                            packApi.getSize(),
                            packApi.getDownloads(),
                            packApi.getPremium(),
                            packApi.getTrusted(),
                            packApi.getCreated(),
                            packApi.getUser(),
                            packApi.getUserimage(),
                            packApi.getUserid(),
                            packApi.getPublisherEmail(),
                            packApi.getPublisherWebsite(),
                            packApi.getPrivacyPolicyWebsite(),
                            packApi.getLicenseAgreementWebsite(),
                            packApi.getAnimated(),
                            packApi.getTelegram(),
                            packApi.getSignal(),
                            packApi.getWhatsapp(),
                            packApi.getSignalurl(),
                            packApi.getTelegramurl()

                    ));
                    List<StickerApi> stickerApiList =  packApi.getStickers();
                    for (int j = 0; j < stickerApiList.size(); j++) {
                        StickerApi stickerApi = stickerApiList.get(j);
                        mStickers.add(new Sticker(
                                stickerApi.getImageFileThum(),
                                stickerApi.getImageFile(),
                                getLastBitFromUrl(stickerApi.getImageFile()).replace(".png",".webp"),
                                mEmojis
                        ));
                        mDownloadFiles.add(stickerApi.getImageFile());
                    }
                    Hawk.put(packApi.getIdentifier()+"", mStickers);
                    stickerPacks.get(position).setStickers(Hawk.get(packApi.getIdentifier()+"",new ArrayList<Sticker>()));
                    stickerPacks.get(position).packApi = packApi;
                    mStickers.clear();


                    Intent intent =  new Intent((getApplicationContext()), StickerDetailsActivity.class);
                    intent.putExtra(EXTRA_STICKERPACK, stickerPacks.get(position));
                    intent.putExtra("from", true);
                    startActivity(intent);
                    overridePendingTransition(R.anim.enter, R.anim.exit);
                    finish();

                }else{
                    Toast.makeText(LoadActivity.this, "Pack Not exit", Toast.LENGTH_SHORT).show();
                    Intent intent  = new Intent(getApplicationContext(),HomeActivity.class);
                    startActivity(intent);
                    finish();
                }
            }
            @Override
            public void onFailure(Call<PackApi> call, Throwable t) {
                Toast.makeText(LoadActivity.this, "Pack Not exit", Toast.LENGTH_SHORT).show();
                Intent intent  = new Intent(getApplicationContext(),HomeActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}



