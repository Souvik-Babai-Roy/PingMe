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
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.pingme.android.R;
import com.pingme.android.models.User;

import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {
    
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

    public FriendsAdapter(Context context, List<User> friendsList, OnFriendClickListener listener) {
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
    
    public void setOnFriendLongClickListener(OnFriendLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_friend, parent, false);
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

    public void updateFriends(List<User> newFriendsList) {
        this.friendsList = newFriendsList;
        notifyDataSetChanged();
    }

    class FriendViewHolder extends RecyclerView.ViewHolder {
        private ImageView profileImage;
        private TextView nameText;
        private TextView emailText;
        private TextView statusText;
        private View onlineIndicator;
        private com.google.android.material.button.MaterialButton btnMessage;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            
            profileImage = itemView.findViewById(R.id.ivProfile);
            nameText = itemView.findViewById(R.id.tvFriendName);
            emailText = itemView.findViewById(R.id.tvFriendAbout);
            statusText = itemView.findViewById(R.id.tvFriendAbout);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            btnMessage = itemView.findViewById(R.id.btnMessage);
            
            btnMessage.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFriendClick(friendsList.get(position));
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onFriendLongClick(friendsList.get(position));
                    return true; // Consume the long press
                }
                return false;
            });
        }

        public void bind(User friend) {
            // Set name (use display name which prefers personal name)
            nameText.setText(friend.getDisplayName());
            
            // Set about or email as secondary text
            String secondaryText = null;
            if (friend.isAboutEnabled() && friend.getDisplayAbout() != null && !friend.getDisplayAbout().isEmpty()) {
                secondaryText = friend.getDisplayAbout();
            } else if (friend.getEmail() != null && !friend.getEmail().isEmpty()) {
                secondaryText = friend.getEmail();
            }
            
            if (secondaryText != null) {
                emailText.setText(secondaryText);
                emailText.setVisibility(View.VISIBLE);
            } else {
                emailText.setVisibility(View.GONE);
            }
            
            // Set profile image with current user priority logic
            boolean isCurrentUser = friend.getId().equals(currentUserId);
            boolean shouldShowAvatar = isCurrentUser || friend.isProfilePhotoEnabled();
            
            if (shouldShowAvatar && friend.getImageUrl() != null && !friend.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(friend.getImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.defaultprofile)
                        .error(R.drawable.defaultprofile)
                        .into(profileImage);
            } else {
                // Use default profile if photo is disabled or not available
                profileImage.setImageResource(R.drawable.defaultprofile);
            }
            
            // Set online indicator - respect last seen privacy settings
            if (friend.isLastSeenEnabled() && friend.isOnline()) {
                onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                onlineIndicator.setVisibility(View.GONE);
            }
        }
    }
}