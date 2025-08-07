package com.pingme.android.fragments;

import android.content.Intent;
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
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.pingme.android.activities.ChatActivity;
import com.pingme.android.adapters.ChatListAdapter;
import com.pingme.android.databinding.FragmentChatsBinding;
import com.pingme.android.models.Chat;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

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
    private Map<String, ChildEventListener> chatListeners = new HashMap<>();
    private Map<String, ValueEventListener> typingListeners = new HashMap<>();
    private List<Chat> chatList = new ArrayList<>();
    private boolean isFragmentActive = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            binding = FragmentChatsBinding.inflate(inflater, container, false);
            return binding.getRoot();
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            // Return a simple view to prevent crash
            return new View(requireContext());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Check for null user to prevent crash
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
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            updateEmptyState(true);
        }
    }

    private void setupRecyclerView() {
        try {
            if (getContext() == null) {
                Log.e(TAG, "Context is null, cannot setup RecyclerView");
                return;
            }
            
            adapter = new ChatListAdapter(getContext());
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            binding.recyclerView.setAdapter(adapter);
            binding.recyclerView.setHasFixedSize(true);
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView", e);
        }
    }

    private void setupSwipeRefresh() {
        try {
            if (binding.getRoot() instanceof SwipeRefreshLayout) {
                SwipeRefreshLayout swipeRefresh = (SwipeRefreshLayout) binding.getRoot();
                swipeRefresh.setOnRefreshListener(() -> {
                    try {
                        loadChats();
                    } catch (Exception e) {
                        Log.e(TAG, "Error refreshing chats", e);
                    } finally {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up swipe refresh", e);
        }
    }

    private void loadChats() {
        if (currentUserId == null || !isFragmentActive) {
            Log.w(TAG, "Cannot load chats - user ID is null or fragment not active");
            return;
        }
        
        try {
            Log.d(TAG, "Loading chats for user: " + currentUserId);
            updateEmptyState(false);

            // Clear existing data
            chatList.clear();
            if (adapter != null) {
                adapter.updateChats(chatList);
            }

            // Load friends as empty chats first
            loadFriendsAsEmptyChats();
            
            // Then load active chats and merge them
            loadActiveChats();
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading chats", e);
            updateEmptyState(true);
        }
    }

    private void loadFriendsAsEmptyChats() {
        if (currentUserId == null || !isFragmentActive) return;

        try {
            Log.d(TAG, "Loading friends as empty chats");

            FirestoreUtil.getFriendsRef(currentUserId)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!isFragmentActive) return;

                        try {
                            for (com.google.firebase.firestore.DocumentSnapshot document : querySnapshot.getDocuments()) {
                                User friend = document.toObject(User.class);
                                if (friend != null) {
                                    friend.setId(document.getId());
                                    
                                    // Create empty chat for friend
                                    String chatId = FirestoreUtil.generateChatId(currentUserId, friend.getId());
                                    Chat friendChat = new Chat();
                                    friendChat.setId(chatId);
                                    friendChat.setOtherUser(friend);
                                    friendChat.setLastMessage("Tap to start messaging");
                                    friendChat.setLastMessageTimestamp(System.currentTimeMillis());
                                    friendChat.setLastMessageSenderId("");
                                    friendChat.setLastMessageType("friend_added");
                                    friendChat.setActive(false);

                                    // Load user presence
                                    loadUserPresence(friend, () -> {
                                        if (isFragmentActive) {
                                            addOrUpdateChat(friendChat);
                                        }
                                    });

                                    // Ensure chat exists in database
                                    ensureChatExistsForFriend(chatId, currentUserId, friend.getId());
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing friends", e);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load friends", e);
                        if (isFragmentActive) {
                            updateEmptyState(true);
                        }
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "Error in loadFriendsAsEmptyChats", e);
        }
    }

    private void ensureChatExistsForFriend(String chatId, String currentUserId, String friendId) {
        try {
            FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!dataSnapshot.exists()) {
                        Log.d(TAG, "Creating chat for friend: " + friendId);
                        FirestoreUtil.createEmptyFriendChat(currentUserId, friendId);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Failed to check chat existence", databaseError.toException());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring chat exists", e);
        }
    }

    private void loadActiveChats() {
        if (currentUserId == null || !isFragmentActive) return;

        try {
            Log.d(TAG, "Loading active chats");

            userChatsListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!isFragmentActive) return;

                    try {
                        // Clear existing chat listeners
                        for (ChildEventListener listener : chatListeners.values()) {
                            // Remove listeners if possible
                        }
                        chatListeners.clear();

                        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                            String chatId = chatSnapshot.getKey();
                            if (chatId != null) {
                                loadChatDetails(chatId);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing active chats", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Failed to load user chats", databaseError.toException());
                    if (isFragmentActive) {
                        updateEmptyState(true);
                    }
                }
            };

            FirestoreUtil.getUserChatsRef(currentUserId).addValueEventListener(userChatsListener);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in loadActiveChats", e);
        }
    }

    private void loadChatDetails(String chatId) {
        if (chatId == null || !isFragmentActive) return;

        try {
            ChildEventListener chatListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                    if (!isFragmentActive) return;
                    updateChatFromSnapshot(chatId, dataSnapshot);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                    if (!isFragmentActive) return;
                    updateChatFromSnapshot(chatId, dataSnapshot);
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                    if (!isFragmentActive) return;
                    // Handle chat removal if needed
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {}

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Chat listener cancelled for: " + chatId, databaseError.toException());
                }
            };

            chatListeners.put(chatId, chatListener);
            FirestoreUtil.getChatRef(chatId).addChildEventListener(chatListener);

            // Set up typing listener
            ValueEventListener typingListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!isFragmentActive) return;
                    updateTypingStatus(chatId, dataSnapshot);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Typing listener cancelled for: " + chatId, databaseError.toException());
                }
            };

            typingListeners.put(chatId, typingListener);
            FirestoreUtil.getTypingRef(chatId).addValueEventListener(typingListener);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading chat details", e);
        }
    }

    private void updateChatFromSnapshot(String chatId, DataSnapshot chatSnapshot) {
        if (!chatSnapshot.exists() || !isFragmentActive) return;

        try {
            // Get the entire chat data at once
            FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!dataSnapshot.exists() || !isFragmentActive) return;

                    try {
                        Log.d(TAG, "Updating chat from full snapshot: " + chatId);

                        // Get chat data
                        String lastMessage = dataSnapshot.child("lastMessage").getValue(String.class);
                        Long lastMessageTimestamp = dataSnapshot.child("lastMessageTimestamp").getValue(Long.class);
                        String lastMessageSenderId = dataSnapshot.child("lastMessageSenderId").getValue(String.class);
                        String lastMessageType = dataSnapshot.child("lastMessageType").getValue(String.class);
                        Boolean isActive = dataSnapshot.child("isActive").getValue(Boolean.class);

                        // Get other user ID from participants
                        DataSnapshot participantsSnapshot = dataSnapshot.child("participants");
                        String otherUserId = null;
                        for (DataSnapshot participantSnapshot : participantsSnapshot.getChildren()) {
                            String participantId = participantSnapshot.getKey();
                            if (participantId != null && !participantId.equals(currentUserId)) {
                                otherUserId = participantId;
                                break;
                            }
                        }

                        if (otherUserId != null) {
                            // Find existing chat in the list
                            Chat existingChat = findChatById(chatId);
                            if (existingChat != null) {
                                Log.d(TAG, "Updating existing chat: " + chatId);
                                // Always update last message data
                                existingChat.setLastMessage(lastMessage != null ? lastMessage : "");
                                existingChat.setLastMessageTimestamp(lastMessageTimestamp != null ? lastMessageTimestamp : System.currentTimeMillis());
                                existingChat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");
                                existingChat.setLastMessageType(lastMessageType != null ? lastMessageType : "text");
                                existingChat.setActive(isActive != null ? isActive : true);

                                calculateUnreadCount(existingChat);
                                updateChatInList(existingChat);
                            } else {
                                Log.d(TAG, "Creating new chat from active chat: " + chatId);
                                // Create new chat if it doesn't exist in friends
                                loadOtherUserDetails(chatId, otherUserId, lastMessage,
                                        lastMessageTimestamp != null ? lastMessageTimestamp : 0,
                                        lastMessageSenderId, lastMessageType);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing chat snapshot", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Failed to load full chat data", databaseError.toException());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in updateChatFromSnapshot", e);
        }
    }

    private void loadOtherUserDetails(String chatId, String otherUserId, String lastMessage,
                                      long lastMessageTimestamp, String lastMessageSenderId, String lastMessageType) {
        if (!isFragmentActive) return;
        
        try {
            Log.d(TAG, "Loading other user details: " + otherUserId + " for chat: " + chatId);

            FirestoreUtil.getUserRef(otherUserId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!isFragmentActive) return;
                        
                        try {
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
                                        if (isFragmentActive) {
                                            addOrUpdateChat(chat);
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing user details", e);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load user details for: " + otherUserId, e);
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "Error in loadOtherUserDetails", e);
        }
    }

    private void loadUserPresence(User user, Runnable onComplete) {
        if (user == null || user.getId() == null || !isFragmentActive) {
            if (onComplete != null) onComplete.run();
            return;
        }

        try {
            FirestoreUtil.getRealtimePresenceRef(user.getId()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!isFragmentActive) return;

                    try {
                        Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                        Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                        user.setOnline(isOnline != null ? isOnline : false);
                        user.setLastSeen(lastSeen != null ? lastSeen : 0);

                        if (onComplete != null) onComplete.run();
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing presence data", e);
                        if (onComplete != null) onComplete.run();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Failed to load user presence", databaseError.toException());
                    if (onComplete != null) onComplete.run();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in loadUserPresence", e);
            if (onComplete != null) onComplete.run();
        }
    }

    private void updateTypingStatus(String chatId, DataSnapshot typingSnapshot) {
        if (!isFragmentActive) return;

        try {
            Chat chat = findChatById(chatId);
            if (chat != null) {
                Boolean isTyping = typingSnapshot.child(chat.getOtherUser().getId()).getValue(Boolean.class);
                chat.setTyping(isTyping != null ? isTyping : false);
                updateChatInList(chat);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating typing status", e);
        }
    }

    private void calculateUnreadCount(Chat chat) {
        if (chat == null || chat.getId() == null || !isFragmentActive) return;

        try {
            FirestoreUtil.getMessagesRef(chat.getId())
                    .orderByChild("timestamp")
                    .startAfter(chat.getLastMessageTimestamp())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (!isFragmentActive) return;

                            try {
                                int unreadCount = 0;
                                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                                    String senderId = messageSnapshot.child("senderId").getValue(String.class);
                                    if (senderId != null && !senderId.equals(currentUserId)) {
                                        unreadCount++;
                                    }
                                }
                                chat.setUnreadCount(unreadCount);
                                updateChatInList(chat);
                            } catch (Exception e) {
                                Log.e(TAG, "Error calculating unread count", e);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.e(TAG, "Failed to calculate unread count", databaseError.toException());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in calculateUnreadCount", e);
        }
    }

    private void updateChatInList(Chat chat) {
        if (!isFragmentActive || chat == null) return;
        addOrUpdateChat(chat);
    }

    private Chat findChatById(String chatId) {
        try {
            for (Chat chat : chatList) {
                if (chat.getId().equals(chatId)) {
                    return chat;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding chat by ID", e);
        }
        return null;
    }

    private void updateEmptyState(boolean isEmpty) {
        if (!isFragmentActive || binding == null) return;

        try {
            if (isEmpty) {
                binding.emptyView.setVisibility(View.VISIBLE);
                binding.recyclerView.setVisibility(View.GONE);
            } else {
                binding.emptyView.setVisibility(View.GONE);
                binding.recyclerView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating empty state", e);
        }
    }

    public void refreshChats() {
        if (isFragmentActive) {
            try {
                loadChats();
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing chats", e);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentActive = false;

        try {
            // Remove all listeners
            if (userChatsListener != null && currentUserId != null) {
                FirestoreUtil.getUserChatsRef(currentUserId).removeEventListener(userChatsListener);
            }

            for (ChildEventListener listener : chatListeners.values()) {
                // Remove listeners if possible
            }
            chatListeners.clear();

            for (ValueEventListener listener : typingListeners.values()) {
                // Remove listeners if possible
            }
            typingListeners.clear();

            binding = null;
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroyView", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isFragmentActive = true;

        try {
            // Update user presence to online
            if (currentUserId != null) {
                FirestoreUtil.updateUserPresence(currentUserId, true);
            }

            // Refresh existing chats
            refreshExistingChats();
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    private void refreshExistingChats() {
        if (!isFragmentActive || currentUserId == null) return;

        try {
            // Refresh user presence for all chats
            for (Chat chat : chatList) {
                if (chat.getOtherUser() != null) {
                    loadUserPresence(chat.getOtherUser(), null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing existing chats", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isFragmentActive = false;

        try {
            // Update user presence to offline
            if (currentUserId != null) {
                FirestoreUtil.updateUserPresence(currentUserId, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }

    private void addOrUpdateChat(Chat chat) {
        if (!isFragmentActive || chat == null) return;

        try {
            // Check if chat already exists
            int existingIndex = -1;
            for (int i = 0; i < chatList.size(); i++) {
                if (chatList.get(i).getId().equals(chat.getId())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                // Update existing chat
                chatList.set(existingIndex, chat);
                if (adapter != null) {
                    adapter.notifyItemChanged(existingIndex);
                }
            } else {
                // Add new chat
                chatList.add(chat);
                if (adapter != null) {
                    adapter.notifyItemInserted(chatList.size() - 1);
                }
            }

            // Sort chats by last message timestamp
            sortChatsByTimestamp();
            updateEmptyState(chatList.isEmpty());
        } catch (Exception e) {
            Log.e(TAG, "Error adding or updating chat", e);
        }
    }

    private void sortChatsByTimestamp() {
        if (!isFragmentActive) return;

        try {
            Collections.sort(chatList, (c1, c2) -> {
                // Put empty chats at the bottom
                boolean c1IsEmpty = "empty_chat".equals(c1.getLastMessageType()) || c1.getLastMessageTimestamp() == 0;
                boolean c2IsEmpty = "empty_chat".equals(c2.getLastMessageType()) || c2.getLastMessageTimestamp() == 0;
                
                if (c1IsEmpty && !c2IsEmpty) return 1;
                if (!c1IsEmpty && c2IsEmpty) return -1;
                if (c1IsEmpty && c2IsEmpty) return 0;
                
                // Sort by timestamp (newest first)
                return Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp());
            });
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sorting chats", e);
        }
    }
}