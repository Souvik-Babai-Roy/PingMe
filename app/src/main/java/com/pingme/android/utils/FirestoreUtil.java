package com.pingme.android.utils;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FirestoreUtil {
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_CHATS = "chats";
    private static final String COLLECTION_MESSAGES = "messages";
    private static final String COLLECTION_STATUS = "status";
    private static final String COLLECTION_PRESENCE = "presence";
    private static final String COLLECTION_FRIENDS = "friends";

    public static DocumentReference getUserRef(String userId) {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_USERS)
                .document(userId);
    }

    public static CollectionReference getUsersCollectionRef() {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_USERS);
    }

    public static DocumentReference getChatRef(String chatId) {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_CHATS)
                .document(chatId);
    }

    public static CollectionReference getChatsCollectionRef() {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_CHATS);
    }

    public static CollectionReference getMessagesRef(String chatId) {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(COLLECTION_MESSAGES);
    }

    public static CollectionReference getStatusCollectionRef() {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_STATUS);
    }

    public static DocumentReference getPresenceRef(String userId) {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_PRESENCE)
                .document(userId);
    }

    public static CollectionReference getFriendsRef(String userId) {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_FRIENDS);
    }

    public static String generateChatId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }

    public static void createNewChat(String chatId, String user1Id, String user2Id) {
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("id", chatId);
        chatData.put("participants", Arrays.asList(user1Id, user2Id));
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", FieldValue.serverTimestamp());
        chatData.put("lastMessageSenderId", "");
        chatData.put("createdAt", FieldValue.serverTimestamp());

        getChatRef(chatId).set(chatData);
    }

    public static void updateUserPresence(String userId, boolean isOnline) {
        Map<String, Object> presence = new HashMap<>();
        presence.put("online", isOnline);
        presence.put("lastSeen", FieldValue.serverTimestamp());

        getPresenceRef(userId).set(presence);
    }

    public static void updateFCMToken(String userId, String token) {
        getUserRef(userId).update("fcmToken", token);
    }

    public static void markMessagesAsRead(String chatId, String userId) {
        getMessagesRef(chatId)
                .whereEqualTo("senderId", userId)
                .whereNotEqualTo("status", 3) // Not already read
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    queryDocumentSnapshots.forEach(document -> {
                        document.getReference().update("status", 3); // Mark as read
                    });
                });
    }
}