package com.pingme.android.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pingme.android.models.Chat;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatRepository {
    private String currentUserId;

    public ChatRepository() {
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public LiveData<List<Chat>> loadChats() {
        MutableLiveData<List<Chat>> chatsLiveData = new MutableLiveData<>();

        FirestoreUtil.getChatsCollectionRef()
                .whereArrayContains("participants", currentUserId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        chatsLiveData.setValue(new ArrayList<>());
                        return;
                    }

                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        List<Chat> chats = new ArrayList<>();
                        AtomicInteger pendingOperations = new AtomicInteger(queryDocumentSnapshots.size());

                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            String chatId = doc.getId();
                            String lastMessage = doc.getString("lastMessage");
                            Long lastMessageTimestamp = doc.getLong("lastMessageTimestamp");
                            String lastMessageSenderId = doc.getString("lastMessageSenderId");

                            @SuppressWarnings("unchecked")
                            List<String> participants = (List<String>) doc.get("participants");

                            if (participants != null && participants.size() == 2) {
                                String otherUserId = participants.get(0).equals(currentUserId)
                                        ? participants.get(1)
                                        : participants.get(0);

                                // Load other user details
                                FirestoreUtil.getUserRef(otherUserId).get()
                                        .addOnSuccessListener(userSnapshot -> {
                                            if (userSnapshot.exists()) {
                                                User otherUser = userSnapshot.toObject(User.class);
                                                if (otherUser != null) {
                                                    Chat chat = new Chat();
                                                    chat.setId(chatId);
                                                    chat.setOtherUser(otherUser);
                                                    chat.setLastMessage(lastMessage != null ? lastMessage : "");
                                                    chat.setLastMessageTimestamp(lastMessageTimestamp != null ? lastMessageTimestamp : 0);
                                                    chat.setLastMessageSenderId(lastMessageSenderId != null ? lastMessageSenderId : "");

                                                    chats.add(chat);
                                                }
                                            }

                                            // Check if all operations are complete
                                            if (pendingOperations.decrementAndGet() == 0) {
                                                // Sort chats by timestamp
                                                chats.sort((c1, c2) -> Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp()));
                                                chatsLiveData.setValue(chats);
                                            }
                                        })
                                        .addOnFailureListener(error -> {
                                            if (pendingOperations.decrementAndGet() == 0) {
                                                chatsLiveData.setValue(chats);
                                            }
                                        });
                            } else {
                                if (pendingOperations.decrementAndGet() == 0) {
                                    chatsLiveData.setValue(chats);
                                }
                            }
                        }
                    } else {
                        chatsLiveData.setValue(new ArrayList<>());
                    }
                });

        return chatsLiveData;
    }

    public void createChat(String otherUserId) {
        String chatId = FirestoreUtil.generateChatId(currentUserId, otherUserId);
        FirestoreUtil.createNewChat(chatId, currentUserId, otherUserId);
    }

    public void markChatAsRead(String chatId) {
        FirestoreUtil.markMessagesAsRead(chatId, currentUserId);
    }

    public LiveData<List<User>> loadFriends() {
        MutableLiveData<List<User>> friendsLiveData = new MutableLiveData<>();

        FirestoreUtil.getFriendsRef(currentUserId)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        friendsLiveData.setValue(new ArrayList<>());
                        return;
                    }

                    List<User> friends = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            User friend = doc.toObject(User.class);
                            if (friend != null) {
                                friends.add(friend);
                            }
                        }
                    }
                    friendsLiveData.setValue(friends);
                });

        return friendsLiveData;
    }
}