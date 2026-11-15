package com.wevlo.amohir;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {

    public interface OnUserClickListener {
        void onUserClick(UserModel user);
    }

    private final List<UserModel> users;
    private final OnUserClickListener listener;

    public UserAdapter(List<UserModel> users, OnUserClickListener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_chat, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        UserModel u = users.get(position);
        h.tvName.setText(u.getName() != null ? u.getName() : "User");
        h.tvAbout.setText(u.getAbout() != null ? u.getAbout() : "Hey there! I am using WhatsApp.");

        if (u.isGlobalRoom()) {
            h.tvAvatar.setText("🌍");
            h.tvAvatar.setVisibility(View.VISIBLE);
            h.ivAvatar.setVisibility(View.GONE);
            h.viewOnline.setVisibility(View.VISIBLE);
            h.tvStatusTime.setText("GROUP");
        } else {
            if (u.getAvatarBase64() != null && !u.getAvatarBase64().isEmpty()) {
                Bitmap bmp = decodeBase64(u.getAvatarBase64());
                if (bmp != null) {
                    h.ivAvatar.setImageBitmap(bmp);
                    h.ivAvatar.setVisibility(View.VISIBLE);
                    h.tvAvatar.setVisibility(View.GONE);
                }
            } else {
                h.ivAvatar.setVisibility(View.GONE);
                h.tvAvatar.setVisibility(View.VISIBLE);
                String n = u.getName();
                h.tvAvatar.setText((n != null && !n.isEmpty()) ? n.substring(0, 1).toUpperCase() : "👤");
            }
            h.viewOnline.setVisibility(u.isOnline() ? View.VISIBLE : View.GONE);
            if (u.isOnline()) {
                h.tvStatusTime.setText("online");
            } else if (u.getLastSeen() > 0) {
                h.tvStatusTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(u.getLastSeen())));
            } else {
                h.tvStatusTime.setText("");
            }
        }

        h.itemView.setOnClickListener(v -> listener.onUserClick(u));
    }

    private Bitmap decodeBase64(String data) {
        try {
            String b64 = data.contains(",") ? data.substring(data.indexOf(",") + 1) : data;
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { return null; }
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvAbout, tvStatusTime;
        ImageView ivAvatar;
        View viewOnline;
        VH(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            viewOnline = itemView.findViewById(R.id.viewOnline);
            tvName = itemView.findViewById(R.id.tvName);
            tvAbout = itemView.findViewById(R.id.tvAbout);
            tvStatusTime = itemView.findViewById(R.id.tvStatusTime);
        }
    }
}
