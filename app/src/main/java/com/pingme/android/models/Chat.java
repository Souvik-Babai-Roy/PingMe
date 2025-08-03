package com.pingme.android.models;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import com.pingme.android.BR;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class Chat extends BaseObservable {
    private String id;
    private String lastMessage;
    private long lastMessageTimestamp;
    private String lastMessageSenderId;
    private User otherUser;
    private int unreadCount;

    public Chat() {
        this.otherUser = new User();
        this.lastMessage = "";
        this.unreadCount = 0;
    }

    public Chat(String id, User otherUser, String lastMessage, long lastMessageTimestamp) {
        this.id = id;
        this.otherUser = otherUser;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.unreadCount = 0;
    }

    @Bindable
    public String getId() { return id; }

    @Bindable
    public String getLastMessage() { return lastMessage != null ? lastMessage : ""; }

    @Bindable
    public long getLastMessageTimestamp() { return lastMessageTimestamp; }

    @Bindable
    public String getLastMessageSenderId() { return lastMessageSenderId; }

    @Bindable
    public User getOtherUser() { return otherUser != null ? otherUser : new User(); }

    @Bindable
    public int getUnreadCount() { return unreadCount; }

    @Bindable
    public String getFormattedTime() {
        if (lastMessageTimestamp == 0) return "";

        Date date = new Date(lastMessageTimestamp);
        Date now = new Date();

        if (isSameDay(date, now)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        } else if (isYesterday(date, now)) {
            return "Yesterday";
        } else {
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
        }
    }

    public void setId(String id) {
        this.id = id;
        notifyPropertyChanged(BR.id);
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
        notifyPropertyChanged(BR.lastMessage);
    }

    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
        notifyPropertyChanged(BR.lastMessageTimestamp);
        notifyPropertyChanged(BR.formattedTime);
    }

    public void setLastMessageSenderId(String lastMessageSenderId) {
        this.lastMessageSenderId = lastMessageSenderId;
        notifyPropertyChanged(BR.lastMessageSenderId);
    }

    public void setOtherUser(User otherUser) {
        this.otherUser = otherUser;
        notifyPropertyChanged(BR.otherUser);
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
        notifyPropertyChanged(BR.unreadCount);
    }

    private boolean isSameDay(Date date1, Date date2) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return fmt.format(date1).equals(fmt.format(date2));
    }

    private boolean isYesterday(Date date, Date today) {
        long yesterday = today.getTime() - (24 * 60 * 60 * 1000);
        return isSameDay(date, new Date(yesterday));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chat chat = (Chat) o;
        return lastMessageTimestamp == chat.lastMessageTimestamp &&
                unreadCount == chat.unreadCount &&
                Objects.equals(id, chat.id) &&
                Objects.equals(lastMessage, chat.lastMessage) &&
                Objects.equals(lastMessageSenderId, chat.lastMessageSenderId) &&
                Objects.equals(otherUser, chat.otherUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lastMessage, lastMessageTimestamp, lastMessageSenderId, otherUser, unreadCount);
    }
}
