package com.pingme.android.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.pingme.android.adapters.ChatListAdapter;
import com.pingme.android.databinding.FragmentChatsBinding;
import com.pingme.android.models.Chat;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;
import com.pingme.android.models.ChatManagement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatsFragment extends Fragment {
    private static final String TAG = "ChatsFragment";

    private FragmentChatsBinding binding;
    private ChatListAdapter adapter;
    private String currentUserId;
    private ValueEventListener userChatsListener;
    private Map<String, ValueEventListener> chatListeners = new HashMap<>();
    private Map<String, ValueEventListener> typingListeners = new HashMap<>();
    private List<Chat> chatList = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // FIXED: Check for null user to prevent crash
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "User not authenticated, cannot load chats");
            updateEmptyState(true);
            return;
        }
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d(TAG, "ChatsFragment created for user: " + currentUserId);

        setupRecyclerView();
        setupSwipeRefresh();
        loadChats();
    }

    private void setupRecyclerView() {
        adapter = new ChatListAdapter(getContext());
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        if (binding.getRoot() instanceof SwipeRefreshLayout) {
            SwipeRefreshLayout swipeRefresh = (SwipeRefreshLayout) binding.getRoot();
            swipeRefresh.setOnRefreshListener(() -> {
                loadChats();
                swipeRefresh.setRefreshing(false);
            });
        }
    }

    private void loadChats() {
        Log.d(TAG, "Loading chats for user: " + currentUserId);

        // Check if user is authenticated
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "User not authenticated, cannot load chats");
            updateEmptyState(true);
            return;
        }

        // Clear existing data
        chatList.clear();
        adapter.notifyDataSetChanged();

        // Only load active chats with messages
        loadActiveChats();
    }

    private void loadActiveChats() {
        Log.d(TAG, "Loading active chats from Realtime Database");

        // Remove existing listener
        if (userChatsListener != null) {
            FirebaseUtil.getUserChatsRef(currentUserId).removeEventListener(userChatsListener);
        }

        userChatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "User chats data changed");
                
                // Clear existing chats
                chatList.clear();
                
                // Load active chats with messages
                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    Chat chatData = chatSnapshot.getValue(Chat.class);
                    
                    if (chatData != null && chatId != null) {
                        chatData.setId(chatId);
                        
                        // Only add chats that have messages (lastMessageTimestamp > 0)
                        if (chatData.getLastMessageTimestamp() > 0) {
                            // Check if chat is not deleted for current user
                            checkChatVisibility(chatData);
                        }
                    }
                }
                
                updateEmptyState(chatList.isEmpty());
                Log.d(TAG, "Loaded " + chatList.size() + " active chats");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load user chats", databaseError.toException());
                updateEmptyState(true);
            }
        };

        FirebaseUtil.getUserChatsRef(currentUserId).addValueEventListener(userChatsListener);
    }

    private void checkChatVisibility(Chat chat) {
        // Check if chat is deleted for current user
        FirebaseUtil.getChatManagementRef(chat.getId()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        ChatManagement chatManagement = documentSnapshot.toObject(ChatManagement.class);
                        if (chatManagement != null && chatManagement.isChatActiveForUser(currentUserId)) {
                            // Chat is active for current user, load user info
                            loadChatUserInfo(chat);
                        } else {
                            // Chat is deleted for current user, skip it
                            Log.d(TAG, "Chat " + chat.getId() + " is deleted for user " + currentUserId);
                        }
                    } else {
                        // No chat management record, assume active
                        loadChatUserInfo(chat);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check chat visibility for: " + chat.getId(), e);
                    // On error, assume chat is active
                    loadChatUserInfo(chat);
                });
    }

    private void loadChatUserInfo(Chat chat) {
        // Get the other user ID from chat ID
        String[] userIds = chat.getId().split("_");
        if (userIds.length != 2) {
            Log.e(TAG, "Invalid chat ID format: " + chat.getId());
            return;
        }
        
        String otherUserId = userIds[0].equals(currentUserId) ? userIds[1] : userIds[0];
        
        // Load the other user's information
        FirebaseUtil.getUserRef(otherUserId).get()
                .addOnSuccessListener(userSnapshot -> {
                    if (userSnapshot.exists()) {
                        User otherUser = userSnapshot.toObject(User.class);
                        if (otherUser != null) {
                            otherUser.setId(otherUserId);
                            chat.setOtherUser(otherUser);
                            
                            // Add chat to list and sort
                            chatList.add(chat);
                            Collections.sort(chatList, (c1, c2) -> Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp()));
                            
                            adapter.notifyDataSetChanged();
                            updateEmptyState(chatList.isEmpty());
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load user info for chat: " + chat.getId(), e);
                });
    }

    private void loadChatDetails(String chatId) {
        Log.d(TAG, "Loading chat details: " + chatId);

        ValueEventListener chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) return;

                Log.d(TAG, "Updating chat from full snapshot: " + chatId);

                String lastMessage = dataSnapshot.child("lastMessage").getValue(String.class);
                Long lastMessageTimestamp = dataSnapshot.child("lastMessageTimestamp").getValue(Long.class);
                String lastMessageSenderId = dataSnapshot.child("lastMessageSenderId").getValue(String.class);
                String lastMessageType = dataSnapshot.child("lastMessageType").getValue(String.class);
                Boolean isActive = dataSnapshot.child("isActive").getValue(Boolean.class);

                DataSnapshot participantsSnapshot = dataSnapshot.child("participants");
                String otherUserId = null;
                for (DataSnapshot participantSnapshot : participantsSnapshot.getChildren()) {
                    String participantId = participantSnapshot.getKey();
                    if (participantId != null && !participantId.equals(currentUserId)) {
                        otherUserId = participantId;
                        break;
                    }
                }

                Log.d(TAG, "Chat " + chatId + " - otherUser: " + otherUserId + " lastMessage: " + lastMessage);

                if (otherUserId != null) {
                    // First check if users are still friends before updating chat
                    final String finalOtherUserId = otherUserId;
                    FirebaseUtil.checkFriendship(currentUserId, finalOtherUserId, areFriends -> {
                        if (!areFriends) {
                            Log.d(TAG, "Users are no longer friends, removing chat: " + chatId);
                            // Remove chat from list
                            Chat chatToRemove = findChatById(chatId);
                            if (chatToRemove != null) {
                                int position = chatList.indexOf(chatToRemove);
                                if (position != -1) {
                                    chatList.remove(position);
                                    if (adapter != null) {
                                        adapter.notifyItemRemoved(position);
                                    }
                                }
                            }
                            return;
                        }
                        
                        // Users are friends, proceed with chat update
                        Chat existingChat = findChatById(chatId);
                        if (existingChat != null) {
                        // Update last message; if blank but type is media, set placeholder
                        String effectiveLastMessage = lastMessage;
                        if ((effectiveLastMessage == null || effectiveLastMessage.trim().isEmpty()) && lastMessageType != null) {
                            switch (lastMessageType) {
                                case "image":
                                    effectiveLastMessage = "\uD83D\uDCF7 Photo";
                                    break;
                                case "video":
                                    effectiveLastMessage = "\uD83C\uDFA5 Video";
                                    break;
                                case "audio":
                                    effectiveLastMessage = "\uD83C\uDFB5 Audio";
                                    break;
                                case "document":
                                    effectiveLastMessage = "\uD83D\uDCC4 Document";
                                    break;
                                default:
                                    effectiveLastMessage = "";
                            }
                        }

                        if (effectiveLastMessage != null) {
                            existingChat.setLastMessage(effectiveLastMessage);
                            existingChat.setLastMessageTimestamp(lastMessageTimestamp != null ? lastMessageTimestamp : existingChat.getLastMessageTimestamp());
                            existingChat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : existingChat.getLastMessageSenderId());
                            existingChat.setLastMessageType(lastMessageType != null ? lastMessageType : existingChat.getLastMessageType());
                            existingChat.setActive(isActive != null ? isActive : true);
                        }

                            calculateUnreadCount(existingChat);
                            updateChatInList(existingChat);
                        } else {
                            loadOtherUserDetails(chatId, finalOtherUserId, lastMessage,
                                    lastMessageTimestamp != null ? lastMessageTimestamp : 0,
                                    lastMessageSenderId, lastMessageType);
                        }
                    }); // Close friendship check callback
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load full chat data", databaseError.toException());
            }
        };

        // Load full chat snapshot once to get last message and participants
        FirebaseUtil.getChatRef(chatId).addListenerForSingleValueEvent(chatListener);
        chatListeners.put(chatId, chatListener);
    }

    private void loadOtherUserDetails(String chatId, String otherUserId, String lastMessage,
                                      long lastMessageTimestamp, String lastMessageSenderId, String lastMessageType) {
        Log.d(TAG, "Loading other user details: " + otherUserId + " for chat: " + chatId);

        // First verify friendship before loading user details
        FirebaseUtil.checkFriendship(currentUserId, otherUserId, areFriends -> {
            if (!areFriends) {
                Log.d(TAG, "Users are not friends, skipping chat: " + chatId);
                return;
            }

            // Load user details from Firestore
            FirebaseUtil.getUserRef(otherUserId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User otherUser = documentSnapshot.toObject(User.class);
                        if (otherUser != null) {
                            otherUser.setId(documentSnapshot.getId());

                            Chat chat = new Chat();
                            chat.setId(chatId);
                            chat.setOtherUser(otherUser);
                            chat.setLastMessage(lastMessage != null ? lastMessage : "");
                            chat.setLastMessageTimestamp(lastMessageTimestamp);
                            chat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");
                            chat.setLastMessageType(lastMessageType != null ? lastMessageType : "text");

                            // Load real-time presence from Realtime Database
                            loadUserPresence(otherUser, () -> {
                                // Update or add chat to list
                                updateChatInList(chat);
                                calculateUnreadCount(chat);
                            });
                        }
                    }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load other user details", e);
                    });
        }); // Close friendship check callback
    }

    private void loadUserPresence(User user, Runnable onComplete) {
        FirebaseUtil.getRealtimePresenceRef(user.getId())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                            Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                            user.setOnline(isOnline != null ? isOnline : false);
                            user.setLastSeen(lastSeen != null ? lastSeen : 0);
                        }
                        onComplete.run();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to load user presence", databaseError.toException());
                        onComplete.run();
                    }
                });
    }

    private void updateTypingStatus(String chatId, DataSnapshot typingSnapshot) {
        Chat chat = findChatById(chatId);
        if (chat == null) return;

        boolean isOtherUserTyping = false;
        for (DataSnapshot userTyping : typingSnapshot.getChildren()) {
            String userId = userTyping.getKey();
            if (userId != null && !userId.equals(currentUserId)) {
                Long typingTimestamp = userTyping.getValue(Long.class);
                if (typingTimestamp != null &&
                        System.currentTimeMillis() - typingTimestamp < 5000) { // 5 seconds threshold
                    isOtherUserTyping = true;
                    break;
                }
            }
        }

        chat.setTyping(isOtherUserTyping);
        adapter.addOrUpdateChat(chat);
    }

    private void calculateUnreadCount(Chat chat) {
        Log.d(TAG, "Calculating unread count for chat: " + chat.getId());
        
        FirebaseUtil.getMessagesRef(chat.getId())
                .orderByChild("senderId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        int unreadCount = 0;
                        
                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            
                            // Count messages from other users that haven't been read
                            if (senderId != null && !currentUserId.equals(senderId)) {
                                // Check if message has been read by current user
                                DataSnapshot readBySnapshot = messageSnapshot.child("readBy");
                                if (!readBySnapshot.hasChild(currentUserId)) {
                                    unreadCount++;
                                }
                            }
                        }
                        
                        chat.setUnreadCount(unreadCount);
                        Log.d(TAG, "Unread count for " + chat.getId() + ": " + unreadCount);
                        
                        // Update the chat in the adapter
                        if (adapter != null) {
                            adapter.addOrUpdateChat(chat);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to calculate unread count", databaseError.toException());
                    }
                });
    }

    private void loadUnreadCount(String chatId, Chat chat) {
        // Calculate unread count by checking messages not read by current user
        FirebaseUtil.getMessagesRef(chatId)
                .orderByChild("timestamp")
                .startAfter(chat.getLastMessageTimestamp())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        int unreadCount = 0;
                        
                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            com.pingme.android.models.Message message = messageSnapshot.getValue(com.pingme.android.models.Message.class);
                            if (message != null && !message.getSenderId().equals(currentUserId)) {
                                // Check if message is not read by current user
                                if (message.getReadBy() == null || !message.getReadBy().containsKey(currentUserId)) {
                                    unreadCount++;
                                }
                            }
                        }
                        
                        chat.setUnreadCount(unreadCount);
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to load unread count for chat: " + chatId, databaseError.toException());
                    }
                });
    }

    private void updateChatInList(Chat updatedChat) {
        // Find existing chat and update it
        for (int i = 0; i < chatList.size(); i++) {
            Chat existingChat = chatList.get(i);
            if (existingChat.getId().equals(updatedChat.getId())) {
                existingChat.setLastMessage(updatedChat.getLastMessage());
                existingChat.setLastMessageTimestamp(updatedChat.getLastMessageTimestamp());
                existingChat.setLastMessageSenderId(updatedChat.getLastMessageSenderId());
                existingChat.setLastMessageType(updatedChat.getLastMessageType());
                // Remove the getLastMessageId() call since it doesn't exist
                adapter.notifyItemChanged(i);
                return;
            }
        }
        
        // If not found, add new chat
        chatList.add(updatedChat);
        sortChats(chatList);
        adapter.notifyDataSetChanged();
    }

    private void setupChatListener(String chatId) {
        // Remove existing listener if any
        if (chatListeners.containsKey(chatId)) {
            FirebaseUtil.getChatRef(chatId).removeEventListener(chatListeners.get(chatId));
        }

        ValueEventListener chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Chat updatedChat = dataSnapshot.getValue(Chat.class);
                    if (updatedChat != null) {
                        updatedChat.setId(chatId);
                        updateChatInList(updatedChat);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen to chat updates", databaseError.toException());
            }
        };

        FirebaseUtil.getChatRef(chatId).addValueEventListener(chatListener);
        chatListeners.put(chatId, chatListener);
    }

    private Chat findChatById(String chatId) {
        for (Chat chat : chatList) {
            if (chat.getId().equals(chatId)) {
                return chat;
            }
        }
        return null;
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh chats when fragment becomes visible
        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadChats();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Clean up listeners when fragment is not visible
        if (userChatsListener != null) {
            FirebaseUtil.getUserChatsRef(currentUserId).removeEventListener(userChatsListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remove listeners
        if (userChatsListener != null) {
            FirebaseUtil.getUserChatsRef(currentUserId).removeEventListener(userChatsListener);
        }
        
        for (ValueEventListener listener : chatListeners.values()) {
            // Remove individual chat listeners if needed
        }
        
        for (ValueEventListener listener : typingListeners.values()) {
            // Remove typing listeners if needed
        }
        
        chatListeners.clear();
        typingListeners.clear();
    }

    public void refreshChats() {
        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadChats();
        }
    }

    private void refreshExistingChats() {
        // Update presence for all users in the chat list
        for (Chat chat : chatList) {
            if (chat.getOtherUser() != null) {
                loadUserPresence(chat.getOtherUser(), () -> {
                    if (adapter != null) {
                        adapter.addOrUpdateChat(chat);
                    }
                });
            }
        }
    }

    private void sortChats(List<Chat> chats) {
        Collections.sort(chats, (a, b) -> {
            boolean aEmpty = isEmptyChat(a);
            boolean bEmpty = isEmptyChat(b);
            if (aEmpty != bEmpty) {
                return aEmpty ? 1 : -1; // non-empty first
            }
            if (!aEmpty && !bEmpty) {
                // Newest first
                return Long.compare(b.getLastMessageTimestamp(), a.getLastMessageTimestamp());
            }
            // Both empty: sort by name
            String an = a.getOtherUser() != null ? a.getOtherUser().getDisplayName() : "";
            String bn = b.getOtherUser() != null ? b.getOtherUser().getDisplayName() : "";
            return an.compareToIgnoreCase(bn);
        });
    }

    private boolean isEmptyChat(Chat chat) {
        String type = chat.getLastMessageType();
        String lastMessage = chat.getLastMessage();
        if (type == null) type = "text";
        if ("friend_added".equals(type) || "empty_chat".equals(type)) return true;
        return lastMessage == null || lastMessage.trim().isEmpty();
    }
}