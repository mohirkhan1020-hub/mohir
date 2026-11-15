package com.wevlo.amohir;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String myName, myAbout, myAvatarBase64;

    private TextView tvAppTitle, tvCallerName, tvCallType;
    private EditText etSearch;
    private TabLayout tabLayout;
    private RecyclerView rvChats, rvStatuses, rvCalls;
    private LinearLayout statusContainer;
    private FrameLayout incomingOverlay;

    private final List<UserModel> allUsers = new ArrayList<>();
    private final List<UserModel> filteredUsers = new ArrayList<>();
    private UserAdapter userAdapter;

    private final List<StatusModel> statusList = new ArrayList<>();
    private StatusAdapter statusAdapter;

    private final List<CallLogModel> callList = new ArrayList<>();
    private CallLogAdapter callAdapter;

    private boolean isPickingAvatar = false;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String b64 = compressUriToBase64(uri);
                    if (b64 != null) {
                        if (isPickingAvatar) {
                            myAvatarBase64 = b64;
                            saveProfileToPrefsAndFirestore();
                            Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show();
                        } else {
                            postStatusToFirestore("", b64);
                        }
                    }
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
        tabLayout = findViewById(R.id.tabLayout);
        rvChats = findViewById(R.id.rvChats);
        rvStatuses = findViewById(R.id.rvStatuses);
        rvCalls = findViewById(R.id.rvCalls);
        statusContainer = findViewById(R.id.statusContainer);
        incomingOverlay = findViewById(R.id.incomingOverlay);
        tvCallerName = findViewById(R.id.tvCallerName);
        tvCallType = findViewById(R.id.tvCallType);

        tabLayout.addTab(tabLayout.newTab().setText("CHATS"));
        tabLayout.addTab(tabLayout.newTab().setText("STATUS"));
        tabLayout.addTab(tabLayout.newTab().setText("CALLS"));

        userAdapter = new UserAdapter(filteredUsers, user -> {
            Intent i = new Intent(MainActivity.this, ChatActivity.class);
            i.putExtra("peerUid", user.getUid());
            i.putExtra("peerName", user.getName());
            i.putExtra("isGlobal", user.isGlobalRoom());
            startActivity(i);
        });
        rvChats.setLayoutManager(new LinearLayoutManager(this));
        rvChats.setAdapter(userAdapter);

        statusAdapter = new StatusAdapter(statusList, this::viewStatusDialog);
        rvStatuses.setLayoutManager(new LinearLayoutManager(this));
        rvStatuses.setAdapter(statusAdapter);

        callAdapter = new CallLogAdapter(callList, null);
        rvCalls.setLayoutManager(new LinearLayoutManager(this));
        rvCalls.setAdapter(callAdapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                rvChats.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
                statusContainer.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                rvCalls.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

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
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filterUsers(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnProfileEdit).setOnClickListener(v -> showProfileSettingsDialog());
        findViewById(R.id.btnMyStatus).setOnClickListener(v -> showAddStatusDialog());
        findViewById(R.id.fabAction).setOnClickListener(v -> {
            if (tabLayout.getSelectedTabPosition() == 1) {
                showAddStatusDialog();
            } else {
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra("isGlobal", true);
                i.putExtra("peerName", "Global Community");
                startActivity(i);
            }
        });
    }

    private void initAuth() {
        SharedPreferences prefs = getSharedPreferences("DivineWA", MODE_PRIVATE);
        myName = prefs.getString("name", "User " + (int) (100 + Math.random() * 900));
        myAbout = prefs.getString("about", "Hey there! I am using Divine WhatsApp.");
        myAvatarBase64 = prefs.getString("avatar", "");

        auth.signInAnonymously().addOnSuccessListener(res -> {
            currentUser = res.getUser();
            if (currentUser != null) {
                callAdapter.setMyUid(currentUser.getUid());
                saveProfileToPrefsAndFirestore();
                listenUsers();
                listenStatuses();
                listenCallLogs();
                listenGlobalIncomingCalls();
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Auth error: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void saveProfileToPrefsAndFirestore() {
        getSharedPreferences("DivineWA", MODE_PRIVATE).edit()
                .putString("name", myName)
                .putString("about", myAbout)
                .putString("avatar", myAvatarBase64 != null ? myAvatarBase64 : "")
                .apply();

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
            filterUsers(etSearch.getText().toString());
        });
    }

    private void filterUsers(String q) {
        filteredUsers.clear();
        String query = q.toLowerCase().trim();
        for (UserModel u : allUsers) {
            if (query.isEmpty() || (u.getName() != null && u.getName().toLowerCase().contains(query))) {
                filteredUsers.add(u);
            }
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
                        incomingOverlay.setVisibility(View.GONE);
                        return;
                    }
                    String uid = currentUser.getUid();
                    DocumentSnapshot foundCall = null;
                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        String callerId = d.getString("callerId");
                        String receiverId = d.getString("receiverId");
                        if (!uid.equals(callerId) && (uid.equals(receiverId) || "live_channel".equals(d.getId()))) {
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
                        tvCallType.setText("video".equals(ctype) ? "Incoming video call..." : "Incoming voice call...");

                        findViewById(R.id.btnRejectCall).setOnClickListener(v -> {
                            incomingOverlay.setVisibility(View.GONE);
                            callDoc.getReference().update("status", "ended");
                        });
                        findViewById(R.id.btnAcceptCall).setOnClickListener(v -> {
                            incomingOverlay.setVisibility(View.GONE);
                            Intent i = new Intent(MainActivity.this, ChatActivity.class);
                            boolean isGlob = "live_channel".equals(callDoc.getId());
                            i.putExtra("isGlobal", isGlob);
                            i.putExtra("peerUid", callDoc.getString("callerId"));
                            i.putExtra("peerName", cname);
                            i.putExtra("autoAcceptCallId", callDoc.getId());
                            startActivity(i);
                        });
                    } else {
                        incomingOverlay.setVisibility(View.GONE);
                    }
                });
    }

    private void showProfileSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 12);

        EditText etName = new EditText(this);
        etName.setHint("Your Name");
        etName.setText(myName);

        EditText etAbout = new EditText(this);
        etAbout.setHint("About / Bio");
        etAbout.setText(myAbout);

        layout.addView(etName);
        layout.addView(etAbout);

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Profile Settings")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String a = etAbout.getText().toString().trim();
                    if (!n.isEmpty()) myName = n;
                    if (!a.isEmpty()) myAbout = a;
                    saveProfileToPrefsAndFirestore();
                    Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Change Photo 📷", (d, w) -> {
                    isPickingAvatar = true;
                    imagePickerLauncher.launch("image/*");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddStatusDialog() {
        String[] opts = {"📝 Text Status", "🖼️ Photo Status"};
        new AlertDialog.Builder(this)
                .setTitle("Add Status Update")
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        EditText et = new EditText(this);
                        et.setHint("What's on your mind?");
                        new AlertDialog.Builder(this)
                                .setTitle("Write Status")
                                .setView(et)
                                .setPositiveButton("Post", (d2, w2) -> {
                                    String txt = et.getText().toString().trim();
                                    if (!txt.isEmpty()) postStatusToFirestore(txt, null);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        isPickingAvatar = false;
                        imagePickerLauncher.launch("image/*");
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
        db.collection("statuses").add(map).addOnSuccessListener(r ->
                Toast.makeText(this, "Status posted!", Toast.LENGTH_SHORT).show());
    }

    private void viewStatusDialog(StatusModel s) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 32, 32, 32);

        if (s.getImageBase64() != null) {
            byte[] bytes = Base64.decode(s.getImageBase64().replace("data:image/jpeg;base64,", ""), Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            box.addView(iv);
        }
        if (s.getText() != null && !s.getText().isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(s.getText());
            tv.setTextSize(18);
            tv.setPadding(0, 16, 0, 0);
            box.addView(tv);
        }
        new AlertDialog.Builder(this).setTitle(s.getUserName() + "'s Status").setView(box).setPositiveButton("Close", null).show();
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
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).update("online", true, "lastSeen", System.currentTimeMillis());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).update("online", false, "lastSeen", System.currentTimeMillis());
        }
    }
}