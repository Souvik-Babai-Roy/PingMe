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
import com.pingme.android.utils.FirebaseUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class EditProfileActivity extends AppCompatActivity {
    private ActivityEditProfileBinding binding;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private Uri selectedImageUri;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupImagePicker();
        loadCurrentUser();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
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
        showLoading(true);

        FirebaseUtil.getUserRef(userId).get().addOnSuccessListener(snapshot -> {
            showLoading(false);
            if (snapshot.exists()) {
                currentUser = snapshot.toObject(User.class);
                if (currentUser != null) {
                    currentUser.setId(snapshot.getId()); // FIXED: Set ID
                    populateFields();
                    setupClickListeners();
                }
            }
        }).addOnFailureListener(e -> {
            showLoading(false);
            Toast.makeText(this, "Failed to load profile: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    // FIXED: Separate method to populate fields
    private void populateFields() {
        binding.etName.setText(currentUser.getName());
        binding.etAbout.setText(currentUser.getAbout());

        if (currentUser.getImageUrl() != null && !currentUser.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(currentUser.getImageUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(binding.ivProfile);
        }
    }

    // FIXED: Separate method to setup click listeners
    private void setupClickListeners() {
        binding.ivProfile.setOnClickListener(v -> openImagePicker());
        binding.btnSave.setOnClickListener(v -> saveProfile());
    }

    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    private void saveProfile() {
        String name = binding.etName.getText().toString().trim();
        String about = binding.etAbout.getText().toString().trim();

        if (name.isEmpty()) {
            binding.etName.setError("Name is required");
            binding.etName.requestFocus();
            return;
        }

        if (about.isEmpty()) {
            about = "Hey there! I'm using PingMe";
        }

        // FIXED: Create updated user object
        User updatedUser = new User();
        updatedUser.setId(currentUser.getId());
        updatedUser.setName(name);
        updatedUser.setAbout(about);
        updatedUser.setEmail(currentUser.getEmail());
        updatedUser.setImageUrl(currentUser.getImageUrl()); // Keep existing URL initially
        updatedUser.setJoinedAt(currentUser.getJoinedAt());
        updatedUser.setFcmToken(currentUser.getFcmToken());

        showLoading(true);

        if (selectedImageUri != null) {
            uploadImageAndSaveProfile(updatedUser);
        } else {
            saveUserProfile(updatedUser);
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
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(EditProfileActivity.this,
                                "Failed to upload image: " + throwable.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void saveUserProfile(User user) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getId())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();

                    // FIXED: Update currentUser and send result back
                    currentUser = user;
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to update profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // FIXED: Add loading state management
    private void showLoading(boolean show) {
        if (show) {
//            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnSave.setEnabled(false);
            binding.ivProfile.setEnabled(false);
        } else {
//            binding.progressBar.setVisibility(View.GONE);
            binding.btnSave.setEnabled(true);
            binding.ivProfile.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}