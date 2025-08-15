package com.pingme.android.utils;

import android.content.Context;
import android.util.Log;

import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageSharingUtil {
    private static final String TAG = "MessageSharingUtil";
    
    public interface ShareCallback {
        void onShareComplete(int successCount, int totalCount);
        void onShareProgress(int completedCount, int totalCount);
        void onShareError(String error);
    }
    
    /**
     * Share a message to multiple users with WhatsApp-like functionality
     */
    public static void shareMessage(String originalChatId, Message originalMessage, 
                                  List<User> recipients, String senderId, ShareCallback callback) {
        
        if (recipients == null || recipients.isEmpty()) {
            callback.onShareError("No recipients selected");
            return;
        }
        
        int totalRecipients = recipients.size();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        
        Log.d(TAG, "Sharing message to " + totalRecipients + " recipients");
        
        for (User recipient : recipients) {
            String chatId = FirebaseUtil.generateChatId(senderId, recipient.getId());
            
            // Create forward message data
            Map<String, Object> messageData = createForwardMessageData(originalMessage, senderId);
            
            // Send the forwarded message
            FirebaseUtil.sendMessageWithDeliveryTracking(chatId, senderId,
                    getForwardedMessageText(originalMessage),
                    originalMessage.getType(),
                    messageData)
                    .addOnCompleteListener(task -> {
                        int completed = completedCount.incrementAndGet();
                        
                        if (task.isSuccessful()) {
                            successCount.incrementAndGet();
                            Log.d(TAG, "Message shared successfully to " + recipient.getName());
                        } else {
                            Log.e(TAG, "Failed to share message to " + recipient.getName(), task.getException());
                        }
                        
                        // Report progress
                        callback.onShareProgress(completed, totalRecipients);
                        
                        // Check if all shares are complete
                        if (completed == totalRecipients) {
                            callback.onShareComplete(successCount.get(), totalRecipients);
                        }
                    });
        }
    }
    
    /**
     * Share multiple messages as a bundle
     */
    public static void shareMultipleMessages(String originalChatId, List<Message> messages,
                                           List<User> recipients, String senderId, ShareCallback callback) {
        
        if (messages == null || messages.isEmpty()) {
            callback.onShareError("No messages to share");
            return;
        }
        
        if (recipients == null || recipients.isEmpty()) {
            callback.onShareError("No recipients selected");
            return;
        }
        
        int totalOperations = recipients.size() * messages.size();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        
        Log.d(TAG, "Sharing " + messages.size() + " messages to " + recipients.size() + " recipients");
        
        for (User recipient : recipients) {
            String chatId = FirebaseUtil.generateChatId(senderId, recipient.getId());
            
            for (Message message : messages) {
                Map<String, Object> messageData = createForwardMessageData(message, senderId);
                
                FirebaseUtil.sendMessageWithDeliveryTracking(chatId, senderId,
                        getForwardedMessageText(message),
                        message.getType(),
                        messageData)
                        .addOnCompleteListener(task -> {
                            int completed = completedCount.incrementAndGet();
                            
                            if (task.isSuccessful()) {
                                successCount.incrementAndGet();
                            }
                            
                            // Report progress
                            callback.onShareProgress(completed, totalOperations);
                            
                            // Check if all shares are complete
                            if (completed == totalOperations) {
                                callback.onShareComplete(successCount.get(), totalOperations);
                            }
                        });
            }
        }
    }
    
    /**
     * Create forwarded message data with proper metadata
     */
    private static Map<String, Object> createForwardMessageData(Message originalMessage, String senderId) {
        Map<String, Object> messageData = new HashMap<>();
        
        // Mark as forwarded
        messageData.put("isForwarded", true);
        messageData.put("originalSenderId", originalMessage.getSenderId());
        
        // Copy media data if present
        if (originalMessage.getImageUrl() != null) {
            messageData.put("imageUrl", originalMessage.getImageUrl());
        }
        if (originalMessage.getVideoUrl() != null) {
            messageData.put("videoUrl", originalMessage.getVideoUrl());
        }
        if (originalMessage.getAudioUrl() != null) {
            messageData.put("audioUrl", originalMessage.getAudioUrl());
        }
        if (originalMessage.getFileUrl() != null) {
            messageData.put("fileUrl", originalMessage.getFileUrl());
        }
        if (originalMessage.getThumbnailUrl() != null) {
            messageData.put("thumbnailUrl", originalMessage.getThumbnailUrl());
        }
        if (originalMessage.getFileName() != null) {
            messageData.put("fileName", originalMessage.getFileName());
        }
        if (originalMessage.getFileSize() > 0) {
            messageData.put("fileSize", originalMessage.getFileSize());
        }
        if (originalMessage.getDuration() > 0) {
            messageData.put("duration", originalMessage.getDuration());
        }
        
        return messageData;
    }
    
    /**
     * Get appropriate text for forwarded message
     */
    private static String getForwardedMessageText(Message originalMessage) {
        String text = originalMessage.getText();
        
        if (text == null || text.trim().isEmpty()) {
            // For media messages without text
            switch (originalMessage.getType()) {
                case "image":
                    return "📷 Image";
                case "video":
                    return "🎥 Video";
                case "audio":
                    return "🎤 Audio";
                case "document":
                    return "📄 " + (originalMessage.getFileName() != null ?
                            originalMessage.getFileName() : "Document");
                default:
                    return "📎 Media";
            }
        }
        
        return text;
    }
    
    /**
     * Share message via external apps (WhatsApp, Telegram, etc.)
     */
    public static void shareMessageExternal(Context context, Message message) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        
        String shareText = getShareableText(message);
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
        
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share message via"));
    }
    
    /**
     * Get shareable text for external sharing
     */
    private static String getShareableText(Message message) {
        StringBuilder text = new StringBuilder();
        
        if (message.getText() != null && !message.getText().trim().isEmpty()) {
            text.append(message.getText());
        } else {
            text.append("Shared from PingMe: ");
            text.append(getForwardedMessageText(message));
        }
        
        return text.toString();
    }
    
    /**
     * Record message sharing for analytics (optional)
     */
    public static void recordMessageShare(String messageId, String originalChatId, 
                                        List<String> recipientIds, String shareType) {
        // This can be used for analytics or tracking
        Map<String, Object> shareRecord = new HashMap<>();
        shareRecord.put("messageId", messageId);
        shareRecord.put("originalChatId", originalChatId);
        shareRecord.put("recipientCount", recipientIds.size());
        shareRecord.put("shareType", shareType); // "forward", "external", etc.
        shareRecord.put("timestamp", System.currentTimeMillis());
        
        // Store in analytics collection (optional)
        FirebaseFirestore.getInstance()
                .collection("message_shares")
                .add(shareRecord)
                .addOnSuccessListener(doc -> Log.d(TAG, "Share recorded: " + doc.getId()))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to record share", e));
    }
}