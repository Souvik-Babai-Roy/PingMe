package com.pingme.android.models;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;

public class Chat extends BaseObservable {
    private String id;
    private User otherUser;
    private String lastMessage;
    private long lastMessageTimestamp;
    private String lastMessageSenderId;
    private String lastMessageType;
    private int unreadCount;
    private boolean typing;
    private String typingUserId;
    private long createdAt;
    private boolean isActive;
    
    // Add missing fields that are being written to Firestore
    @PropertyName("lastMessageId")
    private String lastMessageId;
    
    @PropertyName("participants")
    private Map<String, Boolean> participants = new HashMap<>();

    public Chat() {
        // Required empty constructor for Firestore
    }

    public Chat(String id, User otherUser) {
        this.id = id;
        this.otherUser = otherUser;
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
        this.unreadCount = 0;
        this.typing = false;
    }

    @Bindable
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Bindable
    public User getOtherUser() {
        return otherUser;
    }

    public void setOtherUser(User otherUser) {
        this.otherUser = otherUser;
    }

    @Bindable
    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    @Bindable
    public long getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    @Bindable
    public String getLastMessageSenderId() {
        return lastMessageSenderId;
    }

    public void setLastMessageSenderId(String lastMessageSenderId) {
        this.lastMessageSenderId = lastMessageSenderId;
    }

    @Bindable
    public String getLastMessageType() {
        return lastMessageType;
    }

    public void setLastMessageType(String lastMessageType) {
        this.lastMessageType = lastMessageType;
    }

    @Bindable
    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    @Bindable
    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }

    @Bindable
    public String getTypingUserId() {
        return typingUserId;
    }

    public void setTypingUserId(String typingUserId) {
        this.typingUserId = typingUserId;
    }

    @Bindable
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Bindable
    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    // Add getters and setters for the missing fields
    @Bindable
    public String getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(String lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    @Bindable
    public Map<String, Boolean> getParticipants() {
        return participants;
    }

    public void setParticipants(Map<String, Boolean> participants) {
        this.participants = participants;
    }

    // Helper methods
    public boolean hasUnreadMessages() {
        return unreadCount > 0;
    }

    public String getUnreadCountText() {
        if (unreadCount <= 0) return "";
        if (unreadCount > 99) return "99+";
        return String.valueOf(unreadCount);
    }

    public boolean isLastMessageFromOtherUser(String currentUserId) {
        return lastMessageSenderId != null && !lastMessageSenderId.equals(currentUserId);
    }
}