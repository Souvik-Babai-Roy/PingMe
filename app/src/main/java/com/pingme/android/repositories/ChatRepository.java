package com.pingme.android.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.pingme.android.models.Chat;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ChatRepository {
    private final String currentUserId;
    private final Executor executor = Executors.newFixedThreadPool(4);

    public ChatRepository() {
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public void createNewEmptyChat(String friendId) {
        DatabaseReference chatsRef = FirestoreUtil.getRealtimeDatabase().child("chats").push();
        String chatId = chatsRef.getKey();
        
        HashMap<String, Object> chatMap = new HashMap<>();
        chatMap.put("participants/" + currentUserId, true);
        chatMap.put("participants/" + friendId, true);
        chatMap.put("createdAt", com.google.firebase.database.ServerValue.TIMESTAMP);
        
        chatsRef.updateChildren(chatMap).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Add chat reference to both users
                FirestoreUtil.getUserChatsRef(currentUserId).child(chatId).setValue(true);
                FirestoreUtil.getUserChatsRef(friendId).child(chatId).setValue(true);
            }
        });
    }

    public LiveData<List<Chat>> loadChats() {
        MutableLiveData<List<Chat>> chatsLiveData = new MutableLiveData<>();
        DatabaseReference userChatsRef = FirestoreUtil.getUserChatsRef(currentUserId);
        DatabaseReference blockedUsersRef = FirestoreUtil.getBlockedUsersRef(currentUserId);

        // Listen to user's chat list from Realtime Database
        FirestoreUtil.getUserChatsRef(currentUserId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<String> chatIds = new ArrayList<>();

                        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                            Boolean isActive = chatSnapshot.getValue(Boolean.class);
                            if (isActive != null && isActive) {
                                chatIds.add(chatSnapshot.getKey());
                            }
                        }

                        if (chatIds.isEmpty()) {
                            // Load friends as potential chats with no messages
                            loadFriendsAsChats(chatsLiveData);
                        } else {
                            loadChatDetails(chatIds, chatsLiveData);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        chatsLiveData.setValue(Collections.emptyList());
                    }
                });

        return chatsLiveData;
    }

    private void loadChatDetails(List<String> chatIds, MutableLiveData<List<Chat>> chatsLiveData) {
        List<Chat> chats = new ArrayList<>();
        MutableLiveData<Integer> completionCounter = new MutableLiveData<>(0);

        // Watch for when all async operations complete
        completionCounter.observeForever(count -> {
            if (count == chatIds.size()) {
                // Add friends who don't have chats yet
                addFriendsWithoutChats(chats, chatsLiveData);
            }
        });

        for (String chatId : chatIds) {
            loadSingleChat(chatId, chats, completionCounter);
        }
    }

    private void loadSingleChat(String chatId, List<Chat> chats, MutableLiveData<Integer> completionCounter) {
        FirestoreUtil.getChatRef(chatId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String lastMessage = dataSnapshot.child("lastMessage").getValue(String.class);
                    Long lastMessageTimestamp = dataSnapshot.child("lastMessageTimestamp").getValue(Long.class);
                    String lastMessageSenderId = dataSnapshot.child("lastMessageSenderId").getValue(String.class);

                    @SuppressWarnings("unchecked")
                    List<String> participants = (List<String>) dataSnapshot.child("participants").getValue();

                    if (participants != null && participants.size() == 2) {
                        String otherUserId = participants.get(0).equals(currentUserId)
                                ? participants.get(1)
                                : participants.get(0);

                        // Load other user details
                        loadUserForChat(chatId, otherUserId, lastMessage,
                                lastMessageTimestamp != null ? lastMessageTimestamp : 0,
                                lastMessageSenderId, chats);
                    }
                }
                completionCounter.setValue(completionCounter.getValue() + 1);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                completionCounter.setValue(completionCounter.getValue() + 1);
            }
        });
    }

    private void loadUserForChat(String chatId, String userId, String lastMessage,
                                 long lastMessageTimestamp, String lastMessageSenderId, List<Chat> chats) {
        FirestoreUtil.getUserRef(userId).get()
                .addOnSuccessListener(userSnapshot -> {
                    if (userSnapshot.exists()) {
                        User otherUser = userSnapshot.toObject(User.class);
                        if (otherUser != null) {
                            otherUser.setId(userSnapshot.getId());

                            Chat chat = new Chat();
                            chat.setId(chatId);
                            chat.setOtherUser(otherUser);
                            chat.setLastMessage(lastMessage != null ? lastMessage : "");
                            chat.setLastMessageTimestamp(lastMessageTimestamp);
                            chat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");

                            synchronized (chats) {
                                chats.add(chat);
                            }
                        }
                    }
                });
    }

    private void addFriendsWithoutChats(List<Chat> existingChats, MutableLiveData<List<Chat>> chatsLiveData) {
        // Get list of users already in chats
        List<String> usersInChats = new ArrayList<>();
        for (Chat chat : existingChats) {
            usersInChats.add(chat.getOtherUser().getId());
        }

        // Load friends and add those not in existing chats
        FirestoreUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User friend = doc.toObject(User.class);
                        if (friend != null && !usersInChats.contains(friend.getId())) {
                            friend.setId(doc.getId());

                            // Create chat object with no messages
                            Chat friendChat = new Chat();
                            String chatId = FirestoreUtil.generateChatId(currentUserId, friend.getId());
                            friendChat.setId(chatId);
                            friendChat.setOtherUser(friend);
                            friendChat.setLastMessage("");
                            friendChat.setLastMessageTimestamp(0);
                            friendChat.setLastMessageSenderId("");

                            existingChats.add(friendChat);
                        }
                    }

                    // Sort chats by timestamp (recent first), but put empty chats at bottom
                    Collections.sort(existingChats, (c1, c2) -> {
                        // Empty chats (no messages) go to bottom
                        if (c1.getLastMessageTimestamp() == 0 && c2.getLastMessageTimestamp() == 0) {
                            return c1.getOtherUser().getName().compareTo(c2.getOtherUser().getName());
                        }
                        if (c1.getLastMessageTimestamp() == 0) return 1;
                        if (c2.getLastMessageTimestamp() == 0) return -1;

                        // Sort by timestamp for chats with messages
                        return Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp());
                    });

                    chatsLiveData.setValue(existingChats);
                })
                .addOnFailureListener(e -> {
                    chatsLiveData.setValue(existingChats);
                });
    }

    private void loadFriendsAsChats(MutableLiveData<List<Chat>> chatsLiveData) {
        List<Chat> friendChats = new ArrayList<>();

        FirestoreUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User friend = doc.toObject(User.class);
                        if (friend != null) {
                            friend.setId(doc.getId());

                            // Create chat object with no messages
                            Chat friendChat = new Chat();
                            String chatId = FirestoreUtil.generateChatId(currentUserId, friend.getId());
                            friendChat.setId(chatId);
                            friendChat.setOtherUser(friend);
                            friendChat.setLastMessage("");
                            friendChat.setLastMessageTimestamp(0);
                            friendChat.setLastMessageSenderId("");

                            friendChats.add(friendChat);
                        }
                    }

                    // Sort friends alphabetically
                    Collections.sort(friendChats, (c1, c2) ->
                            c1.getOtherUser().getName().compareTo(c2.getOtherUser().getName()));

                    chatsLiveData.setValue(friendChats);
                })
                .addOnFailureListener(e -> {
                    chatsLiveData.setValue(Collections.emptyList());
                });
    }

    public void createChat(String otherUserId) {
        String chatId = FirestoreUtil.generateChatId(currentUserId, otherUserId);
        FirestoreUtil.createNewChatInRealtime(chatId, currentUserId, otherUserId);
    }

    public void markChatAsRead(String chatId) {
        FirestoreUtil.markMessagesAsRead(chatId, currentUserId);
    }

    public LiveData<List<User>> loadFriends() {
        MutableLiveData<List<User>> friendsLiveData = new MutableLiveData<>();

        FirestoreUtil.getFriendsRef(currentUserId)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        friendsLiveData.setValue(Collections.emptyList());
                        return;
                    }

                    List<User> friends = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            User friend = doc.toObject(User.class);
                            if (friend != null) {
                                friend.setId(doc.getId());
                                friends.add(friend);
                            }
                        }
                    }

                    // Sort friends alphabetically
                    Collections.sort(friends, (u1, u2) -> u1.getName().compareTo(u2.getName()));

                    friendsLiveData.setValue(friends);
                });

        return friendsLiveData;
    }

    // Method to refresh user privacy settings for all chats
    public void refreshUserPrivacySettings(List<Chat> chats, PrivacyUpdateCallback callback) {
        if (chats.isEmpty()) {
            callback.onComplete();
            return;
        }

        MutableLiveData<Integer> counter = new MutableLiveData<>(0);
        counter.observeForever(count -> {
            if (count == chats.size()) {
                callback.onComplete();
            }
        });

        for (Chat chat : chats) {
            String userId = chat.getOtherUser().getId();
            FirestoreUtil.getUserRef(userId).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            User updatedUser = task.getResult().toObject(User.class);
                            if (updatedUser != null) {
                                updatedUser.setId(task.getResult().getId());
                                chat.setOtherUser(updatedUser);
                            }
                        }
                        counter.setValue(counter.getValue() + 1);
                    });
        }
    }

    public interface PrivacyUpdateCallback {
        void onComplete();
    }
}
