package com.wevlo.amohir;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class StarredMessagesActivity extends AppCompatActivity {

    private RecyclerView rvStarredMessages;
    private TextView tvEmptyStarred;
    private MessageAdapter adapter;
    private final List<MessageModel> starredList = new ArrayList<>();
    private String myPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starred);

        myPhone = getSharedPreferences("DivineWA", MODE_PRIVATE).getString("my_phone", "");
        rvStarredMessages = findViewById(R.id.rvStarredMessages);
        tvEmptyStarred = findViewById(R.id.tvEmptyStarred);

        findViewById(R.id.btnBackStarred).setOnClickListener(v -> finish());

        adapter = new MessageAdapter(starredList, myPhone, true, new MessageAdapter.OnMessageActionListener() {
            @Override
            public void onLongClick(MessageModel message) {
                Toast.makeText(StarredMessagesActivity.this, "Starred message", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onViewOnceClick(MessageModel message) {}
        });

        rvStarredMessages.setLayoutManager(new LinearLayoutManager(this));
        rvStarredMessages.setAdapter(adapter);

        loadStarredMessages();
    }

    private void loadStarredMessages() {
        FirebaseFirestore.getInstance().collectionGroup("messages")
                .whereEqualTo("starred", true)
                .get()
                .addOnSuccessListener(snaps -> {
                    starredList.clear();
                    for (DocumentSnapshot d : snaps.getDocuments()) {
                        MessageModel m = d.toObject(MessageModel.class);
                        if (m != null) {
                            m.setId(d.getId());
                            starredList.add(m);
                        }
                    }
                    tvEmptyStarred.setVisibility(starredList.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> tvEmptyStarred.setVisibility(View.VISIBLE));
    }
}
