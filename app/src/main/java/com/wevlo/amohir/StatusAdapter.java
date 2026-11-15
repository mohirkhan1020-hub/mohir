package com.wevlo.amohir;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.VH> {

    public interface OnStatusClickListener {
        void onStatusClick(StatusModel status);
    }

    private final List<StatusModel> list;
    private final OnStatusClickListener listener;

    public StatusAdapter(List<StatusModel> list, OnStatusClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_status, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        StatusModel s = list.get(pos);
        h.tvStatusUser.setText(s.getUserName() != null ? s.getUserName() : "Friend");
        h.tvStatusIcon.setText(s.getImageBase64() != null ? "🖼️" : "📝");
        h.tvStatusPreview.setText(s.getText() != null && !s.getText().isEmpty() ? s.getText() : "Photo status");
        h.tvStatusTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(s.getTimestamp())));
        h.itemView.setOnClickListener(v -> listener.onStatusClick(s));
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvStatusIcon, tvStatusUser, tvStatusPreview, tvStatusTime;
        VH(@NonNull View v) {
            super(v);
            tvStatusIcon = v.findViewById(R.id.tvStatusIcon);
            tvStatusUser = v.findViewById(R.id.tvStatusUser);
            tvStatusPreview = v.findViewById(R.id.tvStatusPreview);
            tvStatusTime = v.findViewById(R.id.tvStatusTime);
        }
    }
}
