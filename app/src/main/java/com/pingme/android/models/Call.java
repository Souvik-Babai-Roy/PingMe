// Call.java
package com.pingme.android.models;

public class Call {
    private String id;
    private String contactName;
    private String contactImage;
    private String callType; // incoming, outgoing, missed
    private String callMode; // audio, video
    private long timestamp;
    private long duration;

    public Call() {}

    public Call(String contactName, String contactImage, String callType, String callMode, long timestamp, long duration) {
        this.contactName = contactName;
        this.contactImage = contactImage;
        this.callType = callType;
        this.callMode = callMode;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactImage() { return contactImage; }
    public void setContactImage(String contactImage) { this.contactImage = contactImage; }

    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }

    public String getCallMode() { return callMode; }
    public void setCallMode(String callMode) { this.callMode = callMode; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
}
