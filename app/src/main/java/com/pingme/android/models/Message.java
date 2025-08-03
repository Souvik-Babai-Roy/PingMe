// Message.java
package com.pingme.android.models;

public class Message {
    public static final int STATUS_SENT = 1;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_READ = 3;

    public static final int TYPE_TEXT = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_VIDEO = 3;
    public static final int TYPE_AUDIO = 4;

    private String id;
    private String senderId;
    private String text;
    private long timestamp;
    private int status;
    private int type;

    public Message() {}

    public Message(String senderId, String text, long timestamp, int status, int type) {
        this.senderId = senderId;
        this.text = text;
        this.timestamp = timestamp;
        this.status = status;
        this.type = type;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
}