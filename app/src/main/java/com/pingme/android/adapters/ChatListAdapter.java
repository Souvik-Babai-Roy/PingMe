package com.pingme.android.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
import com.pingme.android.activities.ChatActivity;
import com.pingme.android.databinding.ItemChatBinding;
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
        ItemChatBinding binding = ItemChatBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatViewHolder(binding);
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
        private ItemChatBinding binding;

        public ChatViewHolder(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Chat chat) {
            User otherUser = chat.getOtherUser();
            if (otherUser == null) return;

            // Set user name
            String displayName = otherUser.getDisplayName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Unknown User";
            }
            binding.tvName.setText(displayName);

            // FIXED: Load profile image respecting privacy settings
            try {
                if (otherUser.isProfilePhotoEnabled() && otherUser.getImageUrl() != null && !otherUser.getImageUrl().trim().isEmpty()) {
                    Glide.with(context)
                            .load(otherUser.getImageUrl())
                            .transform(new CircleCrop())
                            .placeholder(R.drawable.defaultprofile)
                            .error(R.drawable.defaultprofile)
                            .into(binding.ivProfile);
                } else {
                    binding.ivProfile.setImageResource(R.drawable.defaultprofile);
                }
            } catch (Exception e) {
                binding.ivProfile.setImageResource(R.drawable.defaultprofile);
            }

            // FIXED: Show online indicator respecting user's last seen privacy settings
            if (otherUser.isLastSeenEnabled() && otherUser.isOnline()) {
                binding.onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                binding.onlineIndicator.setVisibility(View.GONE);
            }

            // FIXED: Improved last message preview logic
            binding.tvLastMessage.setText(getLastMessagePreview(chat));

            // FIXED: Better time formatting and visibility logic
            String formattedTime = getFormattedTime(chat.getLastMessageTimestamp());
            if (!formattedTime.isEmpty() && hasContent(chat)) {
                binding.tvTime.setText(formattedTime);
                binding.tvTime.setVisibility(View.VISIBLE);
            } else {
                binding.tvTime.setVisibility(View.INVISIBLE);
            }

            // FIXED: Show unread count with better logic
            if (chat.getUnreadCount() > 0) {
                binding.tvUnreadCount.setVisibility(View.VISIBLE);
                String unreadText = chat.getUnreadCount() > 99 ? "99+" : String.valueOf(chat.getUnreadCount());
                binding.tvUnreadCount.setText(unreadText);
            } else {
                binding.tvUnreadCount.setVisibility(View.GONE);
            }

            // FIXED: Typing indicator with proper styling
            if (chat.isTyping()) {
                binding.tvLastMessage.setText("typing...");
                binding.tvLastMessage.setTextColor(context.getColor(R.color.colorPrimary));
                binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
            } else {
                binding.tvLastMessage.setTextColor(context.getColor(R.color.textColorSecondary));
                binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
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
                case "location":
                    return "📍 Location";
                case "contact":
                    return "👤 Contact";
                case "text":
                default:
                    // For text messages, show preview with length limit
                    if (lastMessage.length() > 50) {
                        return lastMessage.substring(0, 47) + "...";
                    }
                    return lastMessage;
            }
        }

        // FIXED: Better time formatting with more accurate logic
        private String getFormattedTime(long timestamp) {
            if (timestamp <= 0) {
                return "";
            }

            try {
                Calendar now = Calendar.getInstance();
                Calendar messageTime = Calendar.getInstance();
                messageTime.setTimeInMillis(timestamp);

                // Check if it's today
                if (isSameDay(now, messageTime)) {
                    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                    return timeFormat.format(new Date(timestamp));
                }

                // Check if it's yesterday
                Calendar yesterday = Calendar.getInstance();
                yesterday.add(Calendar.DAY_OF_YEAR, -1);
                if (isSameDay(yesterday, messageTime)) {
                    return "Yesterday";
                }

                // Check if it's this week (within last 7 days)
                Calendar weekAgo = Calendar.getInstance();
                weekAgo.add(Calendar.DAY_OF_YEAR, -7);
                if (messageTime.after(weekAgo)) {
                    SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
                    return dayFormat.format(new Date(timestamp));
                }

                // Check if it's this year
                if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR)) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                    return dateFormat.format(new Date(timestamp));
                }

                // Older than this year
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            } catch (Exception e) {
                return "";
            }
        }

        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }

        private void showContextMenu(View view, Chat chat) {
            try {
                PopupMenu popup = new PopupMenu(context, view);
                popup.getMenuInflater().inflate(R.menu.chat_context_menu, popup.getMenu());

                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.menu_delete_chat) {
                        deleteChat(chat);
                        return true;
                    } else if (itemId == R.id.menu_clear_chat) {
                        clearChat(chat);
                        return true;
                    } else if (itemId == R.id.menu_block_user) {
                        blockUser(chat.getOtherUser());
                        return true;
                    } else if (itemId == R.id.menu_unfriend) {
                        unfriendUser(chat.getOtherUser());
                        return true;
                    }
                    return false;
                });

                popup.show();
            } catch (Exception e) {
                // Handle popup menu errors gracefully
            }
        }

        // FIXED: Only hide chat for current user, don't affect other participant
        private void deleteChat(Chat chat) {
            FirestoreUtil.deleteChat(chat.getId(), currentUserId);
            removeChat(chat.getId());
        }

        // FIXED: One-sided chat clearing
        private void clearChat(Chat chat) {
            // Clear chat only for current user - other user's chat remains intact
            FirestoreUtil.clearChatHistoryForUser(chat.getId(), currentUserId);

            // Update the chat to show cleared state for this user only
            chat.setLastMessage("You cleared this chat");
            chat.setLastMessageType("chat_cleared");
            chat.setUnreadCount(0);
            notifyItemChanged(getAdapterPosition());
        }

        private void blockUser(User user) {
            FirestoreUtil.blockUser(currentUserId, user.getId());
            // Remove the chat from the list since it will be hidden
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).getOtherUser().getId().equals(user.getId())) {
                    chats.remove(i);
                    notifyItemRemoved(i);
                    break;
                }
            }
        }

        private void unfriendUser(User user) {
            FirestoreUtil.removeFriend(currentUserId, user.getId());
            // Remove the chat from the list
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).getOtherUser().getId().equals(user.getId())) {
                    chats.remove(i);
                    notifyItemRemoved(i);
                    break;
                }
            }
        }

        private boolean hasContent(Chat chat) {
            String lastMessage = chat.getLastMessage();
            String type = chat.getLastMessageType();
            if (type == null) type = "text";
            if ("friend_added".equals(type) || "empty_chat".equals(type)) return false;
            return lastMessage != null && !lastMessage.trim().isEmpty();
        }
    }
}