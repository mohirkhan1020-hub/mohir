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
        void onViewOnceClick(MessageModel message);
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

        if (m.getTimestamp() > 0 && (position == 0 || !isSameDay(messages.get(position - 1).getTimestamp(), m.getTimestamp()))) {
            h.tvDateHeader.setVisibility(View.VISIBLE);
            h.tvDateHeader.setText(formatDateHeader(m.getTimestamp()));
        } else {
            h.tvDateHeader.setVisibility(View.GONE);
        }

        h.msgRoot.setGravity(isOwn ? Gravity.END : Gravity.START);
        h.bubbleContainer.setBackgroundResource(isOwn ? R.drawable.bg_wa_bubble_out : R.drawable.bg_wa_bubble_in);

        h.tvForwarded.setVisibility(View.GONE);
        h.tvSender.setVisibility(View.GONE);
        h.replyBox.setVisibility(View.GONE);
        h.ivMedia.setVisibility(View.GONE);
        h.fileBox.setVisibility(View.GONE);
        h.locationBox.setVisibility(View.GONE);
        h.voiceBox.setVisibility(View.GONE);
        h.tvMessage.setVisibility(View.GONE);
        h.tvReaction.setVisibility(View.GONE);

        if (m.isDeleted()) {
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvMessage.setText("🚫 This message was deleted");
            h.tvMessage.setTypeface(null, Typeface.ITALIC);
            h.tvMessage.setTextColor(Color.parseColor("#8696A0"));
            h.ivTicks.setVisibility(View.GONE);
            h.tvMeta.setText(formatTime(m.getTimestamp()));
            h.bubbleContainer.setOnLongClickListener(null);
            return;
        }

        h.tvMessage.setTypeface(null, Typeface.NORMAL);
        h.tvMessage.setTextColor(Color.parseColor("#E9EDEF"));

        if (m.isForwarded()) h.tvForwarded.setVisibility(View.VISIBLE);
        if (!isOwn && showSenderName) {
            h.tvSender.setVisibility(View.VISIBLE);
            h.tvSender.setText(m.getSenderName() != null ? m.getSenderName() : "Friend");
        }

        if (m.getReplyToText() != null && !m.getReplyToText().isEmpty()) {
            h.replyBox.setVisibility(View.VISIBLE);
            h.tvReplyQuoteSender.setText(m.getReplyToSender() != null ? m.getReplyToSender() : "Message");
            h.tvReplyQuoteText.setText(m.getReplyToText());
        }

        String type = m.getType() != null ? m.getType() : "text";

        // View Once Media
        if (m.isViewOnce()) {
            h.fileBox.setVisibility(View.VISIBLE);
            if (m.isOpened()) {
                h.tvFileName.setText("Opened Photo");
                h.tvFileName.setTextColor(Color.parseColor("#8696A0"));
                h.fileBox.setOnClickListener(null);
            } else {
                h.tvFileName.setText("① Photo (View Once)");
                h.tvFileName.setTextColor(Color.parseColor("#25D366"));
                h.fileBox.setOnClickListener(v -> {
                    if (!isOwn && actionListener != null) actionListener.onViewOnceClick(m);
                    else if (isOwn) Toast.makeText(v.getContext(), "View Once photo sent", Toast.LENGTH_SHORT).show();
                });
            }
        } else if ("image".equals(type) && m.getMediaUrl() != null) {
            h.ivMedia.setVisibility(View.VISIBLE);
            Bitmap bmp = decodeBase64Image(m.getMediaUrl());
            if (bmp != null) {
                h.ivMedia.setImageBitmap(bmp);
                h.ivMedia.setOnClickListener(v -> showFullImage(v.getContext(), bmp));
            }
        } else if ("file".equals(type) && m.getMediaUrl() != null) {
            h.fileBox.setVisibility(View.VISIBLE);
            String fname = m.getFileName() != null ? m.getFileName() : "document";
            h.tvFileName.setText(fname);
            h.tvFileName.setTextColor(Color.parseColor("#E9EDEF"));
            h.fileBox.setOnClickListener(v -> openBase64File(v.getContext(), m.getMediaUrl(), fname));
        } else if ("location".equals(type) && m.getLatLng() != null) {
            h.locationBox.setVisibility(View.VISIBLE);
            h.locationBox.setOnClickListener(v -> {
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + m.getLatLng() + "?q=" + m.getLatLng()));
                v.getContext().startActivity(mapIntent);
            });
        } else if ("voice".equals(type) && m.getMediaUrl() != null) {
            h.voiceBox.setVisibility(View.VISIBLE);
            h.tvVoiceLabel.setText(m.getVoiceDuration() != null ? m.getVoiceDuration() : "0:05");
            h.btnPlayVoice.setImageResource(R.drawable.ic_play);
            h.btnPlayVoice.setOnClickListener(v -> playVoiceNote(v.getContext(), m.getMediaUrl(), h.btnPlayVoice));
        } else {
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvMessage.setText(m.getText() != null ? m.getText() : "");
        }

        String timeStr = formatTime(m.getTimestamp());
        String metaText = (m.isStarred() ? "★ " : "") + (m.isEdited() ? "Edited  " : "") + timeStr;
        h.tvMeta.setText(metaText);

        if (isOwn) {
            h.ivTicks.setVisibility(View.VISIBLE);
            h.ivTicks.setColorFilter(m.isSeen() ? Color.parseColor("#53BDEB") : Color.parseColor("#B0BEC5"));
        } else {
            h.ivTicks.setVisibility(View.GONE);
        }

        if (m.getReaction() != null && !m.getReaction().isEmpty()) {
            h.tvReaction.setVisibility(View.VISIBLE);
            h.tvReaction.setText(m.getReaction());
        }

        h.bubbleContainer.setOnLongClickListener(v -> {
            if (actionListener != null) actionListener.onLongClick(m);
            return true;
        });
    }

    private String formatTime(long ts) {
        return ts > 0 ? new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(ts)) : "";
    }

    private boolean isSameDay(long t1, long t2) {
        Calendar c1 = Calendar.getInstance(); c1.setTimeInMillis(t1);
        Calendar c2 = Calendar.getInstance(); c2.setTimeInMillis(t2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private String formatDateHeader(long ts) {
        long now = System.currentTimeMillis();
        if (isSameDay(ts, now)) return "Today";
        if (isSameDay(ts, now - 86400000L)) return "Yesterday";
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(ts));
    }

    private void openBase64File(Context ctx, String b64, String fileName) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            File out = new File(ctx.getCacheDir(), fileName.replaceAll("[^a-zA-Z0-9._-]", "_"));
            try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(data); }
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
            String ext = MimeTypeMap.getFileExtensionFromUrl(out.getName());
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime != null ? mime : "*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(ctx, "No app found to open " + fileName, Toast.LENGTH_SHORT).show();
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
            activePlayer.setOnCompletionListener(mp -> { btn.setImageResource(R.drawable.ic_play); mp.release(); activePlayer = null; activePlayBtn = null; });
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
        TextView tvDateHeader, tvForwarded, tvSender, tvReplyQuoteSender, tvReplyQuoteText, tvMessage, tvMeta, tvReaction, tvVoiceLabel, tvFileName;
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