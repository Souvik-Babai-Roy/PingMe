package com.pingme.android.viewmodels;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

public class EditProfileViewModel extends ViewModel {
    private static final String TAG = "EditProfileViewModel";
    
    private MutableLiveData<User> user = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<User> getUser() {
        return user;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadCurrentUser() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            error.setValue("User not authenticated");
            return;
        }

        isLoading.setValue(true);
        String userId = firebaseUser.getUid();

        FirebaseUtil.getUserRef(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            currentUser.setId(userId);
                            user.setValue(currentUser);
                        }
                    }
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load user", e);
                    error.setValue("Failed to load user profile");
                    isLoading.setValue(false);
                });
    }

    public void updateProfile(String name, String about, String imageUrl) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            error.setValue("User not authenticated");
            return;
        }

        isLoading.setValue(true);
        String userId = firebaseUser.getUid();

        User currentUser = user.getValue();
        if (currentUser != null) {
            currentUser.setName(name);
            currentUser.setAbout(about);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                currentUser.setImageUrl(imageUrl);
            }

            FirebaseUtil.getUserRef(userId).update(
                    "name", name,
                    "about", about,
                    "imageUrl", currentUser.getImageUrl()
            ).addOnSuccessListener(aVoid -> {
                user.setValue(currentUser);
                isLoading.setValue(false);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Failed to update profile", e);
                error.setValue("Failed to update profile");
                isLoading.setValue(false);
            });
        }
    }
}
