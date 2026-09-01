package com.stickersanimated.kissing.api;



import com.google.gson.JsonObject;
import com.stickersanimated.kissing.config.Config;
import com.stickersanimated.kissing.entity.ApiResponse;
import com.stickersanimated.kissing.entity.CategoryApi;
import com.stickersanimated.kissing.entity.PackApi;
import com.stickersanimated.kissing.entity.ReelApi;
import com.stickersanimated.kissing.entity.SlideApi;
import com.stickersanimated.kissing.entity.TagApi;
import com.stickersanimated.kissing.entity.UserApi;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface apiRest {

    // ----------------------------------------------------------------- reels

    @GET("reel/feed/{page}/{user}/" + Config.SECURE_KEY + "/")
    Call<List<ReelApi>> reelFeed(@Path("page") Integer page, @Path("user") Integer user);

    @GET("reel/by/follow/{page}/{user}/" + Config.SECURE_KEY + "/")
    Call<List<ReelApi>> reelFollowing(@Path("page") Integer page, @Path("user") Integer user);

    @GET("reel/by/user/{page}/{author}/{user}/" + Config.SECURE_KEY + "/")
    Call<List<ReelApi>> reelByUser(@Path("page") Integer page, @Path("author") Integer author,
                                   @Path("user") Integer user);

    /**
     * The reel id is reelId in the path, not id: the server resolves route parameters
     * before the posted body, so a placeholder called id would shadow the user id below.
     */
    @FormUrlEncoded
    @POST("reel/like/{reelId}/" + Config.SECURE_KEY + "/")
    Call<JsonObject> reelLike(@Path("reelId") String reelId,
                              @Field("id") String userId, @Field("key") String key);

    @POST("reel/view/{reelId}/" + Config.SECURE_KEY + "/")
    Call<JsonObject> reelView(@Path("reelId") String reelId);

    @FormUrlEncoded
    @POST("reel/delete/{reelId}/" + Config.SECURE_KEY + "/")
    Call<JsonObject> reelDelete(@Path("reelId") String reelId,
                                @Field("id") String userId, @Field("key") String key);

    /** Step one of an upload: ask for a short lived URL to PUT the file to. */
    @FormUrlEncoded
    @POST("reel/upload/url/" + Config.SECURE_KEY + "/")
    Call<JsonObject> reelUploadUrl(@Field("id") String userId, @Field("key") String key,
                                   @Field("type") String type, @Field("ext") String ext,
                                   @Field("thumbext") String thumbExt);

    /** Step two: the bytes are in the bucket, record the reel. */
    @FormUrlEncoded
    @POST("reel/create/" + Config.SECURE_KEY + "/")
    Call<JsonObject> reelCreate(@Field("id") String userId, @Field("key") String key,
                                @Field("objectkey") String objectKey,
                                @Field("thumbkey") String thumbKey,
                                @Field("type") String type,
                                @Field("caption") String caption,
                                @Field("width") Integer width,
                                @Field("height") Integer height,
                                @Field("duration") Integer duration);

    @GET("stickers/")
    Call<JsonObject> list();

    @GET("slide/all/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<SlideApi>> slideAll();

    @FormUrlEncoded
    @POST("pack/add/download/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<Integer> addDownload(@Field("id")  Integer id);

    @FormUrlEncoded
    @POST("pack/delete/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> deletePack(@Field("user")  Integer user,@Field("key")  String key,@Field("pack")  Integer pack);

    @GET("device/{tkn}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> addDevice(@Path("tkn")  String tkn);

    @GET("install/add/{id}/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> addInstall(@Path("id") String id);


    @GET("pack/all/{page}/{order}/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<List<PackApi>> packsAll(@Path("page") Integer page, @Path("order") String order);

    @GET("pack/by/id/{pack}/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<PackApi> packById(@Path("pack") Integer pack);

    @GET("pack/by/user/{page}/{order}/{user}/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<PackApi>> packsByUser(@Path("page") Integer page,@Path("order") String order, @Path("user") Integer user);

    @GET("pack/by/me/{page}/{user}/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<PackApi>> packsByMe(@Path("page") Integer page, @Path("user") Integer user);


    @GET("pack/by/category/{page}/{order}/{category}/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<List<PackApi>> packsByCategory(@Path("page") Integer page, @Path("order") String order, @Path("category") Integer category);

    @GET("pack/by/follow/{page}/{user}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<PackApi>> packsByFollwing(@Path("page") Integer page, @Path("user") Integer user);

    @GET("pack/by/query/{page}/{query}/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<PackApi>> packsByQuery(@Path("page") Integer page, @Path("query") String query);


    @GET("category/all/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<List<CategoryApi>> AllCategories();

    @GET("category/popular/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<List<CategoryApi>> PopularCategories();

    @GET("tags/all/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<TagApi>> TagList();

    @GET("user/followingstop/{user}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<UserApi>> getFollowingTop(@Path("user") Integer user);

    @GET("user/followers/{user}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<UserApi>> getFollowers(@Path("user") Integer user);

    @GET("user/followings/{user}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<List<UserApi>> getFollowing(@Path("user") Integer user);

    @GET("user/follow/{user}/{follower}/{key}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> follow(@Path("user") Integer user,@Path("follower") Integer follower,@Path("key") String key);


    @GET("rate/add/{user}/{pack}/{value}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> addRate(@Path("user")  String user,@Path("pack") Integer pack,@Path("value") float value);

    @GET("rate/get/{user}/{pack}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> getRate(@Path("user")  String user,@Path("pack") Integer pack);

    @Multipart
    @POST("pack/upload/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> uploadPack(@Part MultipartBody.Part file,@Part List<MultipartBody.Part> files, @Part("size") Integer size, @Part("id") String id, @Part("key") String key, @Part("name") String name, @Part("publisher") String publisher,@Part("email") String email,@Part("website") String website,@Part("privacy") String privacy,@Part("license") String license, @Part("categories") String categories);


    /**
     * Email and password sign in, used when the panel switches manual accounts on. The
     * address and the password go in the body: in the path they have to survive URL
     * encoding, and a password in a URL is written to the server's access log.
     */
    @FormUrlEncoded
    @POST("user/signin/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> login(@Field("username") String username, @Field("password") String password);

    @FormUrlEncoded
    @POST("user/register/"+ Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> register(@Field("name") String name, @Field("username") String username, @Field("password") String password, @Field("type") String type, @Field("image") String image);

    @FormUrlEncoded
    @POST("user/token/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> editToken(@Field("user") Integer user, @Field("key") String key, @Field("token_f") String token_f, @Field("name") String name);


    @GET("user/get/{user}/{me}/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> getUser(@Path("user") Integer user,@Path("me") Integer me);

    @FormUrlEncoded
    @POST("support/add/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> addSupport(@Field("email") String email, @Field("name") String name,
                                 @Field("message") String message,
                                 @Field("kind") String kind, @Field("target") Integer target);

    /**
     * Tells the panel what Play said about this device's subscription.
     *
     * <p>Sent at every launch, so the server keys on the purchase token: the same
     * purchase reported again is the same row, seen once more. An empty token with
     * state expired means the subscription this device used to have is gone.
     */
    @FormUrlEncoded
    @POST("subscription/report/"+Config.SECURE_KEY+"/"+Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> reportSubscription(@Field("device") String device,
                                         @Field("user") Integer user,
                                         @Field("state") String state,
                                         @Field("product") String product,
                                         @Field("token") String token,
                                         @Field("order") String order,
                                         @Field("started") Long started,
                                         @Field("renewing") Integer renewing,
                                         @Field("platform") String platform);

    @Multipart
    @POST("user/edit/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> editUser(@Part MultipartBody.Part file,@Part("user") Integer user,@Part("key") String key,@Part("name") String name,@Part("email") String email,@Part("facebook") String facebook,@Part("twitter") String twitter,@Part("instagram") String instagram);


    @GET("version/check/{code}/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> check(@Path("code") Integer code);

    @GET("user/check/{id}/{key}/"+ Config.SECURE_KEY+"/"+ Config.ITEM_PURCHASE_CODE+"/")
    Call<ApiResponse> checkUser(@Path("id") Integer id,@Path("key") String key);
}
