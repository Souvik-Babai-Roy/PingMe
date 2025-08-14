package com.pingme.android.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ChatHistory {
    private String chatId;
    private Map<String, DeletedMessage> deletedMessages;
    private Map<String, DeletedChat> deletedChats;

    public ChatHistory() {
        this.deletedMessages = new HashMap<>();
        this.deletedChats = new HashMap<>();
    }

    public ChatHistory(String chatId) {
        this.chatId = chatId;
        this.deletedMessages = new HashMap<>();
        this.deletedChats = new HashMap<>();
    }

    // Getters and setters
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public Map<String, DeletedMessage> getDeletedMessages() { return deletedMessages; }
    public void setDeletedMessages(Map<String, DeletedMessage> deletedMessages) { this.deletedMessages = deletedMessages; }

    public Map<String, DeletedChat> getDeletedChats() { return deletedChats; }
    public void setDeletedChats(Map<String, DeletedChat> deletedChats) { this.deletedChats = deletedChats; }

    // Helper methods
    public void addDeletedMessage(String messageId, DeletedMessage deletedMessage) {
        deletedMessages.put(messageId, deletedMessage);
    }

    public void addDeletedChat(String userId, DeletedChat deletedChat) {
        deletedChats.put(userId, deletedChat);
    }

    public DeletedMessage getDeletedMessage(String messageId) {
        return deletedMessages.get(messageId);
    }

    public DeletedChat getDeletedChat(String userId) {
        return deletedChats.get(userId);
    }

    public boolean hasDeletedMessages() {
        return !deletedMessages.isEmpty();
    }

    public boolean hasDeletedChats() {
        return !deletedChats.isEmpty();
    }

    // Inner classes
    public static class DeletedMessage {
        private Message originalMessage;
        private String deletedBy;
        private Long deletedAt;
        private String deletionType; // "clear_chat", "delete_message", "delete_chat"

        public DeletedMessage() {}

        public DeletedMessage(Message originalMessage, String deletedBy, String deletionType) {
            this.originalMessage = originalMessage;
            this.deletedBy = deletedBy;
            this.deletedAt = System.currentTimeMillis();
            this.deletionType = deletionType;
        }

        // Getters and setters
        public Message getOriginalMessage() { return originalMessage; }
        public void setOriginalMessage(Message originalMessage) { this.originalMessage = originalMessage; }

        public String getDeletedBy() { return deletedBy; }
        public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

        public String getDeletionType() { return deletionType; }
        public void setDeletionType(String deletionType) { this.deletionType = deletionType; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DeletedMessage that = (DeletedMessage) o;
            return Objects.equals(originalMessage, that.originalMessage) &&
                    Objects.equals(deletedBy, that.deletedBy) &&
                    Objects.equals(deletedAt, that.deletedAt) &&
                    Objects.equals(deletionType, that.deletionType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(originalMessage, deletedBy, deletedAt, deletionType);
        }
    }

    public static class DeletedChat {
        private ChatInfo chatInfo;
        private String deletedBy;
        private Long deletedAt;
        private String deletionType;

        public DeletedChat() {}

        public DeletedChat(ChatInfo chatInfo, String deletedBy, String deletionType) {
            this.chatInfo = chatInfo;
            this.deletedBy = deletedBy;
            this.deletedAt = System.currentTimeMillis();
            this.deletionType = deletionType;
        }

        // Getters and setters
        public ChatInfo getChatInfo() { return chatInfo; }
        public void setChatInfo(ChatInfo chatInfo) { this.chatInfo = chatInfo; }

        public String getDeletedBy() { return deletedBy; }
        public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

        public String getDeletionType() { return deletionType; }
        public void setDeletionType(String deletionType) { this.deletionType = deletionType; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DeletedChat that = (DeletedChat) o;
            return Objects.equals(chatInfo, that.chatInfo) &&
                    Objects.equals(deletedBy, that.deletedBy) &&
                    Objects.equals(deletedAt, that.deletedAt) &&
                    Objects.equals(deletionType, that.deletionType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chatInfo, deletedBy, deletedAt, deletionType);
        }
    }

    public static class ChatInfo {
        private String[] participants;
        private Long createdAt;
        private Long deletedAt;

        public ChatInfo() {}

        public ChatInfo(String[] participants, Long createdAt) {
            this.participants = participants;
            this.createdAt = createdAt;
        }

        // Getters and setters
        public String[] getParticipants() { return participants; }
        public void setParticipants(String[] participants) { this.participants = participants; }

        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

        public Long getDeletedAt() { return deletedAt; }
        public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChatInfo chatInfo = (ChatInfo) o;
            return Objects.equals(createdAt, chatInfo.createdAt) &&
                    Objects.equals(deletedAt, chatInfo.deletedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(createdAt, deletedAt);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatHistory that = (ChatHistory) o;
        return Objects.equals(chatId, that.chatId) &&
                Objects.equals(deletedMessages, that.deletedMessages) &&
                Objects.equals(deletedChats, that.deletedChats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, deletedMessages, deletedChats);
    }
}