package com.pingme.android.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;

public class Status {
    private String id;
    private String userId;
    private String userName;
    private String userImageUrl;
    private String text;
    private String content;
    private String imageUrl;
    private String videoUrl;
    private String mediaUrl;
    private String mediaType;
    private String type;
    private String backgroundColor;
    private Timestamp timestamp;
    private long expiryTime;
    private boolean isViewed;
    
    @PropertyName("viewers")
    private Map<String, Long> viewers = new HashMap<>();
    private int viewerCount = 0;

    public Status() {
        // Required empty constructor for Firestore
        this.timestamp = Timestamp.now();
        this.expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours
        this.type = "text";
    }

    public Status(String userId, String userName, String userImageUrl, String text, String imageUrl) {
        this();
        this.userId = userId;
        this.userName = userName;
        this.userImageUrl = userImageUrl;
        this.text = text;
        this.content = text;
        this.imageUrl = imageUrl;
        this.isViewed = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserImageUrl() {
        return userImageUrl;
    }

    public void setUserImageUrl(String userImageUrl) {
        this.userImageUrl = userImageUrl;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.text = content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
        if (mediaUrl != null && mediaUrl.contains("video")) {
            this.videoUrl = mediaUrl;
            this.type = "video";
        } else {
            this.imageUrl = mediaUrl;
            this.type = "image";
        }
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
        this.type = mediaType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public boolean isViewed() {
        return isViewed;
    }

    public void setViewed(boolean viewed) {
        isViewed = viewed;
    }

    public Map<String, Long> getViewers() {
        return viewers;
    }

    public void setViewers(Map<String, Long> viewers) {
        this.viewers = viewers;
        this.viewerCount = viewers != null ? viewers.size() : 0;
    }

    public int getViewerCount() {
        return viewerCount;
    }

    public void setViewerCount(int viewerCount) {
        this.viewerCount = viewerCount;
    }

    public String getFormattedTimestamp() {
        if (timestamp == null) return "";
        
        long timeDiff = System.currentTimeMillis() - timestamp.toDate().getTime();
        long seconds = timeDiff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d ago";
        } else if (hours > 0) {
            return hours + "h ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return "Just now";
        }
    }

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
        if (timestamp == null) return "";
        
        long diff = System.currentTimeMillis() - timestamp.toDate().getTime();
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
}