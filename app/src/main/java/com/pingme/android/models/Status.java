// Status.java
package com.pingme.android.models;

public class Status {
    private String id;
    private String userId;
    private String content;
    private String imageUrl;
    private long timestamp;
    private long expiryTime;
    private String backgroundColor;

    public Status() {}

    public Status(String userId, String content, String imageUrl, long timestamp, long expiryTime) {
        this.userId = userId;
        this.content = content;
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
        this.expiryTime = expiryTime;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getExpiryTime() { return expiryTime; }
    public void setExpiryTime(long expiryTime) { this.expiryTime = expiryTime; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
}