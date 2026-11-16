package com.wevlo.amohir;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupInfoActivity extends AppCompatActivity {

    private String groupId, groupName, myPhone;
    private final List<String> participants = new ArrayList<>();
    private final Map<String, String> namesMap = new HashMap<>();

    private TextView tvGroupName, tvGroupMembersCount;
    private RecyclerView rvGroupMembers;
    private MemberAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        myPhone = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("my_phone", "");

        if (groupId == null || myPhone.isEmpty()) { finish(); return; }

        tvGroupName = findViewById(R.id.tvGroupName);
        tvGroupMembersCount = findViewById(R.id.tvGroupMembersCount);
        rvGroupMembers = findViewById(R.id.rvGroupMembers);
        tvGroupName.setText(groupName != null ? groupName : "Group");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddMember).setOnClickListener(v -> showAddMemberDialog());
        findViewById(R.id.btnGroupCall).setOnClickListener(v -> startGroupAudioCall());
        findViewById(R.id.btnLeaveGroup).setOnClickListener(v -> leaveGroup());

        adapter = new MemberAdapter();
        rvGroupMembers.setLayoutManager(new LinearLayoutManager(this));
        rvGroupMembers.setAdapter(adapter);

        loadGroupData();
    }

    @SuppressWarnings("unchecked")
    private void loadGroupData() {
        FirebaseFirestore.getInstance().collection("chats").document(groupId)
                .addSnapshotListener((snap, err) -> {
                    if (snap == null || !snap.exists()) return;
                    String gn = snap.getString("groupName");
                    if (gn != null) tvGroupName.setText(gn);

                    List<String> parts = (List<String>) snap.get("participants");
                    if (parts != null) {
                        participants.clear();
                        participants.addAll(parts);
                        tvGroupMembersCount.setText(participants.size() + " participants");
                        fetchMemberNames();
                    }
                });
    }

    private void fetchMemberNames() {
        for (String phone : participants) {
            FirebaseFirestore.getInstance().collection("users").document(phone).get()
                    .addOnSuccessListener(d -> {
                        if (d.exists()) {
                            String n = d.getString("name");
                            namesMap.put(phone, n != null ? n : phone);
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
        adapter.notifyDataSetChanged();
    }

    private void showAddMemberDialog() {
        EditText et = new EditText(this);
        et.setHint("Enter mobile number to add");
        new AlertDialog.Builder(this)
                .setTitle("Add Member")
                .setView(et)
                .setPositiveButton("Add", (d, w) -> {
                    String phone = LoginActivity.normalizePhone(et.getText().toString());
                    if (phone.length() >= 7) {
                        FirebaseFirestore.getInstance().collection("chats").document(groupId)
                                .update("participants", FieldValue.arrayUnion(phone));
                        Toast.makeText(this, "Member added", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startGroupAudioCall() {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("isGroup", true);
        i.putExtra("groupId", groupId);
        i.putExtra("peerName", groupName);
        i.putExtra("autoStartCall", "audio");
        startActivity(i);
    }

    private void leaveGroup() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Group")
                .setMessage("Are you sure you want to leave this group?")
                .setPositiveButton("Leave", (d, w) -> {
                    FirebaseFirestore.getInstance().collection("chats").document(groupId)
                            .update("participants", FieldValue.arrayRemove(myPhone));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
            View v = LayoutInflater.from(p.getContext()).inflate(android.R.layout.simple_list_item_2, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String p = participants.get(pos);
            String display = namesMap.containsKey(p) ? namesMap.get(p) : p;
            if (p.equals(myPhone)) display += " (You)";
            h.t1.setText(display);
            h.t1.setTextColor(Color.parseColor("#E9EDEF"));
            h.t2.setText(p);
            h.t2.setTextColor(Color.parseColor("#8696A0"));

            h.itemView.setOnLongClickListener(v -> {
                if (!p.equals(myPhone)) {
                    new AlertDialog.Builder(GroupInfoActivity.this)
                            .setTitle("Manage Member")
                            .setItems(new String[]{"Remove from Group"}, (di, wh) -> {
                                FirebaseFirestore.getInstance().collection("chats").document(groupId)
                                        .update("participants", FieldValue.arrayRemove(p));
                                Toast.makeText(GroupInfoActivity.this, "Member removed", Toast.LENGTH_SHORT).show();
                            }).show();
                }
                return true;
            });
        }

        @Override public int getItemCount() { return participants.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            VH(@NonNull View v) {
                super(v);
                t1 = v.findViewById(android.R.id.text1);
                t2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}