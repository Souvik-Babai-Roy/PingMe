package com.pingme.android.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
import com.pingme.android.activities.ChatActivity;
import com.pingme.android.models.Chat;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {

    private Context context;
    private List<Chat> chats = new ArrayList<>();
    private String currentUserId;

    public ChatListAdapter(Context context) {
        this.context = context;
        // FIXED: Check for null user to prevent crash
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            this.currentUserId = "";
        }
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        holder.bind(chat);
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public void updateChats(List<Chat> newChats) {
        this.chats.clear();
        this.chats.addAll(newChats);
        notifyDataSetChanged();
    }

    public void addOrUpdateChat(Chat chat) {
        int existingIndex = -1;
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).getId().equals(chat.getId())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            chats.set(existingIndex, chat);
            notifyItemChanged(existingIndex);
        } else {
            chats.add(0, chat);
            notifyItemInserted(0);
        }
    }

    public void removeChat(String chatId) {
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).getId().equals(chatId)) {
                chats.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfile;
        View onlineIndicator;
        TextView tvName;
        TextView tvLastMessage;
        TextView tvTime;
        TextView tvUnreadCount;

        public ChatViewHolder(View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.ivProfile);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
        }

        public void bind(Chat chat) {
            User otherUser = chat.getOtherUser();
            if (otherUser == null) return;

            // Set user name
            String displayName = otherUser.getDisplayName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Unknown User";
            }
            tvName.setText(displayName);

            // FIXED: Load profile image respecting privacy settings
            try {
                if (otherUser.isProfilePhotoEnabled() && otherUser.getImageUrl() != null && !otherUser.getImageUrl().trim().isEmpty()) {
                    Glide.with(context)
                            .load(otherUser.getImageUrl())
                            .transform(new CircleCrop())
                            .placeholder(R.drawable.defaultprofile)
                            .error(R.drawable.defaultprofile)
                            .into(ivProfile);
                } else {
                    ivProfile.setImageResource(R.drawable.defaultprofile);
                }
            } catch (Exception e) {
                ivProfile.setImageResource(R.drawable.defaultprofile);
            }

            // FIXED: Show online indicator respecting user's last seen privacy settings
            if (otherUser.isLastSeenEnabled() && otherUser.isOnline()) {
                onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                onlineIndicator.setVisibility(View.GONE);
            }

            // FIXED: Improved last message preview logic
            tvLastMessage.setText(getLastMessagePreview(chat));

            // FIXED: Better time formatting and visibility logic
            String formattedTime = getFormattedTime(chat.getLastMessageTimestamp());
            if (!formattedTime.isEmpty() && hasContent(chat)) {
                tvTime.setText(formattedTime);
                tvTime.setVisibility(View.VISIBLE);
            } else {
                tvTime.setVisibility(View.INVISIBLE);
            }

            // FIXED: Show unread count with better logic
            if (chat.getUnreadCount() > 0) {
                tvUnreadCount.setVisibility(View.VISIBLE);
                String unreadText = chat.getUnreadCount() > 99 ? "99+" : String.valueOf(chat.getUnreadCount());
                tvUnreadCount.setText(unreadText);
            } else {
                tvUnreadCount.setVisibility(View.GONE);
            }

            // FIXED: Typing indicator with proper styling
            if (chat.isTyping()) {
                tvLastMessage.setText("typing...");
                tvLastMessage.setTextColor(context.getColor(R.color.colorPrimary));
                tvLastMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
            } else {
                tvLastMessage.setTextColor(context.getColor(R.color.textColorSecondary));
                tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            // FIXED: Click listeners with null checks
            itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(context, ChatActivity.class);
                    intent.putExtra("chatId", chat.getId());
                    intent.putExtra("receiverId", otherUser.getId());
                    context.startActivity(intent);
                } catch (Exception e) {
                    // Handle any potential errors opening chat
                }
            });

            // Long click for context menu
            itemView.setOnLongClickListener(v -> {
                showContextMenu(v, chat);
                return true;
            });
        }

        // FIXED: Improved last message preview logic
        private String getLastMessagePreview(Chat chat) {
            if (chat.isTyping()) {
                return "typing...";
            }

            String lastMessage = chat.getLastMessage();
            String lastMessageType = chat.getLastMessageType();

            // Handle different chat states
            if (lastMessageType == null) {
                lastMessageType = "text";
            }

            // Check for empty or new friend chats
            if (!hasContent(chat)) {
                return "Tap to start messaging";
            }

            // Check if user cleared their chat
            if ("chat_cleared".equals(lastMessageType)) {
                return "You cleared this chat";
            }

            // Handle different message types with proper preview
            switch (lastMessageType.toLowerCase()) {
                case "image":
                    return "📷 Photo";
                case "video":
                    return "🎥 Video";
                case "audio":
                    return "🎤 Audio";
                case "document":
                    return "📄 Document";
                default:
                    return lastMessage;
            }
        }

        private String getFormattedTime(long timestamp) {
            if (timestamp <= 0) return "";

            Calendar today = Calendar.getInstance();
            Calendar dateCal = Calendar.getInstance();
            dateCal.setTimeInMillis(timestamp);

            if (today.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)) {
                return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timestamp));
            }
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            if (yesterday.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)) {
                return "Yesterday";
            }
            return new SimpleDateFormat("MMM dd", Locale.getDefault()).format(new Date(timestamp));
        }

        private boolean hasContent(Chat chat) {
            String lastMessage = chat.getLastMessage();
            String type = chat.getLastMessageType();
            if (type == null) type = "text";
            if ("friend_added".equals(type) || "empty_chat".equals(type)) return false;
            return lastMessage != null && !lastMessage.trim().isEmpty();
        }

        private void showContextMenu(View anchor, Chat chat) {
            PopupMenu popup = new PopupMenu(context, anchor);
            popup.getMenuInflater().inflate(R.menu.chat_context_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_unfriend) {
                    showUnfriendConfirmation(chat);
                    return true;
                } else if (itemId == R.id.menu_block_user) {
                    showBlockConfirmation(chat);
                    return true;
                } else if (itemId == R.id.menu_clear_chat) {
                    showClearChatConfirmation(chat);
                    return true;
                } else if (itemId == R.id.menu_delete_chat) {
                    showDeleteChatConfirmation(chat);
                    return true;
                }
                return false;
            });
            popup.show();
        }

        private void showUnfriendConfirmation(Chat chat) {
            User friend = getFriendFromChat(chat);
            if (friend == null) return;

            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Remove Friend")
                    .setMessage("Are you sure you want to remove " + friend.getDisplayName() + " from your friends? You can add them back later.")
                    .setPositiveButton("Remove", (dialog, which) -> {
                        FirestoreUtil.removeFriend(currentUserId, friend.getId(), new FirestoreUtil.FriendActionCallback() {
                            @Override
                            public void onSuccess() {
                                // Remove chat from list
                                int position = chats.indexOf(chat);
                                if (position != -1) {
                                    chats.remove(position);
                                    notifyItemRemoved(position);
                                }
                            }

                            @Override
                            public void onError(String error) {
                                // Show error message
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void showBlockConfirmation(Chat chat) {
            User friend = getFriendFromChat(chat);
            if (friend == null) return;

            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Block User")
                    .setMessage("Are you sure you want to block " + friend.getDisplayName() + "? They won't be able to send you messages.")
                    .setPositiveButton("Block", (dialog, which) -> {
                        FirestoreUtil.blockUser(currentUserId, friend.getId(), new FirestoreUtil.FriendActionCallback() {
                            @Override
                            public void onSuccess() {
                                // Remove chat from list
                                int position = chats.indexOf(chat);
                                if (position != -1) {
                                    chats.remove(position);
                                    notifyItemRemoved(position);
                                }
                            }

                            @Override
                            public void onError(String error) {
                                // Show error message
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void showClearChatConfirmation(Chat chat) {
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Clear Chat")
                    .setMessage("Are you sure you want to clear this chat? This action cannot be undone.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        // TODO: Implement clear chat functionality
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void showDeleteChatConfirmation(Chat chat) {
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Delete Chat")
                    .setMessage("Are you sure you want to delete this chat? This action cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        // TODO: Implement delete chat functionality
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private User getFriendFromChat(Chat chat) {
            // Get the friend user from the chat (the other user)
            return chat.getOtherUser();
        }
    }
}