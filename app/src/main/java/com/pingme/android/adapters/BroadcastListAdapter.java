package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pingme.android.R;
import com.pingme.android.models.Broadcast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BroadcastListAdapter extends RecyclerView.Adapter<BroadcastListAdapter.BroadcastViewHolder> {

    private List<Broadcast> broadcasts;
    private OnBroadcastClickListener listener;

    public interface OnBroadcastClickListener {
        void onBroadcastClick(Broadcast broadcast);
        void onBroadcastLongClick(Broadcast broadcast);
    }

    public BroadcastListAdapter(List<Broadcast> broadcasts, OnBroadcastClickListener listener) {
        this.broadcasts = broadcasts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BroadcastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_broadcast, parent, false);
        return new BroadcastViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BroadcastViewHolder holder, int position) {
        Broadcast broadcast = broadcasts.get(position);
        holder.bind(broadcast);
    }

    @Override
    public int getItemCount() {
        return broadcasts.size();
    }

    public void updateBroadcasts(List<Broadcast> newBroadcasts) {
        this.broadcasts = newBroadcasts;
        notifyDataSetChanged();
    }

    public class BroadcastViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivBroadcastIcon;
        private TextView tvBroadcastName;
        private TextView tvLastMessage;
        private TextView tvTimestamp;
        private TextView tvMemberCount;

        public BroadcastViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBroadcastIcon = itemView.findViewById(R.id.ivBroadcastIcon);
            tvBroadcastName = itemView.findViewById(R.id.tvBroadcastName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
        }

        public void bind(Broadcast broadcast) {
            tvBroadcastName.setText(broadcast.getName());
            
            // Show member count
            int memberCount = broadcast.getMemberIds() != null ? broadcast.getMemberIds().size() : 0;
            tvMemberCount.setText(memberCount + " recipients");

            // Show last message or default text
            if (broadcast.getLastMessage() != null && !broadcast.getLastMessage().isEmpty()) {
                tvLastMessage.setText(broadcast.getLastMessage());
                tvLastMessage.setVisibility(View.VISIBLE);
            } else {
                tvLastMessage.setText("Tap to send message to this broadcast");
                tvLastMessage.setVisibility(View.VISIBLE);
            }

            // Show timestamp
            if (broadcast.getLastMessageTimestamp() > 0) {
                String timeText = formatTimestamp(broadcast.getLastMessageTimestamp());
                tvTimestamp.setText(timeText);
                tvTimestamp.setVisibility(View.VISIBLE);
            } else {
                tvTimestamp.setVisibility(View.GONE);
            }

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBroadcastClick(broadcast);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onBroadcastLongClick(broadcast);
                }
                return true;
            });
        }

        private String formatTimestamp(long timestamp) {
            Date date = new Date(timestamp);
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 24 * 60 * 60 * 1000) { // Less than 24 hours
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return timeFormat.format(date);
            } else if (diff < 7 * 24 * 60 * 60 * 1000) { // Less than 7 days
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                return dayFormat.format(date);
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                return dateFormat.format(date);
            }
        }
    }
}