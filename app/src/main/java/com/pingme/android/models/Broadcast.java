package com.pingme.android.models;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

public class Broadcast extends BaseObservable {
    private String id;
    private String name;
    private String createdBy;
    private long createdAt;
    private String lastMessage;
    private long lastMessageTimestamp;
    private int unreadCount;
    private java.util.List<String> members;

    public Broadcast() {
        // Required empty constructor for Firestore
    }

    public Broadcast(String id, String name, String createdBy) {
        this.id = id;
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = System.currentTimeMillis();
        this.members = new java.util.ArrayList<>();
        this.unreadCount = 0;
    }

    @Bindable
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Bindable
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Bindable
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Bindable
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
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
    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    @Bindable
    public java.util.List<String> getMembers() {
        return members;
    }

    public void setMembers(java.util.List<String> members) {
        this.members = members;
    }

    public void addMember(String memberId) {
        if (members == null) {
            members = new java.util.ArrayList<>();
        }
        if (!members.contains(memberId)) {
            members.add(memberId);
        }
    }

    public void removeMember(String memberId) {
        if (members != null) {
            members.remove(memberId);
        }
    }

    public boolean hasMember(String memberId) {
        return members != null && members.contains(memberId);
    }

    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }
}