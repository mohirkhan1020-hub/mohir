package com.wevlo.amohir;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    public static boolean isChatOpen = false;
    private static final String[] EMOJIS = {
            "😀","🥰","😘","😍","😊","🤩","🌸","💖","🎀","🥺",
            "✨","🐱","🐰","👍","👏","🙌","🙏","🎉","🎂","⭐"
    };
    private static final String[] REACTIONS = {"❤️", "😂", "👍", "😮", "😢", "🙏"};

    private FirebaseFirestore db;
    private String myUid, myName, peerUid, peerName, chatId, callDocId;
    private boolean isGlobal;

    private TextView tvChatName, tvChatStatus, tvChatAvatar, tvReplySender, tvReplyText, tvRecordTime;
    private TextView tvCallerName, tvCallType, tvActiveTarget, tvCallTimer;
    private ImageView ivChatAvatar;
    private View viewChatOnline;
    private EditText etMessage;
    private ImageButton btnSendOrMic, btnMaximize, btnMic, btnCam, btnSpeaker;
    private LinearLayout replyPreviewLayout, recordingBar, inputRowLayout, callTopBar, callBottomBar, audioCallAvatarBox;
    private FrameLayout incomingOverlay, callOverlay;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private final List<MessageModel> messageList = new ArrayList<>();

    private MessageModel replyingToMsg = null;
    private MediaRecorder recorder;
    private File voiceFile;
    private long recordStartTime = 0;
    private final Handler recordHandler = new Handler(Looper.getMainLooper());
    private Runnable recordRunnable;

    private SurfaceViewRenderer remoteVideoView, localVideoView;
    private EglBase eglBase;
    private WebRTCManager webRTCManager;
    private AudioManager audioManager;
    private Ringtone ringtone;

    private String activeCallType = "video";
    private boolean isMuted = false, isCamOff = false, isSpeakerOn = true;
    private boolean hasRemoteSdp = false, isCallerCurrent = false, isAutoAccepting = false;
    private long callStartTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private ListenerRegistration msgListener, peerListener, callListener, incomingListener, candListener;
    private Uri cameraImageUri;
    private Runnable pendingPermissionAction = null;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                if (hasCam && hasMic && pendingPermissionAction != null) {
                    Runnable action = pendingPermissionAction;
                    pendingPermissionAction = null;
                    action.run();
                } else if (pendingPermissionAction != null) {
                    pendingPermissionAction = null;
                    Toast.makeText(this, "কলের জন্য ক্যামেরা ও মাইক পারমিশন আবশ্যক", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) processAndSendImage(uri); });
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && cameraImageUri != null) processAndSendImage(cameraImageUri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) { finish(); return; }

        myUid = currentUser.getUid();
        myName = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("name", "User");
        isGlobal = getIntent().getBooleanExtra("isGlobal", false);
        peerUid = getIntent().getStringExtra("peerUid");
        peerName = getIntent().getStringExtra("peerName");

        if (isGlobal || peerUid == null) {
            isGlobal = true;
            chatId = "global_room";
            callDocId = "live_channel";
            peerUid = "global_room";
            peerName = "Global Community";
        } else {
            chatId = myUid.compareTo(peerUid) < 0 ? myUid + "_" + peerUid : peerUid + "_" + myUid;
            callDocId = "call_" + chatId;
        }

        initViews();
        initWebRTCViews();
        setupDraggableLocalView();
        listenMessages();
        listenPeerStatus();

        String autoAcceptId = getIntent().getStringExtra("autoAcceptCallId");
        if (autoAcceptId != null) {
            isAutoAccepting = true;
            db.collection("calls").document(autoAcceptId).get().addOnSuccessListener(snap -> {
                if (snap.exists() && "calling".equals(snap.getString("status"))) {
                    acceptIncomingCallWithPermission(snap);
                } else {
                    isAutoAccepting = false;
                }
            });
        }
        listenIncomingCalls();
    }

    private void initViews() {
        tvChatName = findViewById(R.id.tvChatName);
        tvChatStatus = findViewById(R.id.tvChatStatus);
        tvChatAvatar = findViewById(R.id.tvChatAvatar);
        ivChatAvatar = findViewById(R.id.ivChatAvatar);
        viewChatOnline = findViewById(R.id.viewChatOnline);
        etMessage = findViewById(R.id.etMessage);
        btnSendOrMic = findViewById(R.id.btnSendOrMic);
        rvMessages = findViewById(R.id.rvMessages);

        replyPreviewLayout = findViewById(R.id.replyPreviewLayout);
        tvReplySender = findViewById(R.id.tvReplySender);
        tvReplyText = findViewById(R.id.tvReplyText);

        recordingBar = findViewById(R.id.recordingBar);
        tvRecordTime = findViewById(R.id.tvRecordTime);
        inputRowLayout = findViewById(R.id.inputRowLayout);

        incomingOverlay = findViewById(R.id.incomingOverlay);
        tvCallerName = findViewById(R.id.tvCallerName);
        tvCallType = findViewById(R.id.tvCallType);

        callOverlay = findViewById(R.id.callOverlay);
        callTopBar = findViewById(R.id.callTopBar);
        callBottomBar = findViewById(R.id.callBottomBar);
        audioCallAvatarBox = findViewById(R.id.audioCallAvatarBox);
        tvActiveTarget = findViewById(R.id.tvActiveTarget);
        tvCallTimer = findViewById(R.id.tvCallTimer);
        btnMaximize = findViewById(R.id.btnMaximize);
        btnMic = findViewById(R.id.btnMic);
        btnCam = findViewById(R.id.btnCam);
        btnSpeaker = findViewById(R.id.btnSpeaker);

        tvChatName.setText(peerName != null ? peerName : "Chat");
        String peerAvatar = getIntent().getStringExtra("peerAvatar");
        if (isGlobal) {
            tvChatAvatar.setText("🌍");
            tvChatStatus.setText("tap for group chat");
            viewChatOnline.setVisibility(View.VISIBLE);
        } else if (peerAvatar != null && !peerAvatar.isEmpty()) {
            try {
                byte[] b = Base64.decode(peerAvatar, Base64.DEFAULT);
                ivChatAvatar.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                ivChatAvatar.setVisibility(View.VISIBLE);
                tvChatAvatar.setVisibility(View.GONE);
            } catch (Exception ignored) {}
        } else {
            tvChatAvatar.setText(peerName != null && !peerName.isEmpty() ? peerName.substring(0, 1).toUpperCase() : "👤");
        }

        adapter = new MessageAdapter(messageList, myUid, isGlobal, this::showMessageOptionsDialog);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getBindingAdapterPosition();
                if (pos >= 0 && pos < messageList.size()) triggerReply(messageList.get(pos));
                adapter.notifyItemChanged(pos);
            }
        }).attachToRecyclerView(rvMessages);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelReply).setOnClickListener(v -> clearReply());
        findViewById(R.id.btnEmoji).setOnClickListener(v -> showEmojiDialog());
        findViewById(R.id.btnAttach).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        findViewById(R.id.btnCamQuick).setOnClickListener(v -> openCameraForPhoto());
        findViewById(R.id.btnCancelRecord).setOnClickListener(v -> stopRecording(false));
        findViewById(R.id.btnSendRecord).setOnClickListener(v -> stopRecording(true));

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hasText = s.toString().trim().length() > 0;
                btnSendOrMic.setImageResource(hasText ? R.drawable.ic_send : R.drawable.ic_mic);
                if (!isGlobal) db.collection("users").document(myUid).update("typingTo", hasText ? peerUid : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnSendOrMic.setOnClickListener(v -> {
            String txt = etMessage.getText().toString().trim();
            if (!txt.isEmpty()) { etMessage.setText(""); sendMessage(txt, "text", null, null); }
            else startVoiceRecording();
        });

        findViewById(R.id.btnAudioCall).setOnClickListener(v -> checkPermissionsAndStartCall("audio"));
        findViewById(R.id.btnVideoCall).setOnClickListener(v -> checkPermissionsAndStartCall("video"));
        findViewById(R.id.btnSwitchCam).setOnClickListener(v -> { if (webRTCManager != null) webRTCManager.switchCamera(); });
        findViewById(R.id.btnHangup).setOnClickListener(v -> hangupCall(true));
        findViewById(R.id.btnMinimize).setOnClickListener(v -> minimizeCallUI(true));
        btnMaximize.setOnClickListener(v -> minimizeCallUI(false));

        btnMic.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (webRTCManager != null) webRTCManager.setMicEnabled(!isMuted);
            btnMic.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
            btnMic.setAlpha(isMuted ? 0.5f : 1.0f);
        });
        btnCam.setOnClickListener(v -> {
            if ("audio".equals(activeCallType)) return;
            isCamOff = !isCamOff;
            if (webRTCManager != null) webRTCManager.setCamEnabled(!isCamOff);
            btnCam.setAlpha(isCamOff ? 0.45f : 1.0f);
        });
        btnSpeaker.setOnClickListener(v -> {
            isSpeakerOn = !isSpeakerOn;
            if (audioManager != null) audioManager.setSpeakerphoneOn(isSpeakerOn);
            btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.45f);
        });
    }

    private void triggerReply(MessageModel m) {
        replyingToMsg = m;
        replyPreviewLayout.setVisibility(View.VISIBLE);
        tvReplySender.setText(m.getSenderName() != null ? m.getSenderName() : "Message");
        String q = "image".equals(m.getType()) ? "📷 Photo" : ("voice".equals(m.getType()) ? "🎤 Voice Note" : m.getText());
        tvReplyText.setText(q);
    }

    private void clearReply() {
        replyingToMsg = null;
        replyPreviewLayout.setVisibility(View.GONE);
    }

    private void listenMessages() {
        msgListener = db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING).limitToLast(80)
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null) return;
                    for (DocumentChange dc : snaps.getDocumentChanges()) {
                        MessageModel m = dc.getDocument().toObject(MessageModel.class);
                        m.setId(dc.getDocument().getId());
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            messageList.add(m);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            rvMessages.scrollToPosition(messageList.size() - 1);
                            if (!myUid.equals(m.getSenderId()) && !m.isSeen()) {
                                dc.getDocument().getReference().update("seen", true);
                            }
                        } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                            for (int i = 0; i < messageList.size(); i++) {
                                if (messageList.get(i).getId().equals(m.getId())) {
                                    messageList.set(i, m);
                                    adapter.notifyItemChanged(i);
                                    break;
                                }
                            }
                        } else if (dc.getType() == DocumentChange.Type.REMOVED) {
                            for (int i = 0; i < messageList.size(); i++) {
                                if (messageList.get(i).getId().equals(m.getId())) {
                                    messageList.remove(i);
                                    adapter.notifyItemRemoved(i);
                                    break;
                                }
                            }
                        }
                    }
                });
    }

    private void listenPeerStatus() {
        if (isGlobal || peerUid == null) return;
        peerListener = db.collection("users").document(peerUid).addSnapshotListener((snap, err) -> {
            if (snap == null || !snap.exists()) return;
            boolean isOnline = Boolean.TRUE.equals(snap.getBoolean("online"));
            String typingTo = snap.getString("typingTo");
            Long lastSeen = snap.getLong("lastSeen");

            viewChatOnline.setVisibility(isOnline ? View.VISIBLE : View.GONE);
            if (myUid.equals(typingTo)) tvChatStatus.setText("typing...");
            else if (isOnline) tvChatStatus.setText("online");
            else if (lastSeen != null && lastSeen > 0) {
                tvChatStatus.setText("last seen " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(lastSeen)));
            } else tvChatStatus.setText("offline");
        });
    }

    private void sendMessage(String text, String type, String mediaUrl, String voiceDuration) {
        long now = System.currentTimeMillis();
        Map<String, Object> msg = new HashMap<>();
        msg.put("text", text); msg.put("type", type); msg.put("mediaUrl", mediaUrl);
        msg.put("voiceDuration", voiceDuration); msg.put("senderId", myUid); msg.put("senderName", myName);
        msg.put("timestamp", now); msg.put("seen", false); msg.put("reaction", "");

        if (replyingToMsg != null) {
            msg.put("replyToSender", replyingToMsg.getSenderName() != null ? replyingToMsg.getSenderName() : "Message");
            String q = "image".equals(replyingToMsg.getType()) ? "📷 Photo"
                    : ("voice".equals(replyingToMsg.getType()) ? "🎤 Voice Note" : replyingToMsg.getText());
            msg.put("replyToText", q);
            clearReply();
        }
        db.collection("chats").document(chatId).collection("messages").add(msg);

        if (!isGlobal) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("participants", Arrays.asList(myUid, peerUid));
            meta.put("lastMessage", "image".equals(type) ? "📷 Photo" : ("voice".equals(type) ? "🎤 Voice message" : text));
            meta.put("lastTimestamp", now);
            db.collection("chats").document(chatId).set(meta, SetOptions.merge());
        }
    }

    private void showMessageOptionsDialog(MessageModel m) {
        List<String> opts = new ArrayList<>();
        opts.add("↩️ Reply"); opts.add("😊 React");
        if ("text".equals(m.getType()) && m.getText() != null) opts.add("📋 Copy");
        if (myUid.equals(m.getSenderId())) opts.add("🗑️ Delete");

        new AlertDialog.Builder(this).setItems(opts.toArray(new String[0]), (d, which) -> {
            String choice = opts.get(which);
            if (choice.contains("Reply")) triggerReply(m);
            else if (choice.contains("React")) showReactionPicker(m);
            else if (choice.contains("Copy")) {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("msg", m.getText()));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            } else if (choice.contains("Delete")) {
                db.collection("chats").document(chatId).collection("messages").document(m.getId()).delete();
            }
        }).show();
    }

    private void showReactionPicker(MessageModel m) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(24, 24, 24, 24); row.setGravity(Gravity.CENTER);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("React").setView(row).create();
        for (String r : REACTIONS) {
            TextView tv = new TextView(this);
            tv.setText(r); tv.setTextSize(26); tv.setPadding(18, 12, 18, 12);
            tv.setOnClickListener(v -> {
                db.collection("chats").document(chatId).collection("messages").document(m.getId()).update("reaction", r);
                dialog.dismiss();
            });
            row.addView(tv);
        }
        dialog.show();
    }

    @SuppressWarnings("deprecation")
    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }
        try {
            voiceFile = File.createTempFile("rec_", ".m4a", getCacheDir());
            recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(32000);
            recorder.setAudioSamplingRate(16000);
            recorder.setOutputFile(voiceFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            inputRowLayout.setVisibility(View.GONE);
            recordingBar.setVisibility(View.VISIBLE);
            recordStartTime = System.currentTimeMillis();
            recordRunnable = new Runnable() {
                @Override public void run() {
                    long sec = (System.currentTimeMillis() - recordStartTime) / 1000;
                    tvRecordTime.setText(String.format(Locale.getDefault(), "🔴 Recording %02d:%02d", sec / 60, sec % 60));
                    recordHandler.postDelayed(this, 500);
                }
            };
            recordHandler.post(recordRunnable);
        } catch (Exception e) { Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show(); }
    }

    private void stopRecording(boolean send) {
        if (recordRunnable != null) recordHandler.removeCallbacks(recordRunnable);
        recordingBar.setVisibility(View.GONE);
        inputRowLayout.setVisibility(View.VISIBLE);
        long dur = (System.currentTimeMillis() - recordStartTime) / 1000;
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
            recorder = null;
        }
        if (send && voiceFile != null && voiceFile.exists() && voiceFile.length() > 0) {
            String durText = String.format(Locale.getDefault(), "%d:%02d", dur / 60, dur % 60);
            new Thread(() -> {
                try (FileInputStream fis = new FileInputStream(voiceFile); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096]; int read;
                    while ((read = fis.read(buf)) != -1) baos.write(buf, 0, read);
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    runOnUiThread(() -> sendMessage("", "voice", b64, durText));
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private void openCameraForPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
            return;
        }
        try {
            File file = File.createTempFile("cam_", ".jpg", getCacheDir());
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) { Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show(); }
    }

    private void processAndSendImage(Uri uri) {
        Toast.makeText(this, "Sending photo...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap orig = BitmapFactory.decodeStream(is);
                if (orig == null) return;
                int w = orig.getWidth(), h = orig.getHeight(), max = 500;
                if (w > h && w > max) { h = Math.round((float) h * max / w); w = max; }
                else if (h > max) { w = Math.round((float) w * max / h); h = max; }
                Bitmap res = Bitmap.createScaledBitmap(orig, w, h, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                res.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                String b64 = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                runOnUiThread(() -> sendMessage("", "image", b64, null));
            } catch (Exception ignored) {}
        }).start();
    }

    private void showEmojiDialog() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5); grid.setPadding(24, 24, 24, 24);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Emoji").setView(grid).create();
        for (String em : EMOJIS) {
            TextView tv = new TextView(this);
            tv.setText(em); tv.setTextSize(24); tv.setGravity(Gravity.CENTER); tv.setPadding(16, 16, 16, 16);
            tv.setOnClickListener(v -> { etMessage.append(em); dialog.dismiss(); });
            grid.addView(tv);
        }
        dialog.show();
    }

    /* 🎥 WebRTC কল সিস্টেম (সম্পূর্ণ ফিক্সড) */
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
                db.collection("calls").document(callDocId).collection(col).add(map);
            }

            @Override
            public void onRemoteStreamAdded(VideoTrack videoTrack) {
                runOnUiThread(() -> videoTrack.addSink(remoteVideoView));
            }

            @Override
            public void onConnectionStateChanged(PeerConnection.PeerConnectionState state) {
                runOnUiThread(() -> {
                    if (state == PeerConnection.PeerConnectionState.CONNECTING) {
                        tvCallTimer.setText("Connecting...");
                    } else if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        startCallTimer();
                    } else if (state == PeerConnection.PeerConnectionState.FAILED) {
                        tvCallTimer.setText("Connection Failed (Network/NAT)");
                        Toast.makeText(ChatActivity.this, "নেটওয়ার্কের কারণে কল কানেক্ট হয়নি", Toast.LENGTH_LONG).show();
                    } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
                        tvCallTimer.setText("Reconnecting...");
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggableLocalView() {
        localVideoView.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { dX = view.getX() - event.getRawX(); dY = view.getY() - event.getRawY(); return true; }
                if (event.getAction() == MotionEvent.ACTION_MOVE) { view.setX(event.getRawX() + dX); view.setY(event.getRawY() + dY); return true; }
                return false;
            }
        });
    }

    private void checkPermissionsAndStartCall(String type) {
        boolean hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!hasCam || !hasMic) {
            pendingPermissionAction = () -> startCall(type);
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
            return;
        }
        startCall(type);
    }

    @SuppressWarnings("unchecked")
    private void startCall(String type) {
        isCallerCurrent = true;
        activeCallType = type;
        hasRemoteSdp = false;
        logCallHistory(type);
        setupCallUI(type, peerName);

        clearOldCandidates(() -> {
            webRTCManager.startLocalMedia("video".equals(type), localVideoView);
            webRTCManager.createPeerConnection();
            webRTCManager.createOffer(offer -> {
                Map<String, Object> offerMap = new HashMap<>();
                offerMap.put("type", offer.type.canonicalForm());
                offerMap.put("sdp", offer.description);

                Map<String, Object> callData = new HashMap<>();
                callData.put("callerId", myUid);
                callData.put("callerName", myName);
                callData.put("receiverId", peerUid);
                callData.put("receiverName", peerName);
                callData.put("type", type);
                callData.put("status", "calling");
                callData.put("offer", offerMap);
                callData.put("createdAt", System.currentTimeMillis());

                DocumentReference callDoc = db.collection("calls").document(callDocId);
                callDoc.set(callData).addOnSuccessListener(unused -> runOnUiThread(() -> {
                    if (callListener != null) callListener.remove();
                    callListener = callDoc.addSnapshotListener((snap, err) -> {
                        if (snap == null || !snap.exists()) return;
                        String status = snap.getString("status");
                        if ("connected".equals(status) && !hasRemoteSdp) {
                            Map<String, Object> ans = (Map<String, Object>) snap.get("answer");
                            if (ans != null) {
                                hasRemoteSdp = true;
                                webRTCManager.setRemoteDescription((String) ans.get("type"), (String) ans.get("sdp"), () ->
                                        runOnUiThread(() -> tvCallTimer.setText("Connecting..."))
                                );
                            }
                        } else if ("ended".equals(status)) {
                            hangupCall(false);
                        }
                    });
                    listenCandidates("calleeCandidates");
                }));
            });
        });
    }

    private void listenIncomingCalls() {
        incomingListener = db.collection("calls").document(callDocId).addSnapshotListener((snap, err) -> {
            if (snap == null || !snap.exists()) return;
            String status = snap.getString("status");
            String callerId = snap.getString("callerId");
            Long createdAt = snap.getLong("createdAt");
            boolean isFresh = createdAt == null || (System.currentTimeMillis() - createdAt) < 60000;

            if ("calling".equals(status) && isFresh && !myUid.equals(callerId)
                    && callOverlay.getVisibility() != View.VISIBLE && !isAutoAccepting) {
                incomingOverlay.setVisibility(View.VISIBLE);
                tvCallerName.setText(snap.getString("callerName"));
                tvCallType.setText("video".equals(snap.getString("type")) ? "WhatsApp video call" : "WhatsApp voice call");
                playRingtone();
                findViewById(R.id.btnAcceptCall).setOnClickListener(v -> acceptIncomingCallWithPermission(snap));
                findViewById(R.id.btnRejectCall).setOnClickListener(v -> {
                    stopRingtone();
                    incomingOverlay.setVisibility(View.GONE);
                    snap.getReference().update("status", "ended");
                });
            } else if ("ended".equals(status)) {
                stopRingtone();
                isAutoAccepting = false;
                incomingOverlay.setVisibility(View.GONE);
                if (callOverlay.getVisibility() == View.VISIBLE) hangupCall(false);
            }
        });
    }

    private void acceptIncomingCallWithPermission(DocumentSnapshot snap) {
        boolean hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!hasCam || !hasMic) {
            pendingPermissionAction = () -> acceptIncomingCall(snap);
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
            return;
        }
        acceptIncomingCall(snap);
    }

    @SuppressWarnings("unchecked")
    private void acceptIncomingCall(DocumentSnapshot snap) {
        stopRingtone();
        isAutoAccepting = false;
        incomingOverlay.setVisibility(View.GONE);
        isCallerCurrent = false;
        activeCallType = snap.getString("type");
        if (activeCallType == null) activeCallType = "video";

        setupCallUI(activeCallType, snap.getString("callerName"));
        webRTCManager.startLocalMedia("video".equals(activeCallType), localVideoView);
        webRTCManager.createPeerConnection();

        Map<String, Object> offer = (Map<String, Object>) snap.get("offer");
        if (offer != null) {
            webRTCManager.setRemoteDescription((String) offer.get("type"), (String) offer.get("sdp"), () -> {
                webRTCManager.createAnswer(answer -> {
                    Map<String, Object> ansMap = new HashMap<>();
                    ansMap.put("type", answer.type.canonicalForm());
                    ansMap.put("sdp", answer.description);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("answer", ansMap);
                    updates.put("status", "connected");
                    db.collection("calls").document(callDocId).update(updates).addOnSuccessListener(u ->
                            runOnUiThread(() -> {
                                listenCandidates("callerCandidates");
                                tvCallTimer.setText("Connecting...");
                            })
                    );
                });
            });
        }
    }

    private void listenCandidates(String subCol) {
        if (candListener != null) candListener.remove();
        candListener = db.collection("calls").document(callDocId).collection(subCol).addSnapshotListener((snaps, err) -> {
            if (snaps == null) return;
            for (DocumentChange dc : snaps.getDocumentChanges()) {
                if (dc.getType() == DocumentChange.Type.ADDED) {
                    String c = dc.getDocument().getString("candidate");
                    String mid = dc.getDocument().getString("sdpMid");
                    Object idxObj = dc.getDocument().get("sdpMLineIndex");
                    int idx = idxObj instanceof Number ? ((Number) idxObj).intValue() : 0;
                    if (c != null) webRTCManager.addIceCandidate(mid, idx, c);
                }
            }
        });
    }

    private void clearOldCandidates(Runnable onDone) {
        DocumentReference docRef = db.collection("calls").document(callDocId);
        docRef.collection("callerCandidates").get().addOnSuccessListener(s1 -> {
            for (DocumentSnapshot d : s1.getDocuments()) d.getReference().delete();
            docRef.collection("calleeCandidates").get().addOnSuccessListener(s2 -> {
                for (DocumentSnapshot d : s2.getDocuments()) d.getReference().delete();
                onDone.run();
            }).addOnFailureListener(e -> onDone.run());
        }).addOnFailureListener(e -> onDone.run());
    }

    private void logCallHistory(String type) {
        Map<String, Object> log = new HashMap<>();
        log.put("callerId", myUid); log.put("callerName", myName);
        log.put("receiverId", peerUid); log.put("receiverName", peerName);
        log.put("type", type); log.put("timestamp", System.currentTimeMillis());
        db.collection("call_logs").add(log);
    }

    private void playRingtone() {
        try {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {}
    }

    private void stopRingtone() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
    }

    private void setupCallUI(String type, String targetName) {
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn("video".equals(type));
        isSpeakerOn = "video".equals(type);
        isMuted = false;
        isCamOff = false;
        btnMic.setImageResource(R.drawable.ic_mic); btnMic.setAlpha(1.0f);
        btnCam.setAlpha(1.0f);
        btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.45f);

        minimizeCallUI(false);
        callOverlay.setVisibility(View.VISIBLE);
        tvActiveTarget.setText(targetName != null ? targetName : "Friend");
        tvCallTimer.setText("Calling...");

        boolean isVid = "video".equals(type);
        localVideoView.setVisibility(isVid ? View.VISIBLE : View.GONE);
        remoteVideoView.setVisibility(isVid ? View.VISIBLE : View.GONE);
        audioCallAvatarBox.setVisibility(isVid ? View.GONE : View.VISIBLE);
    }

    private void minimizeCallUI(boolean minimize) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) callOverlay.getLayoutParams();
        if (minimize) {
            params.width = dpToPx(145); params.height = dpToPx(205);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.setMargins(0, 0, dpToPx(16), dpToPx(80));
            callTopBar.setVisibility(View.GONE); callBottomBar.setVisibility(View.GONE); btnMaximize.setVisibility(View.VISIBLE);
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT; params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.NO_GRAVITY; params.setMargins(0, 0, 0, 0);
            callTopBar.setVisibility(View.VISIBLE); callBottomBar.setVisibility(View.VISIBLE); btnMaximize.setVisibility(View.GONE);
        }
        callOverlay.setLayoutParams(params);
    }

    private int dpToPx(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    private void startCallTimer() {
        if (timerRunnable != null) return;
        callStartTime = System.currentTimeMillis();
        timerRunnable = new Runnable() {
            @Override public void run() {
                long sec = (System.currentTimeMillis() - callStartTime) / 1000;
                tvCallTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void hangupCall(boolean notifyFirebase) {
        stopRingtone();
        isAutoAccepting = false;
        if (timerRunnable != null) { timerHandler.removeCallbacks(timerRunnable); timerRunnable = null; }
        if (callListener != null) { callListener.remove(); callListener = null; }
        if (candListener != null) { candListener.remove(); candListener = null; }
        webRTCManager.endCall();
        localVideoView.clearImage(); remoteVideoView.clearImage();
        callOverlay.setVisibility(View.GONE);
        audioManager.setMode(AudioManager.MODE_NORMAL);
        if (notifyFirebase) db.collection("calls").document(callDocId).update("status", "ended");
    }

    @Override protected void onStart() { super.onStart(); isChatOpen = true; }
    @Override protected void onStop() { super.onStop(); isChatOpen = false; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingtone();
        if (callOverlay != null && callOverlay.getVisibility() == View.VISIBLE) hangupCall(true);
        if (!isGlobal && myUid != null) db.collection("users").document(myUid).update("typingTo", "");
        if (msgListener != null) msgListener.remove();
        if (peerListener != null) peerListener.remove();
        if (incomingListener != null) incomingListener.remove();
        if (recordRunnable != null) recordHandler.removeCallbacks(recordRunnable);
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (adapter != null) adapter.releasePlayer();
        if (webRTCManager != null) webRTCManager.destroyFactory();
        if (localVideoView != null) localVideoView.release();
        if (remoteVideoView != null) remoteVideoView.release();
        if (eglBase != null) eglBase.release();
    }
}