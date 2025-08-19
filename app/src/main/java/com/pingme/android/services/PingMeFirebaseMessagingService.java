// FirebaseMessaging.java - This should be FirebaseMessagingService
package com.pingme.android.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.pingme.android.utils.FirebaseUtil;
import com.pingme.android.utils.NotificationUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Objects;

public class PingMeFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload
        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage);
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            handleNotificationMessage(remoteMessage);
        }
    }

    private void handleDataMessage(RemoteMessage remoteMessage) {
        String type = remoteMessage.getData().get("type");
        String title = remoteMessage.getData().get("title");
        String body = remoteMessage.getData().get("body");
        String chatId = remoteMessage.getData().get("chatId");
        String receiverId = remoteMessage.getData().get("receiverId");
        String messageId = remoteMessage.getData().get("messageId");

        if ("message".equals(type)) {
            // Check if device is online before marking as delivered
            if (isDeviceOnline()) {
                NotificationUtil.showMessageNotification(this, title, body, chatId, receiverId);
                
                // Mark message as delivered only if device is online
                if (chatId != null && receiverId != null) {
                    if (messageId != null) {
                        FirebaseUtil.markSpecificMessageAsDelivered(chatId, messageId, receiverId);
                    } else {
                        FirebaseUtil.markLatestMessageAsDeliveredForUser(chatId, receiverId);
                    }
                    
                    // Update user presence to online
                    FirebaseUtil.updatePresence(receiverId, true);
                }
            } else {
                // Show notification but don't mark as delivered
                NotificationUtil.showOfflineNotification(this, title, body, chatId, receiverId);
            }
        }
    }
    
    private boolean isDeviceOnline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        String title = Objects.requireNonNull(remoteMessage.getNotification()).getTitle();
        String body = remoteMessage.getNotification().getBody();
        String chatId = remoteMessage.getData().get("chatId");
        String receiverId = remoteMessage.getData().get("receiverId");
        String messageId = remoteMessage.getData().get("messageId");

        // Check if device is online before marking as delivered
        if (isDeviceOnline()) {
            NotificationUtil.showMessageNotification(this, title, body, chatId, receiverId);
            
            // Mark message as delivered only if device is online
            if (chatId != null && receiverId != null) {
                if (messageId != null) {
                    FirebaseUtil.markSpecificMessageAsDelivered(chatId, messageId, receiverId);
                } else {
                    FirebaseUtil.markLatestMessageAsDeliveredForUser(chatId, receiverId);
                }
                
                // Update user presence to online
                FirebaseUtil.updatePresence(receiverId, true);
            }
        } else {
            // Show notification but don't mark as delivered
            NotificationUtil.showOfflineNotification(this, title, body, chatId, receiverId);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);

        // Send token to server
        sendRegistrationToServer(token);
    }

    private void sendRegistrationToServer(String token) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (userId != null) {
            FirebaseUtil.getUserRef(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated successfully"))
                    .addOnFailureListener(e -> Log.w(TAG, "Failed to update FCM token", e));
        }
    }
}