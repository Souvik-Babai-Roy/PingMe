package com.pingme.android.utils;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FirestoreUtil {
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_STATUS = "status";
    private static final String COLLECTION_PRESENCE = "presence";
    private static final String COLLECTION_FRIENDS = "friends";

    // Realtime Database references for chats
    private static final String RT_CHATS = "chats";
    private static final String RT_MESSAGES = "messages";
    private static final String RT_TYPING = "typing";
    private static final String RT_USER_CHATS = "user_chats";
    private static final String RT_BLOCKED_USERS = "blocked_users";

    // ===== FIRESTORE METHODS (for user data, settings, friends) =====

    public static DocumentReference getUserRef(String userId) {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_USERS)
                .document(userId);
    }

    public static CollectionReference getUsersCollectionRef() {
        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_USERS);
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

    // ===== REALTIME DATABASE METHODS (for chats and messages) =====

    public static DatabaseReference getRealtimeDatabase() {
        return FirebaseDatabase.getInstance().getReference();
    }

    public static DatabaseReference getChatRef(String chatId) {
        return getRealtimeDatabase().child(RT_CHATS).child(chatId);
    }

    public static DatabaseReference getChatsRef() {
        return getRealtimeDatabase().child(RT_CHATS);
    }

    public static DatabaseReference getMessagesRef(String chatId) {
        return getRealtimeDatabase().child(RT_MESSAGES).child(chatId);
    }

    public static DatabaseReference getTypingRef(String chatId) {
        return getRealtimeDatabase().child(RT_TYPING).child(chatId);
    }

    public static DatabaseReference getUserChatsRef(String userId) {
        return getRealtimeDatabase().child(RT_USER_CHATS).child(userId);
    }

    // ===== CHAT MANAGEMENT =====

    public static String generateChatId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }

    public static void createNewChatInRealtime(String chatId, String user1Id, String user2Id) {
        DatabaseReference chatRef = getChatRef(chatId);

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("id", chatId);
        chatData.put("participants", Arrays.asList(user1Id, user2Id));
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", System.currentTimeMillis());
        chatData.put("lastMessageSenderId", "");
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("type", "direct");
        chatData.put("isActive", true);

        chatRef.setValue(chatData);

        // Add chat reference to both users
        getUserChatsRef(user1Id).child(chatId).setValue(true);
        getUserChatsRef(user2Id).child(chatId).setValue(true);
    }

    public static void updateChatLastMessage(String chatId, String message, String senderId) {
        DatabaseReference chatRef = getChatRef(chatId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", message);
        updates.put("lastMessageTimestamp", System.currentTimeMillis());
        updates.put("lastMessageSenderId", senderId);

        chatRef.updateChildren(updates);
    }

    public static void sendMessageToRealtime(String chatId, String senderId, String text,
                                             String messageType, Map<String, Object> mediaData) {
        DatabaseReference messagesRef = getMessagesRef(chatId);
        String messageId = messagesRef.push().getKey();

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("status", 1); // Sent
        messageData.put("type", messageType);

        if (mediaData != null) {
            messageData.putAll(mediaData);
        }

        messagesRef.child(messageId).setValue(messageData);

        // Update chat's last message
        updateChatLastMessage(chatId, text, senderId);
    }

    // ===== USER PRESENCE AND SETTINGS =====

    public static void updateUserPresence(String userId, boolean isOnline) {
        Map<String, Object> presence = new HashMap<>();
        presence.put("online", isOnline);
        presence.put("lastSeen", System.currentTimeMillis());

        // Update both in users collection and presence collection
        getUserRef(userId).update(
                "isOnline", isOnline,
                "lastSeen", System.currentTimeMillis()
        );

        getPresenceRef(userId).set(presence);
    }

    public static void updateFCMToken(String userId, String token) {
        getUserRef(userId).update("fcmToken", token);
    }

    public static void markMessagesAsRead(String chatId, String currentUserId) {
        getMessagesRef(chatId)
                .orderByChild("senderId")
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot dataSnapshot) {
                        for (com.google.firebase.database.DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            Integer status = messageSnapshot.child("status").getValue(Integer.class);

                            // Mark as read if not sent by current user and not already read
                            if (senderId != null && !senderId.equals(currentUserId) &&
                                    status != null && status != 3) {
                                messageSnapshot.getRef().child("status").setValue(3);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError databaseError) {
                        // Handle error
                    }
                });
    }

    // ===== SEARCH AND FRIENDS =====

    public static Query searchUserByEmail(String email) {
        return getUsersCollectionRef()
                .whereEqualTo("email", email)
                .limit(1);
    }

    public static void checkFriendship(String currentUserId, String otherUserId,
                                       FriendshipCallback callback) {
        getFriendsRef(currentUserId)
                .document(otherUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    callback.onResult(documentSnapshot.exists());
                })
                .addOnFailureListener(e -> {
                    callback.onResult(false);
                });
    }

    public interface FriendshipCallback {
        void onResult(boolean areFriends);
    }

    // ===== TYPING INDICATORS =====

    public static void setTyping(String chatId, String userId, boolean isTyping) {
        getTypingRef(chatId).child(userId).setValue(isTyping ? System.currentTimeMillis() : null);
    }

    public static DatabaseReference getTypingIndicatorRef(String chatId, String userId) {
        return getTypingRef(chatId).child(userId);
    }
}
