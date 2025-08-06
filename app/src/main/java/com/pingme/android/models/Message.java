package com.pingme.android.models;

import java.util.Objects;

public class Message {
    public static final int STATUS_SENT = 1;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_READ = 3;

    // String constants for type to match Realtime Database
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";

    private String id;
    private String senderId;
    private String text;
    private long timestamp;
    private int status;
    private String type;

    // Media fields
    private String imageUrl;
    private String videoUrl;
    private String audioUrl;
    private String thumbnailUrl;
    private long duration; // For audio/video in milliseconds
    private long fileSize; // For media files in bytes
    private String fileName; // For documents/files

    public Message() {
        this.type = TYPE_TEXT;
        this.status = STATUS_SENT;
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String senderId, String text, long timestamp, int status, String type) {
        this.senderId = senderId;
        this.text = text;
        this.timestamp = timestamp;
        this.status = status;
        this.type = type;
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

    // Helper methods
    public boolean isTextMessage() { return TYPE_TEXT.equals(type); }
    public boolean isImageMessage() { return TYPE_IMAGE.equals(type); }
    public boolean isVideoMessage() { return TYPE_VIDEO.equals(type); }
    public boolean isAudioMessage() { return TYPE_AUDIO.equals(type); }
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
            default:
                return null;
        }
    }

    public String getDisplayText() {
        switch (getType()) {
            case TYPE_IMAGE:
                return "📷 Image";
            case TYPE_VIDEO:
                return "🎥 Video";
            case TYPE_AUDIO:
                return "🎤 Audio";
            case TYPE_TEXT:
            default:
                return getText();
        }
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
                Objects.equals(id, message.id) &&
                Objects.equals(senderId, message.senderId) &&
                Objects.equals(text, message.text) &&
                Objects.equals(type, message.type) &&
                Objects.equals(imageUrl, message.imageUrl) &&
                Objects.equals(videoUrl, message.videoUrl) &&
                Objects.equals(audioUrl, message.audioUrl) &&
                Objects.equals(thumbnailUrl, message.thumbnailUrl) &&
                Objects.equals(fileName, message.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, senderId, text, timestamp, status, type, imageUrl,
                videoUrl, audioUrl, thumbnailUrl, duration, fileSize, fileName);
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
                ", hasMedia=" + hasMedia() +
                '}';
    }
}