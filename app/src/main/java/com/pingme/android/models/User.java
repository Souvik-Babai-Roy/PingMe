package com.pingme.android.models;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import com.pingme.android.BR;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class User extends BaseObservable {
    private String id;
    private String name;
    private String email;
    private String imageUrl;
    private String status;
    private String about;
    private String phone;
    private long joinedDate;
    private boolean isOnline;
    private long lastSeen;
    private String fcmToken;
    private List<String> friends;

    // Preference settings
    private boolean lastSeenEnabled = true;
    private boolean aboutEnabled = true;
    private boolean readReceiptsEnabled = true;
    private boolean profilePhotoEnabled = true;
    private String theme = "auto";

    public User() {
        this.about = "Hey there! I'm using PingMe";
        this.joinedDate = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
        this.friends = new ArrayList<>();
    }

    public User(String id, String name, String email, String imageUrl, String status) {
        this();
        this.id = id;
        this.name = name;
        this.email = email;
        this.imageUrl = imageUrl;
        this.status = status;
    }

    // Getters with @Bindable
    @Bindable public String getId() { return id; }
    @Bindable public String getName() { return name != null ? name : ""; }
    @Bindable public String getEmail() { return email != null ? email : ""; }
    @Bindable public String getImageUrl() { return imageUrl != null ? imageUrl : ""; }
    @Bindable public String getStatus() { return status != null ? status : ""; }
    @Bindable public String getAbout() { return about != null ? about : ""; }
    @Bindable public String getPhone() { return phone != null ? phone : ""; }
    @Bindable public long getJoinedDate() { return joinedDate; }
    @Bindable public boolean isOnline() { return isOnline; }
    @Bindable public long getLastSeen() { return lastSeen; }
    @Bindable public String getFcmToken() { return fcmToken; }
    @Bindable public List<String> getFriends() { return friends != null ? friends : new ArrayList<>(); }

    // Preference getters
    @Bindable public boolean isLastSeenEnabled() { return lastSeenEnabled; }
    @Bindable public boolean isAboutEnabled() { return aboutEnabled; }
    @Bindable public boolean isReadReceiptsEnabled() { return readReceiptsEnabled; }
    @Bindable public boolean isProfilePhotoEnabled() { return profilePhotoEnabled; }
    @Bindable public String getTheme() { return theme; }

    // Setters with notifyPropertyChanged
    public void setId(String id) { this.id = id; notifyPropertyChanged(BR.id); }
    public void setName(String name) { this.name = name; notifyPropertyChanged(BR.name); }
    public void setEmail(String email) { this.email = email; notifyPropertyChanged(BR.email); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; notifyPropertyChanged(BR.imageUrl); }
    public void setStatus(String status) { this.status = status; notifyPropertyChanged(BR.status); }
    public void setAbout(String about) { this.about = about; notifyPropertyChanged(BR.about); }
    public void setPhone(String phone) { this.phone = phone; notifyPropertyChanged(BR.phone); }
    public void setJoinedDate(long joinedDate) { this.joinedDate = joinedDate; notifyPropertyChanged(BR.joinedDate); }
    public void setOnline(boolean online) { this.isOnline = online; notifyPropertyChanged(BR.online); }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; notifyPropertyChanged(BR.lastSeen); }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; notifyPropertyChanged(BR.fcmToken); }
    public void setFriends(List<String> friends) { this.friends = friends; notifyPropertyChanged(BR.friends); }

    // Preference setters
    public void setLastSeenEnabled(boolean lastSeenEnabled) {
        this.lastSeenEnabled = lastSeenEnabled;
        notifyPropertyChanged(BR.lastSeenEnabled);
    }

    public void setAboutEnabled(boolean aboutEnabled) {
        this.aboutEnabled = aboutEnabled;
        notifyPropertyChanged(BR.aboutEnabled);
    }

    public void setReadReceiptsEnabled(boolean readReceiptsEnabled) {
        this.readReceiptsEnabled = readReceiptsEnabled;
        notifyPropertyChanged(BR.readReceiptsEnabled);
    }

    public void setProfilePhotoEnabled(boolean profilePhotoEnabled) {
        this.profilePhotoEnabled = profilePhotoEnabled;
        notifyPropertyChanged(BR.profilePhotoEnabled);
    }

    public void setTheme(String theme) {
        this.theme = theme;
        notifyPropertyChanged(BR.theme);
    }

    public void addFriend(String friendId) {
        if (!friends.contains(friendId)) {
            friends.add(friendId);
            notifyPropertyChanged(BR.friends);
        }
    }

    public void removeFriend(String friendId) {
        if (friends.contains(friendId)) {
            friends.remove(friendId);
            notifyPropertyChanged(BR.friends);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}