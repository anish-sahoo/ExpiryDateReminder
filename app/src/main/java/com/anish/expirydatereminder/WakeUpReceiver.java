package com.anish.expirydatereminder;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.time.LocalDate;

public class WakeUpReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DatabaseHandler dbh = new DatabaseHandler(context);
        NotificationDatabase ndb = new NotificationDatabase(context);
        int z=0;
        for (ItemModel a:dbh.getAllItems()) {
            LocalDate ld = LocalDate.of(a.getYear(), a.getMonth(), a.getDate());
            LocalDate ld2 = ld.minusDays(14);
            LocalDate today = LocalDate.now();

            if (today.isAfter(ld2) || today.isEqual(ld2)) {
                z++;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "edr_channel_1")
                .setSmallIcon(R.drawable.logo_transparent_background)
                .setContentTitle("Expiry Date Reminder")
                .setContentText("You have "+z+" items expiring within 14 days!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,new Intent(context, SplashActivity.class), PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(contentIntent);

        if(ndb.getCurrentSetting()==1 && z>0) {
            notificationManager.notify(13, builder.build());
        }
    }
}
