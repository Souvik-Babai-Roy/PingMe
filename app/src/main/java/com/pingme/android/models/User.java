package com.pingme.android.models;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import com.pingme.android.BR;
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

    public User() {
        this.about = "Hey there! I'm using PingMe";
        this.joinedDate = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
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
