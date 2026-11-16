package com.wevlo.amohir;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RendererCommon;
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
    private boolean isGroup = false;
    private final List<String> groupMembers = new ArrayList<>();

    private TextView tvChatName, tvChatStatus, tvChatAvatar, tvReplySender, tvReplyText, tvRecordTime, blockedBanner;
    private TextView tvCallerName, tvCallType, tvActiveTarget, tvCallTimer;
    private ImageView ivChatAvatar, ivChatWallpaper;
    private View viewChatOnline;
    private FrameLayout chatWallContainer, incomingOverlay, callOverlay;
    private EditText etMessage, etChatSearch;
    private ImageButton btnSendOrMic, btnMaximize, btnMic, btnCam, btnSpeaker, btnScreenShare;
    private LinearLayout replyPreviewLayout, recordingBar, inputRowLayout, callTopBar, callBottomBar, audioCallAvatarBox, chatSearchBar;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private final List<MessageModel> allMessages = new ArrayList<>();
    private final List<MessageModel> displayMessages = new ArrayList<>();

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
    private AudioFocusRequest audioFocusRequest;
    private AudioDeviceCallback audioDeviceCallback;
    private PowerManager.WakeLock callWakeLock, proximityWakeLock;
    private Ringtone ringtone;

    private String activeCallType = "video", selectedAudioRoute = "auto";
    private boolean isMuted = false, isCamOff = false, hasRemoteSdp = false, isCallerCurrent = false, isAutoAccepting = false;
    private boolean iBlockedPeer = false, peerBlockedMe = false;
    private long callStartTime = 0, lastScreenReqTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable, callTimeoutRunnable;

    private ListenerRegistration msgListener, peerListener, callListener, incomingListener, candListener, blockListener1, blockListener2;
    private Uri cameraImageUri;
    private Runnable pendingPermissionAction = null;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                if (pendingPermissionAction != null) {
                    Runnable action = pendingPermissionAction;
                    pendingPermissionAction = null;
                    if (hasCam && hasMic) action.run();
                    else Toast.makeText(this, "অনুগ্রহ করে প্রয়োজনীয় পারমিশন দিন", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> screenShareLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent projectionData = result.getData();
                    ScreenShareService.onServiceReady = () -> runOnUiThread(() -> {
                        if (webRTCManager != null) {
                            webRTCManager.startScreenShare(projectionData);
                            btnScreenShare.setColorFilter(Color.parseColor("#25D366"));
                        }
                    });
                    ContextCompat.startForegroundService(this, new Intent(this, ScreenShareService.class));
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) showImageSendOptions(uri);
            });

    private final ActivityResultLauncher<Intent> doodleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String doodledB64 = result.getData().getStringExtra("doodledB64");
                    if (doodledB64 != null) {
                        sendMessage("", "image", doodledB64, null, null, null, false, false);
                    }
                }
            });

    private final ActivityResultLauncher<String> wallpaperLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) saveCustomWallpaper(uri); });
    private final ActivityResultLauncher<String[]> fileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) processAndSendFile(uri); });
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && cameraImageUri != null) showImageSendOptions(cameraImageUri);
            });

    private final ActivityResultLauncher<Intent> ringtonePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (uri != null) {
                        getSharedPreferences("DivineWA", MODE_PRIVATE).edit().putString("ring_" + peerUid, uri.toString()).apply();
                        Toast.makeText(this, "Custom ringtone saved!", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); }
        setContentView(R.layout.activity_chat);

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey("AIzaSyAmLJ5Vdd6Pqm-FrPfANvjy1TZkhG1mxns")
                    .setApplicationId("1:367706014019:web:f4e33a639f4573262baff6")
                    .setProjectId("good-865a2").setStorageBucket("good-865a2.firebasestorage.app").setGcmSenderId("367706014019").build();
            FirebaseApp.initializeApp(this, options);
        }
        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) FirebaseAuth.getInstance().signInAnonymously();

        myUid = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("my_phone", "");
        myName = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("name", "User");
        isGroup = getIntent().getBooleanExtra("isGroup", false);
        peerUid = getIntent().getStringExtra("peerUid");
        peerName = getIntent().getStringExtra("peerName");

        if (myUid.isEmpty()) { finish(); return; }
        if (isGroup) {
            chatId = getIntent().getStringExtra("groupId");
            if (chatId == null || chatId.isEmpty()) { finish(); return; }
            callDocId = "call_" + chatId;
            loadGroupMembers();
        } else {
            if (peerUid == null || peerUid.isEmpty()) { finish(); return; }
            chatId = myUid.compareTo(peerUid) < 0 ? myUid + "_" + peerUid : peerUid + "_" + myUid;
            callDocId = "call_" + chatId;
        }

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) { nm.cancel(2002); nm.cancel(chatId.hashCode()); }

        initViews();
        applySavedWallpaper();
        resetUnreadCount();
        listenMessages();

        initWebRTCViews();
        setupDraggableLocalView();

        if (!isGroup) {
            listenPeerStatus();
            listenBlockStatus();

            String autoAcceptId = getIntent().getStringExtra("autoAcceptCallId");
            String autoStartCall = getIntent().getStringExtra("autoStartCall");
            if (autoAcceptId != null) {
                isAutoAccepting = true;
                db.collection("calls").document(autoAcceptId).get().addOnSuccessListener(snap -> {
                    if (snap.exists() && "calling".equals(snap.getString("status"))) acceptIncomingCallWithPermission(snap);
                    else isAutoAccepting = false;
                });
            } else if (autoStartCall != null) checkPermissionsAndStartCall(autoStartCall);
            listenIncomingCalls();
        } else {
            String autoStartCall = getIntent().getStringExtra("autoStartCall");
            if (autoStartCall != null) checkPermissionsAndStartCall(autoStartCall);
            listenIncomingCalls();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (callOverlay != null && callOverlay.getVisibility() == View.VISIBLE) {
                    if (callBottomBar.getVisibility() == View.VISIBLE) minimizeCallUI(true);
                    else moveTaskToBack(true);
                } else if (chatSearchBar.getVisibility() == View.VISIBLE) {
                    closeChatSearch();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void loadGroupMembers() {
        db.collection("chats").document(chatId).get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                List<String> parts = (List<String>) snap.get("participants");
                if (parts != null) {
                    groupMembers.clear(); groupMembers.addAll(parts);
                    tvChatStatus.setText(groupMembers.size() + " members");
                }
            }
        });
    }

    private void resetUnreadCount() {
        db.collection("chats").document(chatId).update("unread_" + myUid, 0);
    }

    private void initViews() {
        tvChatName = findViewById(R.id.tvChatName); tvChatStatus = findViewById(R.id.tvChatStatus);
        tvChatAvatar = findViewById(R.id.tvChatAvatar); ivChatAvatar = findViewById(R.id.ivChatAvatar);
        viewChatOnline = findViewById(R.id.viewChatOnline); etMessage = findViewById(R.id.etMessage);
        btnSendOrMic = findViewById(R.id.btnSendOrMic); rvMessages = findViewById(R.id.rvMessages);
        chatWallContainer = findViewById(R.id.chatWallContainer); ivChatWallpaper = findViewById(R.id.ivChatWallpaper);
        blockedBanner = findViewById(R.id.blockedBanner); chatSearchBar = findViewById(R.id.chatSearchBar);
        etChatSearch = findViewById(R.id.etChatSearch);

        replyPreviewLayout = findViewById(R.id.replyPreviewLayout); tvReplySender = findViewById(R.id.tvReplySender); tvReplyText = findViewById(R.id.tvReplyText);
        recordingBar = findViewById(R.id.recordingBar); tvRecordTime = findViewById(R.id.tvRecordTime); inputRowLayout = findViewById(R.id.inputRowLayout);
        incomingOverlay = findViewById(R.id.incomingOverlay); tvCallerName = findViewById(R.id.tvCallerName); tvCallType = findViewById(R.id.tvCallType);
        callOverlay = findViewById(R.id.callOverlay); callTopBar = findViewById(R.id.callTopBar); callBottomBar = findViewById(R.id.callBottomBar);
        audioCallAvatarBox = findViewById(R.id.audioCallAvatarBox); tvActiveTarget = findViewById(R.id.tvActiveTarget); tvCallTimer = findViewById(R.id.tvCallTimer);
        btnMaximize = findViewById(R.id.btnMaximize); btnMic = findViewById(R.id.btnMic); btnCam = findViewById(R.id.btnCam);
        btnSpeaker = findViewById(R.id.btnSpeaker); btnScreenShare = findViewById(R.id.btnScreenShare);

        tvChatName.setText(peerName != null ? peerName : (isGroup ? "Private Group" : peerUid));
        if (isGroup) {
            tvChatAvatar.setText("👥");
            tvChatStatus.setText("Group Info");
            View.OnClickListener openGroupInfo = v -> {
                Intent i = new Intent(this, GroupInfoActivity.class);
                i.putExtra("groupId", chatId);
                i.putExtra("groupName", tvChatName.getText().toString());
                startActivity(i);
            };
            tvChatName.setOnClickListener(openGroupInfo);
            tvChatStatus.setOnClickListener(openGroupInfo);
            tvChatAvatar.setOnClickListener(openGroupInfo);
        } else {
            String peerAvatar = getIntent().getStringExtra("peerAvatar");
            if (peerAvatar != null && !peerAvatar.isEmpty()) {
                try {
                    byte[] b = Base64.decode(peerAvatar, Base64.DEFAULT);
                    ivChatAvatar.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                    ivChatAvatar.setVisibility(View.VISIBLE); tvChatAvatar.setVisibility(View.GONE);
                } catch (Exception ignored) {}
            } else tvChatAvatar.setText(peerName != null && !peerName.isEmpty() ? peerName.substring(0, 1).toUpperCase() : "👤");

            View.OnClickListener openProfileClick = v -> ContactInfoDialog.show(this, peerUid, tvChatName.getText().toString(),
                    new ContactInfoDialog.ActionCallback() {
                        @Override public void onAudioCall() { checkPermissionsAndStartCall("audio"); }
                        @Override public void onVideoCall() { checkPermissionsAndStartCall("video"); }
                    });
            tvChatName.setOnClickListener(openProfileClick); tvChatStatus.setOnClickListener(openProfileClick);
            tvChatAvatar.setOnClickListener(openProfileClick); ivChatAvatar.setOnClickListener(openProfileClick);
        }

        adapter = new MessageAdapter(displayMessages, myUid, isGroup, new MessageAdapter.OnMessageActionListener() {
            @Override public void onLongClick(MessageModel message) { showMessageOptionsDialog(message); }
            @Override public void onViewOnceClick(MessageModel message) { openViewOnceMedia(message); }
        });
        rvMessages.setLayoutManager(new LinearLayoutManager(this)); rvMessages.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getBindingAdapterPosition();
                if (pos >= 0 && pos < displayMessages.size() && !displayMessages.get(pos).isDeleted()) triggerReply(displayMessages.get(pos));
                adapter.notifyItemChanged(pos);
            }
        }).attachToRecyclerView(rvMessages);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelReply).setOnClickListener(v -> clearReply());
        findViewById(R.id.btnEmoji).setOnClickListener(v -> showEmojiDialog());
        findViewById(R.id.btnAttach).setOnClickListener(v -> showAttachOptionsDialog());
        findViewById(R.id.btnCamQuick).setOnClickListener(v -> openCameraForPhoto());
        findViewById(R.id.btnCancelRecord).setOnClickListener(v -> stopRecording(false));
        findViewById(R.id.btnSendRecord).setOnClickListener(v -> stopRecording(true));
        findViewById(R.id.btnChatMore).setOnClickListener(v -> showChatMoreMenu());
        findViewById(R.id.btnCloseChatSearch).setOnClickListener(v -> closeChatSearch());

        blockedBanner.setOnClickListener(v -> {
            if (iBlockedPeer) db.collection("users").document(myUid).collection("blocked").document(peerUid).delete();
        });

        etChatSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filterChatMessages(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hasText = s.toString().trim().length() > 0;
                btnSendOrMic.setImageResource(hasText ? R.drawable.ic_send : R.drawable.ic_mic);
                if (!isGroup) db.collection("users").document(myUid).update("typingTo", hasText ? peerUid : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnSendOrMic.setOnClickListener(v -> {
            String txt = etMessage.getText().toString().trim();
            if (!txt.isEmpty()) { etMessage.setText(""); sendMessage(txt, "text", null, null, null, null, false, false); }
            else startVoiceRecording();
        });

        findViewById(R.id.btnAudioCall).setOnClickListener(v -> checkPermissionsAndStartCall("audio"));
        findViewById(R.id.btnVideoCall).setOnClickListener(v -> checkPermissionsAndStartCall("video"));
        findViewById(R.id.btnSwitchCam).setOnClickListener(v -> { if (webRTCManager != null) webRTCManager.switchCamera(); });
        findViewById(R.id.btnHangup).setOnClickListener(v -> hangupCall(true));
        findViewById(R.id.btnMinimize).setOnClickListener(v -> minimizeCallUI(true));
        btnMaximize.setOnClickListener(v -> minimizeCallUI(false));
        btnScreenShare.setOnClickListener(v -> onScreenShareButtonClick());

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
        btnSpeaker.setOnClickListener(v -> showAudioOutputChooserOrToggle());
    }

    public void onScreenShareButtonClick() {
        if (!"video".equals(activeCallType)) {
            Toast.makeText(this, "স্ক্রিন শেয়ার করার জন্য Video Call দিন", Toast.LENGTH_SHORT).show();
            return;
        }
        if (webRTCManager.isScreenSharing()) {
            webRTCManager.stopScreenShare();
            stopService(new Intent(this, ScreenShareService.class));
            btnScreenShare.clearColorFilter();
            Toast.makeText(this, "Screen sharing stopped", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] opts = {"📱 Share My Screen (আমার স্ক্রিন দেখাও)", "📲 Request Friend's Screen (অন্য ফোনের স্ক্রিন দেখো)"};
        new AlertDialog.Builder(this).setTitle("Screen Share").setItems(opts, (d, which) -> {
            if (which == 0) launchMyScreenSharePrompt();
            else {
                Map<String, Object> req = new HashMap<>();
                req.put("screenRequestTarget", peerUid);
                req.put("screenRequestTime", System.currentTimeMillis());
                db.collection("calls").document(callDocId).set(req, SetOptions.merge());
                Toast.makeText(this, "অন্য ফোনে স্ক্রিন শেয়ার পপআপ পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show();
            }
        }).show();
    }

    public void handleRemoteScreenRequest(DocumentSnapshot snap) {
        String target = snap.getString("screenRequestTarget");
        Long reqTime = snap.getLong("screenRequestTime");
        if (myUid.equals(target) && reqTime != null && reqTime > lastScreenReqTime
                && callOverlay != null && callOverlay.getVisibility() == View.VISIBLE
                && webRTCManager != null && !webRTCManager.isScreenSharing()) {
            lastScreenReqTime = reqTime;
            launchMyScreenSharePrompt();
        }
    }

    private void launchMyScreenSharePrompt() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) screenShareLauncher.launch(mpm.createScreenCaptureIntent());
    }

    private void showImageSendOptions(Uri imageUri) {
        String[] opts = {"Send Normal Photo", "Send View Once (① একবার দেখার ছবি)", "✏️ Doodle / Draw on Photo"};
        new AlertDialog.Builder(this).setTitle("Photo Options").setItems(opts, (d, which) -> {
            if (which == 0) processAndSendImage(imageUri, false);
            else if (which == 1) processAndSendImage(imageUri, true);
            else {
                Intent i = new Intent(this, DoodleActivity.class);
                i.putExtra("imageUri", imageUri.toString());
                doodleLauncher.launch(i);
            }
        }).show();
    }

    private void openViewOnceMedia(MessageModel m) {
        if (m.getMediaUrl() == null || m.isOpened()) return;
        byte[] bytes = Base64.decode(m.getMediaUrl(), Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bmp == null) return;

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);

        new AlertDialog.Builder(this)
                .setTitle("① View Once Photo")
                .setView(iv)
                .setPositiveButton("Close", (d, w) -> {
                    db.collection("chats").document(chatId).collection("messages").document(m.getId())
                            .update("opened", true, "mediaUrl", null);
                })
                .setCancelable(false)
                .show();
    }

    private void showChatMoreMenu() {
        List<String> items = new ArrayList<>();
        items.add("🔍 Search Messages");
        items.add("🖼️ Change Wallpaper");
        items.add("⏱️ Disappearing Messages");
        if (!isGroup) {
            items.add("🔔 Custom Ringtone");
            items.add(iBlockedPeer ? "✅ Unblock Contact" : "🚫 Block Contact");
        } else {
            items.add("👥 Group Info");
        }
        items.add("🧹 Clear Chat");

        new AlertDialog.Builder(this).setItems(items.toArray(new String[0]), (d, which) -> {
            String sel = items.get(which);
            if (sel.contains("Search")) {
                chatSearchBar.setVisibility(View.VISIBLE); etChatSearch.requestFocus();
            } else if (sel.contains("Wallpaper")) {
                showWallpaperPicker();
            } else if (sel.contains("Disappearing")) {
                showDisappearingDialog();
            } else if (sel.contains("Custom Ringtone")) {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
                ringtonePickerLauncher.launch(intent);
            } else if (sel.contains("Group Info")) {
                Intent i = new Intent(this, GroupInfoActivity.class);
                i.putExtra("groupId", chatId); i.putExtra("groupName", tvChatName.getText().toString());
                startActivity(i);
            } else if (sel.contains("Block") || sel.contains("Unblock")) {
                if (iBlockedPeer) db.collection("users").document(myUid).collection("blocked").document(peerUid).delete();
                else {
                    Map<String, Object> b = new HashMap<>(); b.put("blockedAt", System.currentTimeMillis());
                    db.collection("users").document(myUid).collection("blocked").document(peerUid).set(b);
                }
            } else if (sel.contains("Clear")) {
                db.collection("chats").document(chatId).collection("messages").get().addOnSuccessListener(s -> {
                    for (DocumentSnapshot doc : s.getDocuments()) doc.getReference().delete();
                    Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
                });
            }
        }).show();
    }

    private void showDisappearingDialog() {
        String[] opts = {"Off", "24 Hours", "7 Days", "90 Days"};
        new AlertDialog.Builder(this).setTitle("Disappearing Messages").setItems(opts, (d, which) -> {
            long duration = 0;
            if (which == 1) duration = 24 * 3600 * 1000L;
            else if (which == 2) duration = 7 * 86400 * 1000L;
            else if (which == 3) duration = 90L * 86400 * 1000L;
            getSharedPreferences("DivineWA", MODE_PRIVATE).edit().putLong("disappear_" + chatId, duration).apply();
            Toast.makeText(this, "Timer set: " + opts[which], Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void closeChatSearch() {
        etChatSearch.setText(""); chatSearchBar.setVisibility(View.GONE); filterChatMessages("");
    }

    private void filterChatMessages(String q) {
        displayMessages.clear();
        String query = q.toLowerCase().trim();
        for (MessageModel m : allMessages) {
            if (query.isEmpty() || (m.getText() != null && m.getText().toLowerCase().contains(query))) displayMessages.add(m);
        }
        adapter.notifyDataSetChanged();
    }

    private void showWallpaperPicker() {
        String[] opts = {"Default Dark", "Deep Ocean Blue", "Emerald Night", "Choose Photo from Gallery"};
        new AlertDialog.Builder(this).setTitle("Chat Wallpaper").setItems(opts, (d, which) -> {
            SharedPreferences.Editor ed = getSharedPreferences("DivineWA", MODE_PRIVATE).edit();
            if (which == 0) { ed.putString("wall_" + chatId, "default").apply(); applySavedWallpaper(); }
            else if (which == 1) { ed.putString("wall_" + chatId, "#0D1B2A").apply(); applySavedWallpaper(); }
            else if (which == 2) { ed.putString("wall_" + chatId, "#0B201A").apply(); applySavedWallpaper(); }
            else wallpaperLauncher.launch("image/*");
        }).show();
    }

    private void saveCustomWallpaper(Uri uri) {
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap orig = BitmapFactory.decodeStream(is); if (orig == null) return;
                Bitmap res = Bitmap.createScaledBitmap(orig, 540, 960, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(); res.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                getSharedPreferences("DivineWA", MODE_PRIVATE).edit().putString("wall_" + chatId, "img:" + b64).apply();
                runOnUiThread(this::applySavedWallpaper);
            } catch (Exception ignored) {}
        }).start();
    }

    private void applySavedWallpaper() {
        String val = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("wall_" + chatId, "default");
        if (val.startsWith("img:")) {
            try {
                byte[] b = Base64.decode(val.substring(4), Base64.DEFAULT);
                ivChatWallpaper.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                ivChatWallpaper.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {}
        } else if (val.startsWith("#")) {
            ivChatWallpaper.setVisibility(View.GONE);
            chatWallContainer.setBackgroundColor(Color.parseColor(val));
        } else {
            ivChatWallpaper.setVisibility(View.GONE);
            chatWallContainer.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void listenBlockStatus() {
        blockListener1 = db.collection("users").document(myUid).collection("blocked").document(peerUid)
                .addSnapshotListener((s, e) -> { iBlockedPeer = s != null && s.exists(); updateBlockUI(); });
        blockListener2 = db.collection("users").document(peerUid).collection("blocked").document(myUid)
                .addSnapshotListener((s, e) -> { peerBlockedMe = s != null && s.exists(); updateBlockUI(); });
    }

    private void updateBlockUI() {
        boolean blocked = iBlockedPeer || peerBlockedMe;
        inputRowLayout.setVisibility(blocked ? View.GONE : View.VISIBLE);
        blockedBanner.setVisibility(blocked ? View.VISIBLE : View.GONE);
        blockedBanner.setText(iBlockedPeer ? "You blocked this contact. Tap to unblock." : "You cannot message this contact.");
    }

    private void showAttachOptionsDialog() {
        String[] opts = {"🖼️ Gallery Photo", "📄 Document / File (Max 600KB)", "📍 Current Location"};
        new AlertDialog.Builder(this).setTitle("Send").setItems(opts, (d, which) -> {
            if (which == 0) galleryLauncher.launch("image/*");
            else if (which == 1) fileLauncher.launch(new String[]{"*/*"});
            else sendCurrentLocation();
        }).show();
    }

    private void processAndSendFile(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            String name = "file";
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
            }
            byte[] buf = new byte[4096]; int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
                if (baos.size() > 620000) { Toast.makeText(this, "File too large (Max 600KB)", Toast.LENGTH_LONG).show(); return; }
            }
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            sendMessage("", "file", b64, null, name, null, false, false);
        } catch (Exception e) { Toast.makeText(this, "File read failed", Toast.LENGTH_SHORT).show(); }
    }

    private void sendCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = this::sendCurrentLocation;
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION});
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                String latLng = loc.getLatitude() + "," + loc.getLongitude();
                sendMessage("📍 Location", "location", null, null, null, latLng, false, false);
            } else Toast.makeText(this, "Turn on GPS Location and try again", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Location failed", Toast.LENGTH_SHORT).show(); }
    }

    private void listenMessages() {
        msgListener = db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING).limitToLast(120)
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null) return;
                    long now = System.currentTimeMillis();
                    for (DocumentChange dc : snaps.getDocumentChanges()) {
                        MessageModel m = dc.getDocument().toObject(MessageModel.class);
                        m.setId(dc.getDocument().getId());

                        if (m.getExpireAt() > 0 && now > m.getExpireAt()) {
                            dc.getDocument().getReference().delete();
                            continue;
                        }

                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            allMessages.add(m);
                            boolean readReceiptsOn = getSharedPreferences("DivineWA", MODE_PRIVATE).getBoolean("read_receipts", true);
                            if (!myUid.equals(m.getSenderId()) && !m.isSeen() && readReceiptsOn) {
                                dc.getDocument().getReference().update("seen", true);
                            }
                        } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                            for (int i = 0; i < allMessages.size(); i++) if (allMessages.get(i).getId().equals(m.getId())) { allMessages.set(i, m); break; }
                        } else if (dc.getType() == DocumentChange.Type.REMOVED) {
                            for (int i = 0; i < allMessages.size(); i++) if (allMessages.get(i).getId().equals(m.getId())) { allMessages.remove(i); break; }
                        }
                    }
                    resetUnreadCount();
                    filterChatMessages(etChatSearch.getText().toString());
                    if (etChatSearch.getText().toString().isEmpty() && !displayMessages.isEmpty()) rvMessages.scrollToPosition(displayMessages.size() - 1);
                });
    }

    private void sendMessage(String text, String type, String mediaUrl, String voiceDuration, String fileName, String latLng, boolean forwarded, boolean viewOnce) {
        if (!isGroup && (iBlockedPeer || peerBlockedMe)) return;
        long now = System.currentTimeMillis();
        long disappearTimer = getSharedPreferences("DivineWA", MODE_PRIVATE).getLong("disappear_" + chatId, 0);

        Map<String, Object> msg = new HashMap<>();
        msg.put("text", text); msg.put("type", type); msg.put("mediaUrl", mediaUrl);
        msg.put("voiceDuration", voiceDuration); msg.put("fileName", fileName); msg.put("latLng", latLng);
        msg.put("senderId", myUid); msg.put("senderName", myName); msg.put("timestamp", now);
        msg.put("seen", false); msg.put("reaction", ""); msg.put("forwarded", forwarded);
        msg.put("viewOnce", viewOnce); msg.put("opened", false);
        msg.put("starred", false);
        if (disappearTimer > 0) msg.put("expireAt", now + disappearTimer);

        if (replyingToMsg != null) {
            msg.put("replyToSender", replyingToMsg.getSenderName() != null ? replyingToMsg.getSenderName() : "Message");
            String q = "image".equals(replyingToMsg.getType()) ? "📷 Photo"
                    : ("voice".equals(replyingToMsg.getType()) ? "🎤 Voice Note" : replyingToMsg.getText());
            msg.put("replyToText", q);
            clearReply();
        }
        db.collection("chats").document(chatId).collection("messages").add(msg);

        String preview = viewOnce ? "① Photo" : ("image".equals(type) ? "📷 Photo" : ("voice".equals(type) ? "🎤 Voice"
                : ("file".equals(type) ? "📄 " + fileName : ("location".equals(type) ? "📍 Location" : text))));
        Map<String, Object> meta = new HashMap<>();
        meta.put("lastMessage", preview); meta.put("lastTimestamp", now);
        meta.put("lastSenderId", myUid); meta.put("lastSenderName", myName);
        if (!isGroup) {
            meta.put("participants", Arrays.asList(myUid, peerUid));
            meta.put("unread_" + peerUid, FieldValue.increment(1));
        } else {
            for (String member : groupMembers) if (!myUid.equals(member)) meta.put("unread_" + member, FieldValue.increment(1));
        }
        db.collection("chats").document(chatId).set(meta, SetOptions.merge());
    }

    private void showMessageOptionsDialog(MessageModel m) {
        if (m.isDeleted()) return;
        List<String> opts = new ArrayList<>();
        opts.add("↩️ Reply"); opts.add("😊 React");
        opts.add(m.isStarred() ? "★ Unstar Message" : "★ Star Message");
        opts.add("↪ Forward");
        if ("text".equals(m.getType()) && m.getText() != null) opts.add("📋 Copy");
        if (myUid.equals(m.getSenderId()) && "text".equals(m.getType())) opts.add("✏️ Edit");
        if (myUid.equals(m.getSenderId())) opts.add("🗑️ Delete for Everyone");

        new AlertDialog.Builder(this).setItems(opts.toArray(new String[0]), (d, which) -> {
            String choice = opts.get(which);
            if (choice.contains("Reply")) triggerReply(m);
            else if (choice.contains("React")) showReactionPicker(m);
            else if (choice.contains("Star") || choice.contains("Unstar")) {
                db.collection("chats").document(chatId).collection("messages").document(m.getId())
                        .update("starred", !m.isStarred());
                Toast.makeText(this, !m.isStarred() ? "Starred!" : "Unstarred", Toast.LENGTH_SHORT).show();
            } else if (choice.contains("Forward")) showForwardDialog(m);
            else if (choice.contains("Edit")) showEditDialog(m);
            else if (choice.contains("Copy")) {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("msg", m.getText()));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            } else if (choice.contains("Delete")) {
                Map<String, Object> del = new HashMap<>();
                del.put("deleted", true); del.put("text", ""); del.put("mediaUrl", ""); del.put("reaction", "");
                db.collection("chats").document(chatId).collection("messages").document(m.getId()).update(del);
            }
        }).show();
    }

    private void showEditDialog(MessageModel m) {
        EditText et = new EditText(this); et.setText(m.getText());
        new AlertDialog.Builder(this).setTitle("Edit Message").setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String newTxt = et.getText().toString().trim();
                    if (!newTxt.isEmpty()) db.collection("chats").document(chatId).collection("messages").document(m.getId()).update("text", newTxt, "edited", true);
                }).setNegativeButton("Cancel", null).show();
    }

    private void showForwardDialog(MessageModel m) {
        db.collection("users").document(myUid).collection("contacts").get().addOnSuccessListener(snaps -> {
            if (snaps.isEmpty()) { Toast.makeText(this, "No contacts to forward", Toast.LENGTH_SHORT).show(); return; }
            List<String> phones = new ArrayList<>();
            for (DocumentSnapshot d : snaps.getDocuments()) phones.add(d.getId());
            new AlertDialog.Builder(this).setTitle("Forward to").setItems(phones.toArray(new String[0]), (d, w) -> {
                String targetPhone = phones.get(w);
                String targetChatId = myUid.compareTo(targetPhone) < 0 ? myUid + "_" + targetPhone : targetPhone + "_" + myUid;
                long now = System.currentTimeMillis();
                Map<String, Object> fwd = new HashMap<>();
                fwd.put("text", m.getText()); fwd.put("type", m.getType()); fwd.put("mediaUrl", m.getMediaUrl());
                fwd.put("voiceDuration", m.getVoiceDuration()); fwd.put("fileName", m.getFileName()); fwd.put("latLng", m.getLatLng());
                fwd.put("senderId", myUid); fwd.put("senderName", myName); fwd.put("timestamp", now);
                fwd.put("seen", false); fwd.put("forwarded", true);
                db.collection("chats").document(targetChatId).collection("messages").add(fwd);

                Map<String, Object> meta = new HashMap<>();
                meta.put("participants", Arrays.asList(myUid, targetPhone));
                meta.put("lastMessage", "↪ Forwarded message"); meta.put("lastTimestamp", now);
                meta.put("lastSenderId", myUid); meta.put("lastSenderName", myName);
                meta.put("unread_" + targetPhone, FieldValue.increment(1));
                db.collection("chats").document(targetChatId).set(meta, SetOptions.merge());
                Toast.makeText(this, "Forwarded!", Toast.LENGTH_SHORT).show();
            }).show();
        });
    }

    private void showReactionPicker(MessageModel m) {
        LinearLayout row = new LinearLayout(this); row.setPadding(24, 24, 24, 24); row.setGravity(Gravity.CENTER);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("React").setView(row).create();
        for (String r : REACTIONS) {
            TextView tv = new TextView(this); tv.setText(r); tv.setTextSize(26); tv.setPadding(18, 12, 18, 12);
            tv.setOnClickListener(v -> { db.collection("chats").document(chatId).collection("messages").document(m.getId()).update("reaction", r); dialog.dismiss(); });
            row.addView(tv);
        }
        dialog.show();
    }

    private void triggerReply(MessageModel m) {
        replyingToMsg = m; replyPreviewLayout.setVisibility(View.VISIBLE);
        tvReplySender.setText(m.getSenderName() != null ? m.getSenderName() : "Message");
        String q = "image".equals(m.getType()) ? "📷 Photo" : ("voice".equals(m.getType()) ? "🎤 Voice Note" : m.getText());
        tvReplyText.setText(q);
    }

    private void clearReply() { replyingToMsg = null; replyPreviewLayout.setVisibility(View.GONE); }

    private void listenPeerStatus() {
        peerListener = db.collection("users").document(peerUid).addSnapshotListener((snap, err) -> {
            if (snap == null || !snap.exists()) return;
            String latestName = snap.getString("name"); if (latestName != null && !latestName.isEmpty()) tvChatName.setText(latestName);
            boolean isOnline = Boolean.TRUE.equals(snap.getBoolean("online"));
            String typingTo = snap.getString("typingTo"); Long lastSeen = snap.getLong("lastSeen");
            viewChatOnline.setVisibility(isOnline ? View.VISIBLE : View.GONE);
            if (myUid.equals(typingTo)) tvChatStatus.setText("typing...");
            else if (isOnline) tvChatStatus.setText("online");
            else if (lastSeen != null && lastSeen > 0) tvChatStatus.setText("last seen " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(lastSeen)));
            else tvChatStatus.setText("offline");
        });
    }

    private boolean isBluetoothConnected() {
        if (audioManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (AudioDeviceInfo dev : audioManager.getAvailableCommunicationDevices()) {
                int t = dev.getType();
                if (t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return true;
            }
        }
        for (AudioDeviceInfo dev : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = dev.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true;
        }
        return false;
    }

    private void showAudioOutputChooserOrToggle() {
        if (isBluetoothConnected()) {
            String[] opts = {"🎧 Bluetooth", "🔊 Speaker", "📱 Phone (Earpiece)"};
            new AlertDialog.Builder(this).setTitle("Audio Output").setItems(opts, (d, which) -> {
                selectedAudioRoute = which == 0 ? "bluetooth" : (which == 1 ? "speaker" : "earpiece");
                routeAudioForCall();
            }).show();
        } else {
            selectedAudioRoute = "speaker".equals(selectedAudioRoute) ? "earpiece" : "speaker";
            routeAudioForCall();
        }
    }

    @SuppressWarnings("deprecation")
    private void requestVoiceAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setAcceptsDelayedFocusGain(true).setOnAudioFocusChangeListener(fc -> {}).build();
            }
            audioManager.requestAudioFocus(audioFocusRequest);
        } else audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }

    @SuppressWarnings("deprecation")
    private void routeAudioForCall() {
        if (audioManager == null) return;
        requestVoiceAudioFocus(); audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo bt = null, wired = null, speaker = null, earpiece = null;
            for (AudioDeviceInfo dev : audioManager.getAvailableCommunicationDevices()) {
                int t = dev.getType();
                if (t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) bt = dev;
                else if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_USB_HEADSET) wired = dev;
                else if (t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) speaker = dev;
                else if (t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) earpiece = dev;
            }
            AudioDeviceInfo target = null; String label = "earpiece";
            if ("speaker".equals(selectedAudioRoute) && speaker != null) { target = speaker; label = "speaker"; }
            else if ("earpiece".equals(selectedAudioRoute) && earpiece != null) { target = earpiece; label = "earpiece"; }
            else if ("bluetooth".equals(selectedAudioRoute) && bt != null) { target = bt; label = "bluetooth"; }
            else if (bt != null) { target = bt; label = "bluetooth"; }
            else if (wired != null) { target = wired; label = "wired"; }
            else if ("video".equals(activeCallType) && speaker != null) { target = speaker; label = "speaker"; }
            else if (earpiece != null) { target = earpiece; label = "earpiece"; }
            if (target != null && !audioManager.setCommunicationDevice(target) && "bluetooth".equals(label)) {
                audioManager.startBluetoothSco(); audioManager.setBluetoothScoOn(true);
            }
            updateSpeakerIcon(label);
        } else {
            boolean hasBt = false, hasWired = false;
            for (AudioDeviceInfo dev : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = dev.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) hasBt = true;
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) hasWired = true;
            }
            String label;
            if ("speaker".equals(selectedAudioRoute)) { audioManager.stopBluetoothSco(); audioManager.setBluetoothScoOn(false); audioManager.setSpeakerphoneOn(true); label = "speaker"; }
            else if ("earpiece".equals(selectedAudioRoute)) { audioManager.stopBluetoothSco(); audioManager.setBluetoothScoOn(false); audioManager.setSpeakerphoneOn(false); label = "earpiece"; }
            else if ("bluetooth".equals(selectedAudioRoute) || ("auto".equals(selectedAudioRoute) && hasBt)) { audioManager.setSpeakerphoneOn(false); audioManager.startBluetoothSco(); audioManager.setBluetoothScoOn(true); label = "bluetooth"; }
            else if (hasWired) { audioManager.stopBluetoothSco(); audioManager.setBluetoothScoOn(false); audioManager.setSpeakerphoneOn(false); label = "wired"; }
            else if ("video".equals(activeCallType)) { audioManager.stopBluetoothSco(); audioManager.setBluetoothScoOn(false); audioManager.setSpeakerphoneOn(true); label = "speaker"; }
            else { audioManager.stopBluetoothSco(); audioManager.setBluetoothScoOn(false); audioManager.setSpeakerphoneOn(false); label = "earpiece"; }
            updateSpeakerIcon(label);
        }
    }

    private void updateSpeakerIcon(String label) {
        if ("bluetooth".equals(label)) { btnSpeaker.setColorFilter(Color.parseColor("#53BDEB")); btnSpeaker.setAlpha(1.0f); }
        else if ("speaker".equals(label)) { btnSpeaker.setColorFilter(Color.parseColor("#25D366")); btnSpeaker.setAlpha(1.0f); }
        else { btnSpeaker.clearColorFilter(); btnSpeaker.setAlpha(0.55f); }
    }

    private void scheduleBluetoothRouteRetries() {
        timerHandler.postDelayed(() -> { if (callOverlay.getVisibility() == View.VISIBLE) routeAudioForCall(); }, 500);
        timerHandler.postDelayed(() -> { if (callOverlay.getVisibility() == View.VISIBLE) routeAudioForCall(); }, 1500);
    }

    private void registerBluetoothListener() {
        if (audioDeviceCallback != null) return;
        audioDeviceCallback = new AudioDeviceCallback() {
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] a) { if (callOverlay.getVisibility() == View.VISIBLE) { selectedAudioRoute = "auto"; routeAudioForCall(); } }
            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] r) { if (callOverlay.getVisibility() == View.VISIBLE) routeAudioForCall(); }
        };
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, timerHandler);
    }

    @SuppressWarnings("deprecation")
    private void resetAudioRouting() {
        if (audioManager == null) return;
        if (audioDeviceCallback != null) { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback); audioDeviceCallback = null; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice();
        else { audioManager.stopBluetoothSco(); audioManager.setBluetoothScoOn(false); audioManager.setSpeakerphoneOn(false); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) { audioManager.abandonAudioFocusRequest(audioFocusRequest); audioFocusRequest = null; }
        audioManager.setMode(AudioManager.MODE_NORMAL);
    }

    @SuppressWarnings("deprecation")
    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO}); return;
        }
        try {
            voiceFile = File.createTempFile("rec_", ".m4a", getCacheDir());
            recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioEncodingBitRate(32000);
            recorder.setAudioSamplingRate(16000); recorder.setOutputFile(voiceFile.getAbsolutePath());
            recorder.prepare(); recorder.start();
            inputRowLayout.setVisibility(View.GONE); recordingBar.setVisibility(View.VISIBLE); recordStartTime = System.currentTimeMillis();
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
        recordingBar.setVisibility(View.GONE); inputRowLayout.setVisibility(View.VISIBLE);
        long dur = (System.currentTimeMillis() - recordStartTime) / 1000;
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
        if (send && voiceFile != null && voiceFile.exists() && voiceFile.length() > 0) {
            String durText = String.format(Locale.getDefault(), "%d:%02d", dur / 60, dur % 60);
            new Thread(() -> {
                try (FileInputStream fis = new FileInputStream(voiceFile); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096]; int read;
                    while ((read = fis.read(buf)) != -1) baos.write(buf, 0, read);
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    runOnUiThread(() -> sendMessage("", "voice", b64, durText, null, null, false, false));
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private void openCameraForPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA}); return;
        }
        try {
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", File.createTempFile("cam_", ".jpg", getCacheDir()));
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) { Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show(); }
    }

    private void processAndSendImage(Uri uri, boolean viewOnce) {
        Toast.makeText(this, "Sending photo...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap orig = BitmapFactory.decodeStream(is); if (orig == null) return;
                int w = orig.getWidth(), h = orig.getHeight(), max = 700;
                if (w > h && w > max) { h = Math.round((float) h * max / w); w = max; } else if (h > max) { w = Math.round((float) w * max / h); h = max; }
                Bitmap res = Bitmap.createScaledBitmap(orig, w, h, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(); res.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                runOnUiThread(() -> sendMessage("", "image", b64, null, null, null, false, viewOnce));
            } catch (Exception ignored) {}
        }).start();
    }

    private void showEmojiDialog() {
        GridLayout grid = new GridLayout(this); grid.setColumnCount(5); grid.setPadding(24, 24, 24, 24);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Emoji").setView(grid).create();
        for (String em : EMOJIS) {
            TextView tv = new TextView(this); tv.setText(em); tv.setTextSize(24); tv.setGravity(Gravity.CENTER); tv.setPadding(16, 16, 16, 16);
            tv.setOnClickListener(v -> { etMessage.append(em); dialog.dismiss(); });
            grid.addView(tv);
        }
        dialog.show();
    }

    private void initWebRTCViews() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        eglBase = EglBase.create();

        remoteVideoView = findViewById(R.id.remoteVideoView);
        remoteVideoView.init(eglBase.getEglBaseContext(), null);
        remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
        remoteVideoView.setEnableHardwareScaler(true);

        localVideoView = findViewById(R.id.localVideoView);
        localVideoView.init(eglBase.getEglBaseContext(), null);
        localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setMirror(true);
        localVideoView.setZOrderMediaOverlay(true);
        localVideoView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(16)); }
        });

        webRTCManager = new WebRTCManager(this, eglBase, new WebRTCManager.Callback() {
            @Override public void onIceCandidateGenerated(IceCandidate candidate) {
                String col = isCallerCurrent ? "callerCandidates" : "calleeCandidates";
                Map<String, Object> map = new HashMap<>();
                map.put("candidate", candidate.sdp); map.put("sdpMid", candidate.sdpMid); map.put("sdpMLineIndex", candidate.sdpMLineIndex);
                db.collection("calls").document(callDocId).collection(col).add(map);
            }
            @Override public void onRemoteStreamAdded(VideoTrack videoTrack) {
                runOnUiThread(() -> {
                    cancelCallTimeout();
                    if ("video".equals(activeCallType)) setLocalVideoFullscreen(false);
                    webRTCManager.setRemoteVideoTrack(videoTrack);
                    startCallTimer();
                });
            }
            @Override public void onConnectionStateChanged(PeerConnection.PeerConnectionState state) {
                runOnUiThread(() -> {
                    if (state == PeerConnection.PeerConnectionState.CONNECTING && timerRunnable == null) tvCallTimer.setText("Connecting...");
                    else if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        cancelCallTimeout(); if ("video".equals(activeCallType)) setLocalVideoFullscreen(false);
                        routeAudioForCall(); scheduleBluetoothRouteRetries(); startCallTimer();
                    } else if (state == PeerConnection.PeerConnectionState.FAILED) tvCallTimer.setText("Connection Lost");
                    else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) tvCallTimer.setText("Reconnecting...");
                });
            }
            @Override public void onScreenShareStopped() {
                runOnUiThread(() -> { stopService(new Intent(ChatActivity.this, ScreenShareService.class)); btnScreenShare.clearColorFilter(); });
            }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show()); }
        });
    }

    private void setLocalVideoFullscreen(boolean fullscreen) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) localVideoView.getLayoutParams();
        localVideoView.setTranslationX(0f); localVideoView.setTranslationY(0f);
        if (fullscreen) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT; params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.NO_GRAVITY; params.setMargins(0, 0, 0, 0); localVideoView.setClipToOutline(false);
        } else {
            params.width = dpToPx(112); params.height = dpToPx(168);
            params.gravity = Gravity.TOP | Gravity.END; params.setMargins(0, dpToPx(92), dpToPx(16), 0); localVideoView.setClipToOutline(true);
        }
        localVideoView.setLayoutParams(params);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggableLocalView() {
        localVideoView.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY, downX, downY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (view.getWidth() >= callOverlay.getWidth() - 20) return false;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX(); downY = event.getRawY();
                    dX = view.getX() - downX; dY = view.getY() - downY; return true;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    view.setX(event.getRawX() + dX); view.setY(event.getRawY() + dY); return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (Math.abs(event.getRawX() - downX) < 12 && Math.abs(event.getRawY() - downY) < 12) {
                        webRTCManager.swapVideoViews();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void requestCallPermissionsAndRun(Runnable action) {
        if (!isGroup && (iBlockedPeer || peerBlockedMe)) { Toast.makeText(this, "Call unavailable (blocked)", Toast.LENGTH_SHORT).show(); return; }
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!needed.isEmpty()) { pendingPermissionAction = action; permissionLauncher.launch(needed.toArray(new String[0])); }
        else action.run();
    }

    private void checkPermissionsAndStartCall(String type) { requestCallPermissionsAndRun(() -> startCall(type)); }

    @SuppressWarnings("unchecked")
    private void startCall(String type) {
        isCallerCurrent = true; activeCallType = type; hasRemoteSdp = false;
        if (incomingListener != null) { incomingListener.remove(); incomingListener = null; }
        logCallHistory(type);
        setupCallUI(type, tvChatName.getText().toString(), true);

        cancelCallTimeout();
        callTimeoutRunnable = () -> {
            if (callOverlay.getVisibility() == View.VISIBLE && !hasRemoteSdp && timerRunnable == null) { Toast.makeText(this, "No answer", Toast.LENGTH_SHORT).show(); hangupCall(true); }
        };
        timerHandler.postDelayed(callTimeoutRunnable, 45000);

        clearOldCandidates(() -> {
            webRTCManager.startLocalMedia("video".equals(type), localVideoView, remoteVideoView);
            webRTCManager.createPeerConnection();
            scheduleBluetoothRouteRetries();
            webRTCManager.createOffer(offer -> {
                Map<String, Object> offerMap = new HashMap<>(); offerMap.put("type", offer.type.canonicalForm()); offerMap.put("sdp", offer.description);
                Map<String, Object> callData = new HashMap<>();
                callData.put("callerId", myUid); callData.put("callerName", myName);
                callData.put("receiverId", isGroup ? chatId : peerUid);
                callData.put("receiverName", tvChatName.getText().toString()); callData.put("type", type);
                callData.put("status", "calling"); callData.put("offer", offerMap); callData.put("createdAt", System.currentTimeMillis());

                DocumentReference callDoc = db.collection("calls").document(callDocId);
                callDoc.set(callData).addOnSuccessListener(unused -> runOnUiThread(() -> {
                    if (callListener != null) callListener.remove();
                    callListener = callDoc.addSnapshotListener((snap, err) -> {
                        if (snap == null || !snap.exists()) return;
                        handleRemoteScreenRequest(snap);
                        if ("connected".equals(snap.getString("status")) && !hasRemoteSdp) {
                            Map<String, Object> ans = (Map<String, Object>) snap.get("answer");
                            if (ans != null) {
                                hasRemoteSdp = true; cancelCallTimeout();
                                webRTCManager.setRemoteDescription((String) ans.get("type"), (String) ans.get("sdp"), () ->
                                        runOnUiThread(() -> { if (timerRunnable == null) tvCallTimer.setText("Connecting..."); }));
                            }
                        } else if ("ended".equals(snap.getString("status"))) hangupCall(false);
                    });
                    listenCandidates("calleeCandidates");
                }));
            });
        });
    }

    private void cancelCallTimeout() { if (callTimeoutRunnable != null) { timerHandler.removeCallbacks(callTimeoutRunnable); callTimeoutRunnable = null; } }

    private void listenIncomingCalls() {
        if (incomingListener != null) incomingListener.remove();
        incomingListener = db.collection("calls").document(callDocId).addSnapshotListener((snap, err) -> {
            if (snap == null || !snap.exists()) return;
            handleRemoteScreenRequest(snap);
            String status = snap.getString("status"), callerId = snap.getString("callerId");
            Long createdAt = snap.getLong("createdAt");
            boolean isFresh = createdAt == null || Math.abs(System.currentTimeMillis() - createdAt) < 180000;
            if ("calling".equals(status) && isFresh && !myUid.equals(callerId) && callOverlay.getVisibility() != View.VISIBLE && !isAutoAccepting && !iBlockedPeer) {
                incomingOverlay.setVisibility(View.VISIBLE);
                tvCallerName.setText(snap.getString("callerName"));
                tvCallType.setText("video".equals(snap.getString("type")) ? "WhatsApp video call" : "WhatsApp voice call");
                playRingtone();
                findViewById(R.id.btnAcceptCall).setOnClickListener(v -> acceptIncomingCallWithPermission(snap));
                findViewById(R.id.btnRejectCall).setOnClickListener(v -> { stopRingtone(); incomingOverlay.setVisibility(View.GONE); snap.getReference().update("status", "ended"); });
            } else if ("ended".equals(status)) {
                stopRingtone(); isAutoAccepting = false; incomingOverlay.setVisibility(View.GONE);
                if (callOverlay.getVisibility() == View.VISIBLE) hangupCall(false);
            }
        });
    }

    private void acceptIncomingCallWithPermission(DocumentSnapshot snap) { requestCallPermissionsAndRun(() -> acceptIncomingCall(snap)); }

    @SuppressWarnings("unchecked")
    private void acceptIncomingCall(DocumentSnapshot snap) {
        stopRingtone(); NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null) nm.cancel(2002);
        isAutoAccepting = false; incomingOverlay.setVisibility(View.GONE); isCallerCurrent = false;
        activeCallType = snap.getString("type"); if (activeCallType == null) activeCallType = "video";

        setupCallUI(activeCallType, snap.getString("callerName"), false);
        webRTCManager.startLocalMedia("video".equals(activeCallType), localVideoView, remoteVideoView);
        webRTCManager.createPeerConnection(); scheduleBluetoothRouteRetries();

        Map<String, Object> offer = (Map<String, Object>) snap.get("offer");
        if (offer != null) {
            webRTCManager.setRemoteDescription((String) offer.get("type"), (String) offer.get("sdp"), () ->
                webRTCManager.createAnswer(answer -> {
                    Map<String, Object> ansMap = new HashMap<>(); ansMap.put("type", answer.type.canonicalForm()); ansMap.put("sdp", answer.description);
                    Map<String, Object> updates = new HashMap<>(); updates.put("answer", ansMap); updates.put("status", "connected");
                    db.collection("calls").document(callDocId).update(updates).addOnSuccessListener(u ->
                            runOnUiThread(() -> { listenCandidates("callerCandidates"); tvCallTimer.setText("Connecting..."); }));
                }));
        }
    }

    private void listenCandidates(String subCol) {
        if (candListener != null) candListener.remove();
        candListener = db.collection("calls").document(callDocId).collection(subCol).addSnapshotListener((snaps, err) -> {
            if (snaps == null) return;
            for (DocumentChange dc : snaps.getDocumentChanges()) {
                if (dc.getType() == DocumentChange.Type.ADDED) {
                    String c = dc.getDocument().getString("candidate"), mid = dc.getDocument().getString("sdpMid");
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
        log.put("receiverId", isGroup ? chatId : peerUid);
        log.put("receiverName", tvChatName.getText().toString());
        log.put("participants", isGroup ? groupMembers : Arrays.asList(myUid, peerUid));
        log.put("type", type); log.put("timestamp", System.currentTimeMillis());
        db.collection("call_logs").add(log);
    }

    private void playRingtone() {
        try {
            String customUri = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("ring_" + peerUid, null);
            Uri uri = customUri != null ? Uri.parse(customUri) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null && !ringtone.isPlaying()) ringtone.play();
        } catch (Exception ignored) {}
    }

    private void stopRingtone() { try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {} }

    private void setupCallUI(String type, String targetName, boolean isOutgoing) {
        selectedAudioRoute = "auto"; isMuted = false; isCamOff = false;
        btnMic.setImageResource(R.drawable.ic_mic); btnMic.setAlpha(1.0f); btnCam.setAlpha(1.0f); btnScreenShare.clearColorFilter();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        acquireWakeLocks("audio".equals(type));

        Intent callSvc = new Intent(this, ActiveCallService.class); callSvc.putExtra("peerName", targetName);
        ContextCompat.startForegroundService(this, callSvc);

        minimizeCallUI(false); callOverlay.setVisibility(View.VISIBLE);
        tvActiveTarget.setText(targetName != null ? targetName : "Friend");
        tvCallTimer.setText(isOutgoing ? "Calling..." : "Connecting...");

        registerBluetoothListener(); routeAudioForCall(); scheduleBluetoothRouteRetries();

        boolean isVid = "video".equals(type);
        localVideoView.setVisibility(isVid ? View.VISIBLE : View.GONE);
        remoteVideoView.setVisibility(isVid ? View.VISIBLE : View.GONE);
        audioCallAvatarBox.setVisibility(isVid ? View.GONE : View.VISIBLE);
        if (isVid) setLocalVideoFullscreen(isOutgoing);
    }

    private void acquireWakeLocks(boolean enableProximity) {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            if (callWakeLock == null) callWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhatsApp::ActiveCall");
            if (!callWakeLock.isHeld()) callWakeLock.acquire(60 * 60 * 1000L);
            if (enableProximity) {
                if (proximityWakeLock == null) proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "WhatsApp::Proximity");
                if (!proximityWakeLock.isHeld()) proximityWakeLock.acquire(60 * 60 * 1000L);
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLocks() {
        try { if (callWakeLock != null && callWakeLock.isHeld()) callWakeLock.release(); } catch (Exception ignored) {}
        try { if (proximityWakeLock != null && proximityWakeLock.isHeld()) proximityWakeLock.release(); } catch (Exception ignored) {}
    }

    private void minimizeCallUI(boolean minimize) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) callOverlay.getLayoutParams();
        if (minimize) {
            params.width = dpToPx(145); params.height = dpToPx(205); params.gravity = Gravity.BOTTOM | Gravity.END;
            params.setMargins(0, 0, dpToPx(16), dpToPx(80)); callTopBar.setVisibility(View.GONE); callBottomBar.setVisibility(View.GONE); btnMaximize.setVisibility(View.VISIBLE);
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT; params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.NO_GRAVITY; params.setMargins(0, 0, 0, 0); callTopBar.setVisibility(View.VISIBLE); callBottomBar.setVisibility(View.VISIBLE); btnMaximize.setVisibility(View.GONE);
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
        stopRingtone(); cancelCallTimeout(); isAutoAccepting = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        releaseWakeLocks();
        stopService(new Intent(this, ActiveCallService.class)); stopService(new Intent(this, ScreenShareService.class));
        btnScreenShare.clearColorFilter();
        if (timerRunnable != null) { timerHandler.removeCallbacks(timerRunnable); timerRunnable = null; }
        if (callListener != null) { callListener.remove(); callListener = null; }
        if (candListener != null) { candListener.remove(); candListener = null; }
        webRTCManager.endCall();
        localVideoView.clearImage(); remoteVideoView.clearImage();
        callOverlay.setVisibility(View.GONE); resetAudioRouting();
        if (notifyFirebase && callDocId != null) db.collection("calls").document(callDocId).update("status", "ended");
        listenIncomingCalls();
    }

    @Override protected void onStart() { super.onStart(); isChatOpen = true; }
    @Override protected void onStop() { super.onStop(); isChatOpen = false; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingtone(); cancelCallTimeout(); releaseWakeLocks();
        stopService(new Intent(this, ActiveCallService.class)); stopService(new Intent(this, ScreenShareService.class));
        if (callOverlay != null && callOverlay.getVisibility() == View.VISIBLE) hangupCall(true);
        else resetAudioRouting();
        if (!isGroup && myUid != null) db.collection("users").document(myUid).update("typingTo", "");
        if (msgListener != null) msgListener.remove();
        if (peerListener != null) peerListener.remove();
        if (incomingListener != null) incomingListener.remove();
        if (blockListener1 != null) blockListener1.remove();
        if (blockListener2 != null) blockListener2.remove();
        if (recordRunnable != null) recordHandler.removeCallbacks(recordRunnable);
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (adapter != null) adapter.releasePlayer();
        if (webRTCManager != null) webRTCManager.destroyFactory();
        if (localVideoView != null) localVideoView.release();
        if (remoteVideoView != null) remoteVideoView.release();
        if (eglBase != null) eglBase.release();
    }
}