package com.pingme.android.models;

public class Message {
    public static final int STATUS_SENT = 1;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_READ = 3;

    public static final int TYPE_TEXT = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_VIDEO = 3;
    public static final int TYPE_AUDIO = 4;

    private String id;
    private String senderId;
    private String text;
    private long timestamp;
    private int status;
    private int type;

    // FIXED: Add fields for media messages
    private String imageUrl;
    private String videoUrl;
    private String audioUrl;
    private String thumbnailUrl;
    private long duration; // For audio/video
    private long fileSize; // For media files

    public Message() {}

    public Message(String senderId, String text, long timestamp, int status, int type) {
        this.senderId = senderId;
        this.text = text;
        this.timestamp = timestamp;
        this.status = status;
        this.type = type;
    }

    // FIXED: Constructor for image messages
    public Message(String senderId, String text, String imageUrl, long timestamp, int status) {
        this.senderId = senderId;
        this.text = text;
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
        this.status = status;
        this.type = TYPE_IMAGE;
    }

    // FIXED: Constructor for audio messages
    public Message(String senderId, String audioUrl, long duration, long timestamp, int status) {
        this.senderId = senderId;
        this.text = "Audio message";
        this.audioUrl = audioUrl;
        this.duration = duration;
        this.timestamp = timestamp;
        this.status = status;
        this.type = TYPE_AUDIO;
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

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    // FIXED: Media getters and setters
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

    // Helper methods
    public boolean isTextMessage() { return type == TYPE_TEXT; }
    public boolean isImageMessage() { return type == TYPE_IMAGE; }
    public boolean isVideoMessage() { return type == TYPE_VIDEO; }
    public boolean isAudioMessage() { return type == TYPE_AUDIO; }
    public boolean hasMedia() { return type != TYPE_TEXT; }
}