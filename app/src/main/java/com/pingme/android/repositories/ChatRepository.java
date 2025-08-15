package com.pingme.android.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.pingme.android.models.Chat;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ChatRepository {
    private final String currentUserId;
    private final Executor executor = Executors.newFixedThreadPool(4);
    private final Map<String, ValueEventListener> activeListeners = new HashMap<>();
    private final MutableLiveData<List<Chat>> chatsLiveData = new MutableLiveData<>();

    public ChatRepository() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            throw new IllegalStateException("User not authenticated");
        }
    }

    public void cleanup() {
        for (Map.Entry<String, ValueEventListener> entry : activeListeners.entrySet()) {
            FirebaseUtil.getChatRef(entry.getKey()).removeEventListener(entry.getValue());
        }
        activeListeners.clear();
    }

    public LiveData<List<Chat>> getChatsLiveData() {
        return chatsLiveData;
    }

    public void loadChats(String userId) {
        // First, get blocked users from Realtime Database
        FirebaseUtil.getRealtimeBlockedUsersRef(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot blockedSnapshot) {
                List<String> blockedUserIds = new ArrayList<>();
                for (DataSnapshot snapshot : blockedSnapshot.getChildren()) {
                    blockedUserIds.add(snapshot.getKey());
                }

                // Load actual chats from Realtime Database (only chats with messages)
                loadActiveChats(blockedUserIds);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                chatsLiveData.setValue(Collections.emptyList());
            }
        });
    }

    public LiveData<List<Chat>> loadChats() {
        loadChats(currentUserId);
        return chatsLiveData;
    }

    private void loadFriendsAsEmptyChats(List<String> blockedUserIds) {
        FirebaseUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Chat> friendChats = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Map<String, Object> friendData = doc.getData();
                        if (friendData != null && !blockedUserIds.contains(doc.getId())) {
                            User friend = createUserFromFriendData(doc.getId(), friendData);

                            // Create empty chat for friend
                            Chat friendChat = new Chat();
                            String chatId = FirebaseUtil.generateChatId(currentUserId, friend.getId());
                            friendChat.setId(chatId);
                            friendChat.setOtherUser(friend);
                            friendChat.setLastMessage("Tap to start messaging");
                            friendChat.setLastMessageTimestamp(System.currentTimeMillis());
                            friendChat.setLastMessageSenderId("");
                            friendChat.setLastMessageType("empty_chat");

                            friendChats.add(friendChat);
                        }
                    }

                    // Sort friends alphabetically
                    Collections.sort(friendChats, (c1, c2) ->
                            c1.getOtherUser().getName().compareToIgnoreCase(c2.getOtherUser().getName()));

                    chatsLiveData.setValue(friendChats);
                })
                .addOnFailureListener(e -> chatsLiveData.setValue(Collections.emptyList()));
    }

    private User createUserFromFriendData(String friendId, Map<String, Object> friendData) {
        User friend = new User();
        friend.setId(friendId);
        friend.setName((String) friendData.get("name"));
        friend.setEmail((String) friendData.get("email"));
        friend.setImageUrl((String) friendData.get("imageUrl"));
        friend.setAbout((String) friendData.get("about"));
        return friend;
    }

    private void loadActiveChats(List<String> blockedUserIds) {
        FirebaseUtil.getUserChatsRef(currentUserId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<String> chatIds = new ArrayList<>();
                        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                            Boolean isActive = chatSnapshot.getValue(Boolean.class);
                            if (isActive != null && isActive) {
                                chatIds.add(chatSnapshot.getKey());
                            } else {
                                // Remove inactive chats from user's list
                                chatSnapshot.getRef().removeValue();
                            }
                        }

                        if (!chatIds.isEmpty()) {
                            loadChatDetails(chatIds, blockedUserIds);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // Keep existing empty chats if loading active chats fails
                    }
                });
    }

    private void loadChatDetails(List<String> chatIds, List<String> blockedUserIds) {
        List<Chat> chats = Collections.synchronizedList(new ArrayList<>());
        MutableLiveData<Integer> completionCounter = new MutableLiveData<>(0);

        completionCounter.observeForever(count -> {
            if (count == chatIds.size()) {
                mergeChatsWithFriends(chats, blockedUserIds);
            }
        });

        for (String chatId : chatIds) {
            loadSingleChat(chatId, chats, completionCounter, blockedUserIds);
        }
    }

    private void loadSingleChat(String chatId, List<Chat> chats, MutableLiveData<Integer> completionCounter, List<String> blockedUserIds) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String lastMessage = dataSnapshot.child("lastMessage").getValue(String.class);
                    Long lastMessageTimestamp = dataSnapshot.child("lastMessageTimestamp").getValue(Long.class);
                    String lastMessageSenderId = dataSnapshot.child("lastMessageSenderId").getValue(String.class);
                    String lastMessageType = dataSnapshot.child("lastMessageType").getValue(String.class);

                    // Get participants
                    Map<String, Boolean> participantsMap = (Map<String, Boolean>) dataSnapshot.child("participants").getValue();
                    if (participantsMap != null) {
                        List<String> participants = new ArrayList<>(participantsMap.keySet());
                        if (participants.size() == 2) {
                            String otherUserId = participants.get(0).equals(currentUserId)
                                    ? participants.get(1)
                                    : participants.get(0);

                            if (blockedUserIds.contains(otherUserId)) {
                                completionCounter.setValue(completionCounter.getValue() + 1);
                                return;
                            }

                            loadUserForChat(chatId, otherUserId, lastMessage,
                                    lastMessageTimestamp != null ? lastMessageTimestamp : 0,
                                    lastMessageSenderId, lastMessageType, chats, completionCounter);
                            return;
                        }
                    }
                }
                completionCounter.setValue(completionCounter.getValue() + 1);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                completionCounter.setValue(completionCounter.getValue() + 1);
            }
        };

        activeListeners.put(chatId, listener);
        FirebaseUtil.getChatRef(chatId).addValueEventListener(listener);
    }

    private void loadUserForChat(String chatId, String userId, String lastMessage,
                                 long lastMessageTimestamp, String lastMessageSenderId, String lastMessageType,
                                 List<Chat> chats, MutableLiveData<Integer> completionCounter) {
        FirebaseUtil.getUserRef(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        DocumentSnapshot userSnapshot = task.getResult();
                        User otherUser = userSnapshot.toObject(User.class);
                        if (otherUser != null) {
                            otherUser.setId(userSnapshot.getId());

                            Chat chat = new Chat();
                            chat.setId(chatId);
                            chat.setOtherUser(otherUser);
                            chat.setLastMessage(lastMessage != null ? lastMessage : "");
                            chat.setLastMessageTimestamp(lastMessageTimestamp);
                            chat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");
                            chat.setLastMessageType(lastMessageType != null ? lastMessageType : "text");

                            synchronized (chats) {
                                chats.add(chat);
                            }
                        }
                    }
                    completionCounter.setValue(completionCounter.getValue() + 1);
                });
    }

    private void mergeChatsWithFriends(List<Chat> activeChats, List<String> blockedUserIds) {
        // Get current empty chats (friends)
        List<Chat> currentChats = chatsLiveData.getValue();
        if (currentChats == null) currentChats = new ArrayList<>();

        // Create a map of user IDs from active chats
        Map<String, Chat> activeChatMap = new HashMap<>();
        for (Chat chat : activeChats) {
            activeChatMap.put(chat.getOtherUser().getId(), chat);
        }

        // Only show chats that have messages (WhatsApp-like behavior)
        // Empty chats should not appear in the chat list
        List<Chat> mergedChats = new ArrayList<>();
        
        // Add all active chats (chats with messages)
        mergedChats.addAll(activeChats);

        sortChats(mergedChats);
        chatsLiveData.setValue(mergedChats);
    }

    private void sortChats(List<Chat> chats) {
        Collections.sort(chats, (c1, c2) -> {
            // Empty chats (friends without messages) go to bottom, sorted by name
            boolean c1IsEmpty = "empty_chat".equals(c1.getLastMessageType()) || c1.getLastMessageTimestamp() == 0;
            boolean c2IsEmpty = "empty_chat".equals(c2.getLastMessageType()) || c2.getLastMessageTimestamp() == 0;

            if (c1IsEmpty && c2IsEmpty) {
                // Both empty - sort by name
                return c1.getOtherUser().getDisplayName().compareToIgnoreCase(c2.getOtherUser().getDisplayName());
            }
            if (c1IsEmpty) return 1; // c1 to bottom
            if (c2IsEmpty) return -1; // c2 to bottom

            // Both have messages - sort by timestamp (newest first)
            return Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp());
        });
    }

    public LiveData<List<Chat>> searchChats(String query, String userId) {
        MutableLiveData<List<Chat>> searchResults = new MutableLiveData<>();

        List<Chat> currentChats = chatsLiveData.getValue();
        if (currentChats == null || query == null || query.trim().isEmpty()) {
            searchResults.setValue(currentChats != null ? currentChats : new ArrayList<>());
            return searchResults;
        }

        List<Chat> filteredChats = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (Chat chat : currentChats) {
            if (chat.getOtherUser() != null &&
                    chat.getOtherUser().getDisplayName() != null &&
                    chat.getOtherUser().getDisplayName().toLowerCase().contains(lowerQuery)) {
                filteredChats.add(chat);
            }
        }

        searchResults.setValue(filteredChats);
        return searchResults;
    }

    public void createNewEmptyChat(String friendId) {
        String chatId = FirebaseUtil.generateChatId(currentUserId, friendId);
        FirebaseUtil.createNewChatInRealtime(chatId, currentUserId, friendId);
    }

    public void blockUser(String userId) {
        FirebaseUtil.blockUser(currentUserId, userId, new FirebaseUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                // Refresh chats to remove blocked user
                loadChats(currentUserId);
            }

            @Override
            public void onError(String error) {
                // Handle error if needed
            }
        });
    }

    public void unblockUser(String userId) {
        FirebaseUtil.unblockUser(currentUserId, userId, new FirebaseUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                // Refresh chats if needed
                loadChats(currentUserId);
            }

            @Override
            public void onError(String error) {
                // Handle error if needed
            }
        });
    }

    public interface PrivacyUpdateCallback {
        void onComplete();
    }
}