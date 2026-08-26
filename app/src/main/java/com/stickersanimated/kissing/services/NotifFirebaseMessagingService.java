package com.stickersanimated.kissing.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.stickersanimated.kissing.Manager.PrefManager;
import com.stickersanimated.kissing.R;
import com.stickersanimated.kissing.api.apiClient;
import com.stickersanimated.kissing.api.apiRest;
import com.stickersanimated.kissing.entity.ApiResponse;
import com.stickersanimated.kissing.utils.Notifications;
import com.stickersanimated.kissing.ui.CategoryActivity;
import com.stickersanimated.kissing.ui.HomeActivity;
import com.stickersanimated.kissing.ui.LoadActivity;
import com.stickersanimated.kissing.ui.UserActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.annotation.NonNull;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class NotifFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM Service";
    Bitmap bitmap;

    /**
     * FCM hands out a new token on a reinstall, a restore to a new device or a plain
     * refresh. The server only ever heard the token from the login screen, so a refreshed
     * device silently stopped receiving anything; it is sent again here.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        final PrefManager prefManager = new PrefManager(getApplicationContext());
        if (!"TRUE".equals(prefManager.getString("LOGGED"))) {
            // Signed out: the login flow sends the token itself.
            return;
        }
        final int userId;
        try {
            userId = Integer.parseInt(prefManager.getString("ID_USER"));
        } catch (NumberFormatException e) {
            return;
        }
        apiClient.getClient().create(apiRest.class)
                .editToken(userId, prefManager.getString("TOKEN_USER"), token,
                        prefManager.getString("NAME_USER"))
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call,
                                           @NonNull Response<ApiResponse> response) {
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.w(TAG, "Could not hand the refreshed token to the server", t);
                    }
                });
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        Log.v(TAG, remoteMessage.toString());

        String type = remoteMessage.getData().get("type");
        String id = remoteMessage.getData().get("id");
        if (type == null || id == null) {
            // A notification-only message, or one from a sender that does not use these
            // fields. Android posts those itself; there is nothing to build here.
            return;
        }
        String title = remoteMessage.getData().get("title");
        String image = remoteMessage.getData().get("image");
        String icon = remoteMessage.getData().get("icon");
        String message = remoteMessage.getData().get("message");

        PrefManager prf = new PrefManager(getApplicationContext());
        if (!"false".equals(prf.getString("notifications"))) {
            if (type.equals("pack")){
                sendNotification(
                        id,
                        title,
                        image,
                        icon,
                        message
                );
            }else if(type.equals("category")){
                String category_title = remoteMessage.getData().get("title_category");
                String category_image = remoteMessage.getData().get("image_category");

                sendNotificationCategory(
                        id,
                        title,
                        image,
                        icon,
                        message,
                        category_title,
                        category_image);
            }else if(type.equals("user")){
                String name_user = remoteMessage.getData().get("name_user");
                String image_user = remoteMessage.getData().get("image_user");

                sendNotificationUser(
                        id,
                        title,
                        image,
                        icon,
                        message,
                        name_user,
                        image_user);
            }  else if (type.equals("link")){
                String link = remoteMessage.getData().get("link");

                sendNotificationUrl(
                        id,
                        title,
                        image,
                        icon,
                        message,
                        link
                );
            }
        }


    }
    private void sendNotificationCategory(
            String id,
            String title,
            String imageUri,
            String iconUrl,
            String message,
            String category_title,
            String category_image
    ) {


        Bitmap image = getBitmapfromUrl(imageUri);
        Bitmap icon = getBitmapfromUrl(iconUrl);
        Intent intent = new Intent(this, CategoryActivity.class);
        intent.setAction(Long.toString(System.currentTimeMillis()));


        intent.putExtra("id", notificationId(id));
        intent.putExtra("title",category_title);
        intent.putExtra("image",category_image);
        intent.putExtra("from", "notification");



        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        int NOTIFICATION_ID = notificationId(id);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final String CHANNEL_ID = Notifications.CHANNEL_ID;
        Notifications.ensureChannel(getApplicationContext());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.parseColor("#25D366"))
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message);

        if (icon!=null){
            builder.setLargeIcon(icon);
        }else{
            builder.setLargeIcon(largeIcon);
        }
        if (image!=null){
            builder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image));
        }


        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        stackBuilder.addParentStack(HomeActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent;
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S){
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }else {
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        builder.setContentIntent(resultPendingIntent);
        notificationManager.notify(NOTIFICATION_ID, builder.build());


    }
    private void sendNotificationUrl(
            String id,
            String title,
            String imageUri,
            String iconUrl,
            String message,
            String url

    ) {


        Bitmap image = getBitmapfromUrl(imageUri);
        Bitmap icon = getBitmapfromUrl(iconUrl);


        Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
        notificationIntent.setData(Uri.parse(url));
        //PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        int NOTIFICATION_ID = notificationId(id);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final String CHANNEL_ID = Notifications.CHANNEL_ID;
        Notifications.ensureChannel(getApplicationContext());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.parseColor("#25D366"))
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message);

        if (icon!=null){
            builder.setLargeIcon(icon);

        }else{
            builder.setLargeIcon(largeIcon);
        }
        if (image!=null){
            builder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image));
        }


        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        stackBuilder.addParentStack(HomeActivity.class);
        stackBuilder.addNextIntent(notificationIntent);
        PendingIntent resultPendingIntent;
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S){
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }else {
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        builder.setContentIntent(resultPendingIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());


    }
    private void sendNotification(
            String id,
            String title,
            String imageUri,
            String iconUrl,
            String message
    ) {


        Bitmap image = getBitmapfromUrl(imageUri);
        Bitmap icon = getBitmapfromUrl(iconUrl);






        Intent intent = new Intent(this, LoadActivity.class);
        intent.setAction(Long.toString(System.currentTimeMillis()));

        intent.putExtra("id", notificationId(id));
        intent.putExtra("from", "notification");

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        int NOTIFICATION_ID = notificationId(id);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final String CHANNEL_ID = Notifications.CHANNEL_ID;
        Notifications.ensureChannel(getApplicationContext());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.parseColor("#25D366"))
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message);

        if (icon!=null){
            builder.setLargeIcon(icon);

        }else{
            builder.setLargeIcon(largeIcon);
        }
        if (image!=null){
            builder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image));
        }


        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        stackBuilder.addParentStack(HomeActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent;
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S){
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }else {
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        builder.setContentIntent(resultPendingIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());


    }
    private void sendNotificationUser(
            String id,
            String title,
            String imageUri,
            String iconUrl,
            String message,
            String name_user,
            String image_user
    ) {


        Bitmap image = getBitmapfromUrl(imageUri);
        Bitmap icon = getBitmapfromUrl(iconUrl);
        Intent intent = new Intent(this, UserActivity.class);
        intent.setAction(Long.toString(System.currentTimeMillis()));


        intent.putExtra("id", notificationId(id));
        intent.putExtra("name",name_user);
        intent.putExtra("image",image_user);
        intent.putExtra("from", "notification");




        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        int NOTIFICATION_ID = notificationId(id);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final String CHANNEL_ID = Notifications.CHANNEL_ID;
        Notifications.ensureChannel(getApplicationContext());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.parseColor("#25D366"))
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message);

        if (icon!=null){
            builder.setLargeIcon(icon);

        }else{
            builder.setLargeIcon(largeIcon);
        }
        if (image!=null){
            builder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image));
        }


        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        stackBuilder.addParentStack(HomeActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent;
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S){
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }else {
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        builder.setContentIntent(resultPendingIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());

    }
    /*
     *To get a Bitmap image from the URL received
     * */
    public Bitmap getBitmapfromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            return bitmap;

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

}