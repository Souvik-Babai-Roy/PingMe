package com.pingme.android.models;

import androidx.annotation.NonNull;
import com.google.firebase.firestore.PropertyName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class User {
    private String id;
    private String name;
    private String email;
    private String phoneNumber;
    private String imageUrl;
    private String about;
    @PropertyName("isOnline")
    private boolean isOnline;
    private long lastSeen;
    private String fcmToken;

    // Privacy settings - with Firestore field name mappings
    @PropertyName("profile_photo_enabled")
    private boolean profilePhotoEnabled = true;
    @PropertyName("last_seen_enabled")
    private boolean lastSeenEnabled = true;
    @PropertyName("about_enabled")
    private boolean aboutEnabled = true;
    @PropertyName("read_receipts_enabled")
    private boolean readReceiptsEnabled = true;

    // Additional fields
    private long joinedAt;
    private boolean isBlocked;
    
    // Friend relationship status (not stored in Firestore, used for UI state)
    private String friendshipStatus = "none"; // none, friend, blocked, pending
    
    // Personal name for friends (custom nickname)
    private String personalName;

    // Add missing field mappings for Firestore
    @PropertyName("displayName")
    private String displayName;
    
    @PropertyName("onlineStatus")
    private String onlineStatus;
    
    @PropertyName("notificationsEnabled")
    private boolean notificationsEnabled = true;
    
    @PropertyName("friend")
    private boolean friend = false;
    
    @PropertyName("validEmail")
    private boolean validEmail = true;
    
    @PropertyName("theme")
    private String theme = "auto";
    
    @PropertyName("blockedByMe")
    private boolean blockedByMe = false;
    
    @PropertyName("displayAbout")
    private String displayAbout;
    
    @PropertyName("complete")
    private boolean complete = false;

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
    }

    // Constructor for setup profile
    public User(String id, String name, String email, String imageUrl) {
        this();
        this.id = id;
        this.name = name;
        this.email = email;
        this.imageUrl = imageUrl;
    }

    // Constructor for setup profile with phone number
    public User(String id, String name, String email, String phoneNumber, String imageUrl) {
        this();
        this.id = id;
        this.name = name;
        this.email = email;
        this.imageUrl = imageUrl;
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
    public boolean isBlocked() { return isBlocked; }
    public String getFriendshipStatus() { return friendshipStatus; }
    public String getPersonalName() { return personalName; }

    // Privacy settings getters
    public boolean isProfilePhotoEnabled() { return profilePhotoEnabled; }
    public boolean isLastSeenEnabled() { return lastSeenEnabled; }
    public boolean isAboutEnabled() { return aboutEnabled; }
    public boolean isReadReceiptsEnabled() { return readReceiptsEnabled; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; } // Updated to use field

    // Getters for new fields
    public String getDisplayName() { 
        // Use the proper display name logic that handles personal names
        return getDisplayNameForUser(); 
    }
    public String getOnlineStatus() { return onlineStatus; }
    public boolean isFriend() { return friend; }
    public boolean isValidEmail() { return validEmail; }
    public String getTheme() { return theme; }
    public boolean isBlockedByMe() { return blockedByMe; }
    public String getDisplayAbout() { return displayAbout; }
    public boolean isComplete() { return complete; }

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
    public void setBlocked(boolean blocked) { isBlocked = blocked; }
    public void setFriendshipStatus(String friendshipStatus) { this.friendshipStatus = friendshipStatus; }
    public void setPersonalName(String personalName) { this.personalName = personalName; }

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

    // Setters for new fields
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setOnlineStatus(String onlineStatus) { this.onlineStatus = onlineStatus; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public void setFriend(boolean friend) { this.friend = friend; }
    public void setValidEmail(boolean validEmail) { this.validEmail = validEmail; }
    public void setTheme(String theme) { this.theme = theme; }
    public void setBlockedByMe(boolean blockedByMe) { this.blockedByMe = blockedByMe; }
    public void setDisplayAbout(String displayAbout) { this.displayAbout = displayAbout; }
    public void setComplete(boolean complete) { this.complete = complete; }

    // Helper methods
    public boolean hasProfilePhoto() {
        return imageUrl != null && !imageUrl.trim().isEmpty() && profilePhotoEnabled;
    }

    public String getDisplayNameForUser() {
        // Priority: displayName field > personal name > name > phone number > email username
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        } else if (personalName != null && !personalName.trim().isEmpty()) {
            return personalName;
        } else if (name != null && !name.trim().isEmpty()) {
            return name;
        } else if (email != null && !email.trim().isEmpty() && email.contains("@")) {
            return email.split("@")[0];
        }
        return "Unknown User";
    }

    public String getDisplayAboutForUser() {
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

    public String getOnlineStatusForUser() {
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

    public boolean canBeAdded() {
        return "none".equals(friendshipStatus);
    }

    // Validation methods
    public boolean isEmailValid() {
        return validEmail && email != null && email.contains("@") && email.contains(".");
    }

    public boolean isProfileComplete() {
        return complete || (name != null && !name.trim().isEmpty() && isEmailValid());
    }

    // Copy
    public User copy() {
        User copy = new User();
        copy.id = this.id;
        copy.name = this.name;
        copy.email = this.email;
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
        copy.isBlocked = this.isBlocked;
        copy.friendshipStatus = this.friendshipStatus;
        copy.personalName = this.personalName;
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
                Objects.equals(imageUrl, user.imageUrl) &&
                Objects.equals(about, user.about) &&
                Objects.equals(fcmToken, user.fcmToken) &&
                Objects.equals(friendshipStatus, user.friendshipStatus) &&
                Objects.equals(personalName, user.personalName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, email, imageUrl, about, isOnline,
                lastSeen, fcmToken, profilePhotoEnabled, lastSeenEnabled, aboutEnabled,
                readReceiptsEnabled, joinedAt, isBlocked, friendshipStatus, personalName);
    }

    @NonNull
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", isOnline=" + isOnline +
                ", lastSeen=" + lastSeen +
                ", profilePhotoEnabled=" + profilePhotoEnabled +
                ", lastSeenEnabled=" + lastSeenEnabled +
                ", readReceiptsEnabled=" + readReceiptsEnabled +
                ", isBlocked=" + isBlocked +
                ", friendshipStatus='" + friendshipStatus + '\'' +
                ", personalName='" + personalName + '\'' +
                '}';
    }
}
