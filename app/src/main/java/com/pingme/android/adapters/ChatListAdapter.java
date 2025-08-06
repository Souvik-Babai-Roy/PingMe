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
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
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
            binding.tvName.setText(otherUser.getDisplayName());

            // Load profile image based on privacy settings
            if (otherUser.isProfilePhotoEnabled() &&
                    otherUser.getImageUrl() != null &&
                    !otherUser.getImageUrl().isEmpty()) {

                Glide.with(context)
                        .load(otherUser.getImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.defaultprofile)
                        .into(binding.ivProfile);
            } else {
                binding.ivProfile.setImageResource(R.drawable.defaultprofile);
            }

            // Show online indicator based on privacy settings
            if (otherUser.isLastSeenEnabled() && otherUser.isOnline()) {
                binding.onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                binding.onlineIndicator.setVisibility(View.GONE);
            }

            // Set last message preview
            binding.tvLastMessage.setText(getLastMessagePreview(chat));

            // Set formatted time
            binding.tvTime.setText(getFormattedTime(chat.getLastMessageTimestamp()));

            // Show/hide time based on chat status
            if (chat.isEmpty() || "friend_added".equals(chat.getLastMessageType())) {
                binding.tvTime.setVisibility(View.INVISIBLE);
            } else {
                binding.tvTime.setVisibility(View.VISIBLE);
            }

            // Show unread count
            if (chat.getUnreadCount() > 0) {
                binding.tvUnreadCount.setVisibility(View.VISIBLE);
                binding.tvUnreadCount.setText(chat.getUnreadCount() > 99 ? "99+" :
                        String.valueOf(chat.getUnreadCount()));
            } else {
                binding.tvUnreadCount.setVisibility(View.GONE);
            }

            // Set typing indicator
            if (chat.isTyping()) {
                binding.tvLastMessage.setText("typing...");
                binding.tvLastMessage.setTextColor(context.getColor(R.color.colorPrimary));
                binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
            } else {
                binding.tvLastMessage.setTextColor(context.getColor(R.color.textColorSecondary));
                binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            // Click listeners
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra("chatId", chat.getId());
                intent.putExtra("receiverId", otherUser.getId());
                context.startActivity(intent);
            });

            // Long click for context menu
            itemView.setOnLongClickListener(v -> {
                showContextMenu(v, chat);
                return true;
            });
        }

        private String getLastMessagePreview(Chat chat) {
            if (chat.isTyping()) {
                return "typing...";
            }

            String lastMessageType = chat.getLastMessageType();

            if ("friend_added".equals(lastMessageType)) {
                return "Tap to start messaging";
            }

            if ("empty_chat".equals(lastMessageType) ||
                    chat.getLastMessage() == null ||
                    chat.getLastMessage().trim().isEmpty()) {
                return "No messages yet";
            }

            // Handle different message types
            switch (lastMessageType) {
                case "image":
                    return "📷 Photo";
                case "video":
                    return "🎥 Video";
                case "audio":
                    return "🎤 Audio";
                case "document":
                    return "📄 Document";
                default:
                    return chat.getLastMessage();
            }
        }

        private String getFormattedTime(long timestamp) {
            if (timestamp <= 0) {
                return "";
            }

            Calendar today = Calendar.getInstance();
            Calendar messageDate = Calendar.getInstance();
            messageDate.setTimeInMillis(timestamp);

            // Check if it's today
            if (isSameDay(today, messageDate)) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                return timeFormat.format(new Date(timestamp));
            }

            // Check if it's yesterday
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            if (isSameDay(yesterday, messageDate)) {
                return "Yesterday";
            }

            // Check if it's this week
            Calendar weekAgo = Calendar.getInstance();
            weekAgo.add(Calendar.DAY_OF_YEAR, -7);
            if (messageDate.after(weekAgo)) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
                return dayFormat.format(new Date(timestamp));
            }

            // Check if it's this year
            if (today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR)) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            }

            // Older than this year
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return dateFormat.format(new Date(timestamp));
        }

        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }

        private void showContextMenu(View view, Chat chat) {
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
        }

        private void deleteChat(Chat chat) {
            FirestoreUtil.deleteChat(chat.getId(), currentUserId);
            removeChat(chat.getId());
        }

        private void clearChat(Chat chat) {
            FirestoreUtil.clearChatHistory(chat.getId());
            // Update the chat to show empty state
            chat.setLastMessage("");
            chat.setLastMessageType("empty_chat");
            chat.setUnreadCount(0);
            notifyItemChanged(getAdapterPosition());
        }

        private void blockUser(User user) {
            FirestoreUtil.blockUser(currentUserId, user.getId());
            // Remove the chat from the list
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
    }
}