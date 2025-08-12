package com.pingme.android.utils;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.models.Chat;
import com.pingme.android.models.Broadcast;
import com.google.firebase.auth.FirebaseAuth;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

public class FirestoreUtil {
    private static final String TAG = "FirestoreUtil";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_STATUS = "status";
    private static final String COLLECTION_PRESENCE = "presence";
    private static final String COLLECTION_FRIENDS = "friends";

    // Realtime Database references
    private static final String RT_CHATS = "chats";
    private static final String RT_MESSAGES = "messages";
    private static final String RT_TYPING = "typing";
    private static final String RT_USER_CHATS = "user_chats";
    private static final String RT_BLOCKED_USERS = "blocked_users";
    private static final String RT_USER_SETTINGS = "user_settings";
    private static final String NODE_PRESENCE = "presence";

    // ===== COLLECTION REFERENCES =====

    public static CollectionReference getUsersCollectionRef() {
        return FirebaseFirestore.getInstance().collection("users");
    }

    public static DocumentReference getUserRef(String userId) {
        return getUsersCollectionRef().document(userId);
    }

    public static CollectionReference getStatusCollectionRef() {
        return FirebaseFirestore.getInstance().collection("status");
    }

    public static CollectionReference getNotificationsCollectionRef() {
        return FirebaseFirestore.getInstance().collection("notifications");
    }

    public static CollectionReference getReportsCollectionRef() {
        return FirebaseFirestore.getInstance().collection("reports");
    }

    public static CollectionReference getFriendsRef(String userId) {
        return getUserRef(userId).collection("friends");
    }

    public static CollectionReference getBlockedUsersRef(String userId) {
        return FirebaseFirestore.getInstance().collection("blocked_users").document(userId).collection("blocked");
    }

    public static CollectionReference getUserSettingsRef(String userId) {
        return FirebaseFirestore.getInstance().collection("user_settings").document(userId).collection("settings");
    }

    // ===== FIRESTORE METHODS (for user data, settings, friends) =====

    public static void createUser(User user) {
        if (user == null || user.getId() == null) return;

        Log.d(TAG, "Creating user: " + user.getId());

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", user.getName());
        userData.put("email", user.getEmail());
        userData.put("phoneNumber", user.getPhoneNumber());
        userData.put("imageUrl", user.getImageUrl());
        userData.put("about", user.getAbout());
        userData.put("joinedAt", user.getJoinedAt());
        userData.put("status", user.getStatus());
        userData.put("isOnline", user.isOnline());
        userData.put("lastSeen", user.getLastSeen());
        userData.put("fcmToken", user.getFcmToken());

        // Privacy settings
        userData.put("profile_photo_enabled", user.isProfilePhotoEnabled());
        userData.put("last_seen_enabled", user.isLastSeenEnabled());
        userData.put("about_enabled", user.isAboutEnabled());
        userData.put("read_receipts_enabled", user.isReadReceiptsEnabled());

        getUserRef(user.getId()).set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User created successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create user", e));
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

    public static DatabaseReference getRealtimeBlockedUsersRef(String userId) {
        return getRealtimeDatabase().child(RT_BLOCKED_USERS).child(userId);
    }



    public static DatabaseReference getRealtimePresenceRef(String userId) {
        return getRealtimeDatabase().child(NODE_PRESENCE).child(userId);
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
        Log.d(TAG, "Creating new chat: " + chatId + " between " + user1Id + " and " + user2Id);

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("id", chatId);

        // Participants as a map for easier querying
        Map<String, Boolean> participants = new HashMap<>();
        participants.put(user1Id, true);
        participants.put(user2Id, true);
        chatData.put("participants", participants);

        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", System.currentTimeMillis());
        chatData.put("lastMessageSenderId", "");
        chatData.put("lastMessageType", "empty_chat");
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("type", "direct");
        chatData.put("isActive", true);

        // Create chat and update user chat references atomically
        Map<String, Object> updates = new HashMap<>();
        updates.put("chats/" + chatId, chatData);
        updates.put("user_chats/" + user1Id + "/" + chatId, true);
        updates.put("user_chats/" + user2Id + "/" + chatId, true);

        getRealtimeDatabase().updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat created successfully: " + chatId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create chat: " + chatId, e));
    }

    public static void createEmptyFriendChat(String currentUserId, String friendId) {
        String chatId = generateChatId(currentUserId, friendId);
        Log.d(TAG, "Creating empty friend chat: " + chatId);

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("id", chatId);

        Map<String, Boolean> participants = new HashMap<>();
        participants.put(currentUserId, true);
        participants.put(friendId, true);
        chatData.put("participants", participants);

        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", System.currentTimeMillis());
        chatData.put("lastMessageSenderId", "");
        chatData.put("lastMessageType", "friend_added");
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("type", "direct");
        chatData.put("isActive", true);

        Map<String, Object> updates = new HashMap<>();
        updates.put("chats/" + chatId, chatData);
        updates.put("user_chats/" + currentUserId + "/" + chatId, true);
        updates.put("user_chats/" + friendId + "/" + chatId, true);

        getRealtimeDatabase().updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Friend chat created successfully: " + chatId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create friend chat: " + chatId, e));
    }

    public static void updateChatLastMessage(String chatId, String message, String senderId, String messageType) {
        if (chatId == null || senderId == null) return;

        Log.d(TAG, "Updating last message for chat: " + chatId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", message != null ? message : "");
        updates.put("lastMessageTimestamp", System.currentTimeMillis());
        updates.put("lastMessageSenderId", senderId);
        updates.put("lastMessageType", messageType != null ? messageType : "text");
        updates.put("isActive", true);

        getChatRef(chatId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat last message updated successfully: " + chatId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update chat last message: " + chatId, e));
    }

    public static void sendMessageToRealtime(String chatId, String senderId, String text,
                                             String messageType, Map<String, Object> mediaData) {
        if (chatId == null || senderId == null) {
            Log.e(TAG, "Cannot send message: chatId or senderId is null");
            return;
        }

        // Check if sender is blocked before sending message
        getChatRef(chatId).child("participants").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<String> participants = new ArrayList<>();
                        for (DataSnapshot participantSnapshot : dataSnapshot.getChildren()) {
                            String participantId = participantSnapshot.getKey();
                            if (participantId != null && !participantId.equals(senderId)) {
                                participants.add(participantId);
                            }
                        }

                        // Check if sender is blocked by any participant
                        if (!participants.isEmpty()) {
                            String receiverId = participants.get(0);
                            checkMutualBlocking(senderId, receiverId, (user1BlockedUser2, user2BlockedUser1) -> {
                                if (user1BlockedUser2 || user2BlockedUser1) {
                                    Log.d(TAG, "Message not sent - users are blocked");
                                    return;
                                }

                                // Proceed with sending message
                                sendMessageInternal(chatId, senderId, text, messageType, mediaData);
                            });
                        } else {
                            // No other participants, send message anyway
                            sendMessageInternal(chatId, senderId, text, messageType, mediaData);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to check participants for message sending", databaseError.toException());
                    }
                });
    }

    private static void sendMessageInternal(String chatId, String senderId, String text,
                                            String messageType, Map<String, Object> mediaData) {
        String messageId = getMessagesRef(chatId).push().getKey();
        if (messageId == null) {
            Log.e(TAG, "Failed to generate message ID");
            return;
        }

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", senderId);
        messageData.put("text", text != null ? text : "");
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("type", messageType != null ? messageType : "text");
        messageData.put("status", Message.STATUS_SENT);

        if (mediaData != null) {
            messageData.putAll(mediaData);
        }

        getMessagesRef(chatId).child(messageId).setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message sent successfully: " + messageId);
                    updateChatLastMessage(chatId, text, senderId, messageType);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send message", e));
    }

    public static void sendTextMessage(String chatId, String senderId, String receiverId, String text, boolean createIfNeeded) {
        if (createIfNeeded) {
            createNewChatInRealtime(chatId, senderId, receiverId);
        }
        sendMessageToRealtime(chatId, senderId, text, "text", null);
    }

    // ===== USER PRESENCE AND SETTINGS =====

    public static void updateUserPresence(String userId, boolean isOnline) {
        if (userId == null) return;

        long currentTime = System.currentTimeMillis();

        Map<String, Object> presence = new HashMap<>();
        presence.put("online", isOnline);
        presence.put("lastSeen", currentTime);

        // Update in Realtime Database for real-time presence
        getRealtimePresenceRef(userId).setValue(presence)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update presence in Realtime DB", e));

        // Also update in Firestore users collection
        getUserRef(userId).update(
                "isOnline", isOnline,
                "lastSeen", currentTime
        ).addOnFailureListener(e -> Log.e(TAG, "Failed to update presence in Firestore", e));
    }

    public static void updateFCMToken(String userId, String token) {
        if (userId == null || token == null) return;

        getUserRef(userId).update("fcmToken", token)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token", e));
    }

    public static void markMessagesAsRead(String chatId, String currentUserId) {
        if (chatId == null || currentUserId == null) return;

        Log.d(TAG, "Marking messages as read for chat: " + chatId + " user: " + currentUserId);

        getMessagesRef(chatId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Map<String, Object> updates = new HashMap<>();

                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            Integer status = messageSnapshot.child("status").getValue(Integer.class);

                            // Mark as read if not sent by current user and not already read
                            if (senderId != null && !senderId.equals(currentUserId) &&
                                    status != null && status != Message.STATUS_READ) {
                                updates.put("messages/" + chatId + "/" + messageSnapshot.getKey() + "/status", Message.STATUS_READ);
                            }
                        }

                        if (!updates.isEmpty()) {
                            getRealtimeDatabase().updateChildren(updates)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Messages marked as read successfully"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark messages as read", e));
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to mark messages as read", databaseError.toException());
                    }
                });
    }

    // ===== SEARCH AND FRIENDS =====

    public static Query searchUserByEmail(String email) {
        return getUsersCollectionRef()
                .whereEqualTo("email", email)
                .limit(1);
    }

    public static Query searchUserByPhoneNumber(String phoneNumber) {
        return getUsersCollectionRef()
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1);
    }

    public static void checkFriendship(String currentUserId, String otherUserId, FriendshipCallback callback) {
        if (currentUserId == null || otherUserId == null || callback == null) return;

        getFriendsRef(currentUserId)
                .document(otherUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> callback.onResult(documentSnapshot.exists()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check friendship", e);
                    callback.onResult(false);
                });
    }

    public static void addFriend(String currentUserId, String friendId, User currentUser, User friendUser) {
        if (currentUserId == null || friendId == null) return;

        Log.d(TAG, "Adding friend: " + friendId + " to user: " + currentUserId);

        Map<String, Object> friendData = new HashMap<>();
        friendData.put("addedAt", System.currentTimeMillis());
        friendData.put("userId", friendId);

        Map<String, Object> currentUserData = new HashMap<>();
        currentUserData.put("addedAt", System.currentTimeMillis());
        currentUserData.put("userId", currentUserId);

        // Batch write to add both friendships
        getFriendsRef(currentUserId).document(friendId).set(friendData)
                .addOnSuccessListener(aVoid -> {
                    getFriendsRef(friendId).document(currentUserId).set(currentUserData)
                            .addOnSuccessListener(aVoid1 -> {
                                Log.d(TAG, "Friendship created successfully between " + currentUserId + " and " + friendId);
                                createEmptyFriendChat(currentUserId, friendId);
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to add friend for friendUser", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to add friend for currentUser", e));
    }

    public static void removeFriend(String currentUserId, String friendId) {
        if (currentUserId == null || friendId == null) return;

        Log.d(TAG, "Removing friend: " + friendId + " from user: " + currentUserId);

        // Remove from both users' friend lists
        getFriendsRef(currentUserId).document(friendId).delete()
                .addOnFailureListener(e -> Log.e(TAG, "Failed to remove friend from current user", e));
        getFriendsRef(friendId).document(currentUserId).delete()
                .addOnFailureListener(e -> Log.e(TAG, "Failed to remove friend from friend user", e));

        // Remove chat
        String chatId = generateChatId(currentUserId, friendId);
        getUserChatsRef(currentUserId).child(chatId).removeValue();
        getUserChatsRef(friendId).child(chatId).removeValue();
        getChatRef(chatId).removeValue();
        getMessagesRef(chatId).removeValue();
    }

    // ===== TYPING INDICATORS =====

    public static void setTyping(String chatId, String userId, boolean isTyping) {
        if (chatId == null || userId == null) return;

        if (isTyping) {
            getTypingRef(chatId).child(userId).setValue(System.currentTimeMillis())
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to set typing indicator", e));
        } else {
            getTypingRef(chatId).child(userId).removeValue()
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to remove typing indicator", e));
        }
    }

    public static DatabaseReference getTypingIndicatorRef(String chatId, String userId) {
        return getTypingRef(chatId).child(userId);
    }

    // ===== BLOCKING FUNCTIONALITY =====

    public static void blockUser(String currentUserId, String userToBlockId) {
        if (currentUserId == null || userToBlockId == null) return;

        Map<String, Object> updates = new HashMap<>();

        // Add to blocked users in Realtime Database
        updates.put("blocked_users/" + currentUserId + "/" + userToBlockId, System.currentTimeMillis());

        // Hide active chats (don't delete, just mark as inactive)
        String chatId = generateChatId(currentUserId, userToBlockId);
        updates.put("user_chats/" + currentUserId + "/" + chatId, false);

        getRealtimeDatabase().updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User " + userToBlockId + " blocked by " + currentUserId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to block user in Realtime DB", e));

        // Add to Firestore blocked users collection
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("blockedAt", System.currentTimeMillis());
        blockData.put("blockedUserId", userToBlockId);

        FirebaseFirestore.getInstance()
                .collection("blocked_users")
                .document(currentUserId)
                .collection("blocked")
                .document(userToBlockId)
                .set(blockData)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to block user in Firestore", e));
    }

    public static void unblockUser(String currentUserId, String userToUnblockId) {
        if (currentUserId == null || userToUnblockId == null) return;

        // Remove from Realtime Database
        getRealtimeBlockedUsersRef(currentUserId).child(userToUnblockId).removeValue()
                .addOnFailureListener(e -> Log.e(TAG, "Failed to unblock user in Realtime DB", e));

        // Remove from Firestore
        FirebaseFirestore.getInstance()
                .collection("blocked_users")
                .document(currentUserId)
                .collection("blocked")
                .document(userToUnblockId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Failed to unblock user in Firestore", e));

        // Restore chat if they're still friends
        checkFriendship(currentUserId, userToUnblockId, areFriends -> {
            if (areFriends) {
                String chatId = generateChatId(currentUserId, userToUnblockId);
                getUserChatsRef(currentUserId).child(chatId).setValue(true);
                Log.d(TAG, "Chat restored after unblocking");
            }
        });

        Log.d(TAG, "User " + userToUnblockId + " unblocked by " + currentUserId);
    }

    public static void checkIfBlocked(String currentUserId, String otherUserId, BlockStatusCallback callback) {
        if (currentUserId == null || otherUserId == null || callback == null) return;

        getRealtimeBlockedUsersRef(currentUserId).child(otherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        boolean isBlocked = dataSnapshot.exists();
                        callback.onResult(isBlocked);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to check block status", databaseError.toException());
                        callback.onResult(false);
                    }
                });
    }

    public static void getBlockedUsers(String currentUserId, BlockedUsersCallback callback) {
        if (currentUserId == null || callback == null) return;

        FirebaseFirestore.getInstance()
                .collection("blocked_users")
                .document(currentUserId)
                .collection("blocked")
                .orderBy("blockedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> callback.onBlockedUsersLoaded(querySnapshot))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load blocked users", e);
                    callback.onError(e.getMessage());
                });
    }

    public static void checkMutualBlocking(String userId1, String userId2, MutualBlockCallback callback) {
        if (userId1 == null || userId2 == null || callback == null) return;

        checkIfBlocked(userId1, userId2, user1BlockedUser2 -> {
            checkIfBlocked(userId2, userId1, user2BlockedUser1 -> {
                callback.onResult(user1BlockedUser2, user2BlockedUser1);
            });
        });
    }

    // ===== STATUS MANAGEMENT =====

    public static void updateUserStatus(String userId, String status, String imageUrl, long expiresAt) {
        if (userId == null) return;

        Map<String, Object> statusData = new HashMap<>();
        statusData.put("userId", userId);
        statusData.put("status", status);
        statusData.put("imageUrl", imageUrl);
        statusData.put("timestamp", System.currentTimeMillis());
        statusData.put("expiresAt", expiresAt);
        statusData.put("viewers", new HashMap<String, Long>());

        getStatusCollectionRef().add(statusData)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Status updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update status", e));
    }

    // ===== UTILITY METHODS =====

    public static void deleteChat(String chatId, String currentUserId) {
        if (chatId == null || currentUserId == null) return;

        // Remove from user's chat list
        getUserChatsRef(currentUserId).child(chatId).removeValue();

        // Check if both users have removed the chat, then delete the entire chat
        getChatRef(chatId).child("participants").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Implementation to check if chat should be completely deleted
                        // This would require checking if all participants have removed the chat
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to check chat participants for deletion", databaseError.toException());
                    }
                });
    }

    public static void clearChatHistoryForUser(String chatId, String userId) {
        if (chatId == null || userId == null) return;

        Log.d(TAG, "Clearing chat history for user: " + userId + " in chat: " + chatId);

        // Store when user cleared their chat
        getRealtimeDatabase()
                .child("cleared_chats")
                .child(chatId)
                .child(userId)
                .setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Chat history cleared for user: " + userId);

                    // Update chat last message to indicate it's cleared for this user
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", "");
                    updates.put("lastMessageTimestamp", System.currentTimeMillis());
                    updates.put("lastMessageSenderId", "");
                    updates.put("lastMessageType", "empty_chat");

                    getChatRef(chatId).updateChildren(updates);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clear chat history for user", e));
    }

    // For backward compatibility - this method now only clears for all users (admin use)
    public static void clearChatHistory(String chatId) {
        if (chatId == null) return;

        Log.d(TAG, "Clearing chat history for all users in chat: " + chatId);

        getMessagesRef(chatId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Chat history cleared successfully for all users");

                    // Update chat to reflect empty state
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", "");
                    updates.put("lastMessageTimestamp", System.currentTimeMillis());
                    updates.put("lastMessageSenderId", "");
                    updates.put("lastMessageType", "empty_chat");

                    getChatRef(chatId).updateChildren(updates);

                    // Clear the cleared_chats data since all messages are deleted
                    getRealtimeDatabase().child("cleared_chats").child(chatId).removeValue();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clear chat history", e));
    }

    public static DatabaseReference getClearedChatsRef(String chatId) {
        return getRealtimeDatabase().child("cleared_chats").child(chatId);
    }

    public static void getUserClearedTime(String chatId, String userId, ClearTimeCallback callback) {
        if (chatId == null || userId == null || callback == null) return;

        getClearedChatsRef(chatId).child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Long clearedAt = dataSnapshot.getValue(Long.class);
                        callback.onResult(clearedAt != null ? clearedAt : 0);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to get user cleared time", databaseError.toException());
                        callback.onResult(0);
                    }
                });
    }

    // ===== CHAT LIST MANAGEMENT =====

    public static void loadUserChatsWithDetails(String userId, ChatListCallback callback) {
        if (userId == null || callback == null) return;

        getUserChatsRef(userId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                callback.onChatsLoaded(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to load user chats", databaseError.toException());
                callback.onError(databaseError.getMessage());
            }
        });
    }

    public static void loadMessagesWithClearedCheck(String chatId, String userId, MessagesCallback callback) {
        if (chatId == null || userId == null || callback == null) return;

        // First get the user's cleared timestamp
        getUserClearedTime(chatId, userId, clearedAt -> {
            ValueEventListener messagesListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    List<Message> messages = new ArrayList<>();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        Message message = snapshot.getValue(Message.class);
                        if (message != null) {
                            message.setId(snapshot.getKey());

                            // Only include messages after cleared timestamp
                            if (clearedAt == 0 || message.getTimestamp() > clearedAt) {
                                messages.add(message);
                            }
                        }
                    }
                    callback.onMessagesLoaded(messages);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Failed to load messages", databaseError.toException());
                    callback.onError(databaseError.getMessage());
                }
            };

            if (clearedAt > 0) {
                // User has cleared chat, only load messages after cleared timestamp
                getMessagesRef(chatId)
                        .orderByChild("timestamp")
                        .startAfter(clearedAt)
                        .addListenerForSingleValueEvent(messagesListener);
            } else {
                // User hasn't cleared chat, load all messages
                getMessagesRef(chatId)
                        .orderByChild("timestamp")
                        .addListenerForSingleValueEvent(messagesListener);
            }
        });
    }



    public static void sendReplyMessage(String chatId, String senderId, String receiverId, String text, String replyToMessageId) {
        if (chatId == null || senderId == null || text == null || replyToMessageId == null) return;

        String messageId = getMessagesRef(chatId).push().getKey();
        if (messageId == null) return;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("type", Message.TYPE_TEXT);
        messageData.put("status", Message.STATUS_SENT);
        messageData.put("action", Message.ACTION_REPLY);
        messageData.put("replyToMessageId", replyToMessageId);
        messageData.put("isReply", true);

        getMessagesRef(chatId).child(messageId).setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Reply message sent successfully");
                    updateChatLastMessage(chatId, text, senderId, Message.TYPE_TEXT);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send reply message", e));
    }

    // ===== BROADCAST LIST METHODS =====

    private static final String RT_BROADCASTS = "broadcasts";
    private static final String RT_BROADCAST_MESSAGES = "broadcast_messages";

    public static DatabaseReference getBroadcastsRef() {
        return getRealtimeDatabase().child(RT_BROADCASTS);
    }

    public static DatabaseReference getBroadcastRef(String broadcastId) {
        return getBroadcastsRef().child(broadcastId);
    }

    public static DatabaseReference getBroadcastMessagesRef(String broadcastId) {
        return getRealtimeDatabase().child(RT_BROADCAST_MESSAGES).child(broadcastId);
    }

    public static void createBroadcastList(String name, String createdBy, List<String> memberIds, BroadcastCallback callback) {
        String broadcastId = getBroadcastsRef().push().getKey();
        if (broadcastId == null) {
            callback.onError("Failed to generate broadcast ID");
            return;
        }

        Map<String, Object> broadcastData = new HashMap<>();
        broadcastData.put("id", broadcastId);
        broadcastData.put("name", name);
        broadcastData.put("createdBy", createdBy);
        broadcastData.put("createdAt", System.currentTimeMillis());
        broadcastData.put("memberIds", memberIds);
        broadcastData.put("isActive", true);

        getBroadcastRef(broadcastId).setValue(broadcastData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Broadcast list created successfully");
                    callback.onSuccess(broadcastId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create broadcast list", e);
                    callback.onError("Failed to create broadcast list");
                });
    }

    public static void sendBroadcastMessage(String broadcastId, String senderId, String text, String messageType, Map<String, Object> mediaData) {
        String messageId = getBroadcastMessagesRef(broadcastId).push().getKey();
        if (messageId == null) return;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("type", messageType);
        messageData.put("status", Message.STATUS_SENT);
        messageData.put("action", Message.ACTION_NONE);

        if (mediaData != null) {
            messageData.putAll(mediaData);
        }

        getBroadcastMessagesRef(broadcastId).child(messageId).setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Broadcast message sent successfully");
                    updateBroadcastLastMessage(broadcastId, text, senderId, messageType);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send broadcast message", e));
    }

    private static void updateBroadcastLastMessage(String broadcastId, String message, String senderId, String messageType) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", message);
        updates.put("lastMessageTimestamp", System.currentTimeMillis());
        updates.put("lastMessageSenderId", senderId);
        updates.put("lastMessageType", messageType);

        getBroadcastRef(broadcastId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Broadcast last message updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update broadcast last message", e));
    }

    public static void loadUserBroadcasts(String userId, BroadcastListCallback callback) {
        getBroadcastsRef().orderByChild("createdBy").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<Broadcast> broadcasts = new ArrayList<>();
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Broadcast broadcast = snapshot.getValue(Broadcast.class);
                            if (broadcast != null && broadcast.isActive()) {
                                broadcasts.add(broadcast);
                            }
                        }
                        callback.onBroadcastsLoaded(broadcasts);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        callback.onError("Failed to load broadcasts");
                    }
                });
    }

    // ===== ENHANCED MESSAGE METHODS =====

    public static void forwardMessage(String targetChatId, String senderId, String receiverId, Message originalMessage) {
        if (targetChatId == null || senderId == null || originalMessage == null) return;

        String messageId = getMessagesRef(targetChatId).push().getKey();
        if (messageId == null) return;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("senderId", senderId);
        messageData.put("text", originalMessage.getText());
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("type", originalMessage.getType());
        messageData.put("status", Message.STATUS_SENT);
        messageData.put("action", Message.ACTION_FORWARD);
        messageData.put("originalMessageId", originalMessage.getId());
        messageData.put("isForwarded", true);

        // Add media data if present
        if (originalMessage.isImageMessage()) {
            messageData.put("imageUrl", originalMessage.getImageUrl());
        } else if (originalMessage.isVideoMessage()) {
            messageData.put("videoUrl", originalMessage.getVideoUrl());
            messageData.put("thumbnailUrl", originalMessage.getThumbnailUrl());
        } else if (originalMessage.isAudioMessage()) {
            messageData.put("audioUrl", originalMessage.getAudioUrl());
            messageData.put("duration", originalMessage.getDuration());
        } else if (originalMessage.isDocumentMessage()) {
            messageData.put("fileUrl", originalMessage.getFileUrl());
            messageData.put("fileName", originalMessage.getFileName());
            messageData.put("fileSize", originalMessage.getFileSize());
        }

        getMessagesRef(targetChatId).child(messageId).setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message forwarded successfully");
                    updateChatLastMessage(targetChatId, originalMessage.getDisplayText(), senderId, originalMessage.getType());
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to forward message", e));
    }

    public static void editMessage(String chatId, String messageId, String newText) {
        if (chatId == null || messageId == null || newText == null) return;

        DatabaseReference msgRef = getMessagesRef(chatId).child(messageId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("text", newText);
        updates.put("editedText", newText);
        updates.put("editTimestamp", System.currentTimeMillis());
        updates.put("isEdited", true);
        updates.put("action", Message.ACTION_EDIT);

        msgRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message edited successfully");
                    updateChatLastMessage(chatId, newText + " (edited)", msgRef.getKey(), Message.TYPE_TEXT);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to edit message", e));
    }

    public static void deleteMessageForUser(String chatId, String messageId, String userId) {
        if (chatId == null || messageId == null || userId == null) return;

        DatabaseReference msgRef = getMessagesRef(chatId).child(messageId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("deletedAt", System.currentTimeMillis());
        updates.put("deletedBy", userId);
        updates.put("isDeletedForMe", true);
        updates.put("action", Message.ACTION_DELETE_FOR_ME);

        msgRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Message deleted for user"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete message for user", e));
    }

    public static void deleteMessageForEveryone(String chatId, String messageId, String deletedBy) {
        if (chatId == null || messageId == null) return;

        DatabaseReference msgRef = getMessagesRef(chatId).child(messageId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("deletedAt", System.currentTimeMillis());
        updates.put("deletedBy", deletedBy);
        updates.put("isDeletedForEveryone", true);
        updates.put("action", Message.ACTION_DELETE_FOR_EVERYONE);

        msgRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message deleted for everyone");
                    updateChatLastMessage(chatId, "This message was deleted", deletedBy, Message.TYPE_TEXT);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete message for everyone", e));
    }

    // ===== SEARCH METHODS =====

    public static void searchMessages(String chatId, String query, long startDate, long endDate, MessageSearchCallback callback) {
        if (chatId == null || query == null) {
            callback.onError("Invalid search parameters");
            return;
        }

        getMessagesRef(chatId).orderByChild("timestamp")
                .startAt(startDate)
                .endAt(endDate)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<Message> results = new ArrayList<>();
                        String lowerQuery = query.toLowerCase();

                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Message message = snapshot.getValue(Message.class);
                            if (message != null && !message.isDeletedForEveryone()) {
                                // Search in text content
                                if (message.getText().toLowerCase().contains(lowerQuery)) {
                                    results.add(message);
                                }
                                // Search in file names
                                else if (message.getFileName() != null && 
                                         message.getFileName().toLowerCase().contains(lowerQuery)) {
                                    results.add(message);
                                }
                            }
                        }

                        // Sort by timestamp (newest first)
                        results.sort((m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));
                        callback.onMessagesFound(results);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        callback.onError("Search failed");
                    }
                });
    }

    public static void searchAllChats(String userId, String query, SearchCallback callback) {
        if (userId == null || query == null) {
            callback.onError("Invalid search parameters");
            return;
        }

        getUserChatsRef(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<SearchResult> results = new ArrayList<>();
                String lowerQuery = query.toLowerCase();

                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId != null) {
                        // Search in chat messages
                        searchMessagesInChat(chatId, lowerQuery, results);
                    }
                }

                callback.onSearchComplete(results);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onError("Search failed");
            }
        });
    }

    private static void searchMessagesInChat(String chatId, String query, List<SearchResult> results) {
        getMessagesRef(chatId).orderByChild("timestamp")
                .limitToLast(100) // Limit to recent messages for performance
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Message message = snapshot.getValue(Message.class);
                            if (message != null && !message.isDeletedForEveryone()) {
                                if (message.getText().toLowerCase().contains(query) ||
                                    (message.getFileName() != null && 
                                     message.getFileName().toLowerCase().contains(query))) {
                                    results.add(new SearchResult(chatId, message));
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // Continue with other chats
                    }
                });
    }

    // ===== FORGOT PASSWORD METHOD =====

    public static void sendPasswordResetEmail(String email, PasswordResetCallback callback) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Password reset email sent successfully");
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send password reset email", e);
                    callback.onError(e.getLocalizedMessage());
                });
    }

    // ===== CALLBACKS =====

    public interface FriendshipCallback {
        void onResult(boolean areFriends);
    }

    public interface UserCallback {
        void onResult(User user);
    }

    public interface BlockStatusCallback {
        void onResult(boolean isBlocked);
    }

    public interface BlockedUsersCallback {
        void onBlockedUsersLoaded(com.google.firebase.firestore.QuerySnapshot querySnapshot);
        void onError(String error);
    }

    public interface MutualBlockCallback {
        void onResult(boolean user1BlockedUser2, boolean user2BlockedUser1);
    }

    public interface ChatListCallback {
        void onChatsLoaded(DataSnapshot dataSnapshot);
        void onError(String error);
    }

    public interface ClearTimeCallback {
        void onResult(long clearedAt);
    }

    public interface MessagesCallback {
        void onMessagesLoaded(List<Message> messages);
        void onError(String error);
    }

    public interface BroadcastCallback {
        void onSuccess(String broadcastId);
        void onError(String error);
    }

    public interface BroadcastListCallback {
        void onBroadcastsLoaded(List<Broadcast> broadcasts);
        void onError(String error);
    }

    public interface MessageSearchCallback {
        void onMessagesFound(List<Message> messages);
        void onError(String error);
    }

    public interface SearchCallback {
        void onSearchComplete(List<SearchResult> results);
        void onError(String error);
    }

    public interface PasswordResetCallback {
        void onSuccess();
        void onError(String error);
    }

    // Search result class
    public static class SearchResult {
        private String chatId;
        private Message message;
        private String contactName;
        private String chatName;
        private String contactImageUrl;

        public SearchResult(String chatId, Message message) {
            this.chatId = chatId;
            this.message = message;
        }

        public String getChatId() { return chatId; }
        public Message getMessage() { return message; }
        
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
        
        public String getChatName() { return chatName; }
        public void setChatName(String chatName) { this.chatName = chatName; }
        
        public String getContactImageUrl() { return contactImageUrl; }
        public void setContactImageUrl(String contactImageUrl) { this.contactImageUrl = contactImageUrl; }
        
        public String getMessageText() { 
            return message != null ? message.getText() : null; 
        }
        
        public long getTimestamp() { 
            return message != null ? message.getTimestamp() : 0; 
        }
    }

    // ===== MESSAGE STATUS MANAGEMENT =====
    
    public static void updateMessageStatus(String chatId, String messageId, int status) {
        if (chatId == null || messageId == null) return;
        
        Log.d(TAG, "Updating message status: " + messageId + " to status: " + status);
        
        getMessagesRef(chatId)
                .child(messageId)
                .child("status")
                .setValue(status)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message status updated successfully");
                    // Update last message status in chat metadata
                    updateChatLastMessageStatus(chatId, messageId, status);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update message status", e));
    }
    
    private static void updateChatLastMessageStatus(String chatId, String messageId, int status) {
        getChatRef(chatId)
                .child("lastMessageId")
                .get()
                .addOnSuccessListener(snapshot -> {
                    String lastMessageId = snapshot.getValue(String.class);
                    if (messageId.equals(lastMessageId)) {
                        getChatRef(chatId)
                                .child("lastMessageStatus")
                                .setValue(status);
                    }
                });
    }
    
    public static void markMessageAsDelivered(String chatId, String messageId) {
        updateMessageStatus(chatId, messageId, Message.STATUS_DELIVERED);
    }
    
    public static void markMessageAsRead(String chatId, String messageId) {
        updateMessageStatus(chatId, messageId, Message.STATUS_READ);
    }
    
    public static void markAllMessagesAsRead(String chatId, String currentUserId) {
        if (chatId == null || currentUserId == null) return;

        Log.d(TAG, "Marking all messages as read for chat: " + chatId + " user: " + currentUserId);

        getMessagesRef(chatId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        int updatedCount = 0;

                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            Integer status = messageSnapshot.child("status").getValue(Integer.class);

                            // Mark as read if not sent by current user and not already read
                            if (senderId != null && !senderId.equals(currentUserId) &&
                                    status != null && status != Message.STATUS_READ) {
                                updates.put("messages/" + chatId + "/" + messageSnapshot.getKey() + "/status", Message.STATUS_READ);
                                updatedCount++;
                            }
                        }

                        if (!updates.isEmpty()) {
                            getRealtimeDatabase().updateChildren(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Marked " + updatedCount + " messages as read successfully");
                                        // Send read receipt to sender if they have read receipts enabled
                                        sendReadReceipts(chatId, currentUserId, updates);
                                    })
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark messages as read", e));
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to mark messages as read", databaseError.toException());
                    }
                });
    }
    
    private static void sendReadReceipts(String chatId, String currentUserId, Map<String, Object> updatedMessages) {
        // Get the other user in the chat
        getChatRef(chatId)
                .child("participants")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String otherUserId = null;
                        for (DataSnapshot participant : dataSnapshot.getChildren()) {
                            String participantId = participant.getKey();
                            if (!participantId.equals(currentUserId)) {
                                otherUserId = participantId;
                                break;
                            }
                        }
                        
                        if (otherUserId != null) {
                            // Check if other user has read receipts enabled
                            getUserRef(otherUserId)
                                    .get()
                                    .addOnSuccessListener(userSnapshot -> {
                                        if (userSnapshot.exists()) {
                                            User otherUser = userSnapshot.toObject(User.class);
                                            if (otherUser != null && otherUser.isReadReceiptsEnabled()) {
                                                // Send read receipt notification
                                                sendReadReceiptNotification(otherUserId, currentUserId, chatId, updatedMessages.size());
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to get chat participants for read receipts", databaseError.toException());
                    }
                });
    }
    
    private static void sendReadReceiptNotification(String recipientId, String senderId, String chatId, int messageCount) {
        // Create read receipt notification
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "read_receipt");
        notificationData.put("chatId", chatId);
        notificationData.put("senderId", senderId);
        notificationData.put("messageCount", messageCount);
        notificationData.put("timestamp", System.currentTimeMillis());
        
        // Store in notifications collection
        getNotificationsCollectionRef()
                .document(recipientId)
                .collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Read receipt notification sent successfully");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send read receipt notification", e));
    }
    
    // ===== MESSAGE DELIVERY SYSTEM =====
    
    public static void sendMessageWithDeliveryTracking(String chatId, String senderId, String text, String type, Map<String, Object> mediaData) {
        if (chatId == null || senderId == null) return;
        
        // Create message with initial status
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("type", type);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("status", Message.STATUS_SENT);
        
        if (mediaData != null) {
            messageData.putAll(mediaData);
        }
        
        // Generate message ID
        String messageId = getMessagesRef(chatId).push().getKey();
        messageData.put("id", messageId);
        
        // Send message
        getMessagesRef(chatId)
                .child(messageId)
                .setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message sent successfully: " + messageId);
                    
                    // Update chat metadata
                    updateChatLastMessage(chatId, text, senderId, type);
                    
                    // Send push notification to recipient
                    sendMessageNotification(chatId, senderId, text, type);
                    
                    // Start delivery tracking
                    startDeliveryTracking(chatId, messageId, senderId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send message", e);
                    // Update message status to failed
                    messageData.put("status", -1); // Failed status
                    getMessagesRef(chatId).child(messageId).setValue(messageData);
                });
    }
    
    private static void startDeliveryTracking(String chatId, String messageId, String senderId) {
        // Get recipient ID
        getChatRef(chatId)
                .child("participants")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String recipientId = null;
                        for (DataSnapshot participant : dataSnapshot.getChildren()) {
                            String participantId = participant.getKey();
                            if (!participantId.equals(senderId)) {
                                recipientId = participantId;
                                break;
                            }
                        }
                        
                        if (recipientId != null) {
                            // Check if recipient is online
                            getRealtimePresenceRef(recipientId)
                                    .child("online")
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot snapshot) {
                                            Boolean isOnline = snapshot.getValue(Boolean.class);
                                            if (isOnline != null && isOnline) {
                                                // Recipient is online, mark as delivered immediately
                                                markMessageAsDelivered(chatId, messageId);
                                            } else {
                                                // Recipient is offline, will be marked as delivered when they come online
                                                scheduleDeliveryCheck(chatId, messageId, recipientId);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {
                                            Log.e(TAG, "Failed to check recipient online status", databaseError.toException());
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to get chat participants for delivery tracking", databaseError.toException());
                    }
                });
    }
    
    private static void scheduleDeliveryCheck(String chatId, String messageId, String recipientId) {
        // Set up a listener for when recipient comes online
        getRealtimePresenceRef(recipientId)
                .child("online")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Boolean isOnline = snapshot.getValue(Boolean.class);
                        if (isOnline != null && isOnline) {
                            // Recipient came online, mark message as delivered
                            markMessageAsDelivered(chatId, messageId);
                            // Remove this listener
                            getRealtimePresenceRef(recipientId).child("online").removeEventListener(this);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to monitor recipient online status", databaseError.toException());
                    }
                });
    }
    
    private static void sendMessageNotification(String chatId, String senderId, String text, String type) {
        // Get recipient and sender info
        getChatRef(chatId)
                .child("participants")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String recipientId = null;
                        for (DataSnapshot participant : dataSnapshot.getChildren()) {
                            String participantId = participant.getKey();
                            if (!participantId.equals(senderId)) {
                                recipientId = participantId;
                                break;
                            }
                        }
                        
                        if (recipientId != null) {
                            // Get sender info for notification
                            getUserRef(senderId)
                                    .get()
                                    .addOnSuccessListener(senderSnapshot -> {
                                        if (senderSnapshot.exists()) {
                                            User sender = senderSnapshot.toObject(User.class);
                                            if (sender != null) {
                                                // Create notification data
                                                Map<String, Object> notificationData = new HashMap<>();
                                                notificationData.put("type", "new_message");
                                                notificationData.put("chatId", chatId);
                                                notificationData.put("senderId", senderId);
                                                notificationData.put("senderName", sender.getDisplayName());
                                                notificationData.put("message", text);
                                                notificationData.put("messageType", type);
                                                notificationData.put("timestamp", System.currentTimeMillis());
                                                
                                                // Store notification
                                                getNotificationsCollectionRef()
                                                        .document(recipientId)
                                                        .collection("notifications")
                                                        .add(notificationData)
                                                        .addOnSuccessListener(documentReference -> {
                                                            Log.d(TAG, "Message notification sent successfully");
                                                        })
                                                        .addOnFailureListener(e -> Log.e(TAG, "Failed to send message notification", e));
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to get chat participants for notification", databaseError.toException());
                    }
                });
    }
}