package com.pingme.android.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivitySetupProfileBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.CloudinaryUtil;
import com.pingme.android.utils.FirestoreUtil;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

public class SetupProfileActivity extends AppCompatActivity {

    private ActivitySetupProfileBinding binding;
    private Uri selectedImageUri;
    private String uploadedImageUrl;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    loadProfileImage();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); // FIXED: getRoot() resolves

        setupUI();
        populateUserInfo();
    }

    private void setupUI() {
        binding.ivProfile.setOnClickListener(v -> openImagePickerCompat());
        // Wire FAB if present in layout (camera icon)
        View fab = findViewById(com.pingme.android.R.id.fab); // may not exist
        if (fab != null) {
            fab.setOnClickListener(v -> openImagePickerCompat());
        }
        binding.btnSave.setOnClickListener(v -> saveProfile());
        binding.tvSkip.setOnClickListener(v -> skipSetup());

        Glide.with(this)
                .load(R.drawable.ic_profile)
                .circleCrop()
                .into(binding.ivProfile);
    }

    private void populateUserInfo() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String email = auth.getCurrentUser().getEmail();
            String displayName = auth.getCurrentUser().getDisplayName();
            Uri photoUrl = auth.getCurrentUser().getPhotoUrl();

            if (email != null) {
                binding.etEmail.setText(email);
                binding.etEmail.setEnabled(false);
            }

            if (displayName != null && !displayName.isEmpty()) {
                binding.etName.setText(displayName);
            }

            if (photoUrl != null) {
                Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(binding.ivProfile);
                uploadedImageUrl = photoUrl.toString(); // Google profile image
            }
        }
    }

    @Deprecated
    private void checkPermissionAndPickImage() {
        // No-op: modern pickers do not require READ_EXTERNAL_STORAGE; kept for backward compatibility
        openImagePickerCompat();
    }

    private void openImagePickerCompat() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Profile Picture"));
        } catch (Exception e) {
            // Fallback to ACTION_OPEN_DOCUMENT for scoped storage
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Profile Picture"));
        }
    }

    private void loadProfileImage() {
        if (selectedImageUri != null) {
            Glide.with(this)
                    .load(selectedImageUri)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(binding.ivProfile);
        }
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

        showLoading(true);

        if (selectedImageUri != null) {
            uploadImageAndCreateProfile(name, about);
        } else {
            createUserProfile(name, about, uploadedImageUrl); // Use default if present
        }
    }

    private void uploadImageAndCreateProfile(String name, String about) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        CloudinaryUtil.getInstance()
                .uploadImage(selectedImageUri, "profile_pictures/" + userId, this)
                .thenAccept(imageUrl -> runOnUiThread(() ->
                        createUserProfile(name, about, imageUrl)
                ))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showError("Failed to upload image: " + throwable.getMessage());
                    });
                    return null;
                });
    }

    private void createUserProfile(String name, String about, String imageUrl) {
        FirebaseAuth auth = FirebaseAuth.getInstance();

        User user = new User(
                auth.getCurrentUser().getUid(),
                name,
                auth.getCurrentUser().getEmail(),
                imageUrl,
                "Available"
        );
        user.setAbout(about);
        user.setJoinedAt(System.currentTimeMillis());
        user.setOnline(true);
        user.setLastSeen(System.currentTimeMillis());

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        user.setFcmToken(task.getResult());
                    }

                    FirestoreUtil.getUserRef(user.getId())
                            .set(user)
                            .addOnSuccessListener(aVoid -> {
                                showLoading(false);
                                showSuccess("Profile created successfully!");
                                startActivity(new Intent(SetupProfileActivity.this, MainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                showError("Failed to create profile: " + e.getMessage());
                            });
                });
    }

    private void skipSetup() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String displayName = auth.getCurrentUser().getDisplayName();
        String defaultName = displayName != null ? displayName : "User";

        createUserProfile(defaultName, "Hey there! I'm using PingMe", uploadedImageUrl);
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!show);
        binding.tvSkip.setEnabled(!show);
        binding.ivProfile.setEnabled(!show);
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(R.color.error_red))
                .show();
    }

    private void showSuccess(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.success_green))
                .show();
    }
}
