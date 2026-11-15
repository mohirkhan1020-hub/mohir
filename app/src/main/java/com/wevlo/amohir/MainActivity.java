package com.wevlo.amohir;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String[] EMOJIS = {
            "😀","🥰","😘","😍","😊","🤩","🌸","💖","🎀","🥺",
            "✨","🐱","🐰","👍","👏","🙌","🙏","🎉","🎂","⭐",
            "🍓","🍭","🍫","🥳","🌹"
    };

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String myName;

    private TextView tvHeaderName, tvCallerName, tvCallType, tvActiveTarget, tvCallTimer;
    private EditText etMessage;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private final List<MessageModel> messageList = new ArrayList<>();

    private FrameLayout incomingOverlay, callOverlay;
    private LinearLayout callTopBar, callBottomBar;
    private Button btnMaximize, btnMic, btnCam, btnSpeaker;
    private SurfaceViewRenderer remoteVideoView, localVideoView;

    private EglBase eglBase;
    private WebRTCManager webRTCManager;
    private AudioManager audioManager;

    private String activeCallType = "video";
    private boolean isMuted = false, isCamOff = false, isSpeakerOn = true;
    private boolean hasRemoteSdp = false;
    private boolean isCallerCurrent = false;
    private long callStartTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private ListenerRegistration callDocListener, candidatesListener;
    private Uri cameraImageUri;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {});

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) processAndSendImage(uri);
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && cameraImageUri != null) {
                    processAndSendImage(cameraImageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFirebase();
        initViews();
        initWebRTCViews();
        setupDraggableLocalView();
        initUserAndAuth();
    }

    private void initFirebase() {
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
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    private void initViews() {
        tvHeaderName = findViewById(R.id.tvHeaderName);
        etMessage = findViewById(R.id.etMessage);
        rvMessages = findViewById(R.id.rvMessages);

        incomingOverlay = findViewById(R.id.incomingOverlay);
        tvCallerName = findViewById(R.id.tvCallerName);
        tvCallType = findViewById(R.id.tvCallType);

        callOverlay = findViewById(R.id.callOverlay);
        callTopBar = findViewById(R.id.callTopBar);
        callBottomBar = findViewById(R.id.callBottomBar);
        tvActiveTarget = findViewById(R.id.tvActiveTarget);
        tvCallTimer = findViewById(R.id.tvCallTimer);
        btnMaximize = findViewById(R.id.btnMaximize);
        btnMic = findViewById(R.id.btnMic);
        btnCam = findViewById(R.id.btnCam);
        btnSpeaker = findViewById(R.id.btnSpeaker);

        adapter = new MessageAdapter(messageList, null);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        findViewById(R.id.btnProfile).setOnClickListener(v -> showNameDialog());
        findViewById(R.id.btnHelp).setOnClickListener(v -> showHelpDialog());
        findViewById(R.id.btnSend).setOnClickListener(v -> sendTextMessage());
        findViewById(R.id.btnEmoji).setOnClickListener(v -> showEmojiDialog());
        findViewById(R.id.btnAttach).setOnClickListener(v -> showAttachDialog());

        findViewById(R.id.btnAudioCall).setOnClickListener(v -> checkPermissionsAndStartCall("audio"));
        findViewById(R.id.btnVideoCall).setOnClickListener(v -> checkPermissionsAndStartCall("video"));

        findViewById(R.id.btnSwitchCam).setOnClickListener(v -> {
            if (webRTCManager != null) webRTCManager.switchCamera();
        });
        findViewById(R.id.btnHangup).setOnClickListener(v -> hangupCall(true));
        findViewById(R.id.btnMinimize).setOnClickListener(v -> minimizeCallUI(true));
        btnMaximize.setOnClickListener(v -> minimizeCallUI(false));

        btnMic.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (webRTCManager != null) webRTCManager.setMicEnabled(!isMuted);
            btnMic.setText(isMuted ? "🔇" : "🎤");
        });

        btnCam.setOnClickListener(v -> {
            if ("audio".equals(activeCallType)) return;
            isCamOff = !isCamOff;
            if (webRTCManager != null) webRTCManager.setCamEnabled(!isCamOff);
            btnCam.setText(isCamOff ? "🚫" : "📷");
        });

        btnSpeaker.setOnClickListener(v -> {
            isSpeakerOn = !isSpeakerOn;
            if (audioManager != null) audioManager.setSpeakerphoneOn(isSpeakerOn);
            btnSpeaker.setText(isSpeakerOn ? "🔊" : "🔈");
        });
    }

    private void initWebRTCViews() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        eglBase = EglBase.create();

        remoteVideoView = findViewById(R.id.remoteVideoView);
        remoteVideoView.init(eglBase.getEglBaseContext(), null);
        remoteVideoView.setEnableHardwareScaler(true);

        localVideoView = findViewById(R.id.localVideoView);
        localVideoView.init(eglBase.getEglBaseContext(), null);
        localVideoView.setMirror(true);
        localVideoView.setZOrderMediaOverlay(true);

        webRTCManager = new WebRTCManager(this, eglBase, new WebRTCManager.Callback() {
            @Override
            public void onIceCandidateGenerated(IceCandidate candidate) {
                String col = isCallerCurrent ? "callerCandidates" : "calleeCandidates";
                Map<String, Object> map = new HashMap<>();
                map.put("candidate", candidate.sdp);
                map.put("sdpMid", candidate.sdpMid);
                map.put("sdpMLineIndex", candidate.sdpMLineIndex);
                db.collection("calls").document("live_channel").collection(col).add(map);
            }

            @Override
            public void onRemoteStreamAdded(VideoTrack videoTrack) {
                runOnUiThread(() -> videoTrack.addSink(remoteVideoView));
            }

            @Override
            public void onConnectionStateChanged(PeerConnection.PeerConnectionState state) {
                runOnUiThread(() -> {
                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        Toast.makeText(MainActivity.this, "✅ সংযুক্ত হয়েছে", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggableLocalView() {
        localVideoView.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        view.setX(event.getRawX() + dX);
                        view.setY(event.getRawY() + dY);
                        return true;
                }
                return false;
            }
        });
    }

    private void initUserAndAuth() {
        SharedPreferences prefs = getSharedPreferences("DivinePrefs", MODE_PRIVATE);
        myName = prefs.getString("user_name", "মিষ্টি ফ্রেন্ড " + (int) (100 + Math.random() * 900));
        prefs.edit().putString("user_name", myName).apply();
        tvHeaderName.setText(myName + " ✏️");

        auth.signInAnonymously().addOnSuccessListener(authResult -> {
            currentUser = authResult.getUser();
            if (currentUser != null) {
                adapter.setCurrentUserId(currentUser.getUid());
                listenMessages();
                listenIncomingCalls();
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Auth ব্যর্থ: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void listenMessages() {
        db.collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(60)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            MessageModel m = dc.getDocument().toObject(MessageModel.class);
                            m.setId(dc.getDocument().getId());
                            messageList.add(m);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            rvMessages.scrollToPosition(messageList.size() - 1);
                        }
                    }
                });
    }

    private void sendTextMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || currentUser == null) return;
        etMessage.setText("");
        sendMessageToFirestore(text, "text", null);
    }

    private void sendMessageToFirestore(String text, String type, String mediaUrl) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("text", text);
        msg.put("type", type);
        msg.put("mediaUrl", mediaUrl);
        msg.put("senderId", currentUser.getUid());
        msg.put("senderName", myName);
        msg.put("createdAt", FieldValue.serverTimestamp());
        db.collection("messages").add(msg);
    }

    private void showAttachDialog() {
        String[] options = {"📸 ক্যামেরা", "🖼️ গ্যালারি"};
        new AlertDialog.Builder(this)
                .setTitle("ছবি পাঠান")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
                            return;
                        }
                        try {
                            File file = File.createTempFile("cam_", ".jpg", getCacheDir());
                            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                            cameraLauncher.launch(cameraImageUri);
                        } catch (Exception e) {
                            Toast.makeText(this, "ক্যামেরা খোলা যায়নি", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                }).show();
    }

    private void processAndSendImage(Uri uri) {
        Toast.makeText(this, "ছবি আপলোড হচ্ছে...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap original = BitmapFactory.decodeStream(is);
                if (original == null) return;

                int w = original.getWidth(), h = original.getHeight(), max = 500;
                if (w > h && w > max) {
                    h = Math.round((float) h * max / w);
                    w = max;
                } else if (h > max) {
                    w = Math.round((float) w * max / h);
                    h = max;
                }
                Bitmap resized = Bitmap.createScaledBitmap(original, w, h, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                String base64 = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                runOnUiThread(() -> sendMessageToFirestore("", "image", base64));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "ছবি প্রসেস ব্যর্থ", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showEmojiDialog() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(24, 24, 24, 24);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ইমোজি বেছে নিন").setView(grid).create();

        for (String em : EMOJIS) {
            TextView tv = new TextView(this);
            tv.setText(em);
            tv.setTextSize(24);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(16, 16, 16, 16);
            tv.setOnClickListener(v -> {
                etMessage.append(em);
                dialog.dismiss();
            });
            grid.addView(tv);
        }
        dialog.show();
    }

    private void showNameDialog() {
        EditText input = new EditText(this);
        input.setText(myName);
        new AlertDialog.Builder(this)
                .setTitle("🌸 আপনার মিষ্টি নাম লিখুন")
                .setView(input)
                .setPositiveButton("সেভ করুন ✨", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (!n.isEmpty()) {
                        myName = n;
                        getSharedPreferences("DivinePrefs", MODE_PRIVATE).edit().putString("user_name", myName).apply();
                        tvHeaderName.setText(myName + " ✏️");
                        Toast.makeText(this, "🌸 নাম আপডেট হয়েছে!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("💡 ইউজার হেল্প")
                .setMessage("• ক্যামেরা সুইচ: 🔄 বাটনে ক্লিক করে ব্যাক ক্যামেরা ব্যবহার করুন।\n• সেলফ ভিউ: আপনার ভিডিও বক্সটি ড্র্যাগ করে সরাতে পারবেন।\n• PiP মোড: 🗗 বাটনে ক্লিক করে কল মিনিমাইজ করুন।")
                .setPositiveButton("বুঝেছি ✨", null)
                .show();
    }

    private void checkPermissionsAndStartCall(String type) {
        boolean hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!hasCam || !hasMic) {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
            Toast.makeText(this, "অনুগ্রহ করে ক্যামেরা ও মাইক পারমিশন দিন", Toast.LENGTH_SHORT).show();
            return;
        }
        startCall(type);
    }

    @SuppressWarnings("unchecked")
    private void startCall(String type) {
        if (currentUser == null) return;
        isCallerCurrent = true;
        activeCallType = type;
        hasRemoteSdp = false;

        setupCallUI(type, "কল যাচ্ছে...");
        clearOldCandidates(() -> {
            webRTCManager.startLocalMedia("video".equals(type), localVideoView);
            webRTCManager.createPeerConnection();

            webRTCManager.createOffer(offer -> {
                Map<String, Object> offerMap = new HashMap<>();
                offerMap.put("type", offer.type.canonicalForm());
                offerMap.put("sdp", offer.description);

                Map<String, Object> callData = new HashMap<>();
                callData.put("callerId", currentUser.getUid());
                callData.put("callerName", myName);
                callData.put("type", type);
                callData.put("status", "calling");
                callData.put("offer", offerMap);
                callData.put("createdAt", System.currentTimeMillis());

                DocumentReference callDoc = db.collection("calls").document("live_channel");
                callDoc.set(callData);

                if (callDocListener != null) callDocListener.remove();
                callDocListener = callDoc.addSnapshotListener((snap, err) -> {
                    if (snap == null || !snap.exists()) return;
                    String status = snap.getString("status");
                    if ("connected".equals(status) && !hasRemoteSdp) {
                        Map<String, Object> ans = (Map<String, Object>) snap.get("answer");
                        if (ans != null) {
                            hasRemoteSdp = true;
                            webRTCManager.setRemoteDescription((String) ans.get("type"), (String) ans.get("sdp"));
                            startCallTimer();
                        }
                    } else if ("ended".equals(status)) {
                        hangupCall(false);
                    }
                });

                listenCandidates("calleeCandidates");
            });
        });
    }

    private void listenIncomingCalls() {
        db.collection("calls").document("live_channel").addSnapshotListener((snap, err) -> {
            if (snap == null || !snap.exists() || currentUser == null) return;
            String status = snap.getString("status");
            String callerId = snap.getString("callerId");

            if ("calling".equals(status) && !currentUser.getUid().equals(callerId)) {
                String callerName = snap.getString("callerName");
                String type = snap.getString("type");
                incomingOverlay.setVisibility(View.VISIBLE);
                tvCallerName.setText(callerName != null ? callerName : "ফ্রেন্ড");
                tvCallType.setText("video".equals(type) ? "ভিডিও কল করছেন..." : "অডিও কল করছেন...");

                findViewById(R.id.btnAcceptCall).setOnClickListener(v -> acceptIncomingCall(snap));
                findViewById(R.id.btnRejectCall).setOnClickListener(v -> {
                    incomingOverlay.setVisibility(View.GONE);
                    db.collection("calls").document("live_channel").update("status", "ended");
                });
            } else if ("ended".equals(status)) {
                incomingOverlay.setVisibility(View.GONE);
                if (callOverlay.getVisibility() == View.VISIBLE) {
                    hangupCall(false);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void acceptIncomingCall(DocumentSnapshot snap) {
        boolean hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!hasCam || !hasMic) {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
            return;
        }

        incomingOverlay.setVisibility(View.GONE);
        isCallerCurrent = false;
        activeCallType = snap.getString("type");
        if (activeCallType == null) activeCallType = "video";

        setupCallUI(activeCallType, snap.getString("callerName"));
        webRTCManager.startLocalMedia("video".equals(activeCallType), localVideoView);
        webRTCManager.createPeerConnection();

        Map<String, Object> offer = (Map<String, Object>) snap.get("offer");
        if (offer != null) {
            webRTCManager.setRemoteDescription((String) offer.get("type"), (String) offer.get("sdp"));
            webRTCManager.createAnswer(answer -> {
                Map<String, Object> ansMap = new HashMap<>();
                ansMap.put("type", answer.type.canonicalForm());
                ansMap.put("sdp", answer.description);

                Map<String, Object> updates = new HashMap<>();
                updates.put("answer", ansMap);
                updates.put("status", "connected");
                db.collection("calls").document("live_channel").update(updates);

                listenCandidates("callerCandidates");
                startCallTimer();
            });
        }
    }

    private void listenCandidates(String collectionName) {
        if (candidatesListener != null) candidatesListener.remove();
        candidatesListener = db.collection("calls").document("live_channel")
                .collection(collectionName)
                .addSnapshotListener((snapshots, err) -> {
                    if (snapshots == null) return;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String cand = dc.getDocument().getString("candidate");
                            String sdpMid = dc.getDocument().getString("sdpMid");
                            Object idxObj = dc.getDocument().get("sdpMLineIndex");
                            int idx = idxObj instanceof Number ? ((Number) idxObj).intValue() : 0;
                            if (cand != null) {
                                webRTCManager.addIceCandidate(sdpMid, idx, cand);
                            }
                        }
                    }
                });
    }

    private void clearOldCandidates(Runnable onDone) {
        DocumentReference callDoc = db.collection("calls").document("live_channel");
        callDoc.collection("callerCandidates").get().addOnSuccessListener(s1 -> {
            for (DocumentSnapshot d : s1.getDocuments()) d.getReference().delete();
            callDoc.collection("calleeCandidates").get().addOnSuccessListener(s2 -> {
                for (DocumentSnapshot d : s2.getDocuments()) d.getReference().delete();
                onDone.run();
            });
        });
    }

    private void setupCallUI(String type, String targetName) {
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);
        isSpeakerOn = true;
        isMuted = false;
        isCamOff = false;

        minimizeCallUI(false);
        callOverlay.setVisibility(View.VISIBLE);
        tvActiveTarget.setText(targetName != null ? targetName : "ফ্রেন্ড");
        tvCallTimer.setText("সংযুক্ত হচ্ছে... 🌸");

        int vis = "video".equals(type) ? View.VISIBLE : View.GONE;
        localVideoView.setVisibility(vis);
        remoteVideoView.setVisibility(vis);
    }

    private void minimizeCallUI(boolean minimize) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) callOverlay.getLayoutParams();
        if (minimize) {
            params.width = dpToPx(150);
            params.height = dpToPx(210);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.setMargins(0, 0, dpToPx(16), dpToPx(80));
            callTopBar.setVisibility(View.GONE);
            callBottomBar.setVisibility(View.GONE);
            btnMaximize.setVisibility(View.VISIBLE);
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.NO_GRAVITY;
            params.setMargins(0, 0, 0, 0);
            callTopBar.setVisibility(View.VISIBLE);
            callBottomBar.setVisibility(View.VISIBLE);
            btnMaximize.setVisibility(View.GONE);
        }
        callOverlay.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void startCallTimer() {
        callStartTime = System.currentTimeMillis();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long sec = (System.currentTimeMillis() - callStartTime) / 1000;
                tvCallTimer.setText(String.format(Locale.getDefault(), "কথা চলছে: %02d:%02d", sec / 60, sec % 60));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void hangupCall(boolean notifyFirebase) {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (callDocListener != null) { callDocListener.remove(); callDocListener = null; }
        if (candidatesListener != null) { candidatesListener.remove(); candidatesListener = null; }

        webRTCManager.endCall();
        localVideoView.clearImage();
        remoteVideoView.clearImage();
        callOverlay.setVisibility(View.GONE);
        audioManager.setMode(AudioManager.MODE_NORMAL);

        if (notifyFirebase) {
            db.collection("calls").document("live_channel").update("status", "ended");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (webRTCManager != null) webRTCManager.destroyFactory();
        if (localVideoView != null) localVideoView.release();
        if (remoteVideoView != null) remoteVideoView.release();
        if (eglBase != null) eglBase.release();
    }
}