package com.wevlo.amohir;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ScreenShareService extends Service {

    public static Runnable onServiceReady;
    private static final String CH_SCREEN = "wa_screen_share";
    private static final int NOTIF_ID = 3003;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH_SCREEN, "Screen Sharing", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notif = new NotificationCompat.Builder(this, CH_SCREEN)
                .setContentTitle("WhatsApp Screen Share")
                .setContentText("Sharing your screen in call")
                .setSmallIcon(R.drawable.ic_screen_share)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, notif);
        }

        if (onServiceReady != null) {
            Runnable r = onServiceReady;
            onServiceReady = null;
            r.run();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        onServiceReady = null;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
