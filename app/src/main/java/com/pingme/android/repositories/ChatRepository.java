package com.pingme.android.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.pingme.android.models.Chat;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

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
    private final MutableLiveData<List<Chat>> chatsLiveData = new MutableLiveData<>();

    public ChatRepository() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            throw new IllegalStateException("User not authenticated");
        }
    }

    public LiveData<List<Chat>> getChatsLiveData() {
        return chatsLiveData;
    }

    public void loadChats(String userId) {
        loadFriendsAsChats();
    }

    public LiveData<List<Chat>> loadChats() {
        loadChats(currentUserId);
        return chatsLiveData;
    }

    private void loadFriendsAsChats() {
        // Load friends from the simplified structure
        FirestoreUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(this::processFriendsData)
                .addOnFailureListener(e -> chatsLiveData.setValue(Collections.emptyList()));
    }

    private void processFriendsData(QuerySnapshot querySnapshot) {
        if (querySnapshot.isEmpty()) {
            chatsLiveData.setValue(Collections.emptyList());
            return;
        }

        List<Chat> friendChats = new ArrayList<>();
        
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            Map<String, Object> friendData = doc.getData();
            if (friendData != null) {
                // Create User object from friend data
                User friend = createUserFromFriendData(doc.getId(), friendData);
                
                // Create chat for this friend
                Chat friendChat = createChatForFriend(friend);
                friendChats.add(friendChat);
            }
        }

        // Sort friends alphabetically
        Collections.sort(friendChats, (c1, c2) ->
                c1.getOtherUser().getName().compareToIgnoreCase(c2.getOtherUser().getName()));

        chatsLiveData.setValue(friendChats);
        
        // Now load actual chat data from Firestore chats collection to update last messages
        loadChatMessages(friendChats);
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

    private Chat createChatForFriend(User friend) {
        Chat friendChat = new Chat();
        String chatId = FirestoreUtil.generateChatId(currentUserId, friend.getId());
        friendChat.setId(chatId);
        friendChat.setOtherUser(friend);
        friendChat.setLastMessage("Tap to start messaging");
        friendChat.setLastMessageTimestamp(System.currentTimeMillis());
        friendChat.setLastMessageSenderId("");
        friendChat.setLastMessageType("empty_chat");
        return friendChat;
    }

    private void loadChatMessages(List<Chat> friendChats) {
        if (friendChats.isEmpty()) return;

        // Create a map for quick lookup
        Map<String, Chat> chatMap = new HashMap<>();
        for (Chat chat : friendChats) {
            chatMap.put(chat.getId(), chat);
        }

        // Load actual chat data from Firestore
        for (Chat chat : friendChats) {
            FirestoreUtil.getChatsCollectionRef()
                    .document(chat.getId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Update chat with real data
                            updateChatFromFirestore(chat, documentSnapshot);
                        }
                        // Update the list with modified chat
                        updateChatsDisplay(friendChats);
                    })
                    .addOnFailureListener(e -> {
                        // Keep the empty chat even if loading fails
                        updateChatsDisplay(friendChats);
                    });
        }
    }

    private void updateChatFromFirestore(Chat chat, DocumentSnapshot chatDoc) {
        String lastMessage = chatDoc.getString("lastMessage");
        Long lastTimestamp = chatDoc.getLong("lastMessageTimestamp");
        String lastSenderId = chatDoc.getString("lastMessageSenderId");
        String lastType = chatDoc.getString("lastMessageType");

        if (lastMessage != null && !lastMessage.isEmpty()) {
            chat.setLastMessage(lastMessage);
            chat.setLastMessageTimestamp(lastTimestamp != null ? lastTimestamp : System.currentTimeMillis());
            chat.setLastMessageSenderId(lastSenderId != null ? lastSenderId : "");
            chat.setLastMessageType(lastType != null ? lastType : "text");
        }
    }

    private void updateChatsDisplay(List<Chat> chats) {
        // Sort chats: active chats first (by timestamp), then empty chats (by name)
        Collections.sort(chats, (c1, c2) -> {
            boolean c1IsEmpty = "empty_chat".equals(c1.getLastMessageType()) || 
                              "Tap to start messaging".equals(c1.getLastMessage());
            boolean c2IsEmpty = "empty_chat".equals(c2.getLastMessageType()) || 
                              "Tap to start messaging".equals(c2.getLastMessage());

            if (c1IsEmpty && c2IsEmpty) {
                // Both empty - sort by name
                return c1.getOtherUser().getName().compareToIgnoreCase(c2.getOtherUser().getName());
            }
            if (c1IsEmpty) return 1; // c1 to bottom
            if (c2IsEmpty) return -1; // c2 to bottom

            // Both have messages - sort by timestamp (newest first)
            return Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp());
        });

        chatsLiveData.setValue(chats);
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
                    chat.getOtherUser().getName() != null &&
                    chat.getOtherUser().getName().toLowerCase().contains(lowerQuery)) {
                filteredChats.add(chat);
            }
        }

        searchResults.setValue(filteredChats);
        return searchResults;
    }

    public void createNewEmptyChat(String friendId) {
        String chatId = FirestoreUtil.generateChatId(currentUserId, friendId);
        
        // Create chat in Firestore
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("id", chatId);
        chatData.put("type", "direct");
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", System.currentTimeMillis());
        chatData.put("lastMessageSenderId", "");

        Map<String, Boolean> participants = new HashMap<>();
        participants.put(currentUserId, true);
        participants.put(friendId, true);
        chatData.put("participants", participants);

        FirestoreUtil.getChatsCollectionRef().document(chatId).set(chatData);
    }

    public void blockUser(String userId) {
        FirestoreUtil.blockUser(currentUserId, userId, new FirestoreUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                // Refresh chats to remove blocked user
                loadFriendsAsChats();
            }

            @Override
            public void onError(String error) {
                // Handle error if needed
            }
        });
    }

    public void unblockUser(String userId) {
        FirestoreUtil.unblockUser(currentUserId, userId, new FirestoreUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                // Refresh chats if needed
                loadFriendsAsChats();
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