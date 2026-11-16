package com.wevlo.amohir;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String myPhone, myName, myAbout, myAvatarBase64;

    private TextView tvAppTitle, tvCallerName, tvCallType, chipAll, chipUnread, chipOnline, chipGroups, tvEmptyChats;
    private TextView tvStoryTextFull, tvStoryAuthor, tvStoryTime;
    private EditText etSearch;
    private BottomNavigationView bottomNav;
    private LinearLayout chatsContainer, statusContainer;
    private RecyclerView rvChats, rvStatuses, rvCalls;
    private FrameLayout incomingOverlay, statusViewerOverlay;
    private ImageButton fabAction;
    private ImageView ivMyStatusAvatar, ivStoryFull;
    private ProgressBar storyProgress;

    private final Map<String, UserModel> peerUsersMap = new LinkedHashMap<>();
    private final Map<String, UserModel> groupChatsMap = new LinkedHashMap<>();
    private final Map<String, ListenerRegistration> peerListeners = new HashMap<>();
    private final Map<String, String> lastMsgMap = new HashMap<>();
    private final Map<String, Long> lastTimeMap = new HashMap<>();
    private final Map<String, Integer> unreadMap = new HashMap<>();
    private final List<UserModel> filteredUsers = new ArrayList<>();
    private UserAdapter userAdapter;

    private final List<StatusModel> statusList = new ArrayList<>();
    private StatusAdapter statusAdapter;
    private final List<CallLogModel> callList = new ArrayList<>();
    private CallLogAdapter callAdapter;

    private String currentFilter = "all";
    private boolean isPickingAvatar = false;
    private Uri cameraStatusUri;
    private ListenerRegistration updateListener;
    private final Handler storyHandler = new Handler(Looper.getMainLooper());
    private Runnable storyRunnable;

    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (myPhone != null && !myPhone.isEmpty()) ContextCompat.startForegroundService(this, new Intent(this, WhatsAppBgService.class));
            });

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String b64 = compressUriToBase64(uri);
                    if (b64 != null) {
                        if (isPickingAvatar) {
                            myAvatarBase64 = b64; saveProfileToPrefsAndFirestore(); updateMyStatusAvatar();
                            Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show();
                        } else postStatusToFirestore("", b64);
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

        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        myPhone = prefs.getString("my_phone", "");
        if (myPhone.isEmpty()) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }

        setContentView(R.layout.activity_main);
        initFirebase();
        initViews();

        // LOCK logic is completely removed. Direct entry to app.
        proceedAfterUnlock();
    }

    private void proceedAfterUnlock() {
        askRequiredPermissionsAndStartService();
        initSession();
    }

    private void initFirebase() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey("AIzaSyAmLJ5Vdd6Pqm-FrPfANvjy1TZkhG1mxns")
                    .setApplicationId("1:367706014019:web:f4e33a639f4573262baff6")
                    .setProjectId("good-865a2").setStorageBucket("good-865a2.firebasestorage.app").setGcmSenderId("367706014019").build();
            FirebaseApp.initializeApp(this, options);
        }
        auth = FirebaseAuth.getInstance(); db = FirebaseFirestore.getInstance();
    }

    @SuppressLint("BatteryLife")
    private void askRequiredPermissionsAndStartService() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO);
        if (!perms.isEmpty()) permLauncher.launch(perms.toArray(new String[0]));

        ContextCompat.startForegroundService(this, new Intent(this, WhatsAppBgService.class));

        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        if (!prefs.getBoolean("battery_asked", false)) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                prefs.edit().putBoolean("battery_asked", true).apply();
                try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
            }
        }
    }

    private void initViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle); etSearch = findViewById(R.id.etSearch);
        bottomNav = findViewById(R.id.bottomNav); chatsContainer = findViewById(R.id.chatsContainer);
        statusContainer = findViewById(R.id.statusContainer); rvChats = findViewById(R.id.rvChats);
        tvEmptyChats = findViewById(R.id.tvEmptyChats); rvStatuses = findViewById(R.id.rvStatuses);
        rvCalls = findViewById(R.id.rvCalls); fabAction = findViewById(R.id.fabAction);
        ivMyStatusAvatar = findViewById(R.id.ivMyStatusAvatar);

        chipAll = findViewById(R.id.chipAll); chipUnread = findViewById(R.id.chipUnread);
        chipOnline = findViewById(R.id.chipOnline); chipGroups = findViewById(R.id.chipGroups);

        incomingOverlay = findViewById(R.id.incomingOverlay); tvCallerName = findViewById(R.id.tvCallerName); tvCallType = findViewById(R.id.tvCallType);
        statusViewerOverlay = findViewById(R.id.statusViewerOverlay); ivStoryFull = findViewById(R.id.ivStoryFull);
        tvStoryTextFull = findViewById(R.id.tvStoryTextFull); tvStoryAuthor = findViewById(R.id.tvStoryAuthor);
        tvStoryTime = findViewById(R.id.tvStoryTime); storyProgress = findViewById(R.id.storyProgress);

        userAdapter = new UserAdapter(filteredUsers, new UserAdapter.OnUserClickListener() {
            @Override public void onUserClick(UserModel user) {
                if (user.isGlobalRoom()) openGroupChat(user.getUid(), user.getName());
                else openPrivateChat(user.getUid(), user.getName(), user.getAvatarBase64(), null);
            }
            @Override public void onAvatarClick(UserModel user) {
                if (user.isGlobalRoom()) openGroupChat(user.getUid(), user.getName());
                else ContactInfoDialog.show(MainActivity.this, user.getUid(), user.getName(), new ContactInfoDialog.ActionCallback() {
                    @Override public void onAudioCall() { openPrivateChat(user.getUid(), user.getName(), user.getAvatarBase64(), "audio"); }
                    @Override public void onVideoCall() { openPrivateChat(user.getUid(), user.getName(), user.getAvatarBase64(), "video"); }
                });
            }
            @Override public void onUserLongClick(UserModel user) {
                showChatOptionsDialog(user);
            }
        });
        rvChats.setLayoutManager(new LinearLayoutManager(this)); rvChats.setAdapter(userAdapter);

        statusAdapter = new StatusAdapter(statusList, this::openFullscreenStory);
        rvStatuses.setLayoutManager(new LinearLayoutManager(this)); rvStatuses.setAdapter(statusAdapter);

        callAdapter = new CallLogAdapter(callList, myPhone);
        rvCalls.setLayoutManager(new LinearLayoutManager(this)); rvCalls.setAdapter(callAdapter);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            chatsContainer.setVisibility(id == R.id.nav_chats ? View.VISIBLE : View.GONE);
            statusContainer.setVisibility(id == R.id.nav_updates ? View.VISIBLE : View.GONE);
            rvCalls.setVisibility(id == R.id.nav_calls ? View.VISIBLE : View.GONE);
            if (id == R.id.nav_chats) { tvAppTitle.setText("WhatsApp"); fabAction.setImageResource(R.drawable.ic_chat); }
            else if (id == R.id.nav_updates) { tvAppTitle.setText("Updates"); fabAction.setImageResource(R.drawable.ic_camera); }
            else { tvAppTitle.setText("Calls"); fabAction.setImageResource(R.drawable.ic_calls); }
            return true;
        });

        chipAll.setOnClickListener(v -> selectChip("all"));
        chipUnread.setOnClickListener(v -> selectChip("unread"));
        chipOnline.setOnClickListener(v -> selectChip("online"));
        chipGroups.setOnClickListener(v -> selectChip("groups"));

        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            if (etSearch.getVisibility() == View.GONE) { tvAppTitle.setVisibility(View.GONE); etSearch.setVisibility(View.VISIBLE); etSearch.requestFocus(); }
            else { etSearch.setText(""); etSearch.setVisibility(View.GONE); tvAppTitle.setVisibility(View.VISIBLE); }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnCameraTop).setOnClickListener(v -> openCameraStatus());
        findViewById(R.id.btnProfileEdit).setOnClickListener(v -> showMainMenuDialog());
        findViewById(R.id.btnMyStatus).setOnClickListener(v -> showAddStatusDialog());
        findViewById(R.id.btnCloseStory).setOnClickListener(v -> closeFullscreenStory());
        statusViewerOverlay.setOnClickListener(v -> closeFullscreenStory());

        fabAction.setOnClickListener(v -> {
            if (bottomNav.getSelectedItemId() == R.id.nav_updates) showAddStatusDialog();
            else showFindUserByPhoneDialog();
        });
    }

    private void showChatOptionsDialog(UserModel user) {
        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        boolean isPinned = prefs.getBoolean("pin_" + user.getUid(), false);
        long mutedUntil = prefs.getLong("mute_" + user.getUid(), 0);
        boolean isMuted = mutedUntil > System.currentTimeMillis();

        String[] options = {
                isPinned ? "📌 Unpin Chat" : "📌 Pin Chat",
                isMuted ? "🔔 Unmute Notifications" : "🔕 Mute Notifications (8 Hours)",
                "🗑️ Delete Chat"
        };

        new AlertDialog.Builder(this)
                .setTitle(user.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        prefs.edit().putBoolean("pin_" + user.getUid(), !isPinned).apply();
                        applyFilters();
                    } else if (which == 1) {
                        prefs.edit().putLong("mute_" + user.getUid(), isMuted ? 0 : System.currentTimeMillis() + (8 * 3600 * 1000L)).apply();
                        applyFilters();
                    } else {
                        String cId = user.isGlobalRoom() ? user.getUid() : (myPhone.compareTo(user.getUid()) < 0 ? myPhone + "_" + user.getUid() : user.getUid() + "_" + myPhone);
                        db.collection("chats").document(cId).delete();
                    }
                }).show();
    }

    private void selectChip(String chip) {
        currentFilter = chip;
        styleChip(chipAll, "all".equals(chip));
        styleChip(chipUnread, "unread".equals(chip));
        styleChip(chipOnline, "online".equals(chip));
        styleChip(chipGroups, "groups".equals(chip));
        applyFilters();
    }

    private void styleChip(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_wa_chip_active : R.drawable.bg_wa_chip);
        tv.setTextColor(Color.parseColor(active ? "#25D366" : "#8696A0"));
    }

    private void initSession() {
        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        myName = prefs.getString("name", "User");
        myAbout = prefs.getString("about", "Hey there! I am using WhatsApp.");
        myAvatarBase64 = prefs.getString("avatar", "");
        updateMyStatusAvatar();

        if (auth.getCurrentUser() != null) startPrivateListeners();
        else auth.signInAnonymously().addOnSuccessListener(res -> startPrivateListeners())
                .addOnFailureListener(e -> Toast.makeText(this, "Auth error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void startPrivateListeners() {
        saveProfileToPrefsAndFirestore();
        updateListener = AutoUpdater.listenForUpdates(this);
        listenPrivateContacts(); listenPrivateChats(); listenPrivateStatuses(); listenPrivateCallLogs(); listenPrivateIncomingCalls();
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

        Map<String, Object> map = new HashMap<>();
        map.put("uid", myPhone); map.put("phone", myPhone); map.put("shortPhone", LoginActivity.getShortPhone(myPhone));
        map.put("name", myName); map.put("about", myAbout);
        map.put("avatarBase64", myAvatarBase64 != null ? myAvatarBase64 : "");
        map.put("online", true); map.put("lastSeen", System.currentTimeMillis());
        db.collection("users").document(myPhone).set(map, SetOptions.merge());
    }

    private void listenPrivateContacts() {
        db.collection("users").document(myPhone).collection("contacts").addSnapshotListener((snaps, err) -> {
            if (snaps == null) return;
            for (DocumentSnapshot d : snaps.getDocuments()) attachPeerListener(d.getId());
        });
    }

    @SuppressWarnings("unchecked")
    private void listenPrivateChats() {
        db.collection("chats").whereArrayContains("participants", myPhone).addSnapshotListener((snaps, err) -> {
            if (snaps == null) return;
            for (DocumentSnapshot d : snaps.getDocuments()) {
                Long uCountLong = d.getLong("unread_" + myPhone);
                int unread = uCountLong != null ? uCountLong.intValue() : 0;
                String lm = d.getString("lastMessage");
                Long lt = d.getLong("lastTimestamp");

                if (Boolean.TRUE.equals(d.getBoolean("isGroup"))) {
                    UserModel grp = new UserModel();
                    grp.setUid(d.getId());
                    grp.setName(d.getString("groupName"));
                    grp.setGlobalRoom(true);
                    grp.setLastMessage(lm);
                    if (lt != null) grp.setLastMsgTime(lt);
                    grp.setUnreadCount(unread);
                    groupChatsMap.put(d.getId(), grp);
                } else {
                    List<String> parts = (List<String>) d.get("participants");
                    if (parts == null) continue;
                    String otherPhone = null;
                    for (String p : parts) if (!myPhone.equals(p)) { otherPhone = p; break; }
                    if (otherPhone != null) {
                        if (lm != null) lastMsgMap.put(otherPhone, lm);
                        if (lt != null) lastTimeMap.put(otherPhone, lt);
                        unreadMap.put(otherPhone, unread);
                        attachPeerListener(otherPhone);
                    }
                }
            }
            applyFilters();
        });
    }

    private void attachPeerListener(String peerPhone) {
        if (peerPhone == null || peerPhone.equals(myPhone) || peerListeners.containsKey(peerPhone)) return;
        ListenerRegistration reg = db.collection("users").document(peerPhone).addSnapshotListener((snap, err) -> {
            if (snap != null && snap.exists()) {
                UserModel u = snap.toObject(UserModel.class);
                if (u != null) { u.setUid(peerPhone); peerUsersMap.put(peerPhone, u); applyFilters(); }
            }
        });
        peerListeners.put(peerPhone, reg);
    }

    private void applyFilters() {
        filteredUsers.clear();
        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        String query = etSearch.getText().toString().toLowerCase().trim();
        List<UserModel> combined = new ArrayList<>();

        for (UserModel u : peerUsersMap.values()) {
            if (lastMsgMap.containsKey(u.getUid())) u.setLastMessage(lastMsgMap.get(u.getUid()));
            if (lastTimeMap.containsKey(u.getUid())) u.setLastMsgTime(lastTimeMap.get(u.getUid()));
            Integer un = unreadMap.get(u.getUid());
            u.setUnreadCount(un != null ? un : 0);
            u.setPinned(prefs.getBoolean("pin_" + u.getUid(), false));
            u.setMutedUntil(prefs.getLong("mute_" + u.getUid(), 0));
            combined.add(u);
        }

        for (UserModel g : groupChatsMap.values()) {
            g.setPinned(prefs.getBoolean("pin_" + g.getUid(), false));
            g.setMutedUntil(prefs.getLong("mute_" + g.getUid(), 0));
            combined.add(g);
        }

        for (UserModel u : combined) {
            boolean matchesQuery = query.isEmpty()
                    || (u.getName() != null && u.getName().toLowerCase().contains(query))
                    || (u.getUid() != null && u.getUid().contains(query));
            boolean matchesChip = "all".equals(currentFilter)
                    || ("unread".equals(currentFilter) && u.getUnreadCount() > 0)
                    || ("online".equals(currentFilter) && !u.isGlobalRoom() && u.isOnline())
                    || ("groups".equals(currentFilter) && u.isGlobalRoom());
            if (matchesQuery && matchesChip) filteredUsers.add(u);
        }

        // Pinned চ্যাটগুলো সবার ওপরে থাকবে
        Collections.sort(filteredUsers, (a, b) -> {
            if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
            return Long.compare(b.getLastMsgTime(), a.getLastMsgTime());
        });

        userAdapter.notifyDataSetChanged();
        tvEmptyChats.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCreateGroupDialog() {
        List<UserModel> contacts = new ArrayList<>(peerUsersMap.values());
        if (contacts.isEmpty()) { Toast.makeText(this, "আগে অন্তত ১ জন বন্ধুকে অ্যাড করুন", Toast.LENGTH_SHORT).show(); return; }

        String[] names = new String[contacts.size()];
        boolean[] checked = new boolean[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) names[i] = contacts.get(i).getName() + " (" + contacts.get(i).getUid() + ")";

        new AlertDialog.Builder(this).setTitle("Select Group Members")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Next", (d, w) -> {
                    List<String> members = new ArrayList<>();
                    members.add(myPhone);
                    for (int i = 0; i < contacts.size(); i++) if (checked[i]) members.add(contacts.get(i).getUid());
                    if (members.size() < 2) { Toast.makeText(this, "কমপক্ষে ১ জন মেম্বার সিলেক্ট করুন", Toast.LENGTH_SHORT).show(); return; }

                    EditText etName = new EditText(this); etName.setHint("Group Name");
                    new AlertDialog.Builder(this).setTitle("Enter Group Name").setView(etName)
                            .setPositiveButton("Create", (d2, w2) -> {
                                String gName = etName.getText().toString().trim();
                                if (gName.isEmpty()) gName = "Private Group";
                                String grpId = "grp_" + System.currentTimeMillis();
                                Map<String, Object> grpData = new HashMap<>();
                                grpData.put("isGroup", true); grpData.put("groupName", gName);
                                grpData.put("participants", members); grpData.put("lastMessage", "Group created by " + myName);
                                grpData.put("lastTimestamp", System.currentTimeMillis());
                                String finalName = gName;
                                db.collection("chats").document(grpId).set(grpData).addOnSuccessListener(u -> openGroupChat(grpId, finalName));
                            }).setNegativeButton("Cancel", null).show();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showFindUserByPhoneDialog() {
        EditText etPhone = new EditText(this);
        etPhone.setHint("Enter friend's mobile number"); etPhone.setInputType(InputType.TYPE_CLASS_PHONE); etPhone.setPadding(40, 28, 40, 20);
        new AlertDialog.Builder(this).setTitle("Find Friend by Number").setMessage("যে নম্বর দিয়ে অ্যাকাউন্ট খোলা আছে সেটি লিখুন:").setView(etPhone)
                .setPositiveButton("Search & Chat", (d, w) -> {
                    String entered = LoginActivity.normalizePhone(etPhone.getText().toString());
                    if (entered.length() < 7) { Toast.makeText(this, "সঠিক নম্বর লিখুন", Toast.LENGTH_SHORT).show(); return; }
                    if (entered.equals(myPhone)) { Toast.makeText(this, "এটি আপনার নিজের নম্বর!", Toast.LENGTH_SHORT).show(); return; }
                    searchUserInFirestore(entered);
                }).setNegativeButton("Cancel", null).show();
    }

    private void searchUserInFirestore(String cleanPhone) {
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show();
        db.collection("users").document(cleanPhone).get().addOnSuccessListener(doc -> {
            if (doc.exists()) onFriendFound(doc);
            else db.collection("users").whereEqualTo("shortPhone", LoginActivity.getShortPhone(cleanPhone)).limit(1).get()
                    .addOnSuccessListener(snaps -> {
                        if (!snaps.isEmpty()) {
                            DocumentSnapshot found = snaps.getDocuments().get(0);
                            if (myPhone.equals(found.getId())) Toast.makeText(this, "এটি আপনার নিজের নম্বর!", Toast.LENGTH_SHORT).show();
                            else onFriendFound(found);
                        } else Toast.makeText(this, "এই নম্বরে কোনো WhatsApp অ্যাকাউন্ট পাওয়া যায়নি", Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void onFriendFound(DocumentSnapshot doc) {
        String friendPhone = doc.getId(), friendName = doc.getString("name"), friendAvatar = doc.getString("avatarBase64");
        Map<String, Object> contact = new HashMap<>(); contact.put("phone", friendPhone); contact.put("addedAt", System.currentTimeMillis());
        db.collection("users").document(myPhone).collection("contacts").document(friendPhone).set(contact);
        attachPeerListener(friendPhone);
        openPrivateChat(friendPhone, friendName, friendAvatar, null);
    }

    private void openPrivateChat(String peerPhone, String peerName, String peerAvatar, String autoStartCall) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("peerUid", peerPhone); i.putExtra("peerName", peerName); i.putExtra("peerAvatar", peerAvatar);
        if (autoStartCall != null) i.putExtra("autoStartCall", autoStartCall);
        startActivity(i);
    }

    private void openGroupChat(String groupId, String groupName) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("isGroup", true); i.putExtra("groupId", groupId); i.putExtra("peerName", groupName);
        startActivity(i);
    }

    private void listenPrivateStatuses() {
        db.collection("statuses").orderBy("timestamp", Query.Direction.DESCENDING).limit(60).addSnapshotListener((snaps, err) -> {
            if (snaps == null) return;
            statusList.clear();
            long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
            Set<String> allowed = new HashSet<>(peerUsersMap.keySet()); allowed.add(myPhone);
            for (DocumentSnapshot d : snaps.getDocuments()) {
                StatusModel s = d.toObject(StatusModel.class);
                if (s != null && s.getTimestamp() >= cutoff && allowed.contains(s.getUid())) { s.setId(d.getId()); statusList.add(s); }
            }
            statusAdapter.notifyDataSetChanged();
        });
    }

    private void listenPrivateCallLogs() {
        db.collection("call_logs").whereArrayContains("participants", myPhone).addSnapshotListener((snaps, err) -> {
            if (snaps == null) return;
            callList.clear();
            for (DocumentSnapshot d : snaps.getDocuments()) { CallLogModel c = d.toObject(CallLogModel.class); if (c != null) callList.add(c); }
            Collections.sort(callList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            callAdapter.notifyDataSetChanged();
        });
    }

    private void listenPrivateIncomingCalls() {
        db.collection("calls").whereEqualTo("receiverId", myPhone).addSnapshotListener((snaps, err) -> {
            if (snaps == null || ChatActivity.isChatOpen) { incomingOverlay.setVisibility(View.GONE); return; }
            DocumentSnapshot foundCall = null;
            for (DocumentSnapshot d : snaps.getDocuments()) {
                Long createdAt = d.getLong("createdAt");
                if ("calling".equals(d.getString("status")) && (createdAt == null || Math.abs(System.currentTimeMillis() - createdAt) < 180000)) { foundCall = d; break; }
            }
            if (foundCall != null) {
                DocumentSnapshot callDoc = foundCall;
                incomingOverlay.setVisibility(View.VISIBLE);
                String cname = callDoc.getString("callerName");
                tvCallerName.setText(cname != null ? cname : callDoc.getString("callerId"));
                tvCallType.setText("video".equals(callDoc.getString("type")) ? "WhatsApp video call" : "WhatsApp voice call");
                findViewById(R.id.btnRejectCall).setOnClickListener(v -> { incomingOverlay.setVisibility(View.GONE); cancelCallNotification(); callDoc.getReference().update("status", "ended"); });
                findViewById(R.id.btnAcceptCall).setOnClickListener(v -> {
                    incomingOverlay.setVisibility(View.GONE); cancelCallNotification();
                    Intent i = new Intent(this, ChatActivity.class);
                    i.putExtra("peerUid", callDoc.getString("callerId")); i.putExtra("peerName", cname); i.putExtra("autoAcceptCallId", callDoc.getId());
                    startActivity(i);
                });
            } else incomingOverlay.setVisibility(View.GONE);
        });
    }

    private void cancelCallNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null) nm.cancel(2002);
    }

    /* 💾 Local Chat Backup & Restore */
    private void backupChatsToLocalFile() {
        Toast.makeText(this, "Backing up chats...", Toast.LENGTH_SHORT).show();
        db.collection("chats").whereArrayContains("participants", myPhone).get().addOnSuccessListener(snaps -> {
            JSONArray arr = new JSONArray();
            for (DocumentSnapshot d : snaps.getDocuments()) {
                JSONObject obj = new JSONObject(d.getData());
                arr.put(obj);
            }
            try {
                File f = new File(getExternalFilesDir(null), "whatsapp_chats_backup.json");
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(arr.toString(2).getBytes());
                }
                Toast.makeText(this, "Backup saved to: " + f.getName(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void restoreChatsFromLocalFile() {
        File f = new File(getExternalFilesDir(null), "whatsapp_chats_backup.json");
        if (!f.exists()) {
            Toast.makeText(this, "No backup file found!", Toast.LENGTH_SHORT).show();
            return;
        }
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            fis.read(buf);
            JSONArray arr = new JSONArray(new String(buf));
            for (int i = 0; i < arr.length; i++) {
                JSONObject obj = arr.getJSONObject(i);
                Map<String, Object> map = new HashMap<>();
                for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                    String k = it.next();
                    map.put(k, obj.get(k));
                }
                String cId = obj.optString("groupId", "chat_" + System.currentTimeMillis() + "_" + i);
                db.collection("chats").document(cId).set(map, SetOptions.merge());
            }
            Toast.makeText(this, "Chats restored successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Restore error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showMainMenuDialog() {
        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        boolean lockOn = prefs.getBoolean("app_lock_enabled", false);
        String[] items = {
                "➕ Add Contact by Number",
                "👥 New Private Group",
                "⭐ Starred Messages",
                "🔐 Fingerprint Lock: " + (lockOn ? "ON" : "OFF"),
                "💾 Backup Chats",
                "📥 Restore Chats",
                "⚙️ Profile Settings (" + myPhone + ")",
                "🚪 Logout / Switch Number"
        };
        new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0) showFindUserByPhoneDialog();
            else if (which == 1) showCreateGroupDialog();
            else if (which == 2) startActivity(new Intent(this, StarredMessagesActivity.class));
            else if (which == 3) {
                prefs.edit().putBoolean("app_lock_enabled", !lockOn).apply();
                Toast.makeText(this, "Fingerprint Lock " + (!lockOn ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            } else if (which == 4) backupChatsToLocalFile();
            else if (which == 5) restoreChatsFromLocalFile();
            else if (which == 6) showProfileSettingsDialog();
            else {
                stopService(new Intent(this, WhatsAppBgService.class));
                db.collection("users").document(myPhone).update("online", false, "lastSeen", System.currentTimeMillis());
                prefs.edit().clear().apply();
                startActivity(new Intent(this, LoginActivity.class)); finish();
            }
        }).show();
    }

    private void openFullscreenStory(StatusModel s) {
        statusViewerOverlay.setVisibility(View.VISIBLE);
        tvStoryAuthor.setText(s.getUserName() != null ? s.getUserName() : "Friend");
        tvStoryTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(s.getTimestamp())));
        if (s.getImageBase64() != null && !s.getImageBase64().isEmpty()) {
            byte[] b = Base64.decode(s.getImageBase64().replace("data:image/jpeg;base64,", ""), Base64.DEFAULT);
            ivStoryFull.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
            ivStoryFull.setVisibility(View.VISIBLE); tvStoryTextFull.setVisibility(View.GONE);
        } else {
            ivStoryFull.setVisibility(View.GONE); tvStoryTextFull.setVisibility(View.VISIBLE); tvStoryTextFull.setText(s.getText());
        }
        if (storyRunnable != null) storyHandler.removeCallbacks(storyRunnable);
        final long start = System.currentTimeMillis(); storyProgress.setProgress(0);
        storyRunnable = new Runnable() {
            @Override public void run() {
                int pct = (int) Math.min(100, ((System.currentTimeMillis() - start) * 100) / 5000);
                storyProgress.setProgress(pct);
                if (pct < 100) storyHandler.postDelayed(this, 50); else closeFullscreenStory();
            }
        };
        storyHandler.post(storyRunnable);
    }

    private void closeFullscreenStory() { if (storyRunnable != null) storyHandler.removeCallbacks(storyRunnable); statusViewerOverlay.setVisibility(View.GONE); }

    private void openCameraStatus() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { permLauncher.launch(new String[]{Manifest.permission.CAMERA}); return; }
        try {
            cameraStatusUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", File.createTempFile("st_", ".jpg", getCacheDir()));
            cameraStatusLauncher.launch(cameraStatusUri);
        } catch (Exception e) { Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show(); }
    }

    private void showProfileSettingsDialog() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 24, 40, 12);
        EditText etName = new EditText(this); etName.setHint("Your Name"); etName.setText(myName);
        EditText etAbout = new EditText(this); etAbout.setHint("About / Bio"); etAbout.setText(myAbout);
        layout.addView(etName); layout.addView(etAbout);
        new AlertDialog.Builder(this).setTitle("Profile (" + myPhone + ")").setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String n = etName.getText().toString().trim(), a = etAbout.getText().toString().trim();
                    if (!n.isEmpty()) myName = n; if (!a.isEmpty()) myAbout = a;
                    saveProfileToPrefsAndFirestore(); Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show();
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
                        .setPositiveButton("Post", (d2, w2) -> { String txt = et.getText().toString().trim(); if (!txt.isEmpty()) postStatusToFirestore(txt, null); })
                        .setNegativeButton("Cancel", null).show();
            } else if (which == 1) { isPickingAvatar = false; imagePickerLauncher.launch("image/*"); }
            else openCameraStatus();
        }).show();
    }

    private void postStatusToFirestore(String text, String imageBase64) {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", myPhone); map.put("userName", myName); map.put("text", text);
        map.put("imageBase64", imageBase64); map.put("timestamp", System.currentTimeMillis());
        db.collection("statuses").add(map).addOnSuccessListener(r -> Toast.makeText(this, "Status posted!", Toast.LENGTH_SHORT).show());
    }

    private String compressUriToBase64(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            Bitmap orig = BitmapFactory.decodeStream(is); if (orig == null) return null;
            int w = orig.getWidth(), h = orig.getHeight(), max = 450;
            if (w > h && w > max) { h = Math.round((float) h * max / w); w = max; } else if (h > max) { w = Math.round((float) w * max / h); h = max; }
            Bitmap res = Bitmap.createScaledBitmap(orig, w, h, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(); res.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }

    @Override protected void onResume() { super.onResume(); if (myPhone != null && !myPhone.isEmpty()) db.collection("users").document(myPhone).update("online", true, "lastSeen", System.currentTimeMillis()); }
    @Override protected void onPause() { super.onPause(); if (myPhone != null && !myPhone.isEmpty()) db.collection("users").document(myPhone).update("online", false, "lastSeen", System.currentTimeMillis()); }
    @Override protected void onDestroy() { super.onDestroy(); if (updateListener != null) updateListener.remove(); if (storyRunnable != null) storyHandler.removeCallbacks(storyRunnable); for (ListenerRegistration r : peerListeners.values()) r.remove(); }
}