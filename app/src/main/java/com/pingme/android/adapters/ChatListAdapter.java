package com.pingme.android.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
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
import com.pingme.android.utils.FirebaseUtil;
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
        Log.d("ChatListAdapter", "addOrUpdateChat called for chat: " + chat.getId() + " with unread count: " + chat.getUnreadCount());
        // Avoid inserting blank items with no other user info
        if (chat.getOtherUser() == null) {
            Log.d("ChatListAdapter", "Skipping add/update for chat " + chat.getId() + " because otherUser is null");
            return;
        }
        
        int existingIndex = -1;
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).getId().equals(chat.getId())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            Log.d("ChatListAdapter", "Updating existing chat at index: " + existingIndex);
            chats.set(existingIndex, chat);
            notifyItemChanged(existingIndex);
        } else {
            Log.d("ChatListAdapter", "Adding new chat at top");
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
        ImageView ivMessageStatus;

        public ChatViewHolder(View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.ivProfile);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            ivMessageStatus = itemView.findViewById(R.id.ivMessageStatus);
        }

        public void bind(Chat chat) {
            User otherUser = chat.getOtherUser();
            if (otherUser == null) return;

            // Set user name with priority for personal names
            String personalName = otherUser.getPersonalName();
            String regularName = otherUser.getName();
            
            Log.d("ChatListAdapter", "User " + otherUser.getId() + " - personalName: '" + personalName + "', regularName: '" + regularName + "'");
            
            String displayName = personalName != null && !personalName.trim().isEmpty()
                    ? personalName
                    : regularName;
            
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Unknown User";
            }
            
            Log.d("ChatListAdapter", "Final display name for user " + otherUser.getId() + ": '" + displayName + "'");
            tvName.setText(displayName);

            // FIXED: Load profile image with current user priority logic
            try {
                boolean isCurrentUser = otherUser.getId().equals(currentUserId);
                boolean shouldShowAvatar = isCurrentUser || otherUser.isProfilePhotoEnabled();
                
                if (shouldShowAvatar && otherUser.getImageUrl() != null && !otherUser.getImageUrl().trim().isEmpty()) {
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

            // Enhanced online status logic - only show if user allows last seen AND is actually online
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

            // Enhanced unread count display
            if (chat.getUnreadCount() > 0) {
                Log.d("ChatListAdapter", "Showing unread count: " + chat.getUnreadCount() + " for chat: " + chat.getId());
                tvUnreadCount.setVisibility(View.VISIBLE);
                
                // Format unread count like WhatsApp
                String unreadText;
                int count = chat.getUnreadCount();
                if (count > 999) {
                    unreadText = "999+";
                } else if (count > 99) {
                    unreadText = "99+";
                } else {
                    unreadText = String.valueOf(count);
                }
                
                tvUnreadCount.setText(unreadText);
                
                // Set background color based on count for visual emphasis
                if (count > 99) {
                    tvUnreadCount.setBackgroundTintList(context.getColorStateList(R.color.unread_high));
                } else if (count > 9) {
                    tvUnreadCount.setBackgroundTintList(context.getColorStateList(R.color.unread_medium));
                } else {
                    tvUnreadCount.setBackgroundTintList(context.getColorStateList(R.color.unread_normal));
                }
            } else {
                Log.d("ChatListAdapter", "Hiding unread count for chat: " + chat.getId() + " (count: " + chat.getUnreadCount() + ")");
                tvUnreadCount.setVisibility(View.GONE);
            }

            // Show message status for sent messages with actual status from database
            if (chat.getLastMessageSenderId() != null && chat.getLastMessageSenderId().equals(currentUserId)) {
                ivMessageStatus.setVisibility(View.VISIBLE);
                setMessageStatusIcon(ivMessageStatus, chat);
            } else {
                ivMessageStatus.setVisibility(View.GONE);
            }

            // Enhanced typing indicator with proper styling
            if (chat.isTyping()) {
                tvLastMessage.setText("typing...");
                tvLastMessage.setTextColor(context.getColor(R.color.colorPrimary));
                tvLastMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                // Hide message status when typing
                ivMessageStatus.setVisibility(View.GONE);
            } else {
                tvLastMessage.setTextColor(context.getColor(R.color.textColorSecondary));
                tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            // Enhanced click listeners with null checks
            itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(context, ChatActivity.class);
                    intent.putExtra("chatId", chat.getId());
                    intent.putExtra("receiverId", otherUser.getId());
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e("ChatListAdapter", "Error opening chat", e);
                }
            });

            // Long click for context menu
            itemView.setOnLongClickListener(v -> {
                showContextMenu(v, chat);
                return true;
            });
        }

        // Enhanced last message preview logic
        private String getLastMessagePreview(Chat chat) {
            if (chat.isTyping()) {
                return "typing...";
            }

            String lastMessage = chat.getLastMessage();
            String lastMessageType = chat.getLastMessageType();
            String lastMessageSenderId = chat.getLastMessageSenderId();

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

            // Add sender prefix for group-like display (even though it's 1-on-1)
            String senderPrefix = "";
            if (lastMessageSenderId != null && lastMessageSenderId.equals(currentUserId)) {
                senderPrefix = "You: ";
            }

            // Handle different message types with proper preview
            switch (lastMessageType.toLowerCase()) {
                case "image":
                    return senderPrefix + "📷 Photo";
                case "video":
                    return senderPrefix + "🎥 Video";
                case "audio":
                    return senderPrefix + "🎤 Audio";
                case "document":
                    return senderPrefix + "📄 Document";
                case "location":
                    return senderPrefix + "📍 Location";
                case "contact":
                    return senderPrefix + "👤 Contact";
                case "sticker":
                    return senderPrefix + "🎭 Sticker";
                case "gif":
                    return senderPrefix + "🎬 GIF";
                default:
                    // For text messages, truncate if too long
                    if (lastMessage != null && !lastMessage.trim().isEmpty()) {
                        String message = lastMessage.trim();
                        if (message.length() > 35) {
                            message = message.substring(0, 32) + "...";
                        }
                        return senderPrefix + message;
                    }
                    return senderPrefix + "Message";
            }
        }

        // Enhanced time formatting like WhatsApp
        private String getFormattedTime(long timestamp) {
            if (timestamp <= 0) return "";

            Calendar messageTime = Calendar.getInstance();
            messageTime.setTimeInMillis(timestamp);
            
            Calendar now = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            
            // Same day - show time only
            if (isSameDay(messageTime, now)) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return timeFormat.format(new Date(timestamp));
            }
            // Yesterday - show "Yesterday"
            else if (isSameDay(messageTime, yesterday)) {
                return "Yesterday";
            }
            // This week - show day name
            else if (isThisWeek(messageTime, now)) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                return dayFormat.format(new Date(timestamp));
            }
            // Older - show date
            else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            }
        }
        
        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }
        
        private boolean isThisWeek(Calendar messageTime, Calendar now) {
            Calendar weekStart = Calendar.getInstance();
            weekStart.setTime(now.getTime());
            weekStart.add(Calendar.DAY_OF_YEAR, -7);
            
            return messageTime.after(weekStart) && messageTime.before(now);
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
                        FirebaseUtil.removeFriend(currentUserId, friend.getId(), new FirebaseUtil.FriendActionCallback() {
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
                        FirebaseUtil.blockUser(currentUserId, friend.getId(), new FirebaseUtil.FriendActionCallback() {
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

        private void setMessageStatusIcon(ImageView statusIcon, Chat chat) {
            Log.d("ChatListAdapter", "setMessageStatusIcon for chat: " + chat.getId() + " lastMessageId: " + chat.getLastMessageId());
            
            // First try to use lastMessageId if available
            if (chat.getLastMessageId() != null && !chat.getLastMessageId().isEmpty()) {
                Log.d("ChatListAdapter", "Using lastMessageId: " + chat.getLastMessageId());
                loadMessageStatusById(statusIcon, chat, chat.getLastMessageId());
            } else {
                Log.d("ChatListAdapter", "lastMessageId is null/empty, using fallback query");
                // Fallback: Query for the most recent message sent by current user
                loadMostRecentSentMessage(statusIcon, chat);
            }
        }
        
        private void loadMessageStatusById(ImageView statusIcon, Chat chat, String messageId) {
            FirebaseUtil.getMessagesRef(chat.getId()).child(messageId)
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        processMessageStatus(statusIcon, chat, dataSnapshot);
                    } else {
                        // Message not found, try fallback
                        loadMostRecentSentMessage(statusIcon, chat);
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError databaseError) {
                    // Error handling - show default status
                    statusIcon.setImageResource(R.drawable.ic_sent);
                }
            });
        }
        
        private void loadMostRecentSentMessage(ImageView statusIcon, Chat chat) {
            // Query for the most recent message sent by current user
            FirebaseUtil.getMessagesRef(chat.getId())
                    .orderByChild("timestamp")
                    .limitToLast(10) // Get last 10 messages to find the most recent sent by current user
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot dataSnapshot) {
                    com.google.firebase.database.DataSnapshot mostRecentSentMessage = null;
                    
                    // Find the most recent message sent by current user
                    for (com.google.firebase.database.DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                        String senderId = messageSnapshot.child("senderId").getValue(String.class);
                        if (currentUserId.equals(senderId)) {
                            mostRecentSentMessage = messageSnapshot;
                        }
                    }
                    
                    if (mostRecentSentMessage != null) {
                        processMessageStatus(statusIcon, chat, mostRecentSentMessage);
                    } else {
                        // No sent messages found, hide status icon
                        statusIcon.setImageResource(R.drawable.ic_sent);
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError databaseError) {
                    statusIcon.setImageResource(R.drawable.ic_sent);
                }
            });
        }
        
        private void processMessageStatus(ImageView statusIcon, Chat chat, com.google.firebase.database.DataSnapshot messageSnapshot) {
            // Get message data
            Integer status = messageSnapshot.child("status").getValue(Integer.class);
            com.google.firebase.database.DataSnapshot deliveredTo = messageSnapshot.child("deliveredTo");
            com.google.firebase.database.DataSnapshot readBy = messageSnapshot.child("readBy");
            
            // Check receiver's read receipts privacy setting
            User otherUser = chat.getOtherUser();
            boolean receiverAllowsReadReceipts = otherUser != null && otherUser.isReadReceiptsEnabled();
            
            // Determine the actual status based on the message data and privacy settings
            int actualStatus = com.pingme.android.models.Message.STATUS_SENT; // Default
            
            if (status != null) {
                // Check if message has been read (blue ticks) - only if receiver allows read receipts
                if (readBy.hasChildren() && receiverAllowsReadReceipts) {
                    actualStatus = com.pingme.android.models.Message.STATUS_READ;
                }
                // Check if message has been delivered (gray double ticks)
                else if (deliveredTo.hasChildren()) {
                    actualStatus = com.pingme.android.models.Message.STATUS_DELIVERED;
                }
                // Otherwise, message is just sent (gray single tick)
                else {
                    actualStatus = com.pingme.android.models.Message.STATUS_SENT;
                }
            }
            
            // Set the appropriate icon based on actual status
            switch (actualStatus) {
                case com.pingme.android.models.Message.STATUS_SENT:
                    statusIcon.setImageResource(R.drawable.ic_sent); // Single gray tick
                    break;
                case com.pingme.android.models.Message.STATUS_DELIVERED:
                    statusIcon.setImageResource(R.drawable.ic_delivered); // Double gray tick
                    break;
                case com.pingme.android.models.Message.STATUS_READ:
                    statusIcon.setImageResource(R.drawable.ic_read); // Double blue tick
                    break;
                default:
                    statusIcon.setImageResource(R.drawable.ic_sent);
                    break;
            }
        }
    }
}