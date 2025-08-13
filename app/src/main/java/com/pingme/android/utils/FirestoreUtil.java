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
    private static final String COLLECTION_MESSAGES = "messages";
    private static final String COLLECTION_CHATS = "chats";
    private static final String COLLECTION_NOTIFICATIONS = "notifications";
    private static final String COLLECTION_REPORTS = "reports";

    // Realtime Database references for real-time features
    private static final String RT_PRESENCE = "presence";
    private static final String RT_TYPING = "typing";

    // ===== COLLECTION REFERENCES =====

    public static CollectionReference getUsersCollectionRef() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_USERS);
    }

    public static DocumentReference getUserRef(String userId) {
        return getUsersCollectionRef().document(userId);
    }

    public static CollectionReference getFriendsRef(String userId) {
        return getUserRef(userId).collection("friends");
    }

    public static CollectionReference getBlockedUsersRef(String userId) {
        return getUserRef(userId).collection("blocked");
    }

    public static CollectionReference getUserSettingsRef(String userId) {
        return getUserRef(userId).collection("settings");
    }

    public static CollectionReference getStatusCollectionRef() {
        return FirebaseFirestore.getInstance().collection("status");
    }

    // ===== REALTIME DATABASE REFERENCES =====

    private static DatabaseReference getRealtimeDatabase() {
        return FirebaseDatabase.getInstance().getReference();
    }

    public static DatabaseReference getUserChatsRef(String userId) {
        return getRealtimeDatabase().child("user_chats").child(userId);
    }

    public static DatabaseReference getChatRef(String chatId) {
        return getRealtimeDatabase().child("chats").child(chatId);
    }

    public static DatabaseReference getChatsRef() {
        return getRealtimeDatabase().child("chats");
    }

    public static DatabaseReference getMessagesRef(String chatId) {
        return getRealtimeDatabase().child("messages").child(chatId);
    }

    public static DatabaseReference getBroadcastRef(String broadcastId) {
        return getRealtimeDatabase().child("broadcasts").child(broadcastId);
    }

    public static DatabaseReference getPresenceRef(String userId) {
        return getRealtimeDatabase().child(RT_PRESENCE).child(userId);
    }

    public static DatabaseReference getTypingRef(String chatId) {
        return getRealtimeDatabase().child(RT_TYPING).child(chatId);
    }

    // ===== USER MANAGEMENT =====

    public static void createUser(User user) {
        getUserRef(user.getId()).set(user)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User created successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create user", e));
    }

    public static void createUserWithDiscoverableProfile(User user) {
        createUser(user);
        // Additional profile setup if needed
    }

    public static void updateUser(User user) {
        getUserRef(user.getId()).set(user)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update user", e));
    }

    // ===== SEARCH FUNCTIONALITY =====

    public static void searchUserByEmail(String email, UserSearchCallback callback) {
        getUsersCollectionRef()
                .whereEqualTo("email", email.toLowerCase())
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        User user = querySnapshot.getDocuments().get(0).toObject(User.class);
                        if (user != null) {
                            callback.onUserFound(user);
                        } else {
                            callback.onUserNotFound();
                        }
                    } else {
                        callback.onUserNotFound();
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ===== FRIEND MANAGEMENT =====

    public static void addFriend(String currentUserId, String friendId, FriendActionCallback callback) {
        Map<String, Object> friendData = new HashMap<>();
        friendData.put("userId", friendId);
        friendData.put("addedAt", System.currentTimeMillis());

        getFriendsRef(currentUserId).document(friendId).set(friendData)
                .addOnSuccessListener(aVoid -> {
                    // Add reverse friendship
                    Map<String, Object> reverseFriendData = new HashMap<>();
                    reverseFriendData.put("userId", currentUserId);
                    reverseFriendData.put("addedAt", System.currentTimeMillis());
                    
                    getFriendsRef(friendId).document(currentUserId).set(reverseFriendData)
                            .addOnSuccessListener(aVoid2 -> {
                                // Create chat between friends
                                String chatId = generateChatId(currentUserId, friendId);
                                createNewChatInRealtime(chatId, currentUserId, friendId);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public static void removeFriend(String currentUserId, String friendId, FriendActionCallback callback) {
        getFriendsRef(currentUserId).document(friendId).delete()
                .addOnSuccessListener(aVoid -> {
                    getFriendsRef(friendId).document(currentUserId).delete()
                            .addOnSuccessListener(aVoid2 -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public static void blockUser(String currentUserId, String blockedUserId, FriendActionCallback callback) {
        // First update Realtime Database
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("blockedAt", System.currentTimeMillis());
        
        getRealtimeBlockedUsersRef(currentUserId).child(blockedUserId).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    // Update chat to inactive
                    String chatId = generateChatId(currentUserId, blockedUserId);
                    getUserChatsRef(currentUserId).child(chatId).child("isActive").setValue(false);
                    getUserChatsRef(blockedUserId).child(chatId).child("isActive").setValue(false);
                    
                    // Update Firestore
                    getBlockedUsersRef(currentUserId).document(blockedUserId).set(blockData)
                            .addOnSuccessListener(aVoid2 -> {
                                // Remove friendship
                                removeFriend(currentUserId, blockedUserId, callback);
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public static void unblockUser(String currentUserId, String unblockedUserId, FriendActionCallback callback) {
        // Remove from Realtime Database
        getRealtimeBlockedUsersRef(currentUserId).child(unblockedUserId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Remove from Firestore
                    getBlockedUsersRef(currentUserId).document(unblockedUserId).delete()
                            .addOnSuccessListener(aVoid2 -> {
                                // Check if they are still friends and restore chat if needed
                                checkFriendship(currentUserId, unblockedUserId, areFriends -> {
                                    if (areFriends) {
                                        String chatId = generateChatId(currentUserId, unblockedUserId);
                                        getUserChatsRef(currentUserId).child(chatId).child("isActive").setValue(true);
                                        getUserChatsRef(unblockedUserId).child(chatId).child("isActive").setValue(true);
                                    }
                                    callback.onSuccess();
                                });
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
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

    public static void checkFriendship(String currentUserId, String otherUserId, FriendshipStatusCallback callback) {
        getFriendsRef(currentUserId).document(otherUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> callback.onResult(documentSnapshot.exists()))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    public static void getBlockedUsers(String userId, BlockedUsersCallback callback) {
        getBlockedUsersRef(userId).get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> blockedUserIds = new ArrayList<>();
                    querySnapshot.forEach(doc -> blockedUserIds.add(doc.getId()));
                    
                    if (blockedUserIds.isEmpty()) {
                        callback.onBlockedUsersLoaded(new ArrayList<>());
                        return;
                    }
                    
                    // Load user details
                    List<User> blockedUsers = new ArrayList<>();
                    for (String blockedId : blockedUserIds) {
                        getUserRef(blockedId).get()
                                .addOnSuccessListener(userDoc -> {
                                    User user = userDoc.toObject(User.class);
                                    if (user != null) {
                                        blockedUsers.add(user);
                                    }
                                    
                                    if (blockedUsers.size() == blockedUserIds.size()) {
                                        callback.onBlockedUsersLoaded(blockedUsers);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ===== MESSAGING =====

    public static void sendMessageWithDeliveryTracking(String chatId, String senderId, String text, String type, Map<String, Object> mediaData) {
        Message message = new Message();
        message.setId(getMessagesRef(chatId).push().getKey());
        message.setSenderId(senderId);
        message.setText(text);
        message.setType(type);
        message.setTimestamp(System.currentTimeMillis());
        message.setStatus(Message.STATUS_SENT);
        
        if (mediaData != null) {
            if (mediaData.containsKey("imageUrl")) message.setImageUrl((String) mediaData.get("imageUrl"));
            if (mediaData.containsKey("videoUrl")) message.setVideoUrl((String) mediaData.get("videoUrl"));
            if (mediaData.containsKey("audioUrl")) message.setAudioUrl((String) mediaData.get("audioUrl"));
            if (mediaData.containsKey("fileUrl")) message.setFileUrl((String) mediaData.get("fileUrl"));
            if (mediaData.containsKey("fileName")) message.setFileName((String) mediaData.get("fileName"));
            if (mediaData.containsKey("fileSize")) message.setFileSize((Long) mediaData.get("fileSize"));
            if (mediaData.containsKey("duration")) message.setDuration((Long) mediaData.get("duration"));
        }
        
        sendMessageToRealtime(chatId, senderId, text, type, mediaData);
    }

    public static void sendMessageToRealtime(String chatId, String senderId, String text, String type, Map<String, Object> mediaData) {
        String messageId = getMessagesRef(chatId).push().getKey();
        if (messageId == null) return;
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("type", type);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("status", Message.STATUS_SENT);
        
        if (mediaData != null) {
            messageData.putAll(mediaData);
        }
        
        // Add message to chat
        getMessagesRef(chatId).child(messageId).setValue(messageData);
        
        // Update chat's last message
        Map<String, Object> chatUpdate = new HashMap<>();
        chatUpdate.put("lastMessage", text);
        chatUpdate.put("lastMessageTimestamp", System.currentTimeMillis());
        chatUpdate.put("lastMessageSenderId", senderId);
        chatUpdate.put("lastMessageType", type);
        
        getChatRef(chatId).updateChildren(chatUpdate);
    }

    public static void editMessage(String chatId, String messageId, String newText) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("text", newText);
        updates.put("isEdited", true);
        updates.put("editTimestamp", System.currentTimeMillis());
        
        getMessagesRef(chatId).child(messageId).updateChildren(updates);
    }

    public static void deleteMessageForUser(String chatId, String messageId, String userId) {
        getMessagesRef(chatId).child(messageId).child("deletedFor").child(userId).setValue(true);
    }

    public static void deleteMessageForEveryone(String chatId, String messageId, String userId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("text", "This message was deleted");
        updates.put("isDeleted", true);
        updates.put("deletedBy", userId);
        updates.put("deletedAt", System.currentTimeMillis());
        
        getMessagesRef(chatId).child(messageId).updateChildren(updates);
    }

    public static void markAllMessagesAsRead(String chatId, String userId) {
        getMessagesRef(chatId).orderByChild("status")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            if (!userId.equals(senderId)) {
                                updates.put(messageSnapshot.getKey() + "/status", Message.STATUS_READ);
                                updates.put(messageSnapshot.getKey() + "/readAt", System.currentTimeMillis());
                            }
                        }
                        
                        if (!updates.isEmpty()) {
                            getMessagesRef(chatId).updateChildren(updates);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to mark messages as read", databaseError.toException());
                    }
                });
    }

    public static void clearChatHistory(String chatId) {
        getMessagesRef(chatId).removeValue()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat history cleared: " + chatId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clear chat history: " + chatId, e));
    }

    // ===== PRESENCE & TYPING =====

    public static void updatePresence(String userId, boolean isOnline) {
        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("isOnline", isOnline);
        presenceData.put("lastSeen", System.currentTimeMillis());
        
        getPresenceRef(userId).setValue(presenceData);
    }

    public static void updateUserPresence(String userId, boolean isOnline) {
        updatePresence(userId, isOnline);
    }

    public static void setTyping(String chatId, String userId, boolean isTyping) {
        if (isTyping) {
            getTypingRef(chatId).child(userId).setValue(System.currentTimeMillis());
        } else {
            getTypingRef(chatId).child(userId).removeValue();
        }
    }

    // ===== BROADCAST FUNCTIONALITY =====

    public static void createBroadcastList(String name, String creatorId, List<String> memberIds, BroadcastCallback callback) {
        String broadcastId = getBroadcastRef("").push().getKey();
        if (broadcastId == null) {
            callback.onError("Failed to generate broadcast ID");
            return;
        }
        
        Map<String, Object> broadcastData = new HashMap<>();
        broadcastData.put("id", broadcastId);
        broadcastData.put("name", name);
        broadcastData.put("creatorId", creatorId);
        broadcastData.put("members", memberIds);
        broadcastData.put("createdAt", System.currentTimeMillis());
        
        getBroadcastRef(broadcastId).setValue(broadcastData)
                .addOnSuccessListener(aVoid -> {
                    Broadcast broadcast = new Broadcast();
                    broadcast.setId(broadcastId);
                    broadcast.setName(name);
                    broadcast.setCreatorId(creatorId);
                    callback.onBroadcastCreated(broadcast);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public static void loadUserBroadcasts(String userId, BroadcastListCallback callback) {
        getRealtimeDatabase().child("user_broadcasts").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<Broadcast> broadcasts = new ArrayList<>();
                        for (DataSnapshot broadcastSnapshot : dataSnapshot.getChildren()) {
                            Broadcast broadcast = broadcastSnapshot.getValue(Broadcast.class);
                            if (broadcast != null) {
                                broadcasts.add(broadcast);
                            }
                        }
                        callback.onBroadcastsLoaded(broadcasts);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        callback.onError(databaseError.getMessage());
                    }
                });
    }

    // ===== FCM TOKEN MANAGEMENT =====

    public static void updateFCMToken(String userId, String token) {
        getUserRef(userId).update("fcmToken", token)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token", e));
    }

    // ===== UTILITY METHODS =====

    public static String generateChatId(String userId1, String userId2) {
        return userId1.compareTo(userId2) < 0 ? userId1 + "_" + userId2 : userId2 + "_" + userId1;
    }

    public static DatabaseReference getRealtimeBlockedUsersRef(String userId) {
        return getRealtimeDatabase().child("blocked_users").child(userId);
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

    // ===== SEARCH FUNCTIONALITY =====

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
                            Object messageObj = snapshot.getValue();
                            if (messageObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> messageData = (Map<String, Object>) messageObj;
                                String text = (String) messageData.get("text");
                                String fileName = (String) messageData.get("fileName");
                                
                                if ((text != null && text.toLowerCase().contains(query)) ||
                                    (fileName != null && fileName.toLowerCase().contains(query))) {
                                    
                                    // Create a simple message object for search results
                                    Message message = new Message();
                                    message.setId(snapshot.getKey());
                                    message.setText(text != null ? text : "");
                                    message.setFileName(fileName);
                                    Object timestamp = messageData.get("timestamp");
                                    if (timestamp instanceof Long) {
                                        message.setTimestamp((Long) timestamp);
                                    }
                                    
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

    // ===== SEARCH RESULT CLASS =====

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

    // ===== CALLBACK INTERFACES =====

    public interface UserSearchCallback {
        void onUserFound(User user);
        void onUserNotFound();
        void onError(String error);
    }

    public interface FriendActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface BlockStatusCallback {
        void onResult(boolean isBlocked);
    }

    public interface FriendshipStatusCallback {
        void onResult(boolean areFriends);
    }

    public interface BlockedUsersCallback {
        void onBlockedUsersLoaded(List<User> blockedUsers);
        void onError(String error);
    }

    public interface BroadcastCallback {
        void onBroadcastCreated(Broadcast broadcast);
        void onError(String error);
    }

    public interface BroadcastListCallback {
        void onBroadcastsLoaded(List<Broadcast> broadcasts);
        void onError(String error);
    }

    public interface SearchCallback {
        void onSearchComplete(List<SearchResult> results);
        void onError(String error);
    }
}