package com.pingme.android.models;

import androidx.annotation.NonNull;

import java.util.Objects;

public class User {
    private String id;
    private String name;
    private String email;
    private String phoneNumber;
    private String imageUrl;
    private String about;
    private boolean isOnline;
    private long lastSeen;
    private String fcmToken;

    // Privacy settings
    private boolean profilePhotoEnabled = true;
    private boolean lastSeenEnabled = true;
    private boolean aboutEnabled = true;
    private boolean readReceiptsEnabled = true;

    // Additional fields
    private long joinedAt;
    private String status;
    private boolean isBlocked;

    // Default constructor
    public User() {
        this.joinedAt = System.currentTimeMillis();
        this.about = "Hey there! I am using PingMe.";
        this.isOnline = false;
        this.lastSeen = 0;
    }

    // Constructor for registration
    public User(String name, String email, String phoneNumber) {
        this();
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    // Constructor for setup profile
    public User(String id, String name, String email, String imageUrl, String status) {
        this();
        this.id = id;
        this.name = name;
        this.email = email;
        this.imageUrl = imageUrl;
        this.status = status;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name != null ? name : ""; }
    public String getEmail() { return email != null ? email : ""; }
    public String getPhoneNumber() { return phoneNumber != null ? phoneNumber : ""; }
    public String getImageUrl() { return imageUrl; }
    public String getAbout() { return about != null ? about : "Hey there! I am using PingMe."; }
    public boolean isOnline() { return isOnline; }
    public long getLastSeen() { return lastSeen; }
    public String getFcmToken() { return fcmToken; }
    public long getJoinedAt() { return joinedAt; }
    public String getStatus() { return status; }
    public boolean isBlocked() { return isBlocked; }

    // Privacy settings getters
    public boolean isProfilePhotoEnabled() { return profilePhotoEnabled; }
    public boolean isLastSeenEnabled() { return lastSeenEnabled; }
    public boolean isAboutEnabled() { return aboutEnabled; }
    public boolean isReadReceiptsEnabled() { return readReceiptsEnabled; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setAbout(String about) { this.about = about; }
    public void setOnline(boolean online) { isOnline = online; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }
    public void setStatus(String status) { this.status = status; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }

    // Privacy settings setters
    public void setProfilePhotoEnabled(boolean profilePhotoEnabled) {
        this.profilePhotoEnabled = profilePhotoEnabled;
    }
    public void setLastSeenEnabled(boolean lastSeenEnabled) {
        this.lastSeenEnabled = lastSeenEnabled;
    }
    public void setAboutEnabled(boolean aboutEnabled) {
        this.aboutEnabled = aboutEnabled;
    }
    public void setReadReceiptsEnabled(boolean readReceiptsEnabled) {
        this.readReceiptsEnabled = readReceiptsEnabled;
    }

    // Helper methods
    public boolean hasProfilePhoto() {
        return imageUrl != null && !imageUrl.trim().isEmpty() && profilePhotoEnabled;
    }

    public String getDisplayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        } else if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            return phoneNumber;
        } else if (email != null && !email.trim().isEmpty()) {
            return email.split("@")[0];
        }
        return "Unknown User";
    }

    public String getDisplayAbout() {
        return aboutEnabled ? getAbout() : "";
    }

    public boolean shouldShowLastSeen() {
        return lastSeenEnabled;
    }

    public boolean shouldShowProfilePhoto() {
        return profilePhotoEnabled;
    }

    public boolean shouldShowReadReceipts() {
        return readReceiptsEnabled;
    }

    public String getOnlineStatus() {
        if (!lastSeenEnabled) {
            return "";
        }

        if (isOnline) {
            return "online";
        } else if (lastSeen > 0) {
            long diff = System.currentTimeMillis() - lastSeen;
            long minutes = diff / (1000 * 60);
            long hours = minutes / 60;
            long days = hours / 24;

            if (minutes < 1) return "last seen just now";
            if (minutes < 60) return "last seen " + minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
            if (hours < 24) return "last seen " + hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            if (days < 7) return "last seen " + days + " day" + (days > 1 ? "s" : "") + " ago";
            return "last seen a long time ago";
        }
        return "offline";
    }

    // Validation methods
    public boolean isValidEmail() {
        return email != null && email.contains("@") && email.contains(".");
    }

    public boolean isValidPhoneNumber() {
        return phoneNumber != null && phoneNumber.length() >= 10;
    }

    public boolean isComplete() {
        return name != null && !name.trim().isEmpty() &&
                (isValidEmail() || isValidPhoneNumber());
    }

    // Copy
    public User copy() {
        User copy = new User();
        copy.id = this.id;
        copy.name = this.name;
        copy.email = this.email;
        copy.phoneNumber = this.phoneNumber;
        copy.imageUrl = this.imageUrl;
        copy.about = this.about;
        copy.isOnline = this.isOnline;
        copy.lastSeen = this.lastSeen;
        copy.fcmToken = this.fcmToken;
        copy.profilePhotoEnabled = this.profilePhotoEnabled;
        copy.lastSeenEnabled = this.lastSeenEnabled;
        copy.aboutEnabled = this.aboutEnabled;
        copy.readReceiptsEnabled = this.readReceiptsEnabled;
        copy.joinedAt = this.joinedAt;
        copy.status = this.status;
        copy.isBlocked = this.isBlocked;
        return copy;
    }

    // Equality
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return isOnline == user.isOnline &&
                lastSeen == user.lastSeen &&
                profilePhotoEnabled == user.profilePhotoEnabled &&
                lastSeenEnabled == user.lastSeenEnabled &&
                aboutEnabled == user.aboutEnabled &&
                readReceiptsEnabled == user.readReceiptsEnabled &&
                joinedAt == user.joinedAt &&
                isBlocked == user.isBlocked &&
                Objects.equals(id, user.id) &&
                Objects.equals(name, user.name) &&
                Objects.equals(email, user.email) &&
                Objects.equals(phoneNumber, user.phoneNumber) &&
                Objects.equals(imageUrl, user.imageUrl) &&
                Objects.equals(about, user.about) &&
                Objects.equals(fcmToken, user.fcmToken) &&
                Objects.equals(status, user.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, email, phoneNumber, imageUrl, about, isOnline,
                lastSeen, fcmToken, profilePhotoEnabled, lastSeenEnabled, aboutEnabled,
                readReceiptsEnabled, joinedAt, status, isBlocked);
    }

    @NonNull
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", isOnline=" + isOnline +
                ", lastSeen=" + lastSeen +
                ", profilePhotoEnabled=" + profilePhotoEnabled +
                ", lastSeenEnabled=" + lastSeenEnabled +
                ", readReceiptsEnabled=" + readReceiptsEnabled +
                ", isBlocked=" + isBlocked +
                '}';
    }
}
