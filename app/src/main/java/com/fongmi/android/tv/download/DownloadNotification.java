package com.fongmi.android.tv.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.R;

/** 下载前台服务通知。 */
public final class DownloadNotification {

    public static final String CHANNEL_ID = "download_channel";
    public static final int NOTIFICATION_ID = 1001;

    private DownloadNotification() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "视频下载", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    public static Notification build(Context context, String title, String text, int progress, boolean indeterminate) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, Math.max(0, progress), indeterminate);
        PendingIntent intent = contentIntent(context);
        if (intent != null) builder.setContentIntent(intent);
        return builder.build();
    }

    private static PendingIntent contentIntent(Context context) {
        try {
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), "com.fongmi.android.tv.ui.activity.DownloadActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (context.getPackageManager().resolveActivity(intent, 0) == null) return null;
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            return PendingIntent.getActivity(context, 0, intent, flags);
        } catch (Exception e) {
            return null;
        }
    }
}
