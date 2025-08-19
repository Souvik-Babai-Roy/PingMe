package com.pingme.android.models;

import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Status {
    private String id;
    private String userId;
    private String userName;
    private String userImageUrl;
    private String content;
    private String imageUrl;
    private String videoUrl;
    private long timestamp;
    private long expiryTime;
    private String backgroundColor;
    private String type; // text, image, video
    
    // Privacy and interaction
    @PropertyName("viewers")
    private Map<String, Long> viewers = new HashMap<>();
    private boolean isViewed = false;
    
    // For UI state
    private int viewerCount = 0;
    private boolean hasUnviewedStatus = false;
    private int totalStatusCount = 1;

    public Status() {
        this.timestamp = System.currentTimeMillis();
        this.expiryTime = timestamp + (24 * 60 * 60 * 1000); // 24 hours
        this.type = "text";
    }

    public Status(String userId, String content, String imageUrl, long timestamp, long expiryTime) {
        this();
        this.userId = userId;
        this.content = content;
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
        this.expiryTime = expiryTime;
        this.type = imageUrl != null ? "image" : "text";
    }

    public Status(String userId, String userName, String userImageUrl, String content, String type) {
        this();
        this.userId = userId;
        this.userName = userName;
        this.userImageUrl = userImageUrl;
        this.content = content;
        this.type = type != null ? type : "text";
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserImageUrl() { return userImageUrl; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public String getVideoUrl() { return videoUrl; }
    public long getTimestamp() { return timestamp; }
    public long getExpiryTime() { return expiryTime; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getType() { return type; }
    public Map<String, Long> getViewers() { return viewers; }
    public boolean isViewed() { return isViewed; }
    public int getViewerCount() { return viewerCount; }
    public boolean hasUnviewedStatus() { return hasUnviewedStatus; }
    public int getTotalStatusCount() { return totalStatusCount; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setUserImageUrl(String userImageUrl) { this.userImageUrl = userImageUrl; }
    public void setContent(String content) { this.content = content; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setExpiryTime(long expiryTime) { this.expiryTime = expiryTime; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
    public void setType(String type) { this.type = type; }
    public void setViewers(Map<String, Long> viewers) { this.viewers = viewers; }
    public void setViewed(boolean viewed) { isViewed = viewed; }
    public void setViewerCount(int viewerCount) { this.viewerCount = viewerCount; }
    public void setHasUnviewedStatus(boolean hasUnviewedStatus) { this.hasUnviewedStatus = hasUnviewedStatus; }
    public void setTotalStatusCount(int totalStatusCount) { this.totalStatusCount = totalStatusCount; }

    // Additional setters for compatibility
    public void setText(String text) { this.content = text; }
    public void setMediaUrl(String mediaUrl) { 
        if (mediaUrl != null && mediaUrl.contains("video")) {
            this.videoUrl = mediaUrl;
            this.type = "video";
        } else {
            this.imageUrl = mediaUrl;
            this.type = "image";
        }
    }
    public void setMediaType(String mediaType) { this.type = mediaType; }

    // Helper methods
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    public boolean isTextStatus() {
        return "text".equals(type);
    }

    public boolean isImageStatus() {
        return "image".equals(type);
    }

    public boolean isVideoStatus() {
        return "video".equals(type);
    }

    // Additional methods for compatibility with StatusAdapter
    public String getText() {
        return content;
    }

    public String getCaption() {
        return content;
    }

    public String getFormattedTimestamp() {
        return getFormattedTimeAgo();
    }

    public void addViewer(String viewerId) {
        if (viewers == null) {
            viewers = new HashMap<>();
        }
        viewers.put(viewerId, System.currentTimeMillis());
        viewerCount = viewers.size();
    }

    public boolean hasViewedBy(String viewerId) {
        return viewers != null && viewers.containsKey(viewerId);
    }

    public long getTimeLeft() {
        return Math.max(0, expiryTime - System.currentTimeMillis());
    }

    public String getFormattedTimeAgo() {
        long diff = System.currentTimeMillis() - timestamp;
        long hours = diff / (1000 * 60 * 60);
        long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);

        if (hours > 0) {
            return hours + "h";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return "now";
        }
    }

    public boolean canView(String viewerId) {
        return !isExpired() && viewerId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Status)) return false;
        Status status = (Status) o;
        return timestamp == status.timestamp &&
                expiryTime == status.expiryTime &&
                isViewed == status.isViewed &&
                viewerCount == status.viewerCount &&
                Objects.equals(id, status.id) &&
                Objects.equals(userId, status.userId) &&
                Objects.equals(userName, status.userName) &&
                Objects.equals(userImageUrl, status.userImageUrl) &&
                Objects.equals(content, status.content) &&
                Objects.equals(imageUrl, status.imageUrl) &&
                Objects.equals(videoUrl, status.videoUrl) &&
                Objects.equals(backgroundColor, status.backgroundColor) &&
                Objects.equals(type, status.type) &&
                Objects.equals(viewers, status.viewers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, userName, userImageUrl, content, imageUrl, 
                videoUrl, timestamp, expiryTime, backgroundColor, type, viewers, 
                isViewed, viewerCount);
    }

    @Override
    public String toString() {
        return "Status{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", userName='" + userName + '\'' +
                ", content='" + content + '\'' +
                ", type='" + type + '\'' +
                ", timestamp=" + timestamp +
                ", expiryTime=" + expiryTime +
                ", viewerCount=" + viewerCount +
                ", isExpired=" + isExpired() +
                '}';
    }
}