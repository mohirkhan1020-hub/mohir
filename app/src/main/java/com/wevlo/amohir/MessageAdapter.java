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
import android.widget.ImageButton;
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
    private final String currentUserId;
    private final boolean showSenderName;
    private final OnMessageActionListener actionListener;
    private MediaPlayer activePlayer;
    private ImageButton activePlayBtn;

    public MessageAdapter(List<MessageModel> messages, String currentUserId, boolean showSenderName, OnMessageActionListener actionListener) {
        this.messages = messages;
        this.currentUserId = currentUserId;
        this.showSenderName = showSenderName;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public MsgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MsgViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false));
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

        if (m.getReplyToText() != null && !m.getReplyToText().isEmpty()) {
            h.replyBox.setVisibility(View.VISIBLE);
            h.tvReplyQuoteSender.setText(m.getReplyToSender() != null ? m.getReplyToSender() : "Message");
            h.tvReplyQuoteText.setText(m.getReplyToText());
        } else {
            h.replyBox.setVisibility(View.GONE);
        }

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
            h.tvVoiceLabel.setText(m.getVoiceDuration() != null ? m.getVoiceDuration() : "0:05");
            h.btnPlayVoice.setImageResource(R.drawable.ic_play);
            h.btnPlayVoice.setOnClickListener(v -> playVoiceNote(v.getContext(), m.getMediaUrl(), h.btnPlayVoice));
        } else {
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvMessage.setText(m.getText() != null ? m.getText() : "");
        }

        String timeStr = m.getTimestamp() > 0
                ? new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(m.getTimestamp())) : "";
        h.tvMeta.setText(timeStr);

        if (isOwn) {
            h.ivTicks.setVisibility(View.VISIBLE);
            h.ivTicks.setColorFilter(m.isSeen() ? Color.parseColor("#53BDEB") : Color.parseColor("#B0BEC5"));
        } else {
            h.ivTicks.setVisibility(View.GONE);
        }

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

    private void playVoiceNote(Context ctx, String base64Audio, ImageButton btn) {
        try {
            if (activePlayer != null) {
                activePlayer.stop();
                activePlayer.release();
                activePlayer = null;
                if (activePlayBtn != null) activePlayBtn.setImageResource(R.drawable.ic_play);
                if (activePlayBtn == btn) { activePlayBtn = null; return; }
            }
            byte[] audioBytes = Base64.decode(base64Audio, Base64.DEFAULT);
            File temp = File.createTempFile("play_", ".m4a", ctx.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(temp)) { fos.write(audioBytes); }

            activePlayer = new MediaPlayer();
            activePlayBtn = btn;
            activePlayer.setDataSource(temp.getAbsolutePath());
            activePlayer.setOnPreparedListener(mp -> {
                mp.start();
                btn.setImageResource(android.R.drawable.ic_media_pause);
            });
            activePlayer.setOnCompletionListener(mp -> {
                btn.setImageResource(R.drawable.ic_play);
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
        ImageView ivMedia, ivTicks;
        ImageButton btnPlayVoice;

        MsgViewHolder(@NonNull View v) {
            super(v);
            msgRoot = v.findViewById(R.id.msgRoot);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            tvSender = v.findViewById(R.id.tvSender);
            replyBox = v.findViewById(R.id.replyBox);
            tvReplyQuoteSender = v.findViewById(R.id.tvReplyQuoteSender);
            tvReplyQuoteText = v.findViewById(R.id.tvReplyQuoteText);
            ivMedia = v.findViewById(R.id.ivMedia);
            voiceBox = v.findViewById(R.id.voiceBox);
            btnPlayVoice = v.findViewById(R.id.btnPlayVoice);
            tvVoiceLabel = v.findViewById(R.id.tvVoiceLabel);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvMeta = v.findViewById(R.id.tvMeta);
            ivTicks = v.findViewById(R.id.ivTicks);
            tvReaction = v.findViewById(R.id.tvReaction);
        }
    }
}