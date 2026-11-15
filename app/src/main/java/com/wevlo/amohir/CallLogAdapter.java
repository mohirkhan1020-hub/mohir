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

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.VH> {

    private final List<CallLogModel> list;
    private String myUid;

    public CallLogAdapter(List<CallLogModel> list, String myUid) {
        this.list = list;
        this.myUid = myUid;
    }

    public void setMyUid(String myUid) { this.myUid = myUid; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_log, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        CallLogModel c = list.get(pos);
        boolean outgoing = myUid != null && myUid.equals(c.getCallerId());
        String peer = outgoing ? c.getReceiverName() : c.getCallerName();
        h.tvCallPeer.setText(peer != null ? peer : "Friend");

        String time = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date(c.getTimestamp()));
        h.tvCallInfo.setText((outgoing ? "↗ Outgoing • " : "↙ Incoming • ") + time);
        h.tvCallIcon.setText("video".equals(c.getType()) ? "🎥" : "📞");
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCallPeer, tvCallInfo, tvCallIcon;
        VH(@NonNull View v) {
            super(v);
            tvCallPeer = v.findViewById(R.id.tvCallPeer);
            tvCallInfo = v.findViewById(R.id.tvCallInfo);
            tvCallIcon = v.findViewById(R.id.tvCallIcon);
        }
    }
}
