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
    private java.util.List<String> memberIds;
    private boolean isActive = true;

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

    public void setCreatorId(String creatorId) {
        this.createdBy = creatorId;
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
        if (memberIds == null) {
            memberIds = new java.util.ArrayList<>();
        }
        if (!memberIds.contains(memberId)) {
            memberIds.add(memberId);
        }
    }

    public void removeMember(String memberId) {
        if (members != null) {
            members.remove(memberId);
        }
        if (memberIds != null) {
            memberIds.remove(memberId);
        }
    }

    public boolean hasMember(String memberId) {
        return (members != null && members.contains(memberId)) || (memberIds != null && memberIds.contains(memberId));
    }

    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }

    public java.util.List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(java.util.List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}