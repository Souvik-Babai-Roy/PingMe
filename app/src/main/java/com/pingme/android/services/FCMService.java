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
        String message = data.get("message");
        String messageType = data.get("messageType");
        
        if (chatId == null || senderId == null) return;
        
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
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get sender info", e);
                    // Create notification with fallback data
                    createNotification("New Message", message, chatId, senderId);
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
        
        // Build notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);
        
        // Show notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
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