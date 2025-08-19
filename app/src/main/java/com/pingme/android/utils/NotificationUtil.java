// NotificationUtil.java
package com.pingme.android.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.pingme.android.R;
import com.pingme.android.activities.ChatActivity;
import com.pingme.android.activities.MainActivity;

public class NotificationUtil {
    private static final String TAG = "NotificationUtil";
    
    // Notification channels
    private static final String CHANNEL_MESSAGES = "pingme_messages";
    private static final String CHANNEL_STATUS = "pingme_status";
    private static final String CHANNEL_FRIENDS = "pingme_friends";
    
    // Channel names
    private static final String CHANNEL_MESSAGES_NAME = "Messages";
    private static final String CHANNEL_STATUS_NAME = "Status Updates";
    private static final String CHANNEL_FRIENDS_NAME = "Friend Requests";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            
            // Messages channel
            NotificationChannel messagesChannel = new NotificationChannel(
                    CHANNEL_MESSAGES,
                    CHANNEL_MESSAGES_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            messagesChannel.setDescription("Notifications for new messages");
            messagesChannel.enableLights(true);
            messagesChannel.enableVibration(true);
            messagesChannel.setShowBadge(true);
            
            // Status channel
            NotificationChannel statusChannel = new NotificationChannel(
                    CHANNEL_STATUS,
                    CHANNEL_STATUS_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            statusChannel.setDescription("Notifications for status updates");
            statusChannel.enableLights(true);
            statusChannel.enableVibration(false);
            
            // Friends channel
            NotificationChannel friendsChannel = new NotificationChannel(
                    CHANNEL_FRIENDS,
                    CHANNEL_FRIENDS_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            friendsChannel.setDescription("Notifications for friend requests");
            friendsChannel.enableLights(true);
            friendsChannel.enableVibration(false);
            
            notificationManager.createNotificationChannel(messagesChannel);
            notificationManager.createNotificationChannel(statusChannel);
            notificationManager.createNotificationChannel(friendsChannel);
        }
    }

    public static void showMessageNotification(Context context, String senderName, String messageText, String chatId, String receiverId) {
        Log.d(TAG, "Showing message notification from: " + senderName + " message: " + messageText);
        
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("receiverId", receiverId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                chatId.hashCode(), 
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageText));

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = chatId.hashCode();
        notificationManager.notify(notificationId, builder.build());
    }

    public static void showOfflineNotification(Context context, String senderName, String messageText, String chatId, String receiverId) {
        Log.d(TAG, "Showing offline notification from: " + senderName + " message: " + messageText);
        
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("receiverId", receiverId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                chatId.hashCode(), 
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Enhanced notification for offline status
        String offlineMessage = messageText + " (Will sync when online)";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(senderName)
                .setContentText(offlineMessage)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Lower priority for offline notifications
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(offlineMessage))
                .setOnlyAlertOnce(true); // Don't alert repeatedly for offline messages

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = chatId.hashCode();
        notificationManager.notify(notificationId, builder.build());
    }

    public static void showStatusNotification(Context context, String friendName) {
        Log.d(TAG, "Showing status notification from: " + friendName);
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("openStatus", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New Status Update")
                .setContentText(friendName + " added a new status")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }

    public static void showFriendRequestNotification(Context context, String requesterName) {
        Log.d(TAG, "Showing friend request notification from: " + requesterName);
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("openFriends", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_FRIENDS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New Friend Request")
                .setContentText(requesterName + " sent you a friend request")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }

    public static void cancelMessageNotification(Context context, String chatId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = chatId.hashCode();
        notificationManager.cancel(notificationId);
    }

    public static void cancelAllNotifications(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }
}