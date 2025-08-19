package com.pingme.android.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pingme.android.R;
import com.pingme.android.activities.ChatActivity;
import com.pingme.android.activities.MainActivity;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.util.Map;

public class FCMService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "pingme_chat_channel";
    private static final String CHANNEL_NAME = "Chat Messages";
    private static final String CHANNEL_DESCRIPTION = "Notifications for new messages";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        
        // Update token in Firestore
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String userId = auth.getCurrentUser().getUid();
            FirebaseUtil.updateFCMToken(userId, token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());
        
        // Check if user is authenticated
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.d(TAG, "User not authenticated, ignoring message");
            return;
        }
        
        // Handle data payload
        Map<String, String> data = remoteMessage.getData();
        if (data != null && !data.isEmpty()) {
            handleDataMessage(data);
        }
        
        // Handle notification payload
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            handleNotificationMessage(notification, data);
        }
    }

    private void handleDataMessage(Map<String, String> data) {
        String type = data.get("type");
        if (type == null) return;
        
        switch (type) {
            case "new_message":
                handleNewMessageNotification(data);
                break;
            case "read_receipt":
                handleReadReceiptNotification(data);
                break;
            case "typing":
                handleTypingNotification(data);
                break;
            default:
                Log.d(TAG, "Unknown notification type: " + type);
        }
    }

    private void handleNotificationMessage(RemoteMessage.Notification notification, Map<String, String> data) {
        // Create notification for display
        createNotification(
                notification.getTitle(),
                notification.getBody(),
                data.get("chatId"),
                data.get("senderId")
        );
    }

    private void handleNewMessageNotification(Map<String, String> data) {
        String chatId = data.get("chatId");
        String senderId = data.get("senderId");
        String receiverId = data.get("receiverId");
        String message = data.get("message");
        String messageType = data.get("messageType");
        String messageId = data.get("messageId");
        
        if (chatId == null || senderId == null) {
            Log.w(TAG, "Invalid notification data - missing chatId or senderId");
            return;
        }
        
        // Only show notification if this device belongs to the receiver
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.d(TAG, "No current user, ignoring notification");
            return;
        }
        
        String currentUserId = auth.getCurrentUser().getUid();
        if (receiverId != null && !receiverId.equals(currentUserId)) {
            Log.d(TAG, "Notification not for current user, ignoring");
            return;
        }
        
        Log.d(TAG, "Processing notification for user: " + currentUserId + ", from: " + senderId);
        
        // Check if user is online and has internet connectivity
        if (!isDeviceOnline()) {
            Log.w(TAG, "Device is offline, notification received but cannot mark as delivered");
            // Still show notification but don't mark as delivered
            createNotificationWithoutDelivery("New Message", message != null ? message : "New message", chatId, senderId);
            return;
        }
        
        // Get sender info for notification
        FirebaseUtil.getUserRef(senderId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User sender = snapshot.toObject(User.class);
                        if (sender != null) {
                            String title = sender.getDisplayName();
                            String body = getMessagePreview(message, messageType);
                            
                            createNotification(title, body, chatId, senderId);
                            
                            // Only mark as delivered if device has internet connectivity
                            if (isDeviceOnline() && chatId != null && currentUserId != null) {
                                // Mark specific message as delivered, not just the latest
                                if (messageId != null) {
                                    FirebaseUtil.markSpecificMessageAsDelivered(chatId, messageId, currentUserId);
                                } else {
                                    FirebaseUtil.markLatestMessageAsDeliveredForUser(chatId, currentUserId);
                                }
                                
                                // Update user presence to online since they received notification
                                FirebaseUtil.updatePresence(currentUserId, true);
                                Log.d(TAG, "Message marked as delivered and user presence updated to online");
                            } else {
                                Log.w(TAG, "Device offline - message not marked as delivered");
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get sender info", e);
                    // Create notification with fallback data but don't mark as delivered if offline
                    if (isDeviceOnline()) {
                        createNotification("New Message", message != null ? message : "New message", chatId, senderId);
                        if (chatId != null && currentUserId != null) {
                            if (messageId != null) {
                                FirebaseUtil.markSpecificMessageAsDelivered(chatId, messageId, currentUserId);
                            } else {
                                FirebaseUtil.markLatestMessageAsDeliveredForUser(chatId, currentUserId);
                            }
                        }
                    } else {
                        createNotificationWithoutDelivery("New Message", message != null ? message : "New message", chatId, senderId);
                    }
                });
    }

    private void handleReadReceiptNotification(Map<String, String> data) {
        // Handle read receipt notifications (usually no UI notification needed)
        String chatId = data.get("chatId");
        String senderId = data.get("senderId");
        String messageCount = data.get("messageCount");
        
        Log.d(TAG, "Read receipt: " + messageCount + " messages read in chat " + chatId + " by " + senderId);
        
        // Update message status in local cache if needed
        // This could trigger UI updates in the chat
    }

    private void handleTypingNotification(Map<String, String> data) {
        // Handle typing indicators (no notification needed)
        String chatId = data.get("chatId");
        String senderId = data.get("senderId");
        
        Log.d(TAG, "Typing indicator: " + senderId + " is typing in chat " + chatId);
        
        // This could update typing indicators in the UI
    }

    private String getMessagePreview(String message, String messageType) {
        if (message == null) return "";
        
        switch (messageType) {
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

    private void createNotification(String title, String body, String chatId, String senderId) {
        createNotificationInternal(title, body, chatId, senderId, true);
    }
    
    private void createNotificationWithoutDelivery(String title, String body, String chatId, String senderId) {
        createNotificationInternal(title, body, chatId, senderId, false);
    }
    
    private void createNotificationInternal(String title, String body, String chatId, String senderId, boolean markAsDelivered) {
        // Create notification channel for Android O and above
        createNotificationChannel();
        
        // Create intent for notification tap
        Intent intent;
        if (chatId != null) {
            // Open specific chat
            intent = new Intent(this, ChatActivity.class);
            intent.putExtra("chatId", chatId);
            intent.putExtra("receiverId", senderId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else {
            // Open main activity
            intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get default notification sound
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        // Build notification with delivery status indicator
        String notificationBody = body;
        if (!markAsDelivered) {
            notificationBody += " (Offline)";
        }
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(notificationBody)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        
        // Show notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
            Log.d(TAG, "Notification created: " + title + " - " + notificationBody + ", delivered: " + markAsDelivered);
        }
    }
    
    private boolean isDeviceOnline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            Log.d(TAG, "Device online status: " + isConnected);
            return isConnected;
        }
        return false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}