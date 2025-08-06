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
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        // First load friends as empty chats
        loadFriendsAsEmptyChats();

        // Then load active chats and merge them
        loadActiveChats();
    }

    private void loadFriendsAsEmptyChats() {
        Log.d(TAG, "Loading friends as empty chats");

        // Load friends from Firestore
        FirestoreUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " friends");

                    List<Chat> friendChats = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User friend = doc.toObject(User.class);
                        if (friend != null) {
                            friend.setId(doc.getId());

                            Log.d(TAG, "Creating empty chat for friend: " + friend.getName());

                            // Create empty chat for friend
                            Chat friendChat = new Chat();
                            String chatId = FirestoreUtil.generateChatId(currentUserId, friend.getId());
                            friendChat.setId(chatId);
                            friendChat.setOtherUser(friend);
                            friendChat.setLastMessage("Tap to start messaging");
                            friendChat.setLastMessageTimestamp(System.currentTimeMillis());
                            friendChat.setLastMessageSenderId("");
                            friendChat.setLastMessageType("empty_chat");
                            friendChat.setUnreadCount(0);
                            friendChat.setActive(false); // Start as inactive

                            // Load user presence
                            loadUserPresence(friend, () -> {
                                // Update the chat in the list
                                updateChatInList(friendChat);
                            });

                            friendChats.add(friendChat);

                            // Ensure chat exists in Realtime Database for this friend
                            ensureChatExistsForFriend(chatId, currentUserId, friend.getId());
                        }
                    }

                    // Add all friend chats to the main list
                    chatList.clear();
                    chatList.addAll(friendChats);

                    // Sort chats (empty chats by name)
                    Collections.sort(chatList);

                    updateEmptyState(chatList.isEmpty());

                    if (adapter != null) {
                        adapter.updateChats(chatList);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load friends", e);
                    updateEmptyState(true);
                });
    }

    private void ensureChatExistsForFriend(String chatId, String currentUserId, String friendId) {
        // Check if chat already exists in Realtime Database
        FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "Creating chat in Realtime Database for friend: " + chatId);
                    // Create chat in Realtime Database
                    FirestoreUtil.createEmptyFriendChat(currentUserId, friendId);
                } else {
                    Log.d(TAG, "Chat already exists in Realtime Database: " + chatId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to check chat existence", databaseError.toException());
            }
        });
    }

    private void loadActiveChats() {
        Log.d(TAG, "Loading active chats");

        // Listen to user's chat list in Realtime Database
        userChatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "User chats data changed. Exists: " + dataSnapshot.exists());

                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "No active chats found");
                    return; // Keep the empty friend chats that were already loaded
                }

                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    Boolean isActive = chatSnapshot.getValue(Boolean.class);

                    Log.d(TAG, "Found chat: " + chatId + " active: " + isActive);

                    if (chatId != null && isActive != null && isActive) {
                        loadChatDetails(chatId);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "User chats listener cancelled", databaseError.toException());
            }
        };

        FirestoreUtil.getUserChatsRef(currentUserId).addValueEventListener(userChatsListener);
    }

    private void loadChatDetails(String chatId) {
        Log.d(TAG, "Loading chat details: " + chatId);

        // Listen to each chat's details
        ChildEventListener chatListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                Log.d(TAG, "Chat child added: " + dataSnapshot.getKey() + " for chat: " + chatId);
                updateChatFromSnapshot(chatId, dataSnapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                Log.d(TAG, "Chat child changed: " + dataSnapshot.getKey() + " for chat: " + chatId);
                updateChatFromSnapshot(chatId, dataSnapshot);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Chat child removed: " + dataSnapshot.getKey() + " for chat: " + chatId);
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

        // Also listen to typing indicators
        ValueEventListener typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                updateTypingStatus(chatId, dataSnapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Typing listener cancelled for: " + chatId, databaseError.toException());
            }
        };

        typingListeners.put(chatId, typingListener);
        FirestoreUtil.getTypingRef(chatId).addValueEventListener(typingListener);
    }

    private void updateChatFromSnapshot(String chatId, DataSnapshot chatSnapshot) {
        if (!chatSnapshot.exists()) return;

        // Get the entire chat data at once
        FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) return;

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

                Log.d(TAG, "Chat " + chatId + " - otherUser: " + otherUserId + " lastMessage: " + lastMessage);

                if (otherUserId != null) {
                    // Find existing chat in the list (from friends)
                    Chat existingChat = findChatById(chatId);
                    if (existingChat != null) {
                        Log.d(TAG, "Updating existing chat: " + chatId);
                        // Update existing chat with real data
                        existingChat.setLastMessage(lastMessage != null ? lastMessage : "");
                        existingChat.setLastMessageTimestamp(lastMessageTimestamp != null ? lastMessageTimestamp : 0);
                        existingChat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");
                        existingChat.setLastMessageType(lastMessageType != null ? lastMessageType : "text");
                        existingChat.setActive(isActive != null ? isActive : false);

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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load full chat data", databaseError.toException());
            }
        });
    }

    private void loadOtherUserDetails(String chatId, String otherUserId, String lastMessage,
                                      long lastMessageTimestamp, String lastMessageSenderId, String lastMessageType) {
        Log.d(TAG, "Loading other user details: " + otherUserId + " for chat: " + chatId);

        // Load user details from Firestore
        FirestoreUtil.getUserRef(otherUserId).get()
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
    }

    private void loadUserPresence(User user, Runnable onComplete) {
        FirestoreUtil.getRealtimePresenceRef(user.getId())
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
        // Count unread messages for this chat
        FirestoreUtil.getMessagesRef(chat.getId())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        int unreadCount = 0;

                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            Integer status = messageSnapshot.child("status").getValue(Integer.class);

                            // Count messages not sent by current user and not read
                            if (senderId != null && !senderId.equals(currentUserId) &&
                                    status != null && status != 3) { // STATUS_READ = 3
                                unreadCount++;
                            }
                        }

                        chat.setUnreadCount(unreadCount);
                        adapter.addOrUpdateChat(chat);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        // Set unread count to 0 on error
                        chat.setUnreadCount(0);
                        adapter.addOrUpdateChat(chat);
                    }
                });
    }

    private void updateChatInList(Chat chat) {
        // Find existing chat and update or add new one
        int existingIndex = -1;
        for (int i = 0; i < chatList.size(); i++) {
            if (chatList.get(i).getId().equals(chat.getId())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            chatList.set(existingIndex, chat);
        } else {
            chatList.add(chat);
        }

        // Sort chats (active chats first by timestamp, then empty chats by name)
        Collections.sort(chatList);

        updateEmptyState(chatList.isEmpty());

        if (adapter != null) {
            adapter.updateChats(chatList);
        }
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
        if (binding.emptyView != null) {
            binding.emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        binding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    public void refreshChats() {
        Log.d(TAG, "Refreshing chats");
        loadChats();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Log.d(TAG, "ChatsFragment destroyed, removing listeners");

        // Remove all listeners to prevent memory leaks
        if (userChatsListener != null && currentUserId != null) {
            FirestoreUtil.getUserChatsRef(currentUserId).removeEventListener(userChatsListener);
        }

        // Remove chat listeners
        for (Map.Entry<String, ChildEventListener> entry : chatListeners.entrySet()) {
            String chatId = entry.getKey();
            ChildEventListener listener = entry.getValue();
            FirestoreUtil.getChatRef(chatId).removeEventListener(listener);
        }
        chatListeners.clear();

        // Remove typing listeners
        for (Map.Entry<String, ValueEventListener> entry : typingListeners.entrySet()) {
            String chatId = entry.getKey();
            ValueEventListener listener = entry.getValue();
            FirestoreUtil.getTypingRef(chatId).removeEventListener(listener);
        }
        typingListeners.clear();

        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update user presence to online
        if (currentUserId != null) {
            FirestoreUtil.updateUserPresence(currentUserId, true);
        }

        // Refresh chat list
        refreshChats();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Update user presence to offline
        if (currentUserId != null) {
            FirestoreUtil.updateUserPresence(currentUserId, false);
        }
    }
}