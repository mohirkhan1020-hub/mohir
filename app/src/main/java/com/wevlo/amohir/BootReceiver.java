package com.wevlo.amohir;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            String myPhone = context.getSharedPreferences("DivineWA", Context.MODE_PRIVATE).getString("my_phone", "");
            if (!myPhone.isEmpty()) {
                ContextCompat.startForegroundService(context, new Intent(context, WhatsAppBgService.class));
            }
        }
    }
}