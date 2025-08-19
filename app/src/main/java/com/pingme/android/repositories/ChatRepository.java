/*
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

public class ChatRepository {
    private final String currentUserId;
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
        // Simplified implementation - just load friends as empty chats
        loadFriendsAsEmptyChats();
    }

    public LiveData<List<Chat>> loadChats() {
        loadChats(currentUserId);
        return chatsLiveData;
    }

    private void loadFriendsAsEmptyChats() {
        FirebaseUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Chat> friendChats = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Map<String, Object> friendData = doc.getData();
                        if (friendData != null) {
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
*/