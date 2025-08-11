package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.models.Broadcast;
import com.pingme.android.models.User;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class BroadcastListAdapter extends RecyclerView.Adapter<BroadcastListAdapter.BroadcastViewHolder> {
    private List<Broadcast> broadcasts;
    private OnBroadcastClickListener listener;

    public interface OnBroadcastClickListener {
        void onBroadcastClick(Broadcast broadcast);
    }

    public BroadcastListAdapter(List<Broadcast> broadcasts, OnBroadcastClickListener listener) {
        this.broadcasts = broadcasts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BroadcastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_broadcast_list, parent, false);
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

    class BroadcastViewHolder extends RecyclerView.ViewHolder {
        private CircleImageView ivBroadcastIcon;
        private TextView tvBroadcastName;
        private TextView tvMemberCount;
        private TextView tvLastMessage;
        private TextView tvLastMessageTime;
        private TextView tvUnreadCount;

        public BroadcastViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBroadcastIcon = itemView.findViewById(R.id.ivBroadcastIcon);
            tvBroadcastName = itemView.findViewById(R.id.tvBroadcastName);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvLastMessageTime = itemView.findViewById(R.id.tvLastMessageTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBroadcastClick(broadcasts.get(position));
                }
            });
        }

        public void bind(Broadcast broadcast) {
            tvBroadcastName.setText(broadcast.getDisplayName());
            tvMemberCount.setText(broadcast.getMemberCountText());
            tvLastMessage.setText(broadcast.getLastMessagePreview());
            tvLastMessageTime.setText(broadcast.getFormattedTime());

            // Set unread count
            if (broadcast.getHasUnreadMessages()) {
                tvUnreadCount.setVisibility(View.VISIBLE);
                tvUnreadCount.setText(broadcast.getUnreadCountText());
            } else {
                tvUnreadCount.setVisibility(View.GONE);
            }

            // Load broadcast icon
            if (broadcast.getImageUrl() != null && !broadcast.getImageUrl().isEmpty()) {
                Glide.with(ivBroadcastIcon.getContext())
                        .load(broadcast.getImageUrl())
                        .placeholder(R.drawable.ic_broadcast)
                        .error(R.drawable.ic_broadcast)
                        .into(ivBroadcastIcon);
            } else {
                ivBroadcastIcon.setImageResource(R.drawable.ic_broadcast);
            }
        }
    }
}