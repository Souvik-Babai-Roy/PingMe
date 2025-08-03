package com.pingme.android.activities;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityEditProfileBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.CloudinaryUtil;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class EditProfileActivity extends AppCompatActivity {
    private ActivityEditProfileBinding binding;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); // FIXED: getRoot() resolves

        setupToolbar();  // FIXED: Added missing toolbar setup
        setupImagePicker(); // FIXED: Ensure image picker is initialized
        loadCurrentUser();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar); // Now resolves
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Edit Profile");
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        Glide.with(this)
                                .load(uri)
                                .circleCrop()
                                .into(binding.ivProfile);
                    }
                }
        );
    }

    private void loadCurrentUser() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirestoreUtil.getUserRef(userId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                User user = snapshot.toObject(User.class);
                if (user != null) {
                    binding.etName.setText(user.getName());
                    binding.etAbout.setText(user.getAbout()); // Now resolves

                    if (user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
                        Glide.with(this)
                                .load(user.getImageUrl())
                                .circleCrop()
                                .placeholder(R.drawable.ic_profile)
                                .into(binding.ivProfile);
                    }

                    binding.ivProfile.setOnClickListener(v -> openImagePicker());
                    binding.btnSave.setOnClickListener(v -> saveProfile(user));
                }
            }
        });
    }

    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    private void saveProfile(User user) {
        String name = binding.etName.getText().toString().trim();
        String about = binding.etAbout.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        user.setName(name);
        user.setAbout(about);

        if (selectedImageUri != null) {
            uploadImageAndSaveProfile(user);
        } else {
            saveUserProfile(user);
        }
    }

    private void uploadImageAndSaveProfile(User user) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        CloudinaryUtil.getInstance()
                .uploadImage(selectedImageUri, "profile_pictures/" + userId, this)
                .thenAccept(imageUrl -> runOnUiThread(() -> {
                    user.setImageUrl(imageUrl);
                    saveUserProfile(user);
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> Toast.makeText(
                            EditProfileActivity.this,
                            "Failed to upload image: " + throwable.getMessage(),
                            Toast.LENGTH_SHORT).show());
                    return null;
                });
    }

    private void saveUserProfile(User user) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getId())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}
