package com.pingme.android.models;

import android.text.format.DateUtils;
import androidx.annotation.NonNull;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class Chat extends BaseObservable implements Comparable<Chat> {
    private String id;
    private User otherUser;
    private String lastMessage = "";
    private long lastMessageTimestamp;
    private String lastMessageSenderId = "";
    private String lastMessageType = "text";
    private int unreadCount = 0;
    private boolean isTyping = false;
    private boolean isMuted = false;
    private boolean isPinned = false;
    private long createdAt;
    private boolean isActive = true;

    public Chat() {
        this.createdAt = System.currentTimeMillis();
        this.lastMessageTimestamp = System.currentTimeMillis();
    }

    // Basic getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Bindable
    public User getOtherUser() { return otherUser; }
    public void setOtherUser(User otherUser) {
        this.otherUser = otherUser;
        notifyPropertyChanged(com.pingme.android.BR.otherUser);
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

    public String getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }

    public String getLastMessageType() { return lastMessageType != null ? lastMessageType : "text"; }
    public void setLastMessageType(String lastMessageType) { this.lastMessageType = lastMessageType; }

    @Bindable
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
        notifyPropertyChanged(com.pingme.android.BR.unreadCount);
    }

    @Bindable
    public boolean isTyping() { return isTyping; }
    public void setTyping(boolean typing) {
        isTyping = typing;
        notifyPropertyChanged(com.pingme.android.BR.typing);
    }

    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean muted) { isMuted = muted; }

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    // Binding helper methods
    @Bindable
    public String getLastMessagePreview() {
        if (isTyping) {
            return "typing...";
        }

        if (lastMessage == null || lastMessage.trim().isEmpty()) {
            if ("empty_chat".equals(lastMessageType)) {
                return "Tap to start messaging";
            }
            return "No messages yet";
        }

        // Handle different message types
        switch (lastMessageType) {
            case "image":
                return "📷 Image";
            case "video":
                return "🎥 Video";
            case "audio":
                return "🎤 Audio";
            case "document":
                return "📄 Document";
            default:
                return lastMessage;
        }
    }

    @Bindable
    public String getFormattedTime() {
        if (lastMessageTimestamp <= 0 || "empty_chat".equals(lastMessageType)) {
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

    @Bindable
    public boolean isEmpty() {
        return "empty_chat".equals(lastMessageType) ||
                lastMessage == null ||
                lastMessage.trim().isEmpty() ||
                lastMessageTimestamp <= 0;
    }

    // Helper method for date comparison
    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    // Comparable implementation for sorting
    @Override
    public int compareTo(Chat other) {
        // Pinned chats first
        if (this.isPinned && !other.isPinned) return -1;
        if (!this.isPinned && other.isPinned) return 1;

        // Active chats before empty chats
        boolean thisIsEmpty = this.isEmpty();
        boolean otherIsEmpty = other.isEmpty();

        if (!thisIsEmpty && otherIsEmpty) return -1;
        if (thisIsEmpty && !otherIsEmpty) return 1;

        // If both are empty, sort by user name
        if (thisIsEmpty && otherIsEmpty) {
            String thisName = this.otherUser != null ? this.otherUser.getDisplayName() : "";
            String otherName = other.otherUser != null ? other.otherUser.getDisplayName() : "";
            return thisName.compareToIgnoreCase(otherName);
        }

        // Both have messages - sort by timestamp (newest first)
        return Long.compare(other.lastMessageTimestamp, this.lastMessageTimestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chat chat = (Chat) o;
        return Objects.equals(id, chat.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @NonNull
    @Override
    public String toString() {
        return "Chat{" +
                "id='" + id + '\'' +
                ", otherUser=" + (otherUser != null ? otherUser.getDisplayName() : "null") +
                ", lastMessage='" + lastMessage + '\'' +
                ", lastMessageTimestamp=" + lastMessageTimestamp +
                ", unreadCount=" + unreadCount +
                ", isTyping=" + isTyping +
                '}';
    }

    // Copy constructor
    public Chat copy() {
        Chat copy = new Chat();
        copy.id = this.id;
        copy.otherUser = this.otherUser != null ? this.otherUser.copy() : null;
        copy.lastMessage = this.lastMessage;
        copy.lastMessageTimestamp = this.lastMessageTimestamp;
        copy.lastMessageSenderId = this.lastMessageSenderId;
        copy.lastMessageType = this.lastMessageType;
        copy.unreadCount = this.unreadCount;
        copy.isTyping = this.isTyping;
        copy.isMuted = this.isMuted;
        copy.isPinned = this.isPinned;
        copy.createdAt = this.createdAt;
        copy.isActive = this.isActive;
        return copy;
    }
}