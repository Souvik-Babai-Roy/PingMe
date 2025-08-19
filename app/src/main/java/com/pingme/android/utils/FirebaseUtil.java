package com.pingme.android.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pingme.android.models.Broadcast;
import com.pingme.android.models.ChatHistory;
import com.pingme.android.models.ChatManagement;
import com.pingme.android.models.Message;
import com.pingme.android.models.Status;
import com.pingme.android.models.User;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class FirebaseUtil {
    private static final String TAG = "FirebaseUtil";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_NOTIFICATIONS = "notifications";
    private static final String COLLECTION_REPORTS = "reports";

    // Realtime Database references for real-time features
    private static final String RT_PRESENCE = "presence";
    private static final String RT_TYPING = "typing";

    // ===== FIRESTORE INSTANCE =====
    
    public static FirebaseFirestore getFirestoreInstance() {
        return FirebaseFirestore.getInstance();
    }

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
        return FirebaseFirestore.getInstance().collection("statuses");
    }

    public static CollectionReference getStatusesRef(String userId) {
        return FirebaseFirestore.getInstance().collection("users").document(userId).collection("statuses");
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

    public static DatabaseReference getRealtimePresenceRef(String userId) {
        return getPresenceRef(userId);
    }

    // Additional methods for compatibility
    public static DatabaseReference getUsersRef() {
        return getRealtimeDatabase().child("users");
    }

    public static DatabaseReference getChatMessagesRef(String chatId) {
        return getMessagesRef(chatId);
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
        Log.d(TAG, "Searching for user with email: " + email);
        
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email cannot be empty");
            return;
        }

        // Normalize email to lowercase
        String normalizedEmail = email.toLowerCase().trim();
        
        getUsersCollectionRef()
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Search completed. Found " + querySnapshot.size() + " users");
                    
                    if (!querySnapshot.isEmpty()) {
                        User user = querySnapshot.getDocuments().get(0).toObject(User.class);
                        if (user != null) {
                            user.setId(querySnapshot.getDocuments().get(0).getId());
                            Log.d(TAG, "User found: " + user.getDisplayName() + " (" + user.getEmail() + ")");
                            callback.onUserFound(user);
                        } else {
                            Log.w(TAG, "User document exists but failed to parse");
                            callback.onUserNotFound();
                        }
                    } else {
                        Log.d(TAG, "No user found with email: " + normalizedEmail);
                        callback.onUserNotFound();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Search failed for email: " + normalizedEmail, e);
                    callback.onError("Search failed: " + e.getMessage());
                });
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

    // Overloaded method for backward compatibility
    public static void addFriend(String currentUserId, User friendUser, FriendActionCallback callback) {
        addFriend(currentUserId, friendUser.getId(), callback);
    }

    public static void removeFriend(String currentUserId, String friendId, FriendActionCallback callback) {
        if (currentUserId == null || friendId == null) {
            if (callback != null) callback.onError("Invalid user IDs");
            return;
        }
        
        getFriendsRef(currentUserId).document(friendId).delete()
                .addOnSuccessListener(aVoid -> {
                    getFriendsRef(friendId).document(currentUserId).delete()
                            .addOnSuccessListener(aVoid2 -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onError(e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    public static void blockUser(String currentUserId, String blockedUserId, FriendActionCallback callback) {
        if (currentUserId == null || blockedUserId == null) {
            if (callback != null) callback.onError("Invalid user IDs");
            return;
        }
        
        // First update Realtime Database
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("blockedAt", System.currentTimeMillis());
        
        getRealtimeBlockedUsersRef(currentUserId).child(blockedUserId).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    try {
                        // Update chat to inactive
                        String chatId = generateChatId(currentUserId, blockedUserId);
                        getUserChatsRef(currentUserId).child(chatId).child("isActive").setValue(false);
                        getUserChatsRef(blockedUserId).child(chatId).child("isActive").setValue(false);
                        
                        // Update Firestore
                        getBlockedUsersRef(currentUserId).document(blockedUserId).set(blockData)
                                .addOnSuccessListener(aVoid2 -> {
                                    // Remove friendship (with safer callback handling)
                                    removeFriend(currentUserId, blockedUserId, new FriendActionCallback() {
                                        @Override
                                        public void onSuccess() {
                                            if (callback != null) callback.onSuccess();
                                        }

                                        @Override
                                        public void onError(String error) {
                                            // Even if unfriend fails, blocking succeeded
                                            Log.w(TAG, "Failed to remove friendship after blocking: " + error);
                                            if (callback != null) callback.onSuccess();
                                        }
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    if (callback != null) callback.onError(e.getMessage());
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in blockUser", e);
                        if (callback != null) callback.onError("Blocking failed: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
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

    public static void checkFriendship(String currentUserId, String friendId, FriendshipStatusCallback callback) {
        getFriendsRef(currentUserId).document(friendId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean areFriends = documentSnapshot.exists();
                    callback.onResult(areFriends);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check friendship", e);
                    callback.onResult(false);
                });
    }

    public static void checkIfFriends(String currentUserId, String friendId, FriendshipStatusCallback callback) {
        checkFriendship(currentUserId, friendId, callback);
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

    // Overloaded methods without callbacks for backward compatibility
    public static void blockUser(String currentUserId, String blockedUserId) {
        blockUser(currentUserId, blockedUserId, new FriendActionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "User blocked successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to block user: " + error);
            }
        });
    }

    public static void unblockUser(String currentUserId, String unblockedUserId) {
        unblockUser(currentUserId, unblockedUserId, new FriendActionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "User unblocked successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to unblock user: " + error);
            }
        });
    }

    public static void removeFriend(String currentUserId, String friendId) {
        removeFriend(currentUserId, friendId, new FriendActionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Friend removed successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to remove friend: " + error);
            }
        });
    }

    // ===== MESSAGING =====

    public static com.google.android.gms.tasks.Task<Void> sendMessageWithDeliveryTracking(String chatId, String senderId, String text, String type, Map<String, Object> mediaData) {
        if (chatId == null || senderId == null || text == null) {
            Log.e(TAG, "Invalid parameters for sending message");
            return com.google.android.gms.tasks.Tasks.forException(new IllegalArgumentException("Invalid parameters"));
        }
        
        Log.d(TAG, "Sending message to chat: " + chatId + " from: " + senderId + " text: " + text);
        
        com.google.android.gms.tasks.TaskCompletionSource<Void> taskCompletionSource = new com.google.android.gms.tasks.TaskCompletionSource<>();
        
        // First ensure chat exists, if not create it
        getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "Chat doesn't exist, creating it first");
                    // Get the other user ID from chat ID
                    String[] userIds = chatId.split("_");
                    if (userIds.length == 2) {
                        String otherUserId = userIds[0].equals(senderId) ? userIds[1] : userIds[0];
                        createNewChatInRealtime(chatId, senderId, otherUserId);
                        // Wait a moment then send message
                        new android.os.Handler().postDelayed(() -> {
                            sendMessageToRealtime(chatId, senderId, text, type, mediaData, taskCompletionSource);
                        }, 1000);
                    } else {
                        Log.e(TAG, "Invalid chat ID format: " + chatId);
                        taskCompletionSource.setException(new IllegalArgumentException("Invalid chat ID format"));
                    }
                } else {
                    Log.d(TAG, "Chat exists, sending message");
                    sendMessageToRealtime(chatId, senderId, text, type, mediaData, taskCompletionSource);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to check chat existence", databaseError.toException());
                taskCompletionSource.setException(databaseError.toException());
            }
        });
        
        return taskCompletionSource.getTask();
    }

    public static void sendMessageToRealtime(String chatId, String senderId, String text, String type, Map<String, Object> mediaData, com.google.android.gms.tasks.TaskCompletionSource<Void> taskCompletionSource) {
        Log.d(TAG, "=== STARTING MESSAGE SEND ===");
        Log.d(TAG, "Chat ID: " + chatId);
        Log.d(TAG, "Sender ID: " + senderId);
        Log.d(TAG, "Text: " + text);
        
        DatabaseReference messagesRef = getMessagesRef(chatId);
        String messageId = messagesRef.push().getKey();
        
        if (messageId == null) {
            Log.e(TAG, "Failed to generate message ID");
            taskCompletionSource.setException(new RuntimeException("Failed to generate message ID"));
            return;
        }
        
        Log.d(TAG, "Generated message ID: " + messageId);
        Log.d(TAG, "Messages ref path: " + messagesRef.toString());
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("senderId", senderId);
        messageData.put("text", text);
        messageData.put("type", type != null ? type : "text");
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("status", Message.STATUS_SENT); // Start with sent status
        
        // Add delivery tracking
        messageData.put("deliveredTo", new HashMap<String, Long>());
        messageData.put("readBy", new HashMap<String, Long>());
        
        if (mediaData != null) {
            messageData.putAll(mediaData);
        }
        
        Log.d(TAG, "Message data: " + messageData.toString());
        
        // Add message to chat
        messagesRef.child(messageId).setValue(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ MESSAGE SENT SUCCESSFULLY: " + messageId);
                    
                    // Update chat's last message
                    Map<String, Object> chatUpdate = new HashMap<>();
                    chatUpdate.put("lastMessage", text);
                    chatUpdate.put("lastMessageTimestamp", System.currentTimeMillis());
                    chatUpdate.put("lastMessageSenderId", senderId);
                    chatUpdate.put("lastMessageType", type != null ? type : "text");
                    chatUpdate.put("lastMessageId", messageId);
                    
                    Log.d(TAG, "Updating chat with: " + chatUpdate.toString());
                    
                    getChatRef(chatId).updateChildren(chatUpdate)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "✅ CHAT UPDATED SUCCESSFULLY");
                                // Trigger delivery notification to other user
                                triggerDeliveryNotification(chatId, messageId, senderId);
                                
                                // Increment receiver's per-user unread count (WhatsApp-like)
                                try {
                                    String[] userIds = chatId.split("_");
                                    if (userIds.length == 2) {
                                        String receiverId = userIds[0].equals(senderId) ? userIds[1] : userIds[0];
                                        incrementUserChatUnreadCount(receiverId, chatId, 1);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not increment user unread count", e);
                                }

                                // Send push notification to recipient
                                sendPushNotification(chatId, senderId, text, type);
                                
                                taskCompletionSource.setResult(null);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ FAILED TO UPDATE CHAT: " + e.getMessage(), e);
                                taskCompletionSource.setException(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ FAILED TO SEND MESSAGE: " + e.getMessage(), e);
                    taskCompletionSource.setException(e);
                });
    }

    /**
     * Atomically increment a user's unread count for a chat. Caps at 999.
     */
    public static void incrementUserChatUnreadCount(String userId, String chatId, int delta) {
        if (userId == null || chatId == null) return;
        DatabaseReference ref = getUserChatsRef(userId).child(chatId).child("unreadCount");
        ref.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                Integer current = currentData.getValue(Integer.class);
                int next = (current == null ? 0 : current) + (delta == 0 ? 1 : delta);
                if (next < 0) next = 0;
                if (next > 999) next = 999;
                currentData.setValue(next);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (error != null) {
                    Log.w(TAG, "incrementUserChatUnreadCount failed", error.toException());
                } else {
                    Log.d(TAG, "User " + userId + " unreadCount for chat " + chatId + " updated to " + (currentData != null ? currentData.getValue() : "?") );
                }
            }
        });
    }

    /**
     * Set a user's unread count for a chat explicitly (e.g., when chat opened).
     */
    public static void setUserChatUnreadCount(String userId, String chatId, int count) {
        if (userId == null || chatId == null) return;
        int safe = Math.max(0, Math.min(999, count));
        getUserChatsRef(userId).child(chatId).child("unreadCount").setValue(safe)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Set user unreadCount for " + userId + "/" + chatId + " to " + safe))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to set user unreadCount", e));
    }

    private static void sendPushNotification(String chatId, String senderId, String messageText, String messageType) {
        // Get the other user ID from chat ID
        String[] userIds = chatId.split("_");
        if (userIds.length != 2) {
            Log.e(TAG, "Invalid chat ID format for notification: " + chatId);
            return;
        }
        
        String receiverId = userIds[0].equals(senderId) ? userIds[1] : userIds[0];
        Log.d(TAG, "Sending notification - senderId: " + senderId + ", receiverId: " + receiverId);
        
        // FIRST: Check if recipient is online before proceeding
        getPresenceRef(receiverId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot presenceSnapshot) {
                boolean receiverOnline = presenceSnapshot.exists() && 
                    safeBooleanValue(presenceSnapshot.child("isOnline").getValue());
                
                Log.d(TAG, "Receiver " + receiverId + " online status: " + receiverOnline);
                
                // Get sender's name for notification
                getUserRef(senderId).get().addOnSuccessListener(senderSnapshot -> {
                    if (senderSnapshot.exists()) {
                        User sender = senderSnapshot.toObject(User.class);
                        String senderName = sender != null ? sender.getDisplayName() : "Unknown";
                        
                        // Get receiver's info to check if they should receive notifications
                        getUserRef(receiverId).get().addOnSuccessListener(receiverSnapshot -> {
                            if (receiverSnapshot.exists()) {
                                User receiver = receiverSnapshot.toObject(User.class);
                                
                                // Check if receiver has notifications enabled and has FCM token
                                if (receiver != null && receiver.isNotificationsEnabled() && receiver.getFcmToken() != null) {
                                    Log.d(TAG, "Sending FCM notification to receiver: " + receiverId + " with token: " + receiver.getFcmToken());
                                    
                                    // Send FCM notification to receiver's device
                                    String notificationText = messageType.equals(Message.TYPE_TEXT) ? 
                                        messageText : getMediaNotificationText(messageType);
                                    
                                    // Send push notification via FCM
                                    sendFCMNotification(receiver.getFcmToken(), senderName, notificationText, chatId, senderId, receiverId);
                                    
                                    // CRITICAL FIX: Only mark as delivered if receiver is actually online
                                    if (receiverOnline) {
                                        markLatestMessageAsDelivered(chatId, receiverId);
                                        Log.d(TAG, "✅ Message marked as delivered (receiver is online)");
                                    } else {
                                        Log.d(TAG, "⏳ Message stays as SENT (receiver is offline) - will be delivered when they come online");
                                    }
                                    
                                    // Also show local notification if the receiver is the current user (shouldn't happen in normal flow)
                                    android.content.Context context = getApplicationContext();
                                    if (context != null) {
                                        // Only show local notification if this is running on receiver's device
                                        FirebaseAuth auth = FirebaseAuth.getInstance();
                                        if (auth.getCurrentUser() != null && auth.getCurrentUser().getUid().equals(receiverId)) {
                                            com.pingme.android.utils.NotificationUtil.showMessageNotification(
                                                context, senderName, notificationText, chatId, senderId
                                            );
                                        }
                                    }
                                } else {
                                    Log.d(TAG, "Receiver " + receiverId + " has notifications disabled or no FCM token");
                                    
                                    // Still check if receiver is online for delivery status (even without notifications)
                                    if (receiverOnline) {
                                        markLatestMessageAsDelivered(chatId, receiverId);
                                        Log.d(TAG, "✅ Message marked as delivered (receiver is online, no notifications)");
                                    } else {
                                        Log.d(TAG, "⏳ Message stays as SENT (receiver is offline)");
                                    }
                                }
                            }
                        });
                    }
                });
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to check receiver presence for notification", databaseError.toException());
            }
        });
    }

    private static String getMediaNotificationText(String messageType) {
        switch (messageType) {
            case Message.TYPE_IMAGE:
                return "📷 Image";
            case Message.TYPE_VIDEO:
                return "🎥 Video";
            case Message.TYPE_AUDIO:
                return "🎤 Audio";
            case Message.TYPE_DOCUMENT:
                return "📄 Document";
            default:
                return "New message";
        }
    }

    // Send FCM notification to specific device using Firebase Realtime Database trigger
    private static void sendFCMNotification(String fcmToken, String senderName, String messageText, String chatId, String senderId, String receiverId) {
        try {
            // Create notification data
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("type", "new_message");
            notificationData.put("chatId", chatId);
            notificationData.put("senderId", senderId);
            notificationData.put("receiverId", receiverId);
            notificationData.put("message", messageText);
            notificationData.put("senderName", senderName);
            notificationData.put("messageType", "text");
            notificationData.put("fcmToken", fcmToken);
            notificationData.put("timestamp", System.currentTimeMillis());
            notificationData.put("delivered", false);
            
            Log.d(TAG, "FCM notification data prepared for token: " + fcmToken + ", data: " + notificationData.toString());
            
            // Store notification in Firebase Realtime Database for the recipient
            // This will trigger the FCM service on the recipient's device
            String notificationId = getRealtimeDatabase().child("notifications").child(receiverId).push().getKey();
            if (notificationId != null) {
                getRealtimeDatabase().child("notifications").child(receiverId).child(notificationId)
                    .setValue(notificationData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Notification queued for recipient: " + receiverId);
                        
                        // Auto-remove notification after 24 hours to prevent database bloat
                        getRealtimeDatabase().child("notifications").child(receiverId).child(notificationId)
                            .child("expiry").setValue(System.currentTimeMillis() + (24 * 60 * 60 * 1000));
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to queue notification", e));
            }
            
            // Also try direct FCM if we have a server endpoint (for future implementation)
            // For now, we rely on the notification queue system above
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send FCM notification", e);
        }
    }

    // Mark the latest message in a chat as delivered to a specific user
    private static void markLatestMessageAsDelivered(String chatId, String receiverId) {
        getMessagesRef(chatId)
                .orderByChild("timestamp")
                .limitToLast(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                            String messageId = messageSnapshot.getKey();
                            if (messageId != null) {
                                // Update the message delivery status
                                Map<String, Object> deliveryUpdate = new HashMap<>();
                                deliveryUpdate.put("deliveredTo/" + receiverId, System.currentTimeMillis());
                                deliveryUpdate.put("status", Message.STATUS_DELIVERED);
                                
                                getMessagesRef(chatId).child(messageId).updateChildren(deliveryUpdate)
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Latest message marked as delivered: " + messageId))
                                        .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to mark latest message as delivered", e));
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to get latest message for delivery update", databaseError.toException());
                    }
                });
    }

    // Public method for FCM service to mark messages as delivered
    public static void markLatestMessageAsDeliveredForUser(String chatId, String receiverId) {
        markLatestMessageAsDelivered(chatId, receiverId);
    }
    
    // Mark a specific message as delivered (more accurate than marking latest)
    public static void markSpecificMessageAsDelivered(String chatId, String messageId, String receiverId) {
        if (chatId == null || messageId == null || receiverId == null) {
            Log.w(TAG, "Cannot mark message as delivered - missing parameters");
            return;
        }
        
        Log.d(TAG, "Marking specific message as delivered: " + messageId + " for user: " + receiverId);
        
        Map<String, Object> deliveryUpdate = new HashMap<>();
        deliveryUpdate.put("deliveredTo/" + receiverId, System.currentTimeMillis());
        deliveryUpdate.put("status", Message.STATUS_DELIVERED);
        
        getMessagesRef(chatId).child(messageId).updateChildren(deliveryUpdate)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Message marked as delivered: " + messageId))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to mark message as delivered: " + messageId, e));
    }

    private static android.content.Context getApplicationContext() {
        // This is a workaround to get application context
        // In a real implementation, you might want to pass context as parameter
        try {
            return com.pingme.android.App.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get application context", e);
            return null;
        }
    }

    // Enhanced method to trigger delivery notification - WhatsApp-like behavior
    private static void triggerDeliveryNotification(String chatId, String messageId, String senderId) {
        // Get the other user in the chat
        String[] userIds = chatId.split("_");
        if (userIds.length == 2) {
            String receiverId = userIds[0].equals(senderId) ? userIds[1] : userIds[0];
            
            // Check if the recipient is currently online before marking as delivered
            getPresenceRef(receiverId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Use utility method to safely convert online status
                        boolean isOnline = safeBooleanValue(dataSnapshot.child("isOnline").getValue());
                        
                        if (isOnline) {
                            // Recipient is online, mark as delivered immediately
                            Map<String, Object> deliveryUpdate = new HashMap<>();
                            deliveryUpdate.put("deliveredTo/" + receiverId, System.currentTimeMillis());
                            deliveryUpdate.put("status", Message.STATUS_DELIVERED);
                            
                            getMessagesRef(chatId).child(messageId).updateChildren(deliveryUpdate)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ DELIVERY NOTIFICATION SENT (recipient online): " + messageId))
                                    .addOnFailureListener(e -> Log.e(TAG, "❌ FAILED TO SEND DELIVERY NOTIFICATION", e));
                        } else {
                            Log.d(TAG, "Recipient offline - message " + messageId + " stays as SENT until they come online");
                        }
                    } else {
                        Log.d(TAG, "No presence data for recipient, message " + messageId + " stays as SENT");
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Failed to check recipient online status for message: " + messageId, databaseError.toException());
                }
            });
        }
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

    // Enhanced method to mark messages as read (respects privacy settings)
    public static void markAllMessagesAsRead(String chatId, String userId) {
        Log.d(TAG, "Marking messages as read for user: " + userId + " in chat: " + chatId);
        
        // First check if user has read receipts enabled
        getUserRef(userId).get().addOnSuccessListener(userSnapshot -> {
            if (userSnapshot.exists()) {
                User user = userSnapshot.toObject(User.class);
                boolean readReceiptsEnabled = user != null && user.isReadReceiptsEnabled();
                
                getMessagesRef(chatId).orderByChild("senderId")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                Map<String, Object> updates = new HashMap<>();
                                boolean hasUpdates = false;
                                
                                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                                    String senderId = messageSnapshot.child("senderId").getValue(String.class);
                                    
                                    // Only mark messages if they're from other users
                                    if (senderId != null && !userId.equals(senderId)) {
                                        String messageId = messageSnapshot.getKey();
                                        if (messageId != null) {
                                            if (readReceiptsEnabled) {
                                                // Mark as read (blue tick) - user can see read status
                                                updates.put(messageId + "/readBy/" + userId, System.currentTimeMillis());
                                                updates.put(messageId + "/status", Message.STATUS_READ);
                                            } else {
                                                // Only mark as delivered (double tick) - user can't see read status
                                                updates.put(messageId + "/deliveredTo/" + userId, System.currentTimeMillis());
                                                updates.put(messageId + "/status", Message.STATUS_DELIVERED);
                                            }
                                            hasUpdates = true;
                                            Log.d(TAG, "Marking message " + messageId + " as " + (readReceiptsEnabled ? "read" : "delivered"));
                                        }
                                    }
                                }
                                
                                if (hasUpdates) {
                                    getMessagesRef(chatId).updateChildren(updates)
                                            .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ MESSAGES MARKED AS " + (readReceiptsEnabled ? "READ" : "DELIVERED")))
                                            .addOnFailureListener(e -> Log.e(TAG, "❌ FAILED TO MARK MESSAGES", e));
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                Log.e(TAG, "Failed to mark messages", databaseError.toException());
                            }
                        });
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to check user read receipts setting", e);
        });
    }

    // New method to mark a specific message as read
    public static void markMessageAsRead(String chatId, String messageId, String userId) {
        // First get the message to find the sender
        getMessagesRef(chatId).child(messageId).get().addOnSuccessListener(messageSnapshot -> {
            if (messageSnapshot.exists()) {
                String senderId = messageSnapshot.child("senderId").getValue(String.class);
                if (senderId != null) {
                    // Check if RECEIVER has read receipts enabled (receiver controls if they can see read status)
                    getUserRef(userId).get().addOnSuccessListener(receiverSnapshot -> {
                        if (receiverSnapshot.exists()) {
                            User receiver = receiverSnapshot.toObject(User.class);
                            if (receiver != null && receiver.isReadReceiptsEnabled()) {
                                // Receiver allows read receipts, mark as read
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("readBy/" + userId, System.currentTimeMillis());
                                updates.put("status", Message.STATUS_READ);
                                
                                getMessagesRef(chatId).child(messageId).updateChildren(updates)
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ MESSAGE MARKED AS READ: " + messageId))
                                        .addOnFailureListener(e -> Log.e(TAG, "❌ FAILED TO MARK MESSAGE AS READ", e));
                            } else {
                                // Receiver has read receipts disabled, only mark as delivered
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("deliveredTo/" + userId, System.currentTimeMillis());
                                updates.put("status", Message.STATUS_DELIVERED);
                                
                                getMessagesRef(chatId).child(messageId).updateChildren(updates)
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ MESSAGE MARKED AS DELIVERED: " + messageId))
                                        .addOnFailureListener(e -> Log.e(TAG, "❌ FAILED TO MARK MESSAGE AS DELIVERED", e));
                            }
                        }
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get receiver info for read receipts", e);
                        // Fallback: mark as delivered (safer default)
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("deliveredTo/" + userId, System.currentTimeMillis());
                        updates.put("status", Message.STATUS_DELIVERED);
                        
                        getMessagesRef(chatId).child(messageId).updateChildren(updates);
                    });
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get message for read receipts check", e);
        });
    }

    // New method to mark a specific message as delivered
    public static void markMessageAsDelivered(String chatId, String messageId, String userId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveredTo/" + userId, System.currentTimeMillis());
        updates.put("status", Message.STATUS_DELIVERED);
        
        getMessagesRef(chatId).child(messageId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ MESSAGE MARKED AS DELIVERED: " + messageId))
                .addOnFailureListener(e -> Log.e(TAG, "❌ FAILED TO MARK MESSAGE AS DELIVERED", e));
    }

    // Enhanced method to update message status
    public static void updateMessageStatus(String chatId, String messageId, int status) {
        getMessagesRef(chatId).child(messageId).child("status").setValue(status)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ MESSAGE STATUS UPDATED: " + status))
                .addOnFailureListener(e -> Log.e(TAG, "❌ FAILED TO UPDATE MESSAGE STATUS", e));
    }

    public static void clearChatHistory(String chatId) {
        getMessagesRef(chatId).removeValue()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat history cleared: " + chatId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clear chat history: " + chatId, e));
    }

    // ===== PRESENCE & TYPING =====

    // Enhanced presence system with automatic disconnect detection
    public static void updatePresence(String userId, boolean isOnline) {
        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("isOnline", isOnline);
        presenceData.put("lastSeen", System.currentTimeMillis());
        
        DatabaseReference presenceRef = getPresenceRef(userId);
        
        if (isOnline) {
            // Set user as online
            presenceRef.setValue(presenceData);
            
            // Set up automatic offline detection using Firebase's onDisconnect
            Map<String, Object> offlineData = new HashMap<>();
            offlineData.put("isOnline", false);
            offlineData.put("lastSeen", System.currentTimeMillis());
            presenceRef.onDisconnect().updateChildren(offlineData);
            
            // When user comes online, mark pending messages as delivered
            markPendingMessagesAsDelivered(userId);
            
            Log.d(TAG, "User " + userId + " set to ONLINE with auto-disconnect setup");
        } else {
            // Set user as offline
            presenceRef.setValue(presenceData);
            Log.d(TAG, "User " + userId + " set to OFFLINE");
        }
    }
    
    // Mark all pending (sent but not delivered) messages as delivered when user comes online - WhatsApp-like behavior
    private static void markPendingMessagesAsDelivered(String userId) {
        Log.d(TAG, "Marking pending messages as delivered for user coming online: " + userId);
        
        // Get all chats where this user is a participant
        getUserChatsRef(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId != null) {
                        // Check messages in this chat that are sent but not delivered to this user
                        getMessagesRef(chatId).orderByChild("timestamp")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot messagesSnapshot) {
                                        Map<String, Object> batchUpdates = new HashMap<>();
                                        int pendingCount = 0;
                                        
                                        for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                                            String messageId = messageSnapshot.getKey();
                                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                                            Integer status = messageSnapshot.child("status").getValue(Integer.class);
                                            
                                            // Only process messages:
                                            // 1. That have a valid message ID and sender
                                            // 2. That were not sent by this user (only mark received messages as delivered)
                                            // 3. That are currently in SENT status (not already delivered/read)
                                            // 4. That haven't been delivered to this user yet
                                            if (messageId != null && senderId != null && !senderId.equals(userId) &&
                                                status != null && status == Message.STATUS_SENT) {
                                                
                                                DataSnapshot deliveredToSnapshot = messageSnapshot.child("deliveredTo").child(userId);
                                                if (!deliveredToSnapshot.exists()) {
                                                    // This message is pending delivery - mark as delivered
                                                    batchUpdates.put(messageId + "/deliveredTo/" + userId, System.currentTimeMillis());
                                                    batchUpdates.put(messageId + "/status", Message.STATUS_DELIVERED);
                                                    pendingCount++;
                                                    Log.d(TAG, "Marking message " + messageId + " as delivered for user " + userId);
                                                }
                                            }
                                        }
                                        
                                        // Apply all updates in a single batch operation for efficiency
                                        if (!batchUpdates.isEmpty()) {
                                            int finalPendingCount = pendingCount;
                                            getMessagesRef(chatId).updateChildren(batchUpdates)
                                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Marked " + finalPendingCount + " pending messages as delivered in chat " + chatId))
                                                    .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to mark pending messages as delivered in chat " + chatId, e));
                                        }
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {
                                        Log.e(TAG, "Failed to check pending messages in chat " + chatId, databaseError.toException());
                                    }
                                });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to get user chats for pending message delivery", databaseError.toException());
            }
        });
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
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "FCM token updated");
                    // Start listening for queued notifications when token is updated
                    startNotificationListener(userId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token", e));
    }
    
    // Start listening for queued notifications
    public static void startNotificationListener(String userId) {
        if (userId == null) {
            Log.w(TAG, "Cannot start notification listener - userId is null");
            return;
        }
        
        Log.d(TAG, "Starting notification listener for user: " + userId);
        
        getRealtimeDatabase().child("notifications").child(userId)
            .addChildEventListener(new com.google.firebase.database.ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull com.google.firebase.database.DataSnapshot snapshot, String previousChildName) {
                    handleQueuedNotification(snapshot, userId);
                }

                @Override
                public void onChildChanged(@NonNull com.google.firebase.database.DataSnapshot snapshot, String previousChildName) {
                    // Handle notification updates if needed
                    handleQueuedNotification(snapshot, userId);
                }

                @Override
                public void onChildRemoved(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    Log.d(TAG, "Notification removed: " + snapshot.getKey());
                }

                @Override
                public void onChildMoved(@NonNull com.google.firebase.database.DataSnapshot snapshot, String previousChildName) {
                    // Not used
                }

                @Override
                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    Log.e(TAG, "Notification listener cancelled", error.toException());
                }
            });
    }
    
    // Handle queued notification from Firebase Realtime Database
    private static void handleQueuedNotification(com.google.firebase.database.DataSnapshot snapshot, String currentUserId) {
        try {
            String notificationId = snapshot.getKey();
            
            if (!snapshot.exists() || notificationId == null) {
                return;
            }
            
            // Safely extract data from snapshot
            String type = snapshot.child("type").getValue(String.class);
            Boolean delivered = snapshot.child("delivered").getValue(Boolean.class);
            Long timestamp = snapshot.child("timestamp").getValue(Long.class);
            String chatId = snapshot.child("chatId").getValue(String.class);
            String senderId = snapshot.child("senderId").getValue(String.class);
            String receiverId = snapshot.child("receiverId").getValue(String.class);
            String message = snapshot.child("message").getValue(String.class);
            String senderName = snapshot.child("senderName").getValue(String.class);
            String messageType = snapshot.child("messageType").getValue(String.class);
            
            Log.d(TAG, "Processing queued notification: " + notificationId + ", type: " + type + ", delivered: " + delivered);
            
            // Check if notification is not already delivered and not expired
            if (!"new_message".equals(type) || Boolean.TRUE.equals(delivered)) {
                Log.d(TAG, "Skipping notification - already delivered or wrong type");
                return;
            }
            
            // Check expiry (24 hours)
            if (timestamp != null && (System.currentTimeMillis() - timestamp) > (24 * 60 * 60 * 1000)) {
                Log.d(TAG, "Removing expired notification: " + notificationId);
                snapshot.getRef().removeValue();
                return;
            }
            
            // Verify this notification is for the current user
            if (!currentUserId.equals(receiverId)) {
                Log.d(TAG, "Notification not for current user, ignoring");
                return;
            }
            
            Log.d(TAG, "Processing notification from: " + senderName + " - " + message);
            
            // Check if device is online and show notification
            android.content.Context context = getApplicationContext();
            if (context != null && isDeviceOnline(context)) {
                // Show notification
                String notificationText = getNotificationMessagePreview(message, messageType);
                showQueuedNotification(context, senderName, notificationText, chatId, senderId);
                
                // Mark notification as delivered
                snapshot.getRef().child("delivered").setValue(true);
                snapshot.getRef().child("deliveredAt").setValue(System.currentTimeMillis());
                
                // Mark message as delivered in chat
                if (chatId != null) {
                    markLatestMessageAsDeliveredForUser(chatId, receiverId);
                }
                
                // Update user presence to online
                updatePresence(currentUserId, true);
                
                Log.d(TAG, "✅ Queued notification processed and delivered: " + notificationId);
                
                // Remove the notification from queue after successful delivery
                snapshot.getRef().removeValue();
            } else {
                Log.d(TAG, "⏳ Device offline or no context, queued notification will be processed when online: " + notificationId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling queued notification", e);
        }
    }
    
    private static String getNotificationMessagePreview(String message, String messageType) {
        if (message == null) return "";
        
        switch (messageType != null ? messageType : "text") {
            case "image":
                return "📷 Photo";
            case "video":
                return "🎥 Video";
            case "audio":
                return "🎤 Audio";
            case "document":
                return "📄 Document";
            case "text":
            default:
                return message.length() > 50 ? message.substring(0, 47) + "..." : message;
        }
    }
    
    private static void showQueuedNotification(android.content.Context context, String title, String body, String chatId, String senderId) {
        if (context != null) {
            com.pingme.android.utils.NotificationUtil.showMessageNotification(context, title, body, chatId, senderId);
        }
    }
    
    private static boolean isDeviceOnline(android.content.Context context) {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    // ===== UTILITY METHODS =====

    public static String generateChatId(String userId1, String userId2) {
        return userId1.compareTo(userId2) < 0 ? userId1 + "_" + userId2 : userId2 + "_" + userId1;
    }

    public static DatabaseReference getRealtimeBlockedUsersRef(String userId) {
        return getRealtimeDatabase().child("blocked_users").child(userId);
    }

    public static void createNewChatInRealtime(String chatId, String senderId, String otherUserId) {
        Log.d(TAG, "Creating new chat: " + chatId);
        
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("participants", new HashMap<String, Boolean>() {{
            put(senderId, true);
            put(otherUserId, true);
        }});
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTimestamp", 0L);
        chatData.put("lastMessageSenderId", "");
        chatData.put("lastMessageType", "text");
        chatData.put("lastMessageId", "");
        
        // Add chat to both users' chat lists
        getChatRef(chatId).setValue(chatData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Chat created successfully: " + chatId);
                    
                    // Add to sender's chat list
                    getUserChatsRef(senderId).child(chatId).setValue(true);
                    
                    // Add to other user's chat list
                    getUserChatsRef(otherUserId).child(chatId).setValue(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to create chat: " + e.getMessage(), e);
                });
    }

    public static void ensureChatExists(String chatId, String senderId, String otherUserId) {
        // Only create chat if it doesn't exist and we're about to send a message
        getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "Chat doesn't exist, will be created when first message is sent: " + chatId);
                    // Don't create empty chat - it will be created when first message is sent
                } else {
                    Log.d(TAG, "Chat already exists: " + chatId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to check chat existence", databaseError.toException());
            }
        });
    }

    public static void createEmptyFriendChat(String userId1, String userId2) {
        // This method is deprecated - chats should only be created when messages are sent
        Log.d(TAG, "createEmptyFriendChat is deprecated - chats are now created when messages are sent");
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
                int totalChats = (int) dataSnapshot.getChildrenCount();
                
                if (totalChats == 0) {
                    callback.onSearchComplete(results);
                    return;
                }

                final int[] processedChats = {0};

                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId != null) {
                        // Search in chat messages with enhanced info
                        searchMessagesInChatEnhanced(chatId, lowerQuery, results, userId, () -> {
                            processedChats[0]++;
                            if (processedChats[0] == totalChats) {
                                // Sort results by timestamp (newest first)
                                results.sort((r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
                                callback.onSearchComplete(results);
                            }
                        });
                    } else {
                        processedChats[0]++;
                        if (processedChats[0] == totalChats) {
                            results.sort((r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
                            callback.onSearchComplete(results);
                        }
                    }
                }
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

    private static void searchMessagesInChatEnhanced(String chatId, String query, List<SearchResult> results, String currentUserId, Runnable onComplete) {
        getMessagesRef(chatId).orderByChild("timestamp")
                .limitToLast(200) // Increased limit for better search coverage
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<SearchResult> tempResults = new ArrayList<>();
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Object messageObj = snapshot.getValue();
                            if (messageObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> messageData = (Map<String, Object>) messageObj;
                                String text = (String) messageData.get("text");
                                String fileName = (String) messageData.get("fileName");
                                String senderId = (String) messageData.get("senderId");
                                
                                if ((text != null && text.toLowerCase().contains(query)) ||
                                    (fileName != null && fileName.toLowerCase().contains(query))) {
                                    
                                    // Create a message object for search results
                                    Message message = new Message();
                                    message.setId(snapshot.getKey());
                                    message.setText(text != null ? text : "");
                                    message.setFileName(fileName);
                                    message.setSenderId(senderId);
                                    
                                    Object timestamp = messageData.get("timestamp");
                                    if (timestamp instanceof Long) {
                                        message.setTimestamp((Long) timestamp);
                                    }
                                    
                                    SearchResult result = new SearchResult(chatId, message);
                                    tempResults.add(result);
                                }
                            }
                        }
                        
                        // Load contact info for all results and wait for completion
                        if (tempResults.isEmpty()) {
                            onComplete.run();
                        } else {
                            loadSearchResultsInfo(tempResults, currentUserId, () -> {
                                results.addAll(tempResults);
                                onComplete.run();
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        onComplete.run();
                    }
                });
    }

    private static void loadSearchResultsInfo(List<SearchResult> results, String currentUserId, Runnable onComplete) {
        if (results.isEmpty()) {
            onComplete.run();
            return;
        }
        
        final int[] completedCount = {0};
        final int totalResults = results.size();
        
        for (SearchResult result : results) {
            loadSearchResultInfoWithCallback(result, currentUserId, () -> {
                completedCount[0]++;
                if (completedCount[0] == totalResults) {
                    onComplete.run();
                }
            });
        }
    }
    
    private static void loadSearchResultInfoWithCallback(SearchResult result, String currentUserId, Runnable onInfoLoaded) {
        String senderId = result.getSenderId();
        String chatId = result.getChatId();
        
        final boolean[] senderInfoLoaded = {false};
        final boolean[] chatInfoLoaded = {false};
        
        Runnable checkCompletion = () -> {
            if (senderInfoLoaded[0] && chatInfoLoaded[0]) {
                onInfoLoaded.run();
            }
        };
        
        // Load sender information
        if (senderId != null) {
            if (senderId.equals(currentUserId)) {
                result.setSenderName("You");
                result.setContactName("You");
                senderInfoLoaded[0] = true;
                checkCompletion.run();
            } else {
                // First load user information
                getUserRef(senderId).get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            // Check privacy settings for profile photo - current user should always see their avatar
                            boolean isCurrentUser = senderId.equals(currentUserId);
                            boolean shouldShowAvatar = isCurrentUser || user.isProfilePhotoEnabled();
                            String imageUrl = (shouldShowAvatar && user.getImageUrl() != null && !user.getImageUrl().trim().isEmpty()) 
                                ? user.getImageUrl() : null;
                            result.setContactImageUrl(imageUrl);
                            
                            // Now check if this user is in friends list to get personal name
                            getFriendRef(currentUserId, senderId).get().addOnSuccessListener(friendDoc -> {
                                if (friendDoc.exists()) {
                                    // User is a friend, check for personal name
                                    String personalName = friendDoc.getString("personalName");
                                    if (personalName != null && !personalName.trim().isEmpty()) {
                                        result.setSenderName(personalName);
                                        result.setContactName(personalName);
                                    } else {
                                        // Use regular display name
                                        String displayName = user.getDisplayNameForUser();
                                        result.setSenderName(displayName);
                                        result.setContactName(displayName);
                                    }
                                } else {
                                    // Not a friend, use regular display name
                                    String displayName = user.getDisplayNameForUser();
                                    result.setSenderName(displayName);
                                    result.setContactName(displayName);
                                }
                                senderInfoLoaded[0] = true;
                                checkCompletion.run();
                            }).addOnFailureListener(e -> {
                                // Fallback to regular display name
                                String displayName = user.getDisplayNameForUser();
                                result.setSenderName(displayName);
                                result.setContactName(displayName);
                                senderInfoLoaded[0] = true;
                                checkCompletion.run();
                                Log.e(TAG, "Failed to get friend info", e);
                            });
                        } else {
                            senderInfoLoaded[0] = true;
                            checkCompletion.run();
                        }
                    } else {
                        senderInfoLoaded[0] = true;
                        checkCompletion.run();
                    }
                }).addOnFailureListener(e -> {
                    // Continue without sender info
                    Log.e(TAG, "Failed to get sender info", e);
                    senderInfoLoaded[0] = true;
                    checkCompletion.run();
                });
            }
        } else {
            senderInfoLoaded[0] = true;
            checkCompletion.run();
        }
        
        // Load chat information
        getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Object chatName = dataSnapshot.child("name").getValue();
                    if (chatName != null) {
                        result.setChatName(chatName.toString());
                    }
                }
                chatInfoLoaded[0] = true;
                checkCompletion.run();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Continue without chat info
                chatInfoLoaded[0] = true;
                checkCompletion.run();
            }
        });
    }

    private static void loadSearchResultInfo(SearchResult result, String currentUserId) {
        String senderId = result.getSenderId();
        String chatId = result.getChatId();
        
        // Load sender information
        if (senderId != null) {
            if (senderId.equals(currentUserId)) {
                result.setSenderName("You");
            } else {
                // First load user information
                getUserRef(senderId).get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            // Check privacy settings for profile photo - current user should always see their avatar
                            boolean isCurrentUser = senderId.equals(currentUserId);
                            boolean shouldShowAvatar = isCurrentUser || user.isProfilePhotoEnabled();
                            String imageUrl = (shouldShowAvatar && user.getImageUrl() != null && !user.getImageUrl().trim().isEmpty()) 
                                ? user.getImageUrl() : null;
                            result.setContactImageUrl(imageUrl);
                            
                            // Now check if this user is in friends list to get personal name
                            getFriendRef(currentUserId, senderId).get().addOnSuccessListener(friendDoc -> {
                                if (friendDoc.exists()) {
                                    // User is a friend, check for personal name
                                    String personalName = friendDoc.getString("personalName");
                                    if (personalName != null && !personalName.trim().isEmpty()) {
                                        result.setSenderName(personalName);
                                        result.setContactName(personalName);
                                    } else {
                                        // Use regular display name
                                        String displayName = user.getDisplayNameForUser();
                                        result.setSenderName(displayName);
                                        result.setContactName(displayName);
                                    }
                                } else {
                                    // Not a friend, use regular display name
                                    String displayName = user.getDisplayNameForUser();
                                    result.setSenderName(displayName);
                                    result.setContactName(displayName);
                                }
                            }).addOnFailureListener(e -> {
                                // Fallback to regular display name
                                String displayName = user.getDisplayNameForUser();
                                result.setSenderName(displayName);
                                result.setContactName(displayName);
                                Log.e(TAG, "Failed to get friend info", e);
                            });
                        }
                    }
                }).addOnFailureListener(e -> {
                    // Continue without sender info
                    Log.e(TAG, "Failed to get sender info", e);
                });
            }
        }
        
        // Load chat information
        getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Object chatName = dataSnapshot.child("name").getValue();
                    if (chatName != null) {
                        result.setChatName(chatName.toString());
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Continue without chat info
            }
        });
    }

    // ===== UTILITY METHODS =====
    
    /**
     * Safely converts Firebase Database values to boolean, handling various data types
     * that might be stored instead of proper boolean values
     */
    public static boolean safeBooleanValue(Object value) {
        if (value == null) {
            return false;
        }
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Long) {
            return ((Long) value) == 1L;
        } else if (value instanceof Integer) {
            return ((Integer) value) == 1;
        } else if (value instanceof String) {
            return "true".equalsIgnoreCase((String) value) || "1".equals(value);
        }
        
        return false;
    }
    
    // ===== FRIEND REFERENCE METHOD =====
    
    public static DocumentReference getFriendRef(String userId, String friendId) {
        return getFirestoreInstance()
                .collection("users")
                .document(userId)
                .collection("friends")
                .document(friendId);
    }

    // ===== SEARCH RESULT CLASS =====

    public static class SearchResult {
        private String chatId;
        private Message message;
        private String contactName;
        private String chatName;
        private String contactImageUrl;
        private String messageId;
        private String senderId;
        private String senderName;

        public SearchResult(String chatId, Message message) {
            this.chatId = chatId;
            this.message = message;
            this.messageId = message != null ? message.getId() : null;
            this.senderId = message != null ? message.getSenderId() : null;
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

        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }

        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }

        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
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
        void onBroadcastCreated(com.pingme.android.models.Broadcast broadcast);
        void onError(String error);
    }

    public interface BroadcastListCallback {
        void onBroadcastsLoaded(List<com.pingme.android.models.Broadcast> broadcasts);
        void onError(String error);
    }

    public interface SearchCallback {
        void onSearchComplete(List<SearchResult> results);
        void onError(String error);
    }

    public static void addStatus(String userId, String statusText, String mediaUrl, String mediaType) {
        Status status = new Status();
        status.setUserId(userId);
        status.setText(statusText);
        status.setMediaUrl(mediaUrl);
        status.setMediaType(mediaType);
        status.setTimestamp(System.currentTimeMillis());
        status.setExpiryTime(System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // 24 hours
        status.setViewers(new HashMap<>());

        getStatusCollectionRef().add(status)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Status added successfully: " + documentReference.getId());
                    
                    // Send notifications to friends
                    sendStatusNotifications(userId, statusText);
                    
                    // Cleanup expired statuses for this user
                    cleanupExpiredStatuses(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add status", e);
                });
    }
    
    // Clean up expired statuses (older than 24 hours)
    public static void cleanupExpiredStatuses(String userId) {
        long currentTime = System.currentTimeMillis();
        
        getStatusCollectionRef()
                .whereEqualTo("userId", userId)
                .whereLessThan("expiryTime", currentTime)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " expired statuses to delete for user: " + userId);
                    
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        document.getReference().delete()
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Deleted expired status: " + document.getId()))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete expired status: " + document.getId(), e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to query expired statuses", e));
    }
    
    // Clean up all expired statuses globally (can be called periodically)
    public static void cleanupAllExpiredStatuses() {
        long currentTime = System.currentTimeMillis();
        
        getStatusCollectionRef()
                .whereLessThan("expiryTime", currentTime)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " expired statuses to delete globally");
                    
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        document.getReference().delete()
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Deleted expired status: " + document.getId()))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete expired status: " + document.getId(), e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to query all expired statuses", e));
    }
    
    // Delete a specific status manually
    public static void deleteStatus(String statusId, String userId, StatusCallback callback) {
        getStatusCollectionRef()
                .document(statusId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Status status = documentSnapshot.toObject(Status.class);
                        if (status != null && status.getUserId().equals(userId)) {
                            // User can only delete their own status
                            documentSnapshot.getReference().delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Status deleted successfully: " + statusId);
                                        if (callback != null) callback.onSuccess();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to delete status: " + statusId, e);
                                        if (callback != null) callback.onError("Failed to delete status");
                                    });
                        } else {
                            Log.w(TAG, "User " + userId + " cannot delete status " + statusId + " (not owner)");
                            if (callback != null) callback.onError("You can only delete your own status");
                        }
                    } else {
                        Log.w(TAG, "Status not found: " + statusId);
                        if (callback != null) callback.onError("Status not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check status ownership", e);
                    if (callback != null) callback.onError("Failed to delete status");
                });
    }
    
    public interface StatusCallback {
        void onSuccess();
        void onError(String error);
    }

    private static void sendStatusNotifications(String userId, String statusText) {
        // Get user's friends
        getFriendsRef(userId).get().addOnSuccessListener(querySnapshot -> {
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                String friendId = doc.getId();
                
                // Get friend's user info
                getUserRef(friendId).get().addOnSuccessListener(userSnapshot -> {
                    if (userSnapshot.exists()) {
                        User friend = userSnapshot.toObject(User.class);
                        
                        // Check if friend has notifications enabled
                        if (friend != null && friend.isNotificationsEnabled()) {
                            // Get current user's name
                            getUserRef(userId).get().addOnSuccessListener(currentUserSnapshot -> {
                                if (currentUserSnapshot.exists()) {
                                    User currentUser = currentUserSnapshot.toObject(User.class);
                                    String userName = currentUser != null ? currentUser.getDisplayName() : "Unknown";
                                    
                                    // Show status notification
                                    android.content.Context context = getApplicationContext();
                                    if (context != null) {
                                        com.pingme.android.utils.NotificationUtil.showStatusNotification(context, userName);
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    public static void sendFriendRequest(String senderId, String receiverEmail) {
        // Find user by email
        getUsersCollectionRef()
                .whereEqualTo("email", receiverEmail)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                        String receiverId = userDoc.getId();
                        
                        // Check if already friends
                        checkIfFriends(senderId, receiverId, areFriends -> {
                            if (!areFriends) {
                                // Check if request already exists
                                getFriendRequestsRef(receiverId)
                                        .document(senderId)
                                        .get()
                                        .addOnSuccessListener(requestDoc -> {
                                            if (!requestDoc.exists()) {
                                                // Send friend request
                                                Map<String, Object> request = new HashMap<>();
                                                request.put("senderId", senderId);
                                                request.put("timestamp", System.currentTimeMillis());
                                                request.put("status", "pending");
                                                
                                                getFriendRequestsRef(receiverId)
                                                        .document(senderId)
                                                        .set(request)
                                                        .addOnSuccessListener(aVoid -> {
                                                            Log.d(TAG, "Friend request sent successfully");
                                                            
                                                            // Send notification to receiver
                                                            sendFriendRequestNotification(senderId, receiverId);
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            Log.e(TAG, "Failed to send friend request", e);
                                                        });
                                            } else {
                                                Log.d(TAG, "Friend request already exists");
                                            }
                                        });
                            } else {
                                Log.d(TAG, "Users are already friends");
                            }
                        });
                    } else {
                        Log.d(TAG, "User not found with email: " + receiverEmail);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to find user by email", e);
                });
    }

    private static void sendFriendRequestNotification(String senderId, String receiverId) {
        // Get sender's name
        getUserRef(senderId).get().addOnSuccessListener(senderSnapshot -> {
            if (senderSnapshot.exists()) {
                User sender = senderSnapshot.toObject(User.class);
                String senderName = sender != null ? sender.getDisplayName() : "Unknown";
                
                // Get receiver's info
                getUserRef(receiverId).get().addOnSuccessListener(receiverSnapshot -> {
                    if (receiverSnapshot.exists()) {
                        User receiver = receiverSnapshot.toObject(User.class);
                        
                        // Check if receiver has notifications enabled
                        if (receiver != null && receiver.isNotificationsEnabled()) {
                            // Show friend request notification
                            android.content.Context context = getApplicationContext();
                            if (context != null) {
                                com.pingme.android.utils.NotificationUtil.showFriendRequestNotification(context, senderName);
                            }
                        }
                    }
                });
            }
        });
    }

    public static CollectionReference getFriendRequestsRef(String userId) {
        return getUserRef(userId).collection("friend_requests");
    }

    // ===== CHAT MANAGEMENT =====

    public static CollectionReference getChatManagementCollectionRef() {
        return FirebaseFirestore.getInstance().collection("chat_management");
    }

    public static DocumentReference getChatManagementRef(String chatId) {
        return getChatManagementCollectionRef().document(chatId);
    }

    public static CollectionReference getChatHistoryCollectionRef() {
        return FirebaseFirestore.getInstance().collection("chat_history");
    }

    public static DocumentReference getChatHistoryRef(String chatId) {
        return getChatHistoryCollectionRef().document(chatId);
    }

    public static void clearChat(String chatId, String userId) {
        Log.d(TAG, "Clearing chat: " + chatId + " for user: " + userId);
        
        // Get all messages in the chat
        getMessagesRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Mark all messages as cleared for this user
                    for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                        Message message = messageSnapshot.getValue(Message.class);
                        if (message != null) {
                            message.markAsClearedForUser(userId);
                            
                            // Update message in database
                            getMessagesRef(chatId).child(messageSnapshot.getKey())
                                    .child("clearedFor")
                                    .child(userId)
                                    .setValue(System.currentTimeMillis());
                        }
                    }
                    
                    // Update chat management
                    updateChatClearedStatus(chatId, userId);
                    
                    // Store in chat history
                    storeClearedChatHistory(chatId, userId, dataSnapshot);
                    
                    Log.d(TAG, "Chat cleared successfully for user: " + userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to clear chat", databaseError.toException());
            }
        });
    }

    public static void deleteChat(String chatId, String userId) {
        Log.d(TAG, "Deleting chat: " + chatId + " for user: " + userId);
        
        // Get chat info before deletion
        getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot chatSnapshot) {
                if (chatSnapshot.exists()) {
                    // Store chat info in history
                    storeDeletedChatHistory(chatId, userId, chatSnapshot);
                    
                    // Mark chat as deleted for this user in chat management
                    updateChatDeletedStatus(chatId, userId);
                    
                    // Remove from user's chat list
                    getUserChatsRef(userId).child(chatId).removeValue();
                    
                    Log.d(TAG, "Chat deleted successfully for user: " + userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to delete chat", databaseError.toException());
            }
        });
    }

    public static void deleteMessage(String chatId, String messageId, String userId) {
        Log.d(TAG, "Deleting message: " + messageId + " for user: " + userId);
        
        // Get message before deletion
        getMessagesRef(chatId).child(messageId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot messageSnapshot) {
                if (messageSnapshot.exists()) {
                    Message message = messageSnapshot.getValue(Message.class);
                    if (message != null) {
                        // Store in chat history
                        storeDeletedMessageHistory(chatId, messageId, message, userId, "delete_message");
                        
                        // Mark message as deleted for this user
                        message.markAsDeletedForUser(userId);
                        
                        // Update message in database
                        getMessagesRef(chatId).child(messageId)
                                .child("deletedFor")
                                .child(userId)
                                .setValue(System.currentTimeMillis());
                        
                        Log.d(TAG, "Message deleted successfully for user: " + userId);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to delete message", databaseError.toException());
            }
        });
    }

    private static void updateChatClearedStatus(String chatId, String userId) {
        getChatManagementRef(chatId).get().addOnSuccessListener(documentSnapshot -> {
            ChatManagement chatManagement;
            if (documentSnapshot.exists()) {
                chatManagement = documentSnapshot.toObject(ChatManagement.class);
                if (chatManagement == null) {
                    chatManagement = new ChatManagement(chatId);
                }
            } else {
                chatManagement = new ChatManagement(chatId);
            }
            
            chatManagement.addParticipant(userId);
            chatManagement.markChatAsClearedForUser(userId);
            
            getChatManagementRef(chatId).set(chatManagement);
        });
    }

    private static void updateChatDeletedStatus(String chatId, String userId) {
        getChatManagementRef(chatId).get().addOnSuccessListener(documentSnapshot -> {
            ChatManagement chatManagement;
            if (documentSnapshot.exists()) {
                chatManagement = documentSnapshot.toObject(ChatManagement.class);
                if (chatManagement == null) {
                    chatManagement = new ChatManagement(chatId);
                }
            } else {
                chatManagement = new ChatManagement(chatId);
            }
            
            chatManagement.addParticipant(userId);
            chatManagement.markChatAsDeletedForUser(userId);
            
            getChatManagementRef(chatId).set(chatManagement);
        });
    }

    private static void storeClearedChatHistory(String chatId, String userId, DataSnapshot messagesSnapshot) {
        getChatHistoryRef(chatId).get().addOnSuccessListener(documentSnapshot -> {
            ChatHistory chatHistory;
            if (documentSnapshot.exists()) {
                chatHistory = documentSnapshot.toObject(ChatHistory.class);
                if (chatHistory == null) {
                    chatHistory = new ChatHistory(chatId);
                }
            } else {
                chatHistory = new ChatHistory(chatId);
            }
            
            // Store all cleared messages
            for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                Message message = messageSnapshot.getValue(Message.class);
                if (message != null) {
                    ChatHistory.DeletedMessage deletedMessage = 
                        new ChatHistory.DeletedMessage(message, userId, "clear_chat");
                    chatHistory.addDeletedMessage(messageSnapshot.getKey(), deletedMessage);
                }
            }
            
            getChatHistoryRef(chatId).set(chatHistory);
        });
    }

    private static void storeDeletedChatHistory(String chatId, String userId, DataSnapshot chatSnapshot) {
        getChatHistoryRef(chatId).get().addOnSuccessListener(documentSnapshot -> {
            ChatHistory chatHistory;
            if (documentSnapshot.exists()) {
                chatHistory = documentSnapshot.toObject(ChatHistory.class);
                if (chatHistory == null) {
                    chatHistory = new ChatHistory(chatId);
                }
            } else {
                chatHistory = new ChatHistory(chatId);
            }
            
            // Extract chat info without unchecked casts
            String[] participants = new String[0];
            Long createdAt = chatSnapshot.child("createdAt").getValue(Long.class);
            if (createdAt == null) {
                createdAt = System.currentTimeMillis();
            }

            DataSnapshot participantsSnapshot = chatSnapshot.child("participants");
            if (participantsSnapshot.exists()) {
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : participantsSnapshot.getChildren()) {
                    String id = child.getKey();
                    if (id != null) ids.add(id);
                }
                participants = ids.toArray(new String[0]);
            }

            ChatHistory.ChatInfo chatInfo = new ChatHistory.ChatInfo(participants, createdAt);
            ChatHistory.DeletedChat deletedChat = 
                new ChatHistory.DeletedChat(chatInfo, userId, "delete_chat");
            chatHistory.addDeletedChat(userId, deletedChat);
                
                getChatHistoryRef(chatId).set(chatHistory);
        });
    }

    private static void storeDeletedMessageHistory(String chatId, String messageId, Message message, 
                                                  String userId, String deletionType) {
        getChatHistoryRef(chatId).get().addOnSuccessListener(documentSnapshot -> {
            ChatHistory chatHistory;
            if (documentSnapshot.exists()) {
                chatHistory = documentSnapshot.toObject(ChatHistory.class);
                if (chatHistory == null) {
                    chatHistory = new ChatHistory(chatId);
                }
            } else {
                chatHistory = new ChatHistory(chatId);
            }
            
            ChatHistory.DeletedMessage deletedMessage = 
                new ChatHistory.DeletedMessage(message, userId, deletionType);
            chatHistory.addDeletedMessage(messageId, deletedMessage);
            
            getChatHistoryRef(chatId).set(chatHistory);
        });
    }

    public static void getVisibleMessages(String chatId, String userId, ValueEventListener listener) {
        getMessagesRef(chatId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Filter messages based on user's deletion/clear status
                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    Message message = messageSnapshot.getValue(Message.class);
                    if (message != null && message.isVisibleForUser(userId)) {
                        // This message is visible for the user
                        listener.onDataChange(messageSnapshot);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listener.onCancelled(databaseError);
            }
        });
    }

    public static void getActiveChatsForUser(String userId, ValueEventListener listener) {
        getUserChatsRef(userId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Filter chats based on user's deletion status
                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId != null) {
                        // Check if chat is active for this user
                        getChatManagementRef(chatId).get().addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                ChatManagement chatManagement = documentSnapshot.toObject(ChatManagement.class);
                                if (chatManagement != null && chatManagement.isChatActiveForUser(userId)) {
                                    // This chat is active for the user
                                    listener.onDataChange(chatSnapshot);
                                }
                            } else {
                                // No chat management record, assume active
                                listener.onDataChange(chatSnapshot);
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listener.onCancelled(databaseError);
            }
        });
    }
    
    /**
     * Update the unread count for a specific chat
     * @param chatId The ID of the chat to update
     * @param unreadCount The new unread count
     */
    public static void updateChatUnreadCount(String chatId, int unreadCount) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("unreadCount", unreadCount);
        
        getChatRef(chatId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Chat unread count updated successfully for " + chatId + " to " + unreadCount);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to update chat unread count for " + chatId, e);
                });
    }
}