package com.anish.expirydatereminder.messaging;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.anish.expirydatereminder.R;
import com.anish.expirydatereminder.SplashActivity;
import com.anish.expirydatereminder.constants.AppConstants;
import com.anish.expirydatereminder.db.ItemsDatabase;
import com.anish.expirydatereminder.db.NotificationDatabase;

import java.time.LocalDate;

public class WakeUpReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try (
                ItemsDatabase dbh = new ItemsDatabase(context);
                NotificationDatabase ndb = new NotificationDatabase(context);
        ) {

            long itemsExpiringInUnder14Days = dbh.getAllItems().stream()
                    .map(a -> LocalDate.of(a.getYear(), a.getMonth(), a.getDate()).minusDays(AppConstants.NOTIFICATION_THRESHOLD_DAYS))
                    .filter(date -> !LocalDate.now().isBefore(date))
                    .count();

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, AppConstants.NOTIFICATION_CHANNEL)
                    .setSmallIcon(R.drawable.logo_transparent_background)
                    .setContentTitle(AppConstants.APP_TITLE)
                    .setContentText("You have " + itemsExpiringInUnder14Days + " items expiring within 14 days!")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, new Intent(context, SplashActivity.class), PendingIntent.FLAG_IMMUTABLE);
            notificationBuilder.setContentIntent(contentIntent);

            if (ndb.getCurrentSetting() == 1
                    && itemsExpiringInUnder14Days > 0
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(AppConstants.NOTIFICATION_ID, notificationBuilder.build());
            }
        } catch (Exception e) {
            Log.w("Notification", "Error occurred " + e.getMessage());
        }
    }
}
