package com.pingme.android.viewmodels;

import android.net.Uri;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.databinding.Bindable;
import androidx.databinding.Observable;
import androidx.databinding.PropertyChangeRegistry;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.pingme.android.BR;
import com.pingme.android.models.User;
import com.pingme.android.utils.CloudinaryUtil;
import com.pingme.android.utils.FirestoreUtil;

public class EditProfileViewModel extends ViewModel implements Observable {
    private static final String TAG = "EditProfileViewModel";
    private PropertyChangeRegistry callbacks = new PropertyChangeRegistry();
    private User user = new User();

    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> updateSuccess = new MutableLiveData<>();

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getUpdateSuccess() { return updateSuccess; }

    @Bindable
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        notifyPropertyChanged(BR.user);
    }

    public void loadCurrentUser() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            return;
        }
        String userId = currentUser.getUid();
        FirestoreUtil.getUserRef(userId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User loadedUser = snapshot.toObject(User.class);
                        setUser(loadedUser);
                    }
                })
                .addOnFailureListener(e -> {
                    errorMessage.setValue("Failed to load user data: " + e.getMessage());
                });
    }

    public void updateProfile(String name, String about, Uri imageUri) {
        if (name.isEmpty()) {
            errorMessage.setValue("Name is required");
            return;
        }

        isLoading.setValue(true);
        user.setName(name);
        user.setAbout(about);

        if (imageUri != null) {
            CloudinaryUtil.getInstance()
                    .uploadImage(imageUri, "profile_pictures/" + user.getId(), null)
                    .thenAccept(imageUrl -> {
                        user.setImageUrl(imageUrl);
                        saveUserProfile(user);
                    })
                    .exceptionally(throwable -> {
                        isLoading.setValue(false);
                        errorMessage.setValue("Failed to upload image: " + throwable.getMessage());
                        return null;
                    });
        } else {
            saveUserProfile(user);
        }
    }

    private void saveUserProfile(User user) {
        FirestoreUtil.getUserRef(user.getId())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    isLoading.setValue(false);
                    updateSuccess.setValue(true);
                })
                .addOnFailureListener(e -> {
                    isLoading.setValue(false);
                    errorMessage.setValue("Failed to update profile: " + e.getMessage());
                });
    }

    public void onChangePhotoClick() {
        // Logic to handle change photo click
    }

    public void onSaveClick() {
        // Logic to trigger updateProfile() with current values
    }

    // Required for Observable
    @Override
    public void addOnPropertyChangedCallback(OnPropertyChangedCallback callback) {
        callbacks.add(callback);
    }

    @Override
    public void removeOnPropertyChangedCallback(OnPropertyChangedCallback callback) {
        callbacks.remove(callback);
    }

    public void notifyPropertyChanged(int fieldId) {
        callbacks.notifyCallbacks(this, fieldId, null);
    }
}
