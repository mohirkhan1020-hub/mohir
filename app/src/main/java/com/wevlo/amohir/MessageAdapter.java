package com.wevlo.amohir;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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

        // Date Header
        String dayKey = getDayKey(m.getTimestamp());
        String prevDayKey = position > 0 ? getDayKey(messages.get(position - 1).getTimestamp()) : "";
        if (m.getTimestamp() > 0 && !dayKey.equals(prevDayKey)) {
            h.tvDateHeader.setVisibility(View.VISIBLE);
            h.tvDateHeader.setText(formatDateLabel(m.getTimestamp()));
        } else {
            h.tvDateHeader.setVisibility(View.GONE);
        }

        h.msgRoot.setGravity(isOwn ? Gravity.END : Gravity.START);
        h.bubbleContainer.setBackgroundResource(isOwn ? R.drawable.bg_wa_bubble_out : R.drawable.bg_wa_bubble_in);

        if (!isOwn && showSenderName) {
            h.tvSender.setVisibility(View.VISIBLE);
            h.tvSender.setText(m.getSenderName() != null ? m.getSenderName() : "Friend");
        } else {
            h.tvSender.setVisibility(View.GONE);
        }

        h.ivMedia.setVisibility(View.GONE);
        h.voiceBox.setVisibility(View.GONE);
        h.fileBox.setVisibility(View.GONE);
        h.locationBox.setVisibility(View.GONE);
        h.tvMessage.setVisibility(View.GONE);
        h.replyBox.setVisibility(View.GONE);
        h.tvForwarded.setVisibility(View.GONE);
        h.tvReaction.setVisibility(View.GONE);

        if (m.isDeleted()) {
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvMessage.setText("🚫 This message was deleted");
            h.tvMessage.setTextColor(Color.parseColor("#8696A0"));
            h.tvMessage.setTypeface(null, Typeface.ITALIC);
        } else {
            h.tvMessage.setTextColor(Color.parseColor("#E9EDEF"));
            h.tvMessage.setTypeface(null, Typeface.NORMAL);
            if (m.isForwarded()) h.tvForwarded.setVisibility(View.VISIBLE);

            if (m.getReplyToText() != null && !m.getReplyToText().isEmpty()) {
                h.replyBox.setVisibility(View.VISIBLE);
                h.tvReplyQuoteSender.setText(m.getReplyToSender() != null ? m.getReplyToSender() : "Message");
                h.tvReplyQuoteText.setText(m.getReplyToText());
            }

            String type = m.getType();
            if ("image".equals(type) && m.getMediaUrl() != null) {
                h.ivMedia.setVisibility(View.VISIBLE);
                Bitmap bmp = decodeBase64Image(m.getMediaUrl());
                if (bmp != null) {
                    h.ivMedia.setImageBitmap(bmp);
                    h.ivMedia.setOnClickListener(v -> showFullImage(v.getContext(), bmp));
                }
            } else if ("voice".equals(type) && m.getMediaUrl() != null) {
                h.voiceBox.setVisibility(View.VISIBLE);
                h.tvVoiceLabel.setText(m.getVoiceDuration() != null ? m.getVoiceDuration() : "0:05");
                h.btnPlayVoice.setImageResource(R.drawable.ic_play);
                h.btnPlayVoice.setOnClickListener(v -> playVoiceNote(v.getContext(), m.getMediaUrl(), h.btnPlayVoice));
            } else if ("file".equals(type) && m.getMediaUrl() != null) {
                h.fileBox.setVisibility(View.VISIBLE);
                String fName = m.getFileName() != null ? m.getFileName() : "document";
                h.tvFileName.setText(fName);
                h.fileBox.setOnClickListener(v -> openDocumentFile(v.getContext(), fName, m.getMediaUrl()));
            } else if ("location".equals(type) && m.getLatLng() != null) {
                h.locationBox.setVisibility(View.VISIBLE);
                h.locationBox.setOnClickListener(v -> openLocationMap(v.getContext(), m.getLatLng()));
            } else {
                h.tvMessage.setVisibility(View.VISIBLE);
                h.tvMessage.setText(m.getText() != null ? m.getText() : "");
            }

            if (m.getReaction() != null && !m.getReaction().isEmpty()) {
                h.tvReaction.setVisibility(View.VISIBLE);
                h.tvReaction.setText(m.getReaction());
            }
        }

        String timeStr = m.getTimestamp() > 0
                ? new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(m.getTimestamp())) : "";
        h.tvMeta.setText((m.isEdited() && !m.isDeleted() ? "Edited • " : "") + timeStr);

        if (isOwn && !m.isDeleted()) {
            h.ivTicks.setVisibility(View.VISIBLE);
            h.ivTicks.setColorFilter(m.isSeen() ? Color.parseColor("#53BDEB") : Color.parseColor("#B0BEC5"));
        } else {
            h.ivTicks.setVisibility(View.GONE);
        }

        h.bubbleContainer.setOnLongClickListener(v -> {
            if (actionListener != null && !m.isDeleted()) actionListener.onLongClick(m);
            return true;
        });
    }

    private String getDayKey(long ts) {
        return ts > 0 ? new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(ts)) : "";
    }

    private String formatDateLabel(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar msg = Calendar.getInstance();
        msg.setTimeInMillis(ts);
        if (now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)) {
            return "Today";
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        }
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(ts));
    }

    private void openDocumentFile(Context ctx, String fileName, String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            File f = new File(ctx.getCacheDir(), fileName.replaceAll("[^a-zA-Z0-9._-]", "_"));
            try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(bytes); }
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
            String ext = MimeTypeMap.getFileExtensionFromUrl(f.getName());
            String mime = ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase()) : null;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime != null ? mime : "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(ctx, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLocationMap(Context ctx, String latLng) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + latLng));
            ctx.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(ctx, "Cannot open Maps", Toast.LENGTH_SHORT).show();
        }
    }

    private void playVoiceNote(Context ctx, String base64Audio, ImageButton btn) {
        try {
            if (activePlayer != null) {
                activePlayer.stop(); activePlayer.release(); activePlayer = null;
                if (activePlayBtn != null) activePlayBtn.setImageResource(R.drawable.ic_play);
                if (activePlayBtn == btn) { activePlayBtn = null; return; }
            }
            byte[] audioBytes = Base64.decode(base64Audio, Base64.DEFAULT);
            File temp = File.createTempFile("play_", ".m4a", ctx.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(temp)) { fos.write(audioBytes); }

            activePlayer = new MediaPlayer();
            activePlayBtn = btn;
            activePlayer.setDataSource(temp.getAbsolutePath());
            activePlayer.setOnPreparedListener(mp -> { mp.start(); btn.setImageResource(android.R.drawable.ic_media_pause); });
            activePlayer.setOnCompletionListener(mp -> {
                btn.setImageResource(R.drawable.ic_play); mp.release(); activePlayer = null; activePlayBtn = null;
            });
            activePlayer.prepareAsync();
        } catch (Exception ignored) {}
    }

    private void showFullImage(Context ctx, Bitmap bmp) {
        ImageView iv = new ImageView(ctx); iv.setImageBitmap(bmp); iv.setAdjustViewBounds(true);
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
        if (activePlayer != null) { activePlayer.release(); activePlayer = null; }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class MsgViewHolder extends RecyclerView.ViewHolder {
        LinearLayout msgRoot, bubbleContainer, replyBox, voiceBox, fileBox, locationBox;
        TextView tvDateHeader, tvForwarded, tvSender, tvReplyQuoteSender, tvReplyQuoteText;
        TextView tvMessage, tvMeta, tvReaction, tvVoiceLabel, tvFileName, tvLocationLabel;
        ImageView ivMedia, ivTicks;
        ImageButton btnPlayVoice;

        MsgViewHolder(@NonNull View v) {
            super(v);
            msgRoot = v.findViewById(R.id.msgRoot);
            tvDateHeader = v.findViewById(R.id.tvDateHeader);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            tvForwarded = v.findViewById(R.id.tvForwarded);
            tvSender = v.findViewById(R.id.tvSender);
            replyBox = v.findViewById(R.id.replyBox);
            tvReplyQuoteSender = v.findViewById(R.id.tvReplyQuoteSender);
            tvReplyQuoteText = v.findViewById(R.id.tvReplyQuoteText);
            ivMedia = v.findViewById(R.id.ivMedia);
            fileBox = v.findViewById(R.id.fileBox);
            tvFileName = v.findViewById(R.id.tvFileName);
            locationBox = v.findViewById(R.id.locationBox);
            tvLocationLabel = v.findViewById(R.id.tvLocationLabel);
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