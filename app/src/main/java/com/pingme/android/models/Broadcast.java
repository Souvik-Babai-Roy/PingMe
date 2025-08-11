package com.pingme.android.models;

import androidx.annotation.NonNull;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ArrayList;

public class Broadcast extends BaseObservable {
    private String id;
    private String name;
    private String createdBy;
    private long createdAt;
    private List<String> memberIds;
    private List<User> members;
    private String lastMessage = "";
    private long lastMessageTimestamp;
    private int unreadCount = 0;
    private boolean isActive = true;
    private String imageUrl; // Broadcast list image/icon

    public Broadcast() {
        this.createdAt = System.currentTimeMillis();
        this.lastMessageTimestamp = System.currentTimeMillis();
    }

    public Broadcast(String name, String createdBy, List<String> memberIds) {
        this();
        this.name = name;
        this.createdBy = createdBy;
        this.memberIds = memberIds;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Bindable
    public String getName() { return name != null ? name : ""; }
    public void setName(String name) {
        this.name = name;
        notifyPropertyChanged(com.pingme.android.BR.name);
    }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    @Bindable
    public List<User> getMembers() { return members; }
    public void setMembers(List<User> members) {
        this.members = members;
        notifyPropertyChanged(com.pingme.android.BR.members);
    }

    @Bindable
    public String getLastMessage() { return lastMessage != null ? lastMessage : ""; }
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
        notifyPropertyChanged(com.pingme.android.BR.lastMessage);
    }

    @Bindable
    public long getLastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
        notifyPropertyChanged(com.pingme.android.BR.lastMessageTimestamp);
    }

    @Bindable
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
        notifyPropertyChanged(com.pingme.android.BR.unreadCount);
    }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // Helper methods
    @Bindable
    public String getMemberCountText() {
        if (memberIds == null || memberIds.isEmpty()) {
            return "0 contacts";
        }
        return memberIds.size() + " contact" + (memberIds.size() > 1 ? "s" : "");
    }

    @Bindable
    public String getDisplayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return "Broadcast List";
    }

    @Bindable
    public String getFormattedTime() {
        if (lastMessageTimestamp <= 0) {
            return "";
        }

        Calendar today = Calendar.getInstance();
        Calendar messageDate = Calendar.getInstance();
        messageDate.setTimeInMillis(lastMessageTimestamp);

        // Check if it's today
        if (isSameDay(today, messageDate)) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            return timeFormat.format(new Date(lastMessageTimestamp));
        }

        // Check if it's yesterday
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, messageDate)) {
            return "Yesterday";
        }

        // Check if it's this week
        Calendar weekAgo = Calendar.getInstance();
        weekAgo.add(Calendar.DAY_OF_YEAR, -7);
        if (messageDate.after(weekAgo)) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            return dayFormat.format(new Date(lastMessageTimestamp));
        }

        // Older than a week
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        return dateFormat.format(new Date(lastMessageTimestamp));
    }

    @Bindable
    public String getUnreadCountText() {
        if (unreadCount <= 0) return "";
        if (unreadCount > 99) return "99+";
        return String.valueOf(unreadCount);
    }

    @Bindable
    public boolean getHasUnreadMessages() {
        return unreadCount > 0;
    }

    public boolean hasMember(String userId) {
        return memberIds != null && memberIds.contains(userId);
    }

    public int getMemberCount() {
        return memberIds != null ? memberIds.size() : 0;
    }

    public boolean isCreatedBy(String userId) {
        return createdBy != null && createdBy.equals(userId);
    }

    // Helper method for date comparison
    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Broadcast broadcast = (Broadcast) o;
        return Objects.equals(id, broadcast.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @NonNull
    @Override
    public String toString() {
        return "Broadcast{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", memberCount=" + getMemberCount() +
                ", lastMessage='" + lastMessage + '\'' +
                ", lastMessageTimestamp=" + lastMessageTimestamp +
                ", unreadCount=" + unreadCount +
                '}';
    }

    // Copy constructor
    public Broadcast copy() {
        Broadcast copy = new Broadcast();
        copy.id = this.id;
        copy.name = this.name;
        copy.createdBy = this.createdBy;
        copy.createdAt = this.createdAt;
        copy.memberIds = this.memberIds != null ? new ArrayList<>(this.memberIds) : null;
        copy.members = this.members != null ? new ArrayList<>(this.members) : null;
        copy.lastMessage = this.lastMessage;
        copy.lastMessageTimestamp = this.lastMessageTimestamp;
        copy.unreadCount = this.unreadCount;
        copy.isActive = this.isActive;
        copy.imageUrl = this.imageUrl;
        return copy;
    }
}