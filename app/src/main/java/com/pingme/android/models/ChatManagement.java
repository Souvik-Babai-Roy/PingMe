package com.pingme.android.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ChatManagement {
    private String chatId;
    private Map<String, ParticipantInfo> participants;
    private ChatInfo chatInfo;

    public ChatManagement() {
        this.participants = new HashMap<>();
        this.chatInfo = new ChatInfo();
    }

    public ChatManagement(String chatId) {
        this.chatId = chatId;
        this.participants = new HashMap<>();
        this.chatInfo = new ChatInfo();
    }

    // Getters and setters
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public Map<String, ParticipantInfo> getParticipants() { return participants; }
    public void setParticipants(Map<String, ParticipantInfo> participants) { this.participants = participants; }

    public ChatInfo getChatInfo() { return chatInfo; }
    public void setChatInfo(ChatInfo chatInfo) { this.chatInfo = chatInfo; }

    // Helper methods
    public void addParticipant(String userId) {
        if (!participants.containsKey(userId)) {
            participants.put(userId, new ParticipantInfo());
        }
    }

    public ParticipantInfo getParticipantInfo(String userId) {
        return participants.get(userId);
    }

    public boolean isChatActiveForUser(String userId) {
        ParticipantInfo info = participants.get(userId);
        return info != null && info.isActive() && info.getDeletedAt() == null;
    }

    public boolean isChatClearedForUser(String userId) {
        ParticipantInfo info = participants.get(userId);
        return info != null && info.getClearedAt() != null;
    }

    public boolean isChatDeletedForUser(String userId) {
        ParticipantInfo info = participants.get(userId);
        return info != null && info.getDeletedAt() != null;
    }

    public void markChatAsClearedForUser(String userId) {
        ParticipantInfo info = participants.get(userId);
        if (info != null) {
            info.setClearedAt(System.currentTimeMillis());
        }
    }

    public void markChatAsDeletedForUser(String userId) {
        ParticipantInfo info = participants.get(userId);
        if (info != null) {
            info.setDeletedAt(System.currentTimeMillis());
            info.setActive(false);
        }
    }

    public void updateLastSeen(String userId, long timestamp) {
        ParticipantInfo info = participants.get(userId);
        if (info != null) {
            info.setLastSeen(timestamp);
        }
    }

    public void updateUnreadCount(String userId, int count) {
        ParticipantInfo info = participants.get(userId);
        if (info != null) {
            info.setUnreadCount(count);
        }
    }

    // Inner classes
    public static class ParticipantInfo {
        private boolean isActive = true;
        private Long deletedAt = null;
        private Long clearedAt = null;
        private Long lastSeen = null;
        private int unreadCount = 0;

        public ParticipantInfo() {}

        // Getters and setters
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }

        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

        public Long getClearedAt() { return clearedAt; }
        public void setClearedAt(Long clearedAt) { this.clearedAt = clearedAt; }

        public Long getLastSeen() { return lastSeen; }
        public void setLastSeen(Long lastSeen) { this.lastSeen = lastSeen; }

        public int getUnreadCount() { return unreadCount; }
        public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParticipantInfo that = (ParticipantInfo) o;
            return isActive == that.isActive &&
                    unreadCount == that.unreadCount &&
                    Objects.equals(deletedAt, that.deletedAt) &&
                    Objects.equals(clearedAt, that.clearedAt) &&
                    Objects.equals(lastSeen, that.lastSeen);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isActive, deletedAt, clearedAt, lastSeen, unreadCount);
        }
    }

    public static class ChatInfo {
        private Long createdAt = System.currentTimeMillis();
        private String lastMessage = "";
        private Long lastMessageTimestamp = 0L;
        private String lastMessageSenderId = "";
        private String lastMessageType = "text";
        private String lastMessageId = "";

        public ChatInfo() {}

        // Getters and setters
        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

        public Long getLastMessageTimestamp() { return lastMessageTimestamp; }
        public void setLastMessageTimestamp(Long lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }

        public String getLastMessageSenderId() { return lastMessageSenderId; }
        public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }

        public String getLastMessageType() { return lastMessageType; }
        public void setLastMessageType(String lastMessageType) { this.lastMessageType = lastMessageType; }

        public String getLastMessageId() { return lastMessageId; }
        public void setLastMessageId(String lastMessageId) { this.lastMessageId = lastMessageId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChatInfo chatInfo = (ChatInfo) o;
            return Objects.equals(createdAt, chatInfo.createdAt) &&
                    Objects.equals(lastMessage, chatInfo.lastMessage) &&
                    Objects.equals(lastMessageTimestamp, chatInfo.lastMessageTimestamp) &&
                    Objects.equals(lastMessageSenderId, chatInfo.lastMessageSenderId) &&
                    Objects.equals(lastMessageType, chatInfo.lastMessageType) &&
                    Objects.equals(lastMessageId, chatInfo.lastMessageId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(createdAt, lastMessage, lastMessageTimestamp, lastMessageSenderId, lastMessageType, lastMessageId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatManagement that = (ChatManagement) o;
        return Objects.equals(chatId, that.chatId) &&
                Objects.equals(participants, that.participants) &&
                Objects.equals(chatInfo, that.chatInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, participants, chatInfo);
    }
}