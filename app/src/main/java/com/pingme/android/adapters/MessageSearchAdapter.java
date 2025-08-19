package com.pingme.android.adapters;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
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
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageSearchAdapter extends RecyclerView.Adapter<MessageSearchAdapter.MessageViewHolder> {
    private Context context;
    private List<Message> messagesList;
    private List<User> usersList;
    private OnMessageClickListener listener;
    private String searchQuery = "";
    private String currentUserId;

    public interface OnMessageClickListener {
        void onMessageClick(Message message);
    }

    public MessageSearchAdapter(Context context, List<Message> messagesList, List<User> usersList, OnMessageClickListener listener) {
        this.context = context;
        this.messagesList = messagesList;
        this.usersList = usersList;
        this.listener = listener;
        // Get current user ID for privacy checks
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use the proper search result layout that includes avatar
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_result, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messagesList.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messagesList.size();
    }

    public class MessageViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivContactImage;
        private TextView tvContactName;
        private TextView tvMessageText;
        private TextView tvTimestamp;
        private TextView tvChatName;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            // Use the proper IDs from item_search_result.xml
            ivContactImage = itemView.findViewById(R.id.ivContactImage);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvChatName = itemView.findViewById(R.id.tvChatName);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMessageClick(messagesList.get(position));
                }
            });
        }

        public void bind(Message message) {
            // Get sender name
            String senderName = getSenderName(message.getSenderId());
            tvContactName.setText(senderName);

            // Highlight search query in message text
            String messageContent = message.getText() != null ? message.getText() : "";
            SpannableString spannableString = new SpannableString(messageContent);
            
            if (!searchQuery.isEmpty() && messageContent.toLowerCase().contains(searchQuery.toLowerCase())) {
                String lowerMessage = messageContent.toLowerCase();
                String lowerQuery = searchQuery.toLowerCase();
                int start = lowerMessage.indexOf(lowerQuery);
                if (start >= 0) {
                    int end = start + searchQuery.length();
                    spannableString.setSpan(
                        new BackgroundColorSpan(context.getColor(R.color.search_highlight_background)),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }
            
            tvMessageText.setText(spannableString);

            // Set timestamp
            if (message.getTimestamp() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault());
                String timestamp = sdf.format(new Date(message.getTimestamp()));
                tvTimestamp.setText(timestamp);
                tvTimestamp.setVisibility(View.VISIBLE);
            } else {
                tvTimestamp.setVisibility(View.GONE);
            }

            // Hide chat name for now (this is for individual message search)
            tvChatName.setVisibility(View.GONE);

            // Load user avatar with privacy settings
            loadUserAvatar(message.getSenderId(), ivContactImage);
        }

        private String getSenderName(String senderId) {
            for (User user : usersList) {
                if (user.getId().equals(senderId)) {
                    return user.getDisplayName();
                }
            }
            return "Unknown User";
        }

        private void loadUserAvatar(String userId, ImageView imageView) {
            Log.d("MessageSearchAdapter", "Loading avatar for user: " + userId);
            
            FirebaseUtil.getUserRef(userId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        boolean isCurrentUser = userId.equals(currentUserId);
                        
                        // Apply privacy settings: current user always sees their avatar, others based on privacy
                        boolean shouldShowAvatar = isCurrentUser || user.shouldShowProfilePhoto();
                        
                        Log.d("MessageSearchAdapter", "User: " + userId + 
                              ", isCurrentUser: " + isCurrentUser + 
                              ", shouldShowAvatar: " + shouldShowAvatar + 
                              ", hasImageUrl: " + (user.getImageUrl() != null && !user.getImageUrl().trim().isEmpty()));
                        
                        if (shouldShowAvatar && user.hasProfilePhoto()) {
                            try {
                                Glide.with(context)
                                        .load(user.getImageUrl())
                                        .transform(new CircleCrop())
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .into(imageView);
                            } catch (Exception e) {
                                Log.e("MessageSearchAdapter", "Failed to load avatar for user: " + userId, e);
                                imageView.setImageResource(R.drawable.ic_person);
                            }
                        } else {
                            imageView.setImageResource(R.drawable.ic_person);
                        }
                    } else {
                        Log.w("MessageSearchAdapter", "User object is null for user: " + userId);
                        imageView.setImageResource(R.drawable.ic_person);
                    }
                } else {
                    Log.w("MessageSearchAdapter", "User document doesn't exist for user: " + userId);
                    imageView.setImageResource(R.drawable.ic_person);
                }
            }).addOnFailureListener(e -> {
                Log.e("MessageSearchAdapter", "Failed to load user data for user: " + userId, e);
                imageView.setImageResource(R.drawable.ic_person);
            });
        }
    }
}