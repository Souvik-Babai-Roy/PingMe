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
import com.pingme.android.R;
import com.pingme.android.models.User;

import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {
    
    private Context context;
    private List<User> friendsList;
    private OnFriendClickListener listener;

    public interface OnFriendClickListener {
        void onFriendClick(User friend);
    }

    public FriendsAdapter(Context context, List<User> friendsList, OnFriendClickListener listener) {
        this.context = context;
        this.friendsList = friendsList;
        this.listener = listener;
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

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            
            profileImage = itemView.findViewById(R.id.ivProfile);
            nameText = itemView.findViewById(R.id.tvName);
            emailText = itemView.findViewById(R.id.tvEmail);
            statusText = itemView.findViewById(R.id.tvStatus);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFriendClick(friendsList.get(position));
                }
            });
        }

        public void bind(User friend) {
            // Set name
            nameText.setText(friend.getName());
            
            // Set email
            if (friend.getEmail() != null && !friend.getEmail().isEmpty()) {
                emailText.setText(friend.getEmail());
                emailText.setVisibility(View.VISIBLE);
            } else {
                emailText.setVisibility(View.GONE);
            }
            
            // Set profile image
            if (friend.getImageUrl() != null && !friend.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(friend.getImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.defaultprofile)
                        .error(R.drawable.defaultprofile)
                        .into(profileImage);
            } else {
                profileImage.setImageResource(R.drawable.defaultprofile);
            }
            
            // Set online status
            if (friend.isOnline()) {
                statusText.setText("online");
                statusText.setTextColor(context.getResources().getColor(R.color.online_green));
                onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                long lastSeen = friend.getLastSeen();
                if (lastSeen > 0) {
                    // Format last seen time
                    long now = System.currentTimeMillis();
                    long diff = now - lastSeen;
                    
                    if (diff < 60000) { // Less than 1 minute
                        statusText.setText("just now");
                    } else if (diff < 3600000) { // Less than 1 hour
                        long minutes = diff / 60000;
                        statusText.setText(minutes + "m ago");
                    } else if (diff < 86400000) { // Less than 1 day
                        long hours = diff / 3600000;
                        statusText.setText(hours + "h ago");
                    } else {
                        long days = diff / 86400000;
                        statusText.setText(days + "d ago");
                    }
                } else {
                    statusText.setText("offline");
                }
                statusText.setTextColor(context.getResources().getColor(R.color.gray_medium));
                onlineIndicator.setVisibility(View.GONE);
            }
        }
    }
}