package com.wevlo.amohir;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MsgViewHolder> {

    public interface OnMessageActionListener {
        void onLongClick(MessageModel message);
    }

    private final List<MessageModel> messages;
    private String currentUserId;
    private final boolean showSenderName;
    private final OnMessageActionListener actionListener;
    private MediaPlayer activePlayer;
    private Button activePlayBtn;

    public MessageAdapter(List<MessageModel> messages, String currentUserId, boolean showSenderName, OnMessageActionListener actionListener) {
        this.messages = messages;
        this.currentUserId = currentUserId;
        this.showSenderName = showSenderName;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public MsgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MsgViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MsgViewHolder h, int position) {
        MessageModel m = messages.get(position);
        boolean isOwn = currentUserId != null && currentUserId.equals(m.getSenderId());

        h.msgRoot.setGravity(isOwn ? Gravity.END : Gravity.START);
        h.bubbleContainer.setBackgroundResource(isOwn ? R.drawable.bg_wa_bubble_out : R.drawable.bg_wa_bubble_in);

        if (!isOwn && showSenderName) {
            h.tvSender.setVisibility(View.VISIBLE);
            h.tvSender.setText(m.getSenderName() != null ? m.getSenderName() : "Friend");
        } else {
            h.tvSender.setVisibility(View.GONE);
        }

        // Reply Quote
        if (m.getReplyToText() != null && !m.getReplyToText().isEmpty()) {
            h.replyBox.setVisibility(View.VISIBLE);
            h.tvReplyQuoteSender.setText(m.getReplyToSender() != null ? m.getReplyToSender() : "Message");
            h.tvReplyQuoteText.setText(m.getReplyToText());
        } else {
            h.replyBox.setVisibility(View.GONE);
        }

        // Content Type
        h.ivMedia.setVisibility(View.GONE);
        h.voiceBox.setVisibility(View.GONE);
        h.tvMessage.setVisibility(View.GONE);

        if ("image".equals(m.getType()) && m.getMediaUrl() != null) {
            h.ivMedia.setVisibility(View.VISIBLE);
            Bitmap bmp = decodeBase64Image(m.getMediaUrl());
            if (bmp != null) {
                h.ivMedia.setImageBitmap(bmp);
                h.ivMedia.setOnClickListener(v -> showFullImage(v.getContext(), bmp));
            }
        } else if ("voice".equals(m.getType()) && m.getMediaUrl() != null) {
            h.voiceBox.setVisibility(View.VISIBLE);
            String dur = m.getVoiceDuration() != null ? m.getVoiceDuration() : "0:05";
            h.tvVoiceLabel.setText("🎤 Voice (" + dur + ")");
            h.btnPlayVoice.setText("▶");
            h.btnPlayVoice.setOnClickListener(v -> playVoiceNote(v.getContext(), m.getMediaUrl(), h.btnPlayVoice));
        } else {
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvMessage.setText(m.getText() != null ? m.getText() : "");
        }

        // Timestamp + Blue Ticks
        String timeStr = m.getTimestamp() > 0
                ? new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(m.getTimestamp()))
                : "";
        if (isOwn) {
            h.tvMeta.setText(timeStr + " ✓✓");
            h.tvMeta.setTextColor(m.isSeen() ? Color.parseColor("#53BDEB") : Color.parseColor("#B0BEC5"));
        } else {
            h.tvMeta.setText(timeStr);
            h.tvMeta.setTextColor(Color.parseColor("#8696A0"));
        }

        // Reaction
        if (m.getReaction() != null && !m.getReaction().isEmpty()) {
            h.tvReaction.setVisibility(View.VISIBLE);
            h.tvReaction.setText(m.getReaction());
        } else {
            h.tvReaction.setVisibility(View.GONE);
        }

        h.bubbleContainer.setOnLongClickListener(v -> {
            if (actionListener != null) actionListener.onLongClick(m);
            return true;
        });
    }

    private void playVoiceNote(Context ctx, String base64Audio, Button btn) {
        try {
            if (activePlayer != null) {
                activePlayer.stop();
                activePlayer.release();
                activePlayer = null;
                if (activePlayBtn != null) activePlayBtn.setText("▶");
                if (activePlayBtn == btn) { activePlayBtn = null; return; }
            }
            byte[] audioBytes = Base64.decode(base64Audio, Base64.DEFAULT);
            File temp = File.createTempFile("play_", ".m4a", ctx.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                fos.write(audioBytes);
            }
            activePlayer = new MediaPlayer();
            activePlayBtn = btn;
            activePlayer.setDataSource(temp.getAbsolutePath());
            activePlayer.setOnPreparedListener(mp -> {
                mp.start();
                btn.setText("⏸");
            });
            activePlayer.setOnCompletionListener(mp -> {
                btn.setText("▶");
                mp.release();
                activePlayer = null;
                activePlayBtn = null;
            });
            activePlayer.prepareAsync();
        } catch (Exception ignored) {}
    }

    private void showFullImage(Context ctx, Bitmap bmp) {
        ImageView iv = new ImageView(ctx);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        new AlertDialog.Builder(ctx).setView(iv).setPositiveButton("Close", null).show();
    }

    private Bitmap decodeBase64Image(String dataUrl) {
        try {
            String base64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(",") + 1) : dataUrl;
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { return null; }
    }

    public void releasePlayer() {
        if (activePlayer != null) {
            activePlayer.release();
            activePlayer = null;
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class MsgViewHolder extends RecyclerView.ViewHolder {
        LinearLayout msgRoot, bubbleContainer, replyBox, voiceBox;
        TextView tvSender, tvReplyQuoteSender, tvReplyQuoteText, tvMessage, tvMeta, tvReaction, tvVoiceLabel;
        ImageView ivMedia;
        Button btnPlayVoice;

        MsgViewHolder(@NonNull View itemView) {
            super(itemView);
            msgRoot = itemView.findViewById(R.id.msgRoot);
            bubbleContainer = itemView.findViewById(R.id.bubbleContainer);
            tvSender = itemView.findViewById(R.id.tvSender);
            replyBox = itemView.findViewById(R.id.replyBox);
            tvReplyQuoteSender = itemView.findViewById(R.id.tvReplyQuoteSender);
            tvReplyQuoteText = itemView.findViewById(R.id.tvReplyQuoteText);
            ivMedia = itemView.findViewById(R.id.ivMedia);
            voiceBox = itemView.findViewById(R.id.voiceBox);
            btnPlayVoice = itemView.findViewById(R.id.btnPlayVoice);
            tvVoiceLabel = itemView.findViewById(R.id.tvVoiceLabel);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvReaction = itemView.findViewById(R.id.tvReaction);
        }
    }
}