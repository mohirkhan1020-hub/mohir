package com.wevlo.amohir;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MsgViewHolder> {

    private final List<MessageModel> messages;
    private String currentUserId;

    public MessageAdapter(List<MessageModel> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MsgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MsgViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MsgViewHolder holder, int position) {
        MessageModel m = messages.get(position);
        boolean isOwn = currentUserId != null && currentUserId.equals(m.getSenderId());

        holder.msgRoot.setGravity(isOwn ? Gravity.END : Gravity.START);
        holder.bubbleContainer.setBackgroundResource(isOwn ? R.drawable.bg_bubble_own : R.drawable.bg_bubble_other);

        if (isOwn) {
            holder.tvSender.setVisibility(View.GONE);
        } else {
            holder.tvSender.setVisibility(View.VISIBLE);
            holder.tvSender.setText(m.getSenderName() != null ? m.getSenderName() : "ফ্রেন্ড");
        }

        if ("image".equals(m.getType()) && m.getMediaUrl() != null) {
            holder.tvMessage.setVisibility(View.GONE);
            holder.ivMedia.setVisibility(View.VISIBLE);
            Bitmap bmp = decodeBase64(m.getMediaUrl());
            if (bmp != null) {
                holder.ivMedia.setImageBitmap(bmp);
            }
        } else {
            holder.ivMedia.setVisibility(View.GONE);
            holder.tvMessage.setVisibility(View.VISIBLE);
            holder.tvMessage.setText(m.getText() != null ? m.getText() : "");
        }
    }

    private Bitmap decodeBase64(String dataUrl) {
        try {
            String base64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(",") + 1) : dataUrl;
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MsgViewHolder extends RecyclerView.ViewHolder {
        LinearLayout msgRoot, bubbleContainer;
        TextView tvSender, tvMessage;
        ImageView ivMedia;

        MsgViewHolder(@NonNull View itemView) {
            super(itemView);
            msgRoot = itemView.findViewById(R.id.msgRoot);
            bubbleContainer = itemView.findViewById(R.id.bubbleContainer);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            ivMedia = itemView.findViewById(R.id.ivMedia);
        }
    }
}
