package com.wevlo.amohir;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WhatsAppBgService extends Service {

    private static final int SERVICE_NOTIF_ID = 1001;
    private static final int CALL_NOTIF_ID = 2002;
    private static final String CH_SERVICE = "wa_bg_service";
    private static final String CH_MESSAGES = "wa_messages";
    private static final String CH_CALLS = "wa_calls";
    public static final String ACTION_REJECT_CALL = "com.wevlo.amohir.REJECT_CALL";

    private FirebaseFirestore db;
    private String myPhone;
    private ListenerRegistration callListener, chatListener;
    private boolean chatsInitialized = false;
    private final Map<String, Long> lastNotifiedMsgMap = new HashMap<>();
    private Ringtone ringtone;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REJECT_CALL.equals(intent.getAction())) {
            String callDocId = intent.getStringExtra("callDocId");
            stopRingtone();
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(CALL_NOTIF_ID);
            if (callDocId != null && db != null) {
                db.collection("calls").document(callDocId).update("status", "ended");
            }
            return START_STICKY;
        }

        myPhone = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("my_phone", "");
        if (myPhone.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, openApp, PendingIntent.FLAG_IMMUTABLE);

        Notification bgNotif = new NotificationCompat.Builder(this, CH_SERVICE)
                .setContentTitle("WhatsApp Active")
                .setContentText("Ready for incoming calls & messages")
                .setSmallIcon(R.drawable.ic_chat)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(SERVICE_NOTIF_ID, bgNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        } else {
            startForeground(SERVICE_NOTIF_ID, bgNotif);
        }

        initFirebaseAndListen();
        return START_STICKY;
    }

    private void initFirebaseAndListen() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey("AIzaSyAmLJ5Vdd6Pqm-FrPfANvjy1TZkhG1mxns")
                    .setApplicationId("1:367706014019:web:f4e33a639f4573262baff6")
                    .setProjectId("good-865a2")
                    .setStorageBucket("good-865a2.firebasestorage.app")
                    .setGcmSenderId("367706014019")
                    .build();
            FirebaseApp.initializeApp(this, options);
        }
        db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously().addOnSuccessListener(r -> attachListeners());
        } else {
            attachListeners();
        }
    }

    @SuppressWarnings("unchecked")
    private void attachListeners() {
        if (callListener != null) callListener.remove();
        callListener = db.collection("calls").whereEqualTo("receiverId", myPhone)
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null) return;
                    DocumentSnapshot activeCall = null;
                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        String status = d.getString("status");
                        Long createdAt = d.getLong("createdAt");
                        boolean isFresh = createdAt == null || Math.abs(System.currentTimeMillis() - createdAt) < 180000;
                        if ("calling".equals(status) && isFresh) {
                            activeCall = d;
                            break;
                        }
                    }

                    NotificationManager nm = getSystemService(NotificationManager.class);
                    if (activeCall != null && !ChatActivity.isChatOpen) {
                        wakeCpuBriefly();
                        showIncomingCallNotification(activeCall);
                    } else {
                        stopRingtone();
                        if (nm != null) nm.cancel(CALL_NOTIF_ID);
                    }
                });

        if (chatListener != null) chatListener.remove();
        chatsInitialized = false;
        chatListener = db.collection("chats").whereArrayContains("participants", myPhone)
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null) return;

                    if (!chatsInitialized) {
                        for (DocumentSnapshot d : snaps.getDocuments()) {
                            Long lt = d.getLong("lastTimestamp");
                            if (lt != null) lastNotifiedMsgMap.put(d.getId(), lt);
                        }
                        chatsInitialized = true;
                        return;
                    }

                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        Long lastTime = d.getLong("lastTimestamp");
                        String lastMsg = d.getString("lastMessage");
                        String lastSenderId = d.getString("lastSenderId");
                        String lastSenderName = d.getString("lastSenderName");

                        if (lastTime == null || lastMsg == null || myPhone.equals(lastSenderId)) continue;
                        Long prevTime = lastNotifiedMsgMap.get(d.getId());

                        if (prevTime == null || !lastTime.equals(prevTime)) {
                            lastNotifiedMsgMap.put(d.getId(), lastTime);
                            if (!ChatActivity.isChatOpen) {
                                wakeCpuBriefly();
                                List<String> parts = (List<String>) d.get("participants");
                                String peerPhone = lastSenderId;
                                if (peerPhone == null && parts != null) {
                                    for (String p : parts) if (!myPhone.equals(p)) { peerPhone = p; break; }
                                }
                                if (peerPhone != null) {
                                    showMessageNotification(d.getId().hashCode(), peerPhone,
                                            lastSenderName != null ? lastSenderName : peerPhone, lastMsg);
                                }
                            }
                        }
                    }
                });
    }

    private void wakeCpuBriefly() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhatsApp::WakeLock");
                wl.acquire(10000L);
            }
        } catch (Exception ignored) {}
    }

    private void showIncomingCallNotification(DocumentSnapshot callDoc) {
        String callerId = callDoc.getString("callerId");
        String callerName = callDoc.getString("callerName");
        String type = callDoc.getString("type");
        if (callerName == null) callerName = callerId;

        Intent acceptIntent = new Intent(this, ChatActivity.class);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        acceptIntent.putExtra("peerUid", callerId);
        acceptIntent.putExtra("peerName", callerName);
        acceptIntent.putExtra("autoAcceptCallId", callDoc.getId());
        PendingIntent acceptPi = PendingIntent.getActivity(this, 10, acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent rejectIntent = new Intent(this, WhatsAppBgService.class);
        rejectIntent.setAction(ACTION_REJECT_CALL);
        rejectIntent.putExtra("callDocId", callDoc.getId());
        PendingIntent rejectPi = PendingIntent.getService(this, 11, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, CH_CALLS)
                .setSmallIcon(R.drawable.ic_calls)
                .setContentTitle(callerName)
                .setContentText("video".equals(type) ? "Incoming WhatsApp video call" : "Incoming WhatsApp voice call")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(true)
                .setFullScreenIntent(acceptPi, true)
                .setContentIntent(acceptPi)
                .addAction(R.drawable.ic_call_end, "Decline", rejectPi)
                .addAction(R.drawable.ic_calls, "Answer", acceptPi)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(CALL_NOTIF_ID, notif);
        playRingtone();
    }

    private void showMessageNotification(int notifId, String peerPhone, String senderName, String msgText) {
        Intent openIntent = new Intent(this, ChatActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra("peerUid", peerPhone);
        openIntent.putExtra("peerName", senderName);
        PendingIntent pi = PendingIntent.getActivity(this, notifId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, CH_MESSAGES)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(senderName)
                .setContentText(msgText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(notifId, notif);
    }

    private void playRingtone() {
        try {
            if (ringtone == null) ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone != null && !ringtone.isPlaying()) ringtone.play();
        } catch (Exception ignored) {}
    }

    private void stopRingtone() { try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {} }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel bgCh = new NotificationChannel(CH_SERVICE, "Background Connection", NotificationManager.IMPORTANCE_LOW);
            NotificationChannel msgCh = new NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH);
            msgCh.enableVibration(true);
            NotificationChannel callCh = new NotificationChannel(CH_CALLS, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH);
            callCh.enableVibration(true);
            callCh.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            nm.createNotificationChannel(bgCh);
            nm.createNotificationChannel(msgCh);
            nm.createNotificationChannel(callCh);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        String phone = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("my_phone", "");
        if (!phone.isEmpty()) {
            Intent restart = new Intent(getApplicationContext(), WhatsAppBgService.class);
            PendingIntent pi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? PendingIntent.getForegroundService(getApplicationContext(), 99, restart, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE)
                    : PendingIntent.getService(getApplicationContext(), 99, restart, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1500, pi);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRingtone();
        if (callListener != null) callListener.remove();
        if (chatListener != null) chatListener.remove();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}