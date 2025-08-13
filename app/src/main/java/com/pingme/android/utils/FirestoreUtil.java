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

    public static CollectionReference getMessagesCollectionRef() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_MESSAGES);
    }

    public static CollectionReference getChatsCollectionRef() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_CHATS);
    }

    public static CollectionReference getNotificationsCollectionRef() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_NOTIFICATIONS);
    }

    public static CollectionReference getReportsCollectionRef() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_REPORTS);
    }

    // ===== USER MANAGEMENT =====

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
        userData.put("isOnline", user.isOnline());
        userData.put("lastSeen", user.getLastSeen());
        userData.put("fcmToken", user.getFcmToken());

        // Privacy settings
        userData.put("profile_photo_enabled", user.isProfilePhotoEnabled());
        userData.put("last_seen_enabled", user.isLastSeenEnabled());
        userData.put("about_enabled", user.isAboutEnabled());
        userData.put("read_receipts_enabled", user.isReadReceiptsEnabled());

        getUserRef(user.getId()).set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User created successfully: " + user.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create user: " + user.getId(), e));
    }

    public static void updateUser(User user) {
        if (user == null || user.getId() == null) return;

        Log.d(TAG, "Updating user: " + user.getId());

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", user.getName());
        userData.put("email", user.getEmail());
        userData.put("phoneNumber", user.getPhoneNumber());
        userData.put("imageUrl", user.getImageUrl());
        userData.put("about", user.getAbout());
        userData.put("fcmToken", user.getFcmToken());

        // Privacy settings
        userData.put("profile_photo_enabled", user.isProfilePhotoEnabled());
        userData.put("last_seen_enabled", user.isLastSeenEnabled());
        userData.put("about_enabled", user.isAboutEnabled());
        userData.put("read_receipts_enabled", user.isReadReceiptsEnabled());

        getUserRef(user.getId()).update(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User updated successfully: " + user.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update user: " + user.getId(), e));
    }

    // ===== FRIEND MANAGEMENT =====

    /**
     * Search user by email for friend requests
     */
    public static void searchUserByEmail(String email, UserSearchCallback callback) {
        if (email == null || callback == null) return;

        getUsersCollectionRef()
                .whereEqualTo("email", email.toLowerCase().trim())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        User user = task.getResult().getDocuments().get(0).toObject(User.class);
                        if (user != null) {
                            user.setId(task.getResult().getDocuments().get(0).getId());
                            callback.onUserFound(user);
                        } else {
                            callback.onUserNotFound();
                        }
                    } else {
                        callback.onUserNotFound();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Search failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /**
     * Add a friend
     */
    public static void addFriend(String currentUserId, User friendUser, FriendActionCallback callback) {
        if (currentUserId == null || friendUser == null || callback == null) return;

        Log.d(TAG, "Adding friend: " + friendUser.getId() + " for user: " + currentUserId);

        // Check if already blocked
        checkIfBlocked(currentUserId, friendUser.getId(), isBlocked -> {
            if (isBlocked) {
                callback.onError("Cannot add blocked user");
                return;
            }

            checkIfBlocked(friendUser.getId(), currentUserId, hasBlockedMe -> {
                if (hasBlockedMe) {
                    callback.onError("Cannot add this user");
                    return;
                }

                // Get current user data
                getUserRef(currentUserId).get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            currentUser.setId(currentUserId);
                            performAddFriend(currentUser, friendUser, callback);
                        } else {
                            callback.onError("Failed to get current user data");
                        }
                    } else {
                        callback.onError("Current user not found");
                    }
                }).addOnFailureListener(e -> callback.onError("Failed to get current user: " + e.getMessage()));
            });
        });
    }

    private static void performAddFriend(User currentUser, User friendUser, FriendActionCallback callback) {
        // Create friend data objects
        Map<String, Object> currentUserData = createFriendData(currentUser);
        Map<String, Object> friendUserData = createFriendData(friendUser);

        // Add to both users' friends lists
        getFriendsRef(currentUser.getId()).document(friendUser.getId()).set(friendUserData)
                .addOnSuccessListener(aVoid -> {
                    getFriendsRef(friendUser.getId()).document(currentUser.getId()).set(currentUserData)
                            .addOnSuccessListener(aVoid1 -> {
                                Log.d(TAG, "Friendship created successfully between " + currentUser.getId() + " and " + friendUser.getId());
                                createChatBetweenFriends(currentUser.getId(), friendUser.getId());
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> callback.onError("Failed to add to friend's list: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("Failed to add to your friends list: " + e.getMessage()));
    }

    private static Map<String, Object> createFriendData(User user) {
        Map<String, Object> friendData = new HashMap<>();
        friendData.put("name", user.getName());
        friendData.put("email", user.getEmail());
        friendData.put("imageUrl", user.getImageUrl());
        friendData.put("about", user.getAbout());
        friendData.put("addedAt", System.currentTimeMillis());
        return friendData;
    }

    /**
     * Remove a friend
     */
    public static void removeFriend(String currentUserId, String friendId, FriendActionCallback callback) {
        if (currentUserId == null || friendId == null || callback == null) return;

        Log.d(TAG, "Removing friend: " + friendId + " from user: " + currentUserId);

        getFriendsRef(currentUserId).document(friendId).delete()
                .addOnSuccessListener(aVoid -> {
                    getFriendsRef(friendId).document(currentUserId).delete()
                            .addOnSuccessListener(aVoid1 -> {
                                Log.d(TAG, "Friendship removed successfully");
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> callback.onError("Failed to remove from friend's list: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("Failed to remove from your friends list: " + e.getMessage()));
    }

    /**
     * Block a user
     */
    public static void blockUser(String currentUserId, String userToBlockId, FriendActionCallback callback) {
        if (currentUserId == null || userToBlockId == null || callback == null) return;

        Log.d(TAG, "Blocking user: " + userToBlockId + " by user: " + currentUserId);

        // Add to blocked users in Realtime Database
        Map<String, Object> updates = new HashMap<>();
        updates.put("blocked_users/" + currentUserId + "/" + userToBlockId, System.currentTimeMillis());

        // Hide active chats (don't delete, just mark as inactive)
        String chatId = generateChatId(currentUserId, userToBlockId);
        updates.put("user_chats/" + currentUserId + "/" + chatId, false);

        getRealtimeDatabase().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // Also add to Firestore blocked users collection
                    Map<String, Object> blockData = new HashMap<>();
                    blockData.put("blockedAt", System.currentTimeMillis());
                    blockData.put("blockedUserId", userToBlockId);

                    getBlockedUsersRef(currentUserId).document(userToBlockId).set(blockData)
                            .addOnSuccessListener(aVoid1 -> {
                                // Remove from friends list if they were friends
                                removeFriend(currentUserId, userToBlockId, new FriendActionCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "User blocked and removed from friends successfully");
                                        callback.onSuccess();
                                    }

                                    @Override
                                    public void onError(String error) {
                                        // Blocking succeeded but friend removal failed - still consider success
                                        Log.d(TAG, "User blocked successfully (friend removal failed: " + error + ")");
                                        callback.onSuccess();
                                    }
                                });
                            })
                            .addOnFailureListener(e -> callback.onError("Failed to block user in Firestore: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("Failed to block user: " + e.getMessage()));
    }

    /**
     * Unblock a user
     */
    public static void unblockUser(String currentUserId, String userToUnblockId, FriendActionCallback callback) {
        if (currentUserId == null || userToUnblockId == null || callback == null) return;

        Log.d(TAG, "Unblocking user: " + userToUnblockId + " by user: " + currentUserId);

        // Remove from Realtime Database
        getRealtimeBlockedUsersRef(currentUserId).child(userToUnblockId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Remove from Firestore
                    getBlockedUsersRef(currentUserId).document(userToUnblockId).delete()
                            .addOnSuccessListener(aVoid1 -> {
                                // Restore chat if they're still friends
                                checkFriendship(currentUserId, userToUnblockId, areFriends -> {
                                    if (areFriends) {
                                        String chatId = generateChatId(currentUserId, userToUnblockId);
                                        getUserChatsRef(currentUserId).child(chatId).setValue(true);
                                        Log.d(TAG, "Chat restored after unblocking");
                                    }
                                });

                                Log.d(TAG, "User unblocked successfully");
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> callback.onError("Failed to unblock user in Firestore: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("Failed to unblock user: " + e.getMessage()));
    }

    /**
     * Check if a user is blocked
     */
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

    /**
     * Check if users are friends
     */
    public static void checkFriendship(String userId1, String userId2, FriendshipStatusCallback callback) {
        if (userId1 == null || userId2 == null || callback == null) return;

        getFriendsRef(userId1).document(userId2).get()
                .addOnSuccessListener(documentSnapshot -> callback.onResult(documentSnapshot.exists()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check friendship status", e);
                    callback.onResult(false); // Default to not friends if check fails
                });
    }

    // ===== CHAT MANAGEMENT =====

    public static String generateChatId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }

    private static void createChatBetweenFriends(String userId1, String userId2) {
        String chatId = generateChatId(userId1, userId2);
        Log.d(TAG, "Creating chat between friends: " + chatId);

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("id", chatId);
        chatData.put("type", "direct");
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", System.currentTimeMillis());
        chatData.put("lastMessageSenderId", "");

        Map<String, Boolean> participants = new HashMap<>();
        participants.put(userId1, true);
        participants.put(userId2, true);
        chatData.put("participants", participants);

        getChatsCollectionRef().document(chatId).set(chatData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat created successfully: " + chatId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create chat: " + chatId, e));
    }

    // ===== REALTIME DATABASE METHODS (for presence and typing) =====

    public static DatabaseReference getRealtimeDatabase() {
        return FirebaseDatabase.getInstance().getReference();
    }

    public static DatabaseReference getRealtimePresenceRef(String userId) {
        return getRealtimeDatabase().child(RT_PRESENCE).child(userId);
    }

    public static DatabaseReference getTypingRef(String chatId) {
        return getRealtimeDatabase().child(RT_TYPING).child(chatId);
    }

    public static void setTyping(String chatId, String userId, boolean isTyping) {
        if (chatId == null || userId == null) return;
        getTypingRef(chatId).child(userId).setValue(isTyping);
    }

    public static void updatePresence(String userId, boolean isOnline) {
        if (userId == null) return;

        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("online", isOnline);
        presenceData.put("lastSeen", System.currentTimeMillis());

        getRealtimePresenceRef(userId).setValue(presenceData);
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

    // Keep existing methods for backward compatibility but update them to work with new structure
    
    // ===== DEPRECATED METHODS (for backward compatibility) =====
    
    @Deprecated
    public static void searchUserForFriendRequest(String email, UserSearchCallback callback) {
        searchUserByEmail(email, callback);
    }

    @Deprecated
    public static void createEmptyFriendChat(String currentUserId, String friendId) {
        createChatBetweenFriends(currentUserId, friendId);
    }

    public static CollectionReference getStatusCollectionRef() {
        return FirebaseFirestore.getInstance().collection("status");
    }

    @Deprecated
    public static CollectionReference getUserPublicCollectionRef() {
        // No longer needed with simplified structure
        return FirebaseFirestore.getInstance().collection("deprecated_user_public");
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

    // ===== CALLBACK INTERFACES FOR SEARCH =====

    public interface SearchCallback {
        void onSearchComplete(List<SearchResult> results);
        void onError(String error);
    }
}