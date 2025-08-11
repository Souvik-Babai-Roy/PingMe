package com.pingme.android.utils;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pingme.android.models.Broadcast;
import com.pingme.android.models.Chat;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreUtil {
    private static final String TAG = "FirestoreUtil";
    
    // Firestore Collections
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_STATUS = "status";
    private static final String COLLECTION_PRESENCE = "presence";
    private static final String COLLECTION_FRIENDS = "friends";
    
    // Realtime Database References
    private static final String RT_CHATS = "chats";
    private static final String RT_MESSAGES = "messages";
    private static final String RT_TYPING = "typing";
    private static final String RT_USER_CHATS = "user_chats";
    private static final String RT_BLOCKED_USERS = "blocked_users";
    private static final String RT_USER_SETTINGS = "user_settings";
    private static final String RT_BROADCASTS = "broadcasts";
    private static final String RT_BROADCAST_MESSAGES = "broadcast_messages";
    private static final String NODE_PRESENCE = "presence";

    // ===== FIRESTORE REFERENCE METHODS =====

    public static DocumentReference getUserRef(String userId) {
        return FirebaseFirestore.getInstance().collection(COLLECTION_USERS).document(userId);
    }

    public static CollectionReference getUsersCollectionRef() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_USERS);
    }

    public static CollectionReference getStatusCollectionRef() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_STATUS);
    }

    public static DocumentReference getPresenceRef(String userId) {
        return FirebaseFirestore.getInstance().collection(COLLECTION_PRESENCE).document(userId);
    }

    public static CollectionReference getFriendsRef(String userId) {
        return FirebaseFirestore.getInstance().collection(COLLECTION_FRIENDS);
    }

    // ===== REALTIME DATABASE REFERENCE METHODS =====

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

    // ===== BROADCAST REFERENCE METHODS =====

    public static DatabaseReference getBroadcastsRef() {
        return getRealtimeDatabase().child(RT_BROADCASTS);
    }

    public static DatabaseReference getBroadcastRef(String broadcastId) {
        return getBroadcastsRef().child(broadcastId);
    }

    public static DatabaseReference getBroadcastMessagesRef(String broadcastId) {
        return getRealtimeDatabase().child(RT_BROADCAST_MESSAGES).child(broadcastId);
    }

    // ===== CHAT MANAGEMENT METHODS =====

    public static String generateChatId(String userId1, String userId2) {
        // Sort user IDs to ensure consistent chat ID generation
        String[] userIds = {userId1, userId2};
        java.util.Arrays.sort(userIds);
        return userIds[0] + "_" + userIds[1];
    }

    public static void createNewChatInRealtime(String chatId, String user1Id, String user2Id) {
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("id", chatId);
        chatData.put("user1Id", user1Id);
        chatData.put("user2Id", user2Id);
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTime", 0L);
        chatData.put("lastMessageSender", "");

        getChatRef(chatId).setValue(chatData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Chat created successfully");
                    // Add chat to both users' chat lists
                    getUserChatsRef(user1Id).child(chatId).setValue(true);
                    getUserChatsRef(user2Id).child(chatId).setValue(true);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create chat", e));
    }

    public static void createEmptyFriendChat(String currentUserId, String friendId) {
        String chatId = generateChatId(currentUserId, friendId);
        
        getChatRef(chatId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                createNewChatInRealtime(chatId, currentUserId, friendId);
            }
        });
    }

    public static void updateChatLastMessage(String chatId, String message, String senderId, String messageType) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", message);
        updates.put("lastMessageTime", System.currentTimeMillis());
        updates.put("lastMessageSender", senderId);
        updates.put("lastMessageType", messageType);

        getChatRef(chatId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat last message updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update chat last message", e));
    }

    // ===== MESSAGE METHODS =====

    public static void sendMessageToRealtime(String chatId, String senderId, String text,
                                           String messageType, Map<String, Object> mediaData) {
        getChatRef(chatId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                sendMessageInternal(chatId, senderId, text, messageType, mediaData);
            } else {
                Log.e(TAG, "Chat does not exist");
            }
        });
    }

    private static void sendMessageInternal(String chatId, String senderId, String text,
                                          String messageType, Map<String, Object> mediaData) {
        String messageId = getMessagesRef(chatId).push().getKey();
        if (messageId == null) return;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("type", messageType);
        messageData.put("status", Message.STATUS_SENT);

        if (mediaData != null) {
            messageData.putAll(mediaData);
        }

        getMessagesRef(chatId).child(messageId).setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message sent successfully");
                    updateChatLastMessage(chatId, text, senderId, messageType);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send message", e));
    }

    public static void sendTextMessage(String chatId, String senderId, String receiverId, String text, boolean createIfNeeded) {
        if (createIfNeeded) {
            createEmptyFriendChat(senderId, receiverId);
        }
        sendMessageToRealtime(chatId, senderId, text, Message.TYPE_TEXT, null);
    }

    // ===== PRESENCE METHODS =====

    public static void updateUserPresence(String userId, boolean isOnline) {
        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("online", isOnline);
        presenceData.put("lastSeen", System.currentTimeMillis());

        getRealtimePresenceRef(userId).setValue(presenceData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User presence updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update user presence", e));
    }

    public static void updateFCMToken(String userId, String token) {
        getUserRef(userId).update("fcmToken", token)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token", e));
    }

    // ===== MESSAGE STATUS METHODS =====

    public static void markMessagesAsRead(String chatId, String currentUserId) {
        getMessagesRef(chatId).orderByChild("senderId").equalTo(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Message message = snapshot.getValue(Message.class);
                    if (message != null && message.getStatus() == Message.STATUS_DELIVERED) {
                        snapshot.getRef().child("status").setValue(Message.STATUS_READ);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to mark messages as read", databaseError.toException());
            }
        });
    }

    // ===== USER SEARCH METHODS =====

    public static Query searchUserByEmail(String email) {
        return getUsersCollectionRef().whereEqualTo("email", email);
    }

    public static Query searchUserByPhoneNumber(String phoneNumber) {
        return getUsersCollectionRef().whereEqualTo("phoneNumber", phoneNumber);
    }

    // ===== FRIENDSHIP METHODS =====

    public static void checkFriendship(String currentUserId, String otherUserId, FriendshipCallback callback) {
        getFriendsRef(currentUserId).document(otherUserId).get()
                .addOnSuccessListener(documentSnapshot -> callback.onResult(documentSnapshot.exists()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check friendship", e);
                    callback.onResult(false);
                });
    }

    public static void addFriend(String currentUserId, String friendId, User currentUser, User friendUser) {
        // Add to current user's friends
        getFriendsRef(currentUserId).document(friendId).set(friendUser)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Friend added to current user");
                    // Add current user to friend's friends list
                    getFriendsRef(friendId).document(currentUserId).set(currentUser)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "Current user added to friend's list");
                                createEmptyFriendChat(currentUserId, friendId);
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to add current user to friend's list", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to add friend", e));
    }

    public static void removeFriend(String currentUserId, String friendId) {
        // Remove from current user's friends
        getFriendsRef(currentUserId).document(friendId).delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Friend removed from current user");
                    // Remove current user from friend's friends list
                    getFriendsRef(friendId).document(currentUserId).delete()
                            .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Current user removed from friend's list"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to remove current user from friend's list", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to remove friend", e));
    }

    // ===== TYPING INDICATOR METHODS =====

    public static void setTyping(String chatId, String userId, boolean isTyping) {
        getTypingIndicatorRef(chatId, userId).setValue(isTyping)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Typing indicator updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update typing indicator", e));
    }

    public static DatabaseReference getTypingIndicatorRef(String chatId, String userId) {
        return getTypingRef(chatId).child(userId);
    }

    // ===== BLOCKING METHODS =====

    public static void blockUser(String currentUserId, String userToBlockId) {
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("blockedAt", System.currentTimeMillis());
        blockData.put("blockedBy", currentUserId);

        getBlockedUsersRef(currentUserId).child(userToBlockId).setValue(blockData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User blocked successfully");
                    // Remove from friends if they were friends
                    removeFriend(currentUserId, userToBlockId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to block user", e));
    }

    public static void unblockUser(String currentUserId, String userToUnblockId) {
        getBlockedUsersRef(currentUserId).child(userToUnblockId).removeValue()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User unblocked successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to unblock user", e));
    }

    public static void checkIfBlocked(String currentUserId, String otherUserId, BlockStatusCallback callback) {
        getBlockedUsersRef(currentUserId).child(otherUserId).get()
                .addOnSuccessListener(dataSnapshot -> callback.onResult(dataSnapshot.exists()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check block status", e);
                    callback.onResult(false);
                });
    }

    public static void getBlockedUsers(String currentUserId, BlockedUsersCallback callback) {
        getBlockedUsersRef(currentUserId).get()
                .addOnSuccessListener(dataSnapshot -> {
                    // Convert to Firestore QuerySnapshot format for compatibility
                    callback.onBlockedUsersLoaded(null); // This needs to be implemented properly
                })
                .addOnFailureListener(e -> callback.onError("Failed to load blocked users"));
    }

    public static void checkMutualBlocking(String userId1, String userId2, MutualBlockCallback callback) {
        checkIfBlocked(userId1, userId2, new BlockStatusCallback() {
            @Override
            public void onResult(boolean user1BlockedUser2) {
                checkIfBlocked(userId2, userId1, new BlockStatusCallback() {
                    @Override
                    public void onResult(boolean user2BlockedUser1) {
                        callback.onResult(user1BlockedUser2, user2BlockedUser1);
                    }
                });
            }
        });
    }

    // ===== STATUS METHODS =====

    public static void updateUserStatus(String userId, String status, String imageUrl, long expiresAt) {
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("text", status);
        statusData.put("imageUrl", imageUrl);
        statusData.put("createdAt", System.currentTimeMillis());
        statusData.put("expiresAt", expiresAt);

        getStatusCollectionRef().document(userId).set(statusData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Status updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update status", e));
    }

    // ===== CHAT MANAGEMENT METHODS =====

    public static void deleteChat(String chatId, String currentUserId) {
        getUserChatsRef(currentUserId).child(chatId).removeValue()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat deleted for user"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete chat", e));
    }

    public static void clearChatHistoryForUser(String chatId, String userId) {
        getClearedChatsRef(chatId).child(userId).setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat history cleared for user"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clear chat history", e));
    }

    public static void clearChatHistory(String chatId) {
        getMessagesRef(chatId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Chat history cleared");
                    // Update chat's last message
                    updateChatLastMessage(chatId, "", "", "");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clear chat history", e));
    }

    public static DatabaseReference getClearedChatsRef(String chatId) {
        return getRealtimeDatabase().child("cleared_chats").child(chatId);
    }

    public static void getUserClearedTime(String chatId, String userId, ClearTimeCallback callback) {
        getClearedChatsRef(chatId).child(userId).get()
                .addOnSuccessListener(dataSnapshot -> {
                    long clearedAt = dataSnapshot.exists() ? dataSnapshot.getValue(Long.class) : 0;
                    callback.onResult(clearedAt);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get cleared time", e);
                    callback.onResult(0);
                });
    }

    // ===== CHAT LOADING METHODS =====

    public static void loadUserChatsWithDetails(String userId, ChatListCallback callback) {
        getUserChatsRef(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                callback.onChatsLoaded(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onError("Failed to load chats");
            }
        });
    }

    public static void loadMessagesWithClearedCheck(String chatId, String userId, MessagesCallback callback) {
        getUserClearedTime(chatId, userId, new ClearTimeCallback() {
            @Override
            public void onResult(long clearedAt) {
                ValueEventListener messagesListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<Message> messages = new ArrayList<>();
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Message message = snapshot.getValue(Message.class);
                            if (message != null && message.getTimestamp() > clearedAt) {
                                message.setId(snapshot.getKey());
                                messages.add(message);
                            }
                        }
                        callback.onMessagesLoaded(messages);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        callback.onError("Failed to load messages");
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
            }
        });
    }

    // ===== ENHANCED MESSAGE ACTION METHODS =====

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

    // ===== BROADCAST METHODS =====

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
        updates.put("lastMessageSender", senderId);
        updates.put("lastMessageType", messageType);

        getBroadcastRef(broadcastId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Broadcast last message updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update broadcast last message", e));
    }

    public static void loadUserBroadcasts(String userId, BroadcastListCallback callback) {
        getBroadcastsRef().orderByChild("createdBy").equalTo(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Broadcast> broadcasts = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Broadcast broadcast = snapshot.getValue(Broadcast.class);
                    if (broadcast != null && broadcast.isActive()) {
                        broadcast.setId(snapshot.getKey());
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
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String chatId = snapshot.getKey();
                    searchMessagesInChat(chatId, query, results);
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
        getMessagesRef(chatId).orderByChild("text").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String lowerQuery = query.toLowerCase();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Message message = snapshot.getValue(Message.class);
                    if (message != null && message.getText().toLowerCase().contains(lowerQuery)) {
                        results.add(new SearchResult(chatId, message));
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to search messages in chat", databaseError.toException());
            }
        });
    }

    // ===== PASSWORD RESET METHOD =====

    public static void sendPasswordResetEmail(String email, PasswordResetCallback callback) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onError(task.getException() != null ? task.getException().getMessage() : "Failed to send reset email");
                    }
                });
    }

    // ===== CALLBACK INTERFACES =====

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

    // ===== SEARCH RESULT CLASS =====

    public static class SearchResult {
        private String chatId;
        private Message message;

        public SearchResult(String chatId, Message message) {
            this.chatId = chatId;
            this.message = message;
        }

        public String getChatId() { return chatId; }
        public Message getMessage() { return message; }
    }
}