package com.pingme.android.models;

import java.util.Map;
import java.util.Objects;
import java.util.HashMap;

public class Message {
    public static final int STATUS_SENT = 1;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_READ = 3;

    // String constants for type to match Realtime Database
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_DOCUMENT = "document";

    // Message actions
    public static final String ACTION_NONE = "none";
    public static final String ACTION_REPLY = "reply";
    public static final String ACTION_FORWARD = "forward";
    public static final String ACTION_EDIT = "edit";
    public static final String ACTION_DELETE_FOR_ME = "delete_for_me";
    public static final String ACTION_DELETE_FOR_EVERYONE = "delete_for_everyone";

    private String id;
    private String senderId;
    private String text;
    private long timestamp;
    private int status;
    private String type;
    
    // Search and navigation fields
    private String chatId;
    private String otherUserId;

    // Media fields
    private String imageUrl;
    private String videoUrl;
    private String audioUrl;
    private String thumbnailUrl;
    private long duration; // For audio/video in milliseconds
    private long fileSize; // For media files in bytes
    private String fileName; // For documents/files
    private String fileUrl; // For documents/files

    // Message actions and metadata
    private String action = ACTION_NONE;
    private String replyToMessageId; // For reply messages
    private String originalMessageId; // For forwarded messages
    private String editedText; // For edited messages
    private long editTimestamp; // When message was edited
    private boolean isEdited = false;
    private boolean isForwarded = false;
    private boolean isReply = false;
    
    // Deletion tracking
    private long deletedAt; // When message was deleted
    private String deletedBy; // Who deleted the message
    private boolean isDeletedForMe = false;
    private boolean isDeletedForEveryone = false;

    // Delivery tracking (WhatsApp-like)
    private Map<String, Long> deliveredTo; // Map of userId -> timestamp when delivered
    private Map<String, Long> readBy; // Map of userId -> timestamp when read

    // Chat management fields (NEW)
    private Map<String, Long> deletedFor; // Map of userId -> timestamp when deleted for user
    private Map<String, Long> clearedFor; // Map of userId -> timestamp when cleared for user

    // Encryption
    private boolean isEncrypted = false;
    private String encryptedContent; // For E2E encryption

    public Message() {
        this.type = TYPE_TEXT;
        this.status = STATUS_SENT;
        this.timestamp = System.currentTimeMillis();
        this.action = ACTION_NONE;
    }

    public Message(String senderId, String text, long timestamp, int status, String type) {
        this.senderId = senderId;
        this.text = text;
        this.timestamp = timestamp;
        this.status = status;
        this.type = type;
        this.action = ACTION_NONE;
    }

    // Constructor for text messages
    public static Message createTextMessage(String senderId, String text) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setText(text);
        message.setType(TYPE_TEXT);
        return message;
    }

    // Constructor for image messages
    public static Message createImageMessage(String senderId, String imageUrl) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setText("📷 Image");
        message.setImageUrl(imageUrl);
        message.setType(TYPE_IMAGE);
        return message;
    }

    // Constructor for video messages
    public static Message createVideoMessage(String senderId, String videoUrl, String thumbnailUrl) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setText("🎥 Video");
        message.setVideoUrl(videoUrl);
        message.setThumbnailUrl(thumbnailUrl);
        message.setType(TYPE_VIDEO);
        return message;
    }

    // Constructor for audio messages
    public static Message createAudioMessage(String senderId, String audioUrl, long duration) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setText("🎤 Audio");
        message.setAudioUrl(audioUrl);
        message.setDuration(duration);
        message.setType(TYPE_AUDIO);
        return message;
    }

    // Constructor for document messages
    public static Message createDocumentMessage(String senderId, String fileUrl, String fileName, long fileSize) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setText("📄 " + fileName);
        message.setFileUrl(fileUrl);
        message.setFileName(fileName);
        message.setFileSize(fileSize);
        message.setType(TYPE_DOCUMENT);
        return message;
    }

    // Constructor for reply messages
    public static Message createReplyMessage(String senderId, String text, String replyToMessageId) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setText(text);
        message.setType(TYPE_TEXT);
        message.setReplyToMessageId(replyToMessageId);
        message.setAction(ACTION_REPLY);
        message.setReply(true);
        return message;
    }

    // Constructor for forwarded messages
    public static Message createForwardedMessage(String senderId, Message originalMessage) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setText(originalMessage.getText());
        message.setType(originalMessage.getType());
        message.setImageUrl(originalMessage.getImageUrl());
        message.setVideoUrl(originalMessage.getVideoUrl());
        message.setAudioUrl(originalMessage.getAudioUrl());
        message.setFileUrl(originalMessage.getFileUrl());
        message.setFileName(originalMessage.getFileName());
        message.setFileSize(originalMessage.getFileSize());
        message.setOriginalMessageId(originalMessage.getId());
        message.setAction(ACTION_FORWARD);
        message.setForwarded(true);
        return message;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getText() { return text != null ? text : ""; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getType() { return type != null ? type : TYPE_TEXT; }
    public void setType(String type) { this.type = type; }

    // Media getters and setters
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    // Message actions getters and setters
    public String getAction() { return action != null ? action : ACTION_NONE; }
    public void setAction(String action) { this.action = action; }

    public String getReplyToMessageId() { return replyToMessageId; }
    public void setReplyToMessageId(String replyToMessageId) { this.replyToMessageId = replyToMessageId; }

    public String getOriginalMessageId() { return originalMessageId; }
    public void setOriginalMessageId(String originalMessageId) { this.originalMessageId = originalMessageId; }

    public String getEditedText() { return editedText; }
    public void setEditedText(String editedText) { this.editedText = editedText; }

    public long getEditTimestamp() { return editTimestamp; }
    public void setEditTimestamp(long editTimestamp) { this.editTimestamp = editTimestamp; }

    public boolean isEdited() { return isEdited; }
    public void setEdited(boolean edited) { isEdited = edited; }

    public boolean isForwarded() { return isForwarded; }
    public void setForwarded(boolean forwarded) { isForwarded = forwarded; }

    public boolean isReply() { return isReply; }
    public void setReply(boolean reply) { isReply = reply; }

    // Deletion getters and setters
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public boolean isDeletedForMe() { return isDeletedForMe; }
    public void setDeletedForMe(boolean deletedForMe) { isDeletedForMe = deletedForMe; }

    public boolean isDeletedForEveryone() { return isDeletedForEveryone; }
    public void setDeletedForEveryone(boolean deletedForEveryone) { isDeletedForEveryone = deletedForEveryone; }

    // Delivery tracking getters and setters
    public Map<String, Long> getDeliveredTo() { return deliveredTo; }
    public void setDeliveredTo(Map<String, Long> deliveredTo) { this.deliveredTo = deliveredTo; }

    public Map<String, Long> getReadBy() { return readBy; }
    public void setReadBy(Map<String, Long> readBy) { this.readBy = readBy; }

    // Chat management getters and setters
    public Map<String, Long> getDeletedFor() { return deletedFor; }
    public void setDeletedFor(Map<String, Long> deletedFor) { this.deletedFor = deletedFor; }

    public Map<String, Long> getClearedFor() { return clearedFor; }
    public void setClearedFor(Map<String, Long> clearedFor) { this.clearedFor = clearedFor; }

    // Encryption getters and setters
    public boolean isEncrypted() { return isEncrypted; }
    public void setEncrypted(boolean encrypted) { isEncrypted = encrypted; }

    public String getEncryptedContent() { return encryptedContent; }
    public void setEncryptedContent(String encryptedContent) { this.encryptedContent = encryptedContent; }

    // Search and navigation getters and setters
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getOtherUserId() { return otherUserId; }
    public void setOtherUserId(String otherUserId) { this.otherUserId = otherUserId; }

    // Helper methods
    public boolean isTextMessage() { return TYPE_TEXT.equals(type); }
    public boolean isImageMessage() { return TYPE_IMAGE.equals(type); }
    public boolean isVideoMessage() { return TYPE_VIDEO.equals(type); }
    public boolean isAudioMessage() { return TYPE_AUDIO.equals(type); }
    public boolean isDocumentMessage() { return TYPE_DOCUMENT.equals(type); }
    public boolean hasMedia() { return !TYPE_TEXT.equals(type); }

    public boolean isSentByCurrentUser(String currentUserId) {
        return currentUserId != null && currentUserId.equals(senderId);
    }

    public String getMediaUrl() {
        switch (getType()) {
            case TYPE_IMAGE:
                return getImageUrl();
            case TYPE_VIDEO:
                return getVideoUrl();
            case TYPE_AUDIO:
                return getAudioUrl();
            case TYPE_DOCUMENT:
                return getFileUrl();
            default:
                return null;
        }
    }

    public String getDisplayText() {
        if (isDeletedForEveryone) {
            return "This message was deleted";
        }
        
        switch (getType()) {
            case TYPE_IMAGE:
                return "📷 Image";
            case TYPE_VIDEO:
                return "🎥 Video";
            case TYPE_AUDIO:
                return "🎤 Audio";
            case TYPE_DOCUMENT:
                return "📄 " + (getFileName() != null ? getFileName() : "Document");
            case TYPE_TEXT:
            default:
                String displayText = getText();
                if (displayText == null) {
                    displayText = "";
                }
                if (isEdited) {
                    displayText += " (edited)";
                }
                return displayText;
        }
    }

    public boolean canBeEdited() {
        return isTextMessage() && !isDeletedForEveryone && !isDeletedForMe;
    }

    public boolean canBeDeleted() {
        return !isDeletedForEveryone;
    }

    public boolean isVisibleToUser(String userId) {
        if (isDeletedForEveryone) return false;
        if (isDeletedForMe && userId.equals(senderId)) return false;
        return true;
    }

    // Delivery tracking helper methods
    public boolean isDeliveredTo(String userId) {
        return deliveredTo != null && deliveredTo.containsKey(userId);
    }

    public boolean isReadBy(String userId) {
        return readBy != null && readBy.containsKey(userId);
    }

    public long getDeliveredTimestamp(String userId) {
        return deliveredTo != null ? deliveredTo.getOrDefault(userId, 0L) : 0L;
    }

    public long getReadTimestamp(String userId) {
        return readBy != null ? readBy.getOrDefault(userId, 0L) : 0L;
    }

    public int getDeliveryStatus(String currentUserId) {
        if (isSentByCurrentUser(currentUserId)) {
            // For sent messages, determine status based on recipient's interaction
            // Get the other user ID from the message context
            String recipientId = getRecipientId(currentUserId);
            
            if (recipientId != null) {
                // Check if read by the specific recipient
                if (readBy != null && readBy.containsKey(recipientId)) {
                    return STATUS_READ; // Blue double tick - message was read
                }
                // Check if delivered to the specific recipient  
                else if (deliveredTo != null && deliveredTo.containsKey(recipientId)) {
                    return STATUS_DELIVERED; // Gray double tick - message was delivered
                }
                // Otherwise, message is only sent
                else {
                    return STATUS_SENT; // Single gray tick - message sent but not delivered yet
                }
            } else {
                // Fallback to general status checking (for group chats or when recipient ID is unclear)
                if (readBy != null && !readBy.isEmpty()) {
                    return STATUS_READ; // Blue double tick
                } else if (deliveredTo != null && !deliveredTo.isEmpty()) {
                    return STATUS_DELIVERED; // Double tick
                } else {
                    return STATUS_SENT; // Single tick
                }
            }
        }
        return status; // For received messages, return original status
    }
    
    // Enhanced method that accepts recipient ID for accurate status calculation
    public int getDeliveryStatus(String currentUserId, String recipientId) {
        if (isSentByCurrentUser(currentUserId)) {
            // For sent messages, check status for the specific recipient
            if (recipientId != null) {
                // Check if read by the specific recipient
                if (readBy != null && readBy.containsKey(recipientId)) {
                    return STATUS_READ; // Blue double tick - message was read
                }
                // Check if delivered to the specific recipient  
                else if (deliveredTo != null && deliveredTo.containsKey(recipientId)) {
                    return STATUS_DELIVERED; // Gray double tick - message was delivered
                }
                // Otherwise, message is only sent
                else {
                    return STATUS_SENT; // Single gray tick - message sent but not delivered yet
                }
            } else {
                // Fallback to general method
                return getDeliveryStatus(currentUserId);
            }
        }
        return status; // For received messages, return original status
    }
    
    // Helper method to determine recipient ID for one-on-one chats
    private String getRecipientId(String currentUserId) {
        // In a one-on-one chat, the recipient is the other user
        // This would typically be determined from the chat context
        // For now, return null - this should be enhanced to get the actual recipient
        // In a full implementation, you'd pass the chat participant IDs or get them from chat context
        return null; // TODO: Implement proper recipient ID detection
    }

    public String getStatusText(String currentUserId) {
        if (!isSentByCurrentUser(currentUserId)) {
            return ""; // No status for received messages
        }

        int deliveryStatus = getDeliveryStatus(currentUserId);
        switch (deliveryStatus) {
            case STATUS_SENT:
                return "✓"; // Single tick
            case STATUS_DELIVERED:
                return "✓✓"; // Double tick
            case STATUS_READ:
                return "✓✓"; // Blue double tick (color handled in UI)
            default:
                return "";
        }
    }

    // Chat management helper methods
    public boolean isDeletedForUser(String userId) {
        return deletedFor != null && deletedFor.containsKey(userId);
    }

    public boolean isClearedForUser(String userId) {
        return clearedFor != null && clearedFor.containsKey(userId);
    }

    public boolean isVisibleForUser(String userId) {
        return !isDeletedForUser(userId) && !isClearedForUser(userId);
    }

    public void markAsDeletedForUser(String userId) {
        if (deletedFor == null) {
            deletedFor = new HashMap<>();
        }
        deletedFor.put(userId, System.currentTimeMillis());
    }

    public void markAsClearedForUser(String userId) {
        if (clearedFor == null) {
            clearedFor = new HashMap<>();
        }
        clearedFor.put(userId, System.currentTimeMillis());
    }

    public Long getDeletedTimestampForUser(String userId) {
        return deletedFor != null ? deletedFor.get(userId) : null;
    }

    public Long getClearedTimestampForUser(String userId) {
        return clearedFor != null ? clearedFor.get(userId) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return timestamp == message.timestamp &&
                status == message.status &&
                duration == message.duration &&
                fileSize == message.fileSize &&
                editTimestamp == message.editTimestamp &&
                isEdited == message.isEdited &&
                isForwarded == message.isForwarded &&
                isReply == message.isReply &&
                deletedAt == message.deletedAt &&
                isDeletedForMe == message.isDeletedForMe &&
                isDeletedForEveryone == message.isDeletedForEveryone &&
                isEncrypted == message.isEncrypted &&
                Objects.equals(id, message.id) &&
                Objects.equals(senderId, message.senderId) &&
                Objects.equals(text, message.text) &&
                Objects.equals(type, message.type) &&
                Objects.equals(imageUrl, message.imageUrl) &&
                Objects.equals(videoUrl, message.videoUrl) &&
                Objects.equals(audioUrl, message.audioUrl) &&
                Objects.equals(thumbnailUrl, message.thumbnailUrl) &&
                Objects.equals(fileName, message.fileName) &&
                Objects.equals(fileUrl, message.fileUrl) &&
                Objects.equals(action, message.action) &&
                Objects.equals(replyToMessageId, message.replyToMessageId) &&
                Objects.equals(originalMessageId, message.originalMessageId) &&
                Objects.equals(editedText, message.editedText) &&
                Objects.equals(deletedBy, message.deletedBy) &&
                Objects.equals(encryptedContent, message.encryptedContent) &&
                Objects.equals(deliveredTo, message.deliveredTo) &&
                Objects.equals(readBy, message.readBy) &&
                Objects.equals(deletedFor, message.deletedFor) &&
                Objects.equals(clearedFor, message.clearedFor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, senderId, text, timestamp, status, type, imageUrl,
                videoUrl, audioUrl, thumbnailUrl, duration, fileSize, fileName, fileUrl,
                action, replyToMessageId, originalMessageId, editedText, editTimestamp,
                isEdited, isForwarded, isReply, deletedAt, deletedBy, isDeletedForMe,
                isDeletedForEveryone, isEncrypted, encryptedContent, deliveredTo, readBy,
                deletedFor, clearedFor);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", senderId='" + senderId + '\'' +
                ", text='" + text + '\'' +
                ", timestamp=" + timestamp +
                ", status=" + status +
                ", type='" + type + '\'' +
                ", action='" + action + '\'' +
                ", hasMedia=" + hasMedia() +
                ", isEdited=" + isEdited +
                ", isForwarded=" + isForwarded +
                ", isReply=" + isReply +
                ", isDeletedForEveryone=" + isDeletedForEveryone +
                '}';
    }
}