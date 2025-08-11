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

    public static DatabaseReference getBlockedUsersRef(String userId) {
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
        getBlockedUsersRef(currentUserId).child(userToUnblockId).removeValue()
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

        getBlockedUsersRef(currentUserId).child(otherUserId)
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
                .addOnSuccessListener(callback::onBlockedUsersLoaded)
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

    public static void editMessage(String chatId, String messageId, String newText) {
        if (chatId == null || messageId == null || newText == null) return;

        DatabaseReference msgRef = getMessagesRef(chatId).child(messageId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("text", newText);
        updates.put("edited", true);
        updates.put("editedAt", System.currentTimeMillis());

        msgRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Message edited successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to edit message", e));
    }

    public static void deleteMessageForUser(String chatId, String messageId, String userId) {
        if (chatId == null || messageId == null || userId == null) return;

        DatabaseReference msgRef = getMessagesRef(chatId).child(messageId).child("deletedFor");
        msgRef.child(userId).setValue(true)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Message deleted for user"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete message for user", e));
    }

    public static void deleteMessageForEveryone(String chatId, String messageId) {
        if (chatId == null || messageId == null) return;

        DatabaseReference msgRef = getMessagesRef(chatId).child(messageId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("deletedForEveryone", true);
        updates.put("deletedAt", System.currentTimeMillis());

        msgRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Message deleted for everyone"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete message for everyone", e));
    }

    public static void sendReplyMessage(String chatId, String senderId, String receiverId, String text, String replyToMessageId) {
        if (chatId == null || senderId == null || text == null || replyToMessageId == null) return;

        String messageId = getMessagesRef(chatId).push().getKey();
        if (messageId == null) return;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("type", "text");
        messageData.put("status", Message.STATUS_SENT);
        messageData.put("replyTo", replyToMessageId);

        getMessagesRef(chatId).child(messageId).setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Reply message sent successfully");
                    updateChatLastMessage(chatId, text, senderId, "text");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send reply message", e));
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
}