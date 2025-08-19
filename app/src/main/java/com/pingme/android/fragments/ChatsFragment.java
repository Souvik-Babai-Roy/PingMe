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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class ChatsFragment extends Fragment {
    private static final String TAG = "ChatsFragment";

    private FragmentChatsBinding binding;
    private ChatListAdapter adapter;
    private String currentUserId;
    private ValueEventListener userChatsListener;
    private Map<String, ValueEventListener> chatListeners = new HashMap<>();
    private Map<String, ValueEventListener> typingListeners = new HashMap<>();
    private List<Chat> chatList = new ArrayList<>();
    // Track per-user unread counts from user_chats to avoid placeholders and prefer accurate badge values
    private final Map<String, Integer> perUserUnreadCounts = new HashMap<>();
    
    // Broadcast receiver for chat updates
    private BroadcastReceiver chatUpdateReceiver;

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
        setupBroadcastReceiver();
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
    
    private void setupBroadcastReceiver() {
        chatUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "📡 Broadcast received: " + intent.getAction());
                if ("CHAT_UPDATED".equals(intent.getAction())) {
                    String chatId = intent.getStringExtra("chatId");
                    int unreadCount = intent.getIntExtra("unreadCount", 0);
                    
                    Log.d(TAG, "📡 Chat update broadcast - chatId: " + chatId + ", unreadCount: " + unreadCount);
                    
                    if (chatId != null) {
                        // Update the chat in the list
                        updateChatUnreadCount(chatId, unreadCount);
                    }
                }
            }
        };
        
        // Register the receiver
        IntentFilter filter = new IntentFilter("CHAT_UPDATED");
        requireActivity().registerReceiver(chatUpdateReceiver, filter);
    }
    
    private void updateChatUnreadCount(String chatId, int newUnreadCount) {
        Log.d(TAG, "🔄 Updating unread count for chat " + chatId + " to " + newUnreadCount);
        
        // Find the chat in the list and update its unread count
        for (int i = 0; i < chatList.size(); i++) {
            Chat chat = chatList.get(i);
            if (chat.getId().equals(chatId)) {
                Log.d(TAG, "✅ Found chat " + chatId + " at position " + i + ", updating unread count from " + chat.getUnreadCount() + " to " + newUnreadCount);
                chat.setUnreadCount(newUnreadCount);
                // Update the adapter
                adapter.addOrUpdateChat(chat);
                Log.d(TAG, "✅ Chat updated in adapter");
                break;
            }
        }
        
        Log.d(TAG, "🔍 Chat list size: " + chatList.size());
        for (Chat chat : chatList) {
            Log.d(TAG, "  - Chat " + chat.getId() + ": unreadCount = " + chat.getUnreadCount());
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
                Log.d(TAG, "User chats data changed, found " + dataSnapshot.getChildrenCount() + " chat entries");
                
                // Clear existing chats
                chatList.clear();
                adapter.notifyDataSetChanged();
                perUserUnreadCounts.clear();
                
                // Load active chats with messages
                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    Log.d(TAG, "Processing chat: " + chatId + " with value: " + chatSnapshot.getValue());
                    
                    if (chatId != null) {
                        // Read per-user unreadCount if present under user_chats/{uid}/{chatId}/unreadCount
                        Integer perUserUnread = chatSnapshot.child("unreadCount").getValue(Integer.class);
                        if (perUserUnread != null) {
                            perUserUnreadCounts.put(chatId, perUserUnread);
                            // If chat already exists in memory, update its unread count immediately
                            Chat existing = findChatById(chatId);
                            if (existing != null) {
                                existing.setUnreadCount(perUserUnread);
                                if (adapter != null) adapter.addOrUpdateChat(existing);
                            }
                        }
                        // Always load from chats node to get complete data
                        loadChatFromChatsNode(chatId);
                    }
                }
                
                // Update empty state after processing all chats
                new android.os.Handler().postDelayed(() -> {
                    updateEmptyState(chatList.isEmpty());
                    Log.d(TAG, "Final chat list size: " + chatList.size());
                }, 1000); // Give time for async loading
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
                            
                            // Load personal name from friendship relationship
                            FirebaseUtil.getFriendsRef(currentUserId)
                                    .document(otherUserId)
                                    .get()
                                    .addOnSuccessListener(friendDoc -> {
                                        if (friendDoc.exists()) {
                                            String personalName = friendDoc.getString("personalName");
                                            Log.d(TAG, "Friend doc for " + otherUserId + " exists, personalName: '" + personalName + "'");
                                            if (personalName != null && !personalName.trim().isEmpty()) {
                                                otherUser.setPersonalName(personalName);
                                                Log.d(TAG, "✅ Set personal name for " + otherUserId + " to: '" + personalName + "'");
                                            } else {
                                                Log.d(TAG, "No personal name found for " + otherUserId);
                                            }
                                        } else {
                                            Log.d(TAG, "Friend doc for " + otherUserId + " does not exist");
                                        }
                                        
                                        chat.setOtherUser(otherUser);
                                        
                                        // Load user presence and then add to list
                                        loadUserPresence(otherUser, () -> {
                                            // Add chat to list and sort
                                            chatList.add(chat);
                                            Collections.sort(chatList, (c1, c2) -> Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp()));
                                            
                                            // FIXED: Update adapter properly
                                            if (adapter != null) {
                                                adapter.updateChats(chatList);
                                                Log.d(TAG, "✅ Chat added to UI: " + chat.getId() + " with user: " + otherUser.getDisplayName());
                                            }
                                            updateEmptyState(chatList.isEmpty());
                                        });
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to load personal name for user: " + otherUserId, e);
                                        // Continue without personal name
                                        chat.setOtherUser(otherUser);
                                        
                                        // Load user presence and then add to list
                                        loadUserPresence(otherUser, () -> {
                                            // Add chat to list and sort
                                            chatList.add(chat);
                                            Collections.sort(chatList, (c1, c2) -> Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp()));
                                            
                                            // FIXED: Update adapter properly
                                            if (adapter != null) {
                                                adapter.updateChats(chatList);
                                                Log.d(TAG, "✅ Chat added to UI: " + chat.getId() + " with user: " + otherUser.getDisplayName());
                                            }
                                            updateEmptyState(chatList.isEmpty());
                                        });
                                    });
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
                String lastMessageId = dataSnapshot.child("lastMessageId").getValue(String.class);
                // Use utility method to safely convert active status
                boolean isActive = FirebaseUtil.safeBooleanValue(dataSnapshot.child("isActive").getValue());
                Integer unreadCount = dataSnapshot.child("unreadCount").getValue(Integer.class);

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
                            existingChat.setLastMessageId(lastMessageId != null ? lastMessageId : existingChat.getLastMessageId());
                            existingChat.setActive(isActive ? isActive : true);
                        }
                        
                        // Prefer per-user unread count from user_chats; fallback to chat-level or calculated
                        Integer perUser = perUserUnreadCounts.get(chatId);
                        if (perUser != null) {
                            existingChat.setUnreadCount(perUser);
                            Log.d(TAG, "✅ Applied per-user unread count for existing chat " + chatId + ": " + perUser);
                        } else if (unreadCount != null && unreadCount > 0) {
                            existingChat.setUnreadCount(unreadCount);
                            Log.d(TAG, "✅ Updated existing chat " + chatId + " unread count from database: " + unreadCount);
                        } else {
                            // Only calculate unread count if not already set from any source
                            if (existingChat.getUnreadCount() == 0) {
                                calculateUnreadCount(existingChat);
                            }
                        }
                        
                        updateChatInList(existingChat);
                        } else {
                            loadOtherUserDetails(chatId, finalOtherUserId, lastMessage,
                                    lastMessageTimestamp != null ? lastMessageTimestamp : 0,
                                    lastMessageSenderId, lastMessageType, lastMessageId, unreadCount);
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
                                      long lastMessageTimestamp, String lastMessageSenderId, String lastMessageType, String lastMessageId, Integer unreadCount) {
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

                            // Load personal name from friendship relationship
                            FirebaseUtil.getFriendsRef(currentUserId)
                                    .document(otherUserId)
                                    .get()
                                    .addOnSuccessListener(friendDoc -> {
                                        if (friendDoc.exists()) {
                                            String personalName = friendDoc.getString("personalName");
                                            Log.d(TAG, "Friend doc for " + otherUserId + " exists, personalName: '" + personalName + "'");
                                            if (personalName != null && !personalName.trim().isEmpty()) {
                                                otherUser.setPersonalName(personalName);
                                                Log.d(TAG, "✅ Set personal name for " + otherUserId + " to: '" + personalName + "'");
                                            } else {
                                                Log.d(TAG, "No personal name found for " + otherUserId);
                                            }
                                        } else {
                                            Log.d(TAG, "Friend doc for " + otherUserId + " does not exist");
                                        }

                                        Chat chat = new Chat();
                                        chat.setId(chatId);
                                        chat.setOtherUser(otherUser);
                                        chat.setLastMessage(lastMessage != null ? lastMessage : "");
                                        chat.setLastMessageTimestamp(lastMessageTimestamp);
                                        chat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");
                                        chat.setLastMessageType(lastMessageType != null ? lastMessageType : "text");
                                        chat.setLastMessageId(lastMessageId != null ? lastMessageId : "");
                                        
                                        // Prefer per-user unread count from user_chats; fallback to chat-level
                                        Integer perUser = perUserUnreadCounts.get(chatId);
                                        if (perUser != null) {
                                            chat.setUnreadCount(perUser);
                                            Log.d(TAG, "✅ Applied per-user unread count for chat " + chatId + ": " + perUser);
                                        } else if (unreadCount != null && unreadCount > 0) {
                                            chat.setUnreadCount(unreadCount);
                                            Log.d(TAG, "✅ Set unread count from database for chat " + chatId + ": " + unreadCount);
                                        } else {
                                            Log.d(TAG, "No unread count available for chat " + chatId + ", will calculate later if needed");
                                        }

                                        // Load real-time presence from Realtime Database
                                        loadUserPresence(otherUser, () -> {
                                            // Update or add chat to list
                                            updateChatInList(chat);
                                            // Only calculate unread count if not already set from database
                                            if (chat.getUnreadCount() == 0) {
                                                calculateUnreadCount(chat);
                                            }
                                        });
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to load personal name for user: " + otherUserId, e);
                                        // Continue without personal name
                                        Chat chat = new Chat();
                                        chat.setId(chatId);
                                        chat.setOtherUser(otherUser);
                                        chat.setLastMessage(lastMessage != null ? lastMessage : "");
                                        chat.setLastMessageTimestamp(lastMessageTimestamp);
                                        chat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");
                                        chat.setLastMessageType(lastMessageType != null ? lastMessageType : "text");
                                        chat.setLastMessageId(lastMessageId != null ? lastMessageId : "");
                                        
                                        // Prefer per-user unread count from user_chats; fallback to chat-level
                                        Integer perUser = perUserUnreadCounts.get(chatId);
                                        if (perUser != null) {
                                            chat.setUnreadCount(perUser);
                                            Log.d(TAG, "✅ Applied per-user unread count for chat " + chatId + ": " + perUser);
                                        } else if (unreadCount != null && unreadCount > 0) {
                                            chat.setUnreadCount(unreadCount);
                                            Log.d(TAG, "✅ Set unread count from database for chat " + chatId + ": " + unreadCount);
                                        } else {
                                            Log.d(TAG, "No unread count available for chat " + chatId + ", will calculate later if needed");
                                        }

                                        // Load real-time presence from Realtime Database
                                        loadUserPresence(otherUser, () -> {
                                            // Update or add chat to list
                                            updateChatInList(chat);
                                            // Only calculate unread count if not already set from database
                                            if (chat.getUnreadCount() == 0) {
                                                calculateUnreadCount(chat);
                                            }
                                        });
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
                            // Use utility method to safely convert online status
                            boolean isOnline = FirebaseUtil.safeBooleanValue(dataSnapshot.child("isOnline").getValue());
                            Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                            user.setOnline(isOnline);
                            user.setLastSeen(lastSeen != null ? lastSeen : 0);
                            
                            Log.d(TAG, "Loaded presence for " + user.getDisplayName() + " - online: " + isOnline);
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
        
        // Use a more efficient query - only get recent messages and those not read by current user
        FirebaseUtil.getMessagesRef(chat.getId())
                .orderByChild("timestamp")
                .limitToLast(100) // Only check last 100 messages for efficiency
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        int unreadCount = 0;
                        
                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);
                            
                            // Only count messages from other users that haven't been read
                            if (senderId != null && !currentUserId.equals(senderId)) {
                                // Check if message has been read by current user
                                DataSnapshot readBySnapshot = messageSnapshot.child("readBy");
                                if (!readBySnapshot.hasChild(currentUserId)) {
                                    // Also check if the message is not too old (within last 7 days)
                                    if (timestamp != null && timestamp > (System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000))) {
                                        unreadCount++;
                                    }
                                }
                            }
                        }
                        
                        // Cap unread count at 999 like WhatsApp
                        unreadCount = Math.min(unreadCount, 999);
                        
                        chat.setUnreadCount(unreadCount);
                        Log.d(TAG, "Unread count for " + chat.getId() + ": " + unreadCount);
                        
                        // Update the chat in the adapter
                        if (adapter != null) {
                            adapter.addOrUpdateChat(chat);
                            Log.d(TAG, "✅ Updated chat in adapter with unread count: " + unreadCount);
                        } else {
                            Log.w(TAG, "Adapter is null, cannot update chat");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to calculate unread count", databaseError.toException());
                        // Set unread count to 0 on error
                        chat.setUnreadCount(0);
                        if (adapter != null) {
                            adapter.addOrUpdateChat(chat);
                        }
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
                existingChat.setUnreadCount(updatedChat.getUnreadCount());
                // Resort to reflect new message order
                sortChats(chatList);
                adapter.notifyDataSetChanged();
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
        
        // Unregister broadcast receiver
        if (chatUpdateReceiver != null) {
            try {
                requireActivity().unregisterReceiver(chatUpdateReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering broadcast receiver", e);
            }
        }
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
    
    private void loadChatFromChatsNode(String chatId) {
        if (chatId == null) return;
        
        Log.d(TAG, "Loading chat data from chats node: " + chatId);
        DatabaseReference chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId);
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    if (dataSnapshot.exists()) {
                        Log.d(TAG, "Chat data found for " + chatId + ": " + dataSnapshot.getValue());
                        
                        // Create Chat object manually to handle potential data issues
                        Chat chat = new Chat();
                        chat.setId(chatId);
                        
                        // Get all chat properties safely
                        String lastMessage = dataSnapshot.child("lastMessage").getValue(String.class);
                        Long lastMessageTimestamp = dataSnapshot.child("lastMessageTimestamp").getValue(Long.class);
                        String lastMessageSenderId = dataSnapshot.child("lastMessageSenderId").getValue(String.class);
                        String lastMessageType = dataSnapshot.child("lastMessageType").getValue(String.class);
                        Integer chatLevelUnread = dataSnapshot.child("unreadCount").getValue(Integer.class);
                        
                        chat.setLastMessage(lastMessage != null ? lastMessage : "");
                        chat.setLastMessageTimestamp(lastMessageTimestamp != null ? lastMessageTimestamp : 0);
                        chat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");
                        chat.setLastMessageType(lastMessageType != null ? lastMessageType : "text");
                        // Apply unread count preference: per-user > chat-level
                        Integer perUser = perUserUnreadCounts.get(chatId);
                        if (perUser != null) {
                            chat.setUnreadCount(perUser);
                            Log.d(TAG, "✅ Applied per-user unread count in chats node load for " + chatId + ": " + perUser);
                        } else if (chatLevelUnread != null && chatLevelUnread > 0) {
                            chat.setUnreadCount(chatLevelUnread);
                            Log.d(TAG, "✅ Applied chat-level unread count in chats node load for " + chatId + ": " + chatLevelUnread);
                        }
                        
                        Log.d(TAG, "Chat " + chatId + " - lastMessage: " + lastMessage + ", timestamp: " + lastMessageTimestamp);
                        
                        // Show chats even if they don't have messages yet (for new conversations)
                        if (lastMessageTimestamp != null && lastMessageTimestamp > 0) {
                            // Check if chat is not deleted for current user
                            checkChatVisibility(chat);
                        } else {
                            Log.d(TAG, "Chat " + chatId + " has no messages yet, skipping");
                        }
                    } else {
                        Log.w(TAG, "No chat data found for chatId: " + chatId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading chat from chats node for chatId: " + chatId, e);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Database error loading chat " + chatId + ": " + databaseError.getMessage());
            }
        });
    }
}