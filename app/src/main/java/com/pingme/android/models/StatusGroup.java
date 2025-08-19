package com.pingme.android.models;

import java.util.ArrayList;
import java.util.List;

public class StatusGroup {
    private String userId;
    private String userName;
    private String userImageUrl;
    private List<Status> statuses;
    private Status latestStatus;
    private int unviewedCount;
    private int totalCount;
    private boolean hasUnviewedStatus;
    private long latestTimestamp;

    public StatusGroup() {
        this.statuses = new ArrayList<>();
        this.unviewedCount = 0;
        this.totalCount = 0;
        this.hasUnviewedStatus = false;
    }

    public StatusGroup(String userId, String userName, String userImageUrl) {
        this();
        this.userId = userId;
        this.userName = userName;
        this.userImageUrl = userImageUrl;
    }

    // Getters
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserImageUrl() { return userImageUrl; }
    public List<Status> getStatuses() { return statuses; }
    public Status getLatestStatus() { return latestStatus; }
    public int getUnviewedCount() { return unviewedCount; }
    public int getTotalCount() { return totalCount; }
    public boolean hasUnviewedStatus() { return hasUnviewedStatus; }
    public long getLatestTimestamp() { return latestTimestamp; }

    // Setters
    public void setUserId(String userId) { this.userId = userId; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setUserImageUrl(String userImageUrl) { this.userImageUrl = userImageUrl; }
    public void setStatuses(List<Status> statuses) { this.statuses = statuses; }
    public void setLatestStatus(Status latestStatus) { this.latestStatus = latestStatus; }
    public void setUnviewedCount(int unviewedCount) { this.unviewedCount = unviewedCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public void setHasUnviewedStatus(boolean hasUnviewedStatus) { this.hasUnviewedStatus = hasUnviewedStatus; }
    public void setLatestTimestamp(long latestTimestamp) { this.latestTimestamp = latestTimestamp; }

    // Helper methods
    public void addStatus(Status status) {
        if (status == null) return;
        
        statuses.add(status);
        totalCount = statuses.size();
        
        // Update latest status if this one is newer
        if (latestStatus == null || status.getTimestamp() > latestStatus.getTimestamp()) {
            latestStatus = status;
            latestTimestamp = status.getTimestamp();
        }
        
        // Update user info from latest status if not set
        if (userName == null || userName.isEmpty()) {
            userName = status.getUserName();
        }
        if (userImageUrl == null || userImageUrl.isEmpty()) {
            userImageUrl = status.getUserImageUrl();
        }
        
        updateViewedStatus();
    }

    public void updateViewedStatus() {
        unviewedCount = 0;
        for (Status status : statuses) {
            if (!status.isViewed()) {
                unviewedCount++;
            }
        }
        hasUnviewedStatus = unviewedCount > 0;
    }

    public String getStatusCountText() {
        if (totalCount <= 1) {
            return "";
        }
        return totalCount + " updates";
    }

    public String getPreviewText() {
        if (latestStatus == null) return "";
        
        if (latestStatus.isImageStatus() || latestStatus.isVideoStatus()) {
            return latestStatus.isImageStatus() ? "📷 Photo" : "🎥 Video";
        }
        
        String content = latestStatus.getContent();
        if (content != null && !content.isEmpty()) {
            return content.length() > 30 ? content.substring(0, 30) + "..." : content;
        }
        
        return "Status update";
    }

    public String getFormattedTimeAgo() {
        if (latestStatus != null) {
            return latestStatus.getFormattedTimeAgo();
        }
        return "";
    }

    // Get status ring color based on viewed status
    public int getStatusRingColor() {
        // This will be used to determine ring color in adapter
        return hasUnviewedStatus ? 1 : 0; // 1 for unviewed (green), 0 for viewed (gray)
    }
}