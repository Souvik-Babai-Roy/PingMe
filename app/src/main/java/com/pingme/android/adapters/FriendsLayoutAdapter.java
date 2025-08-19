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
import com.google.firebase.auth.FirebaseAuth;
import com.pingme.android.R;
import com.pingme.android.models.User;

import java.util.List;

public class FriendsLayoutAdapter extends RecyclerView.Adapter<FriendsLayoutAdapter.FriendViewHolder> {
    private Context context;
    private List<User> friendsList;
    private OnFriendClickListener listener;
    private OnFriendLongClickListener longClickListener;
    private String currentUserId;

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
        // Initialize current user ID
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            this.currentUserId = "";
        }
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
            
            // Show email address instead of phone number
            if (friend.getEmail() != null && !friend.getEmail().isEmpty()) {
                phoneText.setText(friend.getEmail());
                phoneText.setVisibility(View.VISIBLE);
            } else {
                phoneText.setVisibility(View.GONE);
            }
            
            // Show online status respecting privacy settings
            if (friend.isLastSeenEnabled()) {
                if (friend.isOnline()) {
                    statusText.setText("online");
                    statusText.setTextColor(context.getColor(R.color.online_green));
                } else {
                    statusText.setText("last seen " + getLastSeenText(friend.getLastSeen()));
                    statusText.setTextColor(context.getColor(R.color.gray));
                }
                statusText.setVisibility(View.VISIBLE);
            } else {
                // Don't show any status if last seen is disabled
                statusText.setVisibility(View.GONE);
            }

            // Load profile image with current user priority logic
            boolean isCurrentUser = friend.getId().equals(currentUserId);
            boolean shouldShowAvatar = isCurrentUser || friend.isProfilePhotoEnabled();
            
            if (shouldShowAvatar && friend.getImageUrl() != null && !friend.getImageUrl().isEmpty()) {
                Glide.with(context)
                    .load(friend.getImageUrl())
                    .placeholder(R.drawable.defaultprofile)
                    .error(R.drawable.defaultprofile)
                    .circleCrop()
                    .into(profileImage);
            } else {
                profileImage.setImageResource(R.drawable.defaultprofile);
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