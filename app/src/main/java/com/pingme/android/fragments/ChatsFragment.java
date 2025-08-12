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

        // First load friends as empty chats
        loadFriendsAsEmptyChats();

        // Then load active chats and merge them
        loadActiveChats();
    }

    private void loadFriendsAsEmptyChats() {
        Log.d(TAG, "Loading friends as empty chats");

        FirestoreUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " friends");

                    List<Chat> friendChats = new ArrayList<>();
                    if (querySnapshot.isEmpty()) {
                        // Nothing to add
                        updateEmptyState(chatList.isEmpty());
                        if (adapter != null) adapter.updateChats(chatList);
                        return;
                    }

                    final int total = querySnapshot.size();
                    final int[] loaded = {0};

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String friendId = doc.getId();
                        FirestoreUtil.getUserRef(friendId).get()
                                .addOnSuccessListener(userSnap -> {
                                    if (userSnap.exists()) {
                                        User friend = userSnap.toObject(User.class);
                                        if (friend != null) {
                                            friend.setId(userSnap.getId());

                                            Chat friendChat = new Chat();
                                            String chatId = FirestoreUtil.generateChatId(currentUserId, friend.getId());
                                            friendChat.setId(chatId);
                                            friendChat.setOtherUser(friend);
                                            friendChat.setLastMessage("");
                                            friendChat.setLastMessageTimestamp(0);
                                            friendChat.setLastMessageSenderId("");
                                            friendChat.setLastMessageType("friend_added");
                                            friendChat.setUnreadCount(0);
                                            friendChat.setActive(false);

                                            // Load presence before merging
                                            loadUserPresence(friend, () -> {
                                                friendChats.add(friendChat);
                                                loaded[0]++;
                                                if (loaded[0] == total) {
                                                    // Merge without clearing to preserve existing last messages
                                                    for (Chat c : friendChats) {
                                                        Chat existing = findChatById(c.getId());
                                                        if (existing == null) {
                                                            chatList.add(c);
                                                        }
                                                    }
                                                    sortChats(chatList);
                                                    updateEmptyState(chatList.isEmpty());
                                                    if (adapter != null) adapter.updateChats(chatList);
                                                }
                                            });
                                        } else {
                                            loaded[0]++;
                                        }
                                    } else {
                                        loaded[0]++;
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    loaded[0]++;
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load friends", e);
                    updateEmptyState(chatList.isEmpty());
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

        ChildEventListener chatListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                Log.d(TAG, "Chat child added: " + dataSnapshot.getKey() + " for chat: " + chatId);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                Log.d(TAG, "Chat child changed: " + dataSnapshot.getKey() + " for chat: " + chatId);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Chat listener cancelled", databaseError.toException());
            }
        };

        // Load full chat snapshot once to get last message and participants
        FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
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
        sortChats(chatList);

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

        // FIXED: Only refresh if we have data, otherwise do full reload
        if (chatList.isEmpty()) {
            loadChats();
        } else {
            // Just refresh existing data to update timestamps and presence
            refreshExistingChats();
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

    @Override
    public void onPause() {
        super.onPause();

        // Update user presence to offline
        if (currentUserId != null) {
            FirestoreUtil.updateUserPresence(currentUserId, false);
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