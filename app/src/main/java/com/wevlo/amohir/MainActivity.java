package com.wevlo.amohir;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String myName, myAbout, myAvatarBase64;

    private TextView tvAppTitle, tvCallerName, tvCallType, chipAll, chipOnline, chipGroups;
    private TextView tvStoryTextFull, tvStoryAuthor, tvStoryTime;
    private EditText etSearch;
    private BottomNavigationView bottomNav;
    private LinearLayout chatsContainer, statusContainer;
    private RecyclerView rvChats, rvStatuses, rvCalls;
    private FrameLayout incomingOverlay, statusViewerOverlay;
    private ImageButton fabAction;
    private ImageView ivMyStatusAvatar, ivStoryFull;
    private ProgressBar storyProgress;

    private final List<UserModel> allUsers = new ArrayList<>();
    private final List<UserModel> filteredUsers = new ArrayList<>();
    private final Map<String, String> lastMsgMap = new HashMap<>();
    private final Map<String, Long> lastTimeMap = new HashMap<>();
    private UserAdapter userAdapter;

    private final List<StatusModel> statusList = new ArrayList<>();
    private StatusAdapter statusAdapter;

    private final List<CallLogModel> callList = new ArrayList<>();
    private CallLogAdapter callAdapter;

    private String currentFilter = "all";
    private boolean isPickingAvatar = false;
    private Uri cameraStatusUri;
    private Ringtone ringtone;
    private final Handler storyHandler = new Handler(Looper.getMainLooper());
    private Runnable storyRunnable;

    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {});

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String b64 = compressUriToBase64(uri);
                    if (b64 != null) {
                        if (isPickingAvatar) {
                            myAvatarBase64 = b64;
                            saveProfileToPrefsAndFirestore();
                            updateMyStatusAvatar();
                            Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show();
                        } else {
                            postStatusToFirestore("", b64);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Uri> cameraStatusLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && cameraStatusUri != null) {
                    String b64 = compressUriToBase64(cameraStatusUri);
                    if (b64 != null) postStatusToFirestore("", b64);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFirebase();
        initViews();
        initAuth();
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
        tvAppTitle = findViewById(R.id.tvAppTitle);
        etSearch = findViewById(R.id.etSearch);
        bottomNav = findViewById(R.id.bottomNav);
        chatsContainer = findViewById(R.id.chatsContainer);
        statusContainer = findViewById(R.id.statusContainer);
        rvChats = findViewById(R.id.rvChats);
        rvStatuses = findViewById(R.id.rvStatuses);
        rvCalls = findViewById(R.id.rvCalls);
        fabAction = findViewById(R.id.fabAction);
        ivMyStatusAvatar = findViewById(R.id.ivMyStatusAvatar);

        chipAll = findViewById(R.id.chipAll);
        chipOnline = findViewById(R.id.chipOnline);
        chipGroups = findViewById(R.id.chipGroups);

        incomingOverlay = findViewById(R.id.incomingOverlay);
        tvCallerName = findViewById(R.id.tvCallerName);
        tvCallType = findViewById(R.id.tvCallType);

        statusViewerOverlay = findViewById(R.id.statusViewerOverlay);
        ivStoryFull = findViewById(R.id.ivStoryFull);
        tvStoryTextFull = findViewById(R.id.tvStoryTextFull);
        tvStoryAuthor = findViewById(R.id.tvStoryAuthor);
        tvStoryTime = findViewById(R.id.tvStoryTime);
        storyProgress = findViewById(R.id.storyProgress);

        userAdapter = new UserAdapter(filteredUsers, user -> {
            Intent i = new Intent(MainActivity.this, ChatActivity.class);
            i.putExtra("peerUid", user.getUid());
            i.putExtra("peerName", user.getName());
            i.putExtra("peerAvatar", user.getAvatarBase64());
            i.putExtra("isGlobal", user.isGlobalRoom());
            startActivity(i);
        });
        rvChats.setLayoutManager(new LinearLayoutManager(this));
        rvChats.setAdapter(userAdapter);

        statusAdapter = new StatusAdapter(statusList, this::openFullscreenStory);
        rvStatuses.setLayoutManager(new LinearLayoutManager(this));
        rvStatuses.setAdapter(statusAdapter);

        callAdapter = new CallLogAdapter(callList, null);
        rvCalls.setLayoutManager(new LinearLayoutManager(this));
        rvCalls.setAdapter(callAdapter);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            chatsContainer.setVisibility(id == R.id.nav_chats ? View.VISIBLE : View.GONE);
            statusContainer.setVisibility(id == R.id.nav_updates ? View.VISIBLE : View.GONE);
            rvCalls.setVisibility(id == R.id.nav_calls ? View.VISIBLE : View.GONE);

            if (id == R.id.nav_chats) {
                tvAppTitle.setText("WhatsApp");
                fabAction.setImageResource(R.drawable.ic_chat);
            } else if (id == R.id.nav_updates) {
                tvAppTitle.setText("Updates");
                fabAction.setImageResource(R.drawable.ic_camera);
            } else {
                tvAppTitle.setText("Calls");
                fabAction.setImageResource(R.drawable.ic_calls);
            }
            return true;
        });

        chipAll.setOnClickListener(v -> selectChip("all"));
        chipOnline.setOnClickListener(v -> selectChip("online"));
        chipGroups.setOnClickListener(v -> selectChip("groups"));

        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            if (etSearch.getVisibility() == View.GONE) {
                tvAppTitle.setVisibility(View.GONE);
                etSearch.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
            } else {
                etSearch.setText("");
                etSearch.setVisibility(View.GONE);
                tvAppTitle.setVisibility(View.VISIBLE);
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnCameraTop).setOnClickListener(v -> openCameraStatus());
        findViewById(R.id.btnProfileEdit).setOnClickListener(v -> showProfileSettingsDialog());
        findViewById(R.id.btnMyStatus).setOnClickListener(v -> showAddStatusDialog());
        findViewById(R.id.btnCloseStory).setOnClickListener(v -> closeFullscreenStory());
        statusViewerOverlay.setOnClickListener(v -> closeFullscreenStory());

        fabAction.setOnClickListener(v -> {
            int sel = bottomNav.getSelectedItemId();
            if (sel == R.id.nav_updates) {
                showAddStatusDialog();
            } else {
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra("isGlobal", true);
                i.putExtra("peerName", "Global Community");
                startActivity(i);
            }
        });
    }

    private void selectChip(String chip) {
        currentFilter = chip;
        chipAll.setBackgroundResource("all".equals(chip) ? R.drawable.bg_wa_chip_active : R.drawable.bg_wa_chip);
        chipAll.setTextColor(Color.parseColor("all".equals(chip) ? "#25D366" : "#8696A0"));

        chipOnline.setBackgroundResource("online".equals(chip) ? R.drawable.bg_wa_chip_active : R.drawable.bg_wa_chip);
        chipOnline.setTextColor(Color.parseColor("online".equals(chip) ? "#25D366" : "#8696A0"));

        chipGroups.setBackgroundResource("groups".equals(chip) ? R.drawable.bg_wa_chip_active : R.drawable.bg_wa_chip);
        chipGroups.setTextColor(Color.parseColor("groups".equals(chip) ? "#25D366" : "#8696A0"));
        applyFilters();
    }

    private void initAuth() {
        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        myName = prefs.getString("name", "User " + (int) (100 + Math.random() * 900));
        myAbout = prefs.getString("about", "Hey there! I am using WhatsApp.");
        myAvatarBase64 = prefs.getString("avatar", "");
        updateMyStatusAvatar();

        auth.signInAnonymously().addOnSuccessListener(res -> {
            currentUser = res.getUser();
            if (currentUser != null) {
                callAdapter.setMyUid(currentUser.getUid());
                saveProfileToPrefsAndFirestore();
                listenChatPreviews();
                listenUsers();
                listenStatuses();
                listenCallLogs();
                listenGlobalIncomingCalls();
            }
        }).addOnFailureListener(e -> Toast.makeText(this, "Auth error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void updateMyStatusAvatar() {
        if (myAvatarBase64 != null && !myAvatarBase64.isEmpty()) {
            try {
                byte[] b = Base64.decode(myAvatarBase64, Base64.DEFAULT);
                ivMyStatusAvatar.setPadding(0, 0, 0, 0);
                ivMyStatusAvatar.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
            } catch (Exception ignored) {}
        }
    }

    private void saveProfileToPrefsAndFirestore() {
        getSharedPreferences("DivineWA", MODE_PRIVATE).edit()
                .putString("name", myName).putString("about", myAbout)
                .putString("avatar", myAvatarBase64 != null ? myAvatarBase64 : "").apply();
        if (currentUser == null) return;
        Map<String, Object> map = new HashMap<>();
        map.put("uid", currentUser.getUid());
        map.put("name", myName);
        map.put("about", myAbout);
        map.put("avatarBase64", myAvatarBase64 != null ? myAvatarBase64 : "");
        map.put("online", true);
        map.put("lastSeen", System.currentTimeMillis());
        db.collection("users").document(currentUser.getUid()).set(map, SetOptions.merge());
    }

    private void listenChatPreviews() {
        if (currentUser == null) return;
        db.collection("chats").whereArrayContains("participants", currentUser.getUid())
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null) return;
                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        String otherId = d.getId().replace(currentUser.getUid(), "").replace("_", "");
                        String lm = d.getString("lastMessage");
                        Long lt = d.getLong("lastTimestamp");
                        if (lm != null) lastMsgMap.put(otherId, lm);
                        if (lt != null) lastTimeMap.put(otherId, lt);
                    }
                    syncPreviewsIntoUsers();
                });
    }

    private void listenUsers() {
        db.collection("users").addSnapshotListener((snaps, err) -> {
            if (snaps == null || currentUser == null) return;
            allUsers.clear();

            UserModel global = new UserModel();
            global.setUid("global_room");
            global.setName("Global Community Chat");
            global.setAbout("Tap to chat & call with everyone!");
            global.setGlobalRoom(true);
            allUsers.add(global);

            for (DocumentSnapshot d : snaps.getDocuments()) {
                UserModel u = d.toObject(UserModel.class);
                if (u != null && !currentUser.getUid().equals(u.getUid())) {
                    allUsers.add(u);
                }
            }
            syncPreviewsIntoUsers();
        });
    }

    private void syncPreviewsIntoUsers() {
        for (UserModel u : allUsers) {
            if (!u.isGlobalRoom() && u.getUid() != null) {
                if (lastMsgMap.containsKey(u.getUid())) u.setLastMessage(lastMsgMap.get(u.getUid()));
                if (lastTimeMap.containsKey(u.getUid())) u.setLastMsgTime(lastTimeMap.get(u.getUid()));
            }
        }
        applyFilters();
    }

    private void applyFilters() {
        filteredUsers.clear();
        String query = etSearch.getText().toString().toLowerCase().trim();
        for (UserModel u : allUsers) {
            boolean matchesQuery = query.isEmpty() || (u.getName() != null && u.getName().toLowerCase().contains(query));
            boolean matchesChip = "all".equals(currentFilter)
                    || ("online".equals(currentFilter) && u.isOnline())
                    || ("groups".equals(currentFilter) && u.isGlobalRoom());
            if (matchesQuery && matchesChip) filteredUsers.add(u);
        }
        userAdapter.notifyDataSetChanged();
    }

    private void listenStatuses() {
        db.collection("statuses").orderBy("timestamp", Query.Direction.DESCENDING).limit(40)
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null) return;
                    statusList.clear();
                    long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        StatusModel s = d.toObject(StatusModel.class);
                        if (s != null && s.getTimestamp() >= cutoff) {
                            s.setId(d.getId());
                            statusList.add(s);
                        }
                    }
                    statusAdapter.notifyDataSetChanged();
                });
    }

    private void listenCallLogs() {
        db.collection("call_logs").orderBy("timestamp", Query.Direction.DESCENDING).limit(40)
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null || currentUser == null) return;
                    callList.clear();
                    String uid = currentUser.getUid();
                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        CallLogModel c = d.toObject(CallLogModel.class);
                        if (c != null && (uid.equals(c.getCallerId()) || uid.equals(c.getReceiverId()) || "global_room".equals(c.getReceiverId()))) {
                            callList.add(c);
                        }
                    }
                    callAdapter.notifyDataSetChanged();
                });
    }

    private void listenGlobalIncomingCalls() {
        db.collection("calls").whereEqualTo("status", "calling")
                .addSnapshotListener((snaps, err) -> {
                    if (snaps == null || currentUser == null || ChatActivity.isChatOpen) {
                        stopRingtone();
                        incomingOverlay.setVisibility(View.GONE);
                        return;
                    }
                    String uid = currentUser.getUid();
                    DocumentSnapshot foundCall = null;
                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        String callerId = d.getString("callerId");
                        String receiverId = d.getString("receiverId");
                        Long createdAt = d.getLong("createdAt");
                        boolean isFresh = createdAt == null || (System.currentTimeMillis() - createdAt) < 60000;
                        if (isFresh && !uid.equals(callerId) && (uid.equals(receiverId) || "live_channel".equals(d.getId()))) {
                            foundCall = d;
                            break;
                        }
                    }
                    if (foundCall != null) {
                        DocumentSnapshot callDoc = foundCall;
                        incomingOverlay.setVisibility(View.VISIBLE);
                        String cname = callDoc.getString("callerName");
                        String ctype = callDoc.getString("type");
                        tvCallerName.setText(cname != null ? cname : "Friend");
                        tvCallType.setText("video".equals(ctype) ? "WhatsApp video call" : "WhatsApp voice call");
                        playRingtone();

                        findViewById(R.id.btnRejectCall).setOnClickListener(v -> {
                            stopRingtone();
                            incomingOverlay.setVisibility(View.GONE);
                            callDoc.getReference().update("status", "ended");
                        });
                        findViewById(R.id.btnAcceptCall).setOnClickListener(v -> {
                            stopRingtone();
                            incomingOverlay.setVisibility(View.GONE);
                            Intent i = new Intent(MainActivity.this, ChatActivity.class);
                            i.putExtra("isGlobal", "live_channel".equals(callDoc.getId()));
                            i.putExtra("peerUid", callDoc.getString("callerId"));
                            i.putExtra("peerName", cname);
                            i.putExtra("autoAcceptCallId", callDoc.getId());
                            startActivity(i);
                        });
                    } else {
                        stopRingtone();
                        incomingOverlay.setVisibility(View.GONE);
                    }
                });
    }

    private void playRingtone() {
        try {
            if (ringtone == null) {
                ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            }
            if (ringtone != null && !ringtone.isPlaying()) ringtone.play();
        } catch (Exception ignored) {}
    }

    private void stopRingtone() {
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Exception ignored) {}
    }

    private void openFullscreenStory(StatusModel s) {
        statusViewerOverlay.setVisibility(View.VISIBLE);
        tvStoryAuthor.setText(s.getUserName() != null ? s.getUserName() : "Friend");
        tvStoryTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(s.getTimestamp())));

        if (s.getImageBase64() != null && !s.getImageBase64().isEmpty()) {
            byte[] b = Base64.decode(s.getImageBase64().replace("data:image/jpeg;base64,", ""), Base64.DEFAULT);
            ivStoryFull.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
            ivStoryFull.setVisibility(View.VISIBLE);
            tvStoryTextFull.setVisibility(View.GONE);
        } else {
            ivStoryFull.setVisibility(View.GONE);
            tvStoryTextFull.setVisibility(View.VISIBLE);
            tvStoryTextFull.setText(s.getText());
        }

        if (storyRunnable != null) storyHandler.removeCallbacks(storyRunnable);
        final long start = System.currentTimeMillis();
        storyProgress.setProgress(0);
        storyRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - start;
                int pct = (int) Math.min(100, (elapsed * 100) / 5000);
                storyProgress.setProgress(pct);
                if (pct < 100) storyHandler.postDelayed(this, 50);
                else closeFullscreenStory();
            }
        };
        storyHandler.post(storyRunnable);
    }

    private void closeFullscreenStory() {
        if (storyRunnable != null) storyHandler.removeCallbacks(storyRunnable);
        statusViewerOverlay.setVisibility(View.GONE);
    }

    private void openCameraStatus() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(new String[]{Manifest.permission.CAMERA});
            return;
        }
        try {
            File f = File.createTempFile("st_", ".jpg", getCacheDir());
            cameraStatusUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            cameraStatusLauncher.launch(cameraStatusUri);
        } catch (Exception e) { Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show(); }
    }

    private void showProfileSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 12);
        EditText etName = new EditText(this); etName.setHint("Your Name"); etName.setText(myName);
        EditText etAbout = new EditText(this); etAbout.setHint("About / Bio"); etAbout.setText(myAbout);
        layout.addView(etName); layout.addView(etAbout);

        new AlertDialog.Builder(this).setTitle("Profile Settings").setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String n = etName.getText().toString().trim(), a = etAbout.getText().toString().trim();
                    if (!n.isEmpty()) myName = n;
                    if (!a.isEmpty()) myAbout = a;
                    saveProfileToPrefsAndFirestore();
                    Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Change Photo", (d, w) -> { isPickingAvatar = true; imagePickerLauncher.launch("image/*"); })
                .setNegativeButton("Cancel", null).show();
    }

    private void showAddStatusDialog() {
        String[] opts = {"📝 Text Status", "🖼️ Gallery Photo", "📷 Camera Photo"};
        new AlertDialog.Builder(this).setTitle("Add Status Update").setItems(opts, (d, which) -> {
            if (which == 0) {
                EditText et = new EditText(this); et.setHint("Type a status...");
                new AlertDialog.Builder(this).setTitle("Text Status").setView(et)
                        .setPositiveButton("Post", (d2, w2) -> {
                            String txt = et.getText().toString().trim();
                            if (!txt.isEmpty()) postStatusToFirestore(txt, null);
                        }).setNegativeButton("Cancel", null).show();
            } else if (which == 1) {
                isPickingAvatar = false; imagePickerLauncher.launch("image/*");
            } else {
                openCameraStatus();
            }
        }).show();
    }

    private void postStatusToFirestore(String text, String imageBase64) {
        if (currentUser == null) return;
        Map<String, Object> map = new HashMap<>();
        map.put("uid", currentUser.getUid());
        map.put("userName", myName);
        map.put("text", text);
        map.put("imageBase64", imageBase64);
        map.put("timestamp", System.currentTimeMillis());
        db.collection("statuses").add(map).addOnSuccessListener(r -> Toast.makeText(this, "Status posted!", Toast.LENGTH_SHORT).show());
    }

    private String compressUriToBase64(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            Bitmap orig = BitmapFactory.decodeStream(is);
            if (orig == null) return null;
            int w = orig.getWidth(), h = orig.getHeight(), max = 450;
            if (w > h && w > max) { h = Math.round((float) h * max / w); w = max; }
            else if (h > max) { w = Math.round((float) w * max / h); h = max; }
            Bitmap res = Bitmap.createScaledBitmap(orig, w, h, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            res.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUser != null) db.collection("users").document(currentUser.getUid()).update("online", true, "lastSeen", System.currentTimeMillis());
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRingtone();
        if (currentUser != null) db.collection("users").document(currentUser.getUid()).update("online", false, "lastSeen", System.currentTimeMillis());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingtone();
        if (storyRunnable != null) storyHandler.removeCallbacks(storyRunnable);
    }
}