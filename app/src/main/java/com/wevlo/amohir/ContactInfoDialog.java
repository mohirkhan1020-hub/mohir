package com.wevlo.amohir;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ContactInfoDialog {

    public interface ActionCallback {
        void onAudioCall();
        void onVideoCall();
    }

    public static void show(Context ctx, String peerPhone, String fallbackName, ActionCallback callback) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B141A"));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(44, 48, 44, 36);

        ShapeableImageView ivAvatar = new ShapeableImageView(ctx);
        int size = Math.round(105 * ctx.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams avParams = new LinearLayout.LayoutParams(size, size);
        ivAvatar.setLayoutParams(avParams);
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivAvatar.setBackgroundColor(Color.parseColor("#202C33"));
        ivAvatar.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(ShapeAppearanceModel.PILL).build());
        ivAvatar.setImageResource(R.drawable.ic_chat);

        TextView tvName = new TextView(ctx);
        tvName.setText(fallbackName != null ? fallbackName : peerPhone);
        tvName.setTextColor(Color.parseColor("#E9EDEF"));
        tvName.setTextSize(21);
        tvName.setPadding(0, 22, 0, 4);

        TextView tvPhone = new TextView(ctx);
        tvPhone.setText("📱 " + peerPhone);
        tvPhone.setTextColor(Color.parseColor("#00A884"));
        tvPhone.setTextSize(15);

        TextView tvStatus = new TextView(ctx);
        tvStatus.setText("Loading account details...");
        tvStatus.setTextColor(Color.parseColor("#8696A0"));
        tvStatus.setTextSize(13);
        tvStatus.setPadding(0, 6, 0, 20);

        TextView tvAboutTitle = new TextView(ctx);
        tvAboutTitle.setText("About & Bio");
        tvAboutTitle.setTextColor(Color.parseColor("#8696A0"));
        tvAboutTitle.setTextSize(12);
        tvAboutTitle.setGravity(Gravity.START);
        tvAboutTitle.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvAbout = new TextView(ctx);
        tvAbout.setText("Hey there! I am using WhatsApp.");
        tvAbout.setTextColor(Color.parseColor("#E9EDEF"));
        tvAbout.setTextSize(15);
        tvAbout.setBackgroundColor(Color.parseColor("#1F2C34"));
        tvAbout.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams abParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        abParams.setMargins(0, 8, 0, 24);
        tvAbout.setLayoutParams(abParams);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button btnAudio = new Button(ctx);
        btnAudio.setText("📞 Audio");
        btnAudio.setTextColor(Color.WHITE);
        btnAudio.setBackgroundColor(Color.parseColor("#202C33"));

        Button btnVideo = new Button(ctx);
        btnVideo.setText("🎥 Video");
        btnVideo.setTextColor(Color.WHITE);
        btnVideo.setBackgroundColor(Color.parseColor("#00A884"));

        Button btnCopy = new Button(ctx);
        btnCopy.setText("📋 Copy");
        btnCopy.setTextColor(Color.WHITE);
        btnCopy.setBackgroundColor(Color.parseColor("#202C33"));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bp.setMargins(6, 0, 6, 0);
        btnAudio.setLayoutParams(bp);
        btnVideo.setLayoutParams(bp);
        btnCopy.setLayoutParams(bp);

        if (callback != null) {
            btnRow.addView(btnAudio);
            btnRow.addView(btnVideo);
        }
        btnRow.addView(btnCopy);

        root.addView(ivAvatar);
        root.addView(tvName);
        root.addView(tvPhone);
        root.addView(tvStatus);
        root.addView(tvAboutTitle);
        root.addView(tvAbout);
        root.addView(btnRow);

        AlertDialog dialog = new AlertDialog.Builder(ctx).setView(root).create();

        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("phone", peerPhone));
            Toast.makeText(ctx, "Number copied!", Toast.LENGTH_SHORT).show();
        });
        btnAudio.setOnClickListener(v -> { dialog.dismiss(); if (callback != null) callback.onAudioCall(); });
        btnVideo.setOnClickListener(v -> { dialog.dismiss(); if (callback != null) callback.onVideoCall(); });

        FirebaseFirestore.getInstance().collection("users").document(peerPhone).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String about = doc.getString("about");
                        String avatar = doc.getString("avatarBase64");
                        Boolean online = doc.getBoolean("online");
                        Long lastSeen = doc.getLong("lastSeen");

                        if (name != null && !name.isEmpty()) tvName.setText(name);
                        if (about != null && !about.isEmpty()) tvAbout.setText(about);

                        if (Boolean.TRUE.equals(online)) {
                            tvStatus.setText("● Online now");
                            tvStatus.setTextColor(Color.parseColor("#25D366"));
                        } else if (lastSeen != null && lastSeen > 0) {
                            String t = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date(lastSeen));
                            tvStatus.setText("Last seen: " + t);
                        } else {
                            tvStatus.setText("Offline");
                        }

                        if (avatar != null && !avatar.isEmpty()) {
                            try {
                                String b64 = avatar.contains(",") ? avatar.substring(avatar.indexOf(",") + 1) : avatar;
                                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                if (bmp != null) ivAvatar.setImageBitmap(bmp);
                            } catch (Exception ignored) {}
                        }
                    }
                });

        dialog.show();
    }
}
