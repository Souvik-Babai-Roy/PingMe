package com.pingme.android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.models.User;

import java.util.List;

public class FriendsLayoutAdapter extends RecyclerView.Adapter<FriendsLayoutAdapter.FriendViewHolder> {
    private Context context;
    private List<User> friendsList;
    private OnFriendClickListener listener;
    private OnFriendLongClickListener longClickListener;

    public interface OnFriendClickListener {
        void onFriendClick(User friend);
    }

    public interface OnFriendLongClickListener {
        void onFriendLongClick(User friend);
    }

    public FriendsLayoutAdapter(Context context, List<User> friendsList, OnFriendClickListener listener) {
        this.context = context;
        this.friendsList = friendsList;
        this.listener = listener;
    }

    public void setOnFriendLongClickListener(OnFriendLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_friend_layout, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        User friend = friendsList.get(position);
        holder.bind(friend);
    }

    @Override
    public int getItemCount() {
        return friendsList.size();
    }

    public class FriendViewHolder extends RecyclerView.ViewHolder {
        private ImageView profileImage;
        private TextView nameText;
        private TextView phoneText;
        private TextView statusText;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profileImage);
            nameText = itemView.findViewById(R.id.nameText);
            phoneText = itemView.findViewById(R.id.phoneText);
            statusText = itemView.findViewById(R.id.statusText);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFriendClick(friendsList.get(position));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onFriendLongClick(friendsList.get(position));
                    return true;
                }
                return false;
            });
        }

        public void bind(User friend) {
            // Display personal name if available, otherwise use original name
            String displayName = friend.getPersonalName() != null && !friend.getPersonalName().isEmpty() 
                ? friend.getPersonalName() 
                : friend.getName();
            
            nameText.setText(displayName);
            phoneText.setText(friend.getPhoneNumber());
            
            // Show online status
            if (friend.isOnline()) {
                statusText.setText("online");
                statusText.setTextColor(context.getColor(R.color.online_green));
            } else {
                statusText.setText("last seen " + getLastSeenText(friend.getLastSeen()));
                statusText.setTextColor(context.getColor(R.color.gray));
            }

            // Load profile image
            if (friend.getImageUrl() != null && !friend.getImageUrl().isEmpty()) {
                Glide.with(context)
                    .load(friend.getImageUrl())
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .circleCrop()
                    .into(profileImage);
            } else {
                profileImage.setImageResource(R.drawable.default_avatar);
            }
        }

        private String getLastSeenText(long lastSeen) {
            if (lastSeen == 0) return "unknown";
            
            long currentTime = System.currentTimeMillis();
            long diff = currentTime - lastSeen;
            
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) {
                return days + " day" + (days > 1 ? "s" : "") + " ago";
            } else if (hours > 0) {
                return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            } else if (minutes > 0) {
                return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
            } else {
                return "just now";
            }
        }
    }
}