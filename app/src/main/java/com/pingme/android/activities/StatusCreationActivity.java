package com.pingme.android.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.pingme.android.databinding.ActivityStatusCreationBinding;
import com.pingme.android.models.Status;
import com.pingme.android.models.User;
import com.pingme.android.utils.CloudinaryUtil;
import com.pingme.android.utils.FirebaseUtil;

import java.util.HashMap;
import java.util.Map;

public class StatusCreationActivity extends AppCompatActivity {
    private static final String TAG = "StatusCreationActivity";
    
    private ActivityStatusCreationBinding binding;
    private String currentUserId;
    private User currentUser;
    private Uri selectedImageUri;
    private String uploadedImageUrl;
    
    // Background colors for text status
    private final String[] backgroundColors = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FECA57",
            "#FF9FF3", "#54A0FF", "#5F27CD", "#00D2D3", "#FF9F43",
            "#10AC84", "#EE5A24", "#0984E3", "#A3CB38", "#FDA7DF"
    };
    
    private int currentColorIndex = 0;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        showImagePreview();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStatusCreationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = firebaseUser.getUid();

        setupUI();
        loadCurrentUser();
    }

    private void setupUI() {
        setupToolbar();
        setupClickListeners();
        setupTextMode();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Status");
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupClickListeners() {
        binding.btnSend.setOnClickListener(v -> createStatus());
        binding.btnSelectImage.setOnClickListener(v -> selectImage());
        binding.btnTextMode.setOnClickListener(v -> switchToTextMode());
        binding.btnImageMode.setOnClickListener(v -> switchToImageMode());
        binding.btnChangeColor.setOnClickListener(v -> changeBackgroundColor());
        binding.btnRemoveImage.setOnClickListener(v -> removeImage());
    }

    private void setupTextMode() {
        binding.layoutTextMode.setVisibility(View.VISIBLE);
        binding.layoutImageMode.setVisibility(View.GONE);
        binding.btnTextMode.setSelected(true);
        binding.btnImageMode.setSelected(false);
        
        // Set initial background color
        changeBackgroundColor();
    }

    private void switchToTextMode() {
        binding.layoutTextMode.setVisibility(View.VISIBLE);
        binding.layoutImageMode.setVisibility(View.GONE);
        binding.btnTextMode.setSelected(true);
        binding.btnImageMode.setSelected(false);
        selectedImageUri = null;
        uploadedImageUrl = null;
    }

    private void switchToImageMode() {
        binding.layoutTextMode.setVisibility(View.GONE);
        binding.layoutImageMode.setVisibility(View.VISIBLE);
        binding.btnTextMode.setSelected(false);
        binding.btnImageMode.setSelected(true);
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Image"));
    }

    private void showImagePreview() {
        if (selectedImageUri != null) {
            switchToImageMode();
            binding.imagePreview.setVisibility(View.VISIBLE);
            binding.btnSelectImage.setVisibility(View.GONE);
            binding.btnRemoveImage.setVisibility(View.VISIBLE);
            
            Glide.with(this)
                    .load(selectedImageUri)
                    .centerCrop()
                    .into(binding.imagePreview);
        }
    }

    private void removeImage() {
        selectedImageUri = null;
        uploadedImageUrl = null;
        binding.imagePreview.setVisibility(View.GONE);
        binding.btnSelectImage.setVisibility(View.VISIBLE);
        binding.btnRemoveImage.setVisibility(View.GONE);
    }

    private void changeBackgroundColor() {
        currentColorIndex = (currentColorIndex + 1) % backgroundColors.length;
        String color = backgroundColors[currentColorIndex];
        binding.layoutTextMode.setBackgroundColor(Color.parseColor(color));
    }

    private void loadCurrentUser() {
        FirebaseUtil.getUserRef(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            currentUser.setId(currentUserId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                });
    }

    private void createStatus() {
        String content = binding.etStatusText.getText().toString().trim();
        
        // Validate input
        if (selectedImageUri == null && TextUtils.isEmpty(content)) {
            Toast.makeText(this, "Please add some text or select an image", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUser == null) {
            Toast.makeText(this, "User data not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        if (selectedImageUri != null) {
            // Upload image first, then create status
            uploadImageAndCreateStatus(content);
        } else {
            // Create text status
            createTextStatus(content);
        }
    }

    private void uploadImageAndCreateStatus(String content) {
        CloudinaryUtil.getInstance()
                .uploadStatusImage(selectedImageUri, this)
                .thenAccept(imageUrl -> {
                    runOnUiThread(() -> {
                        uploadedImageUrl = imageUrl;
                        createImageStatus(content, imageUrl);
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(StatusCreationActivity.this, "Failed to upload image: " + throwable.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void createTextStatus(String content) {
        Status status = new Status();
        status.setUserId(currentUserId);
        status.setUserName(currentUser.getDisplayName());
        status.setUserImageUrl(currentUser.getImageUrl());
        status.setContent(content);
        status.setType("text");
        status.setBackgroundColor(backgroundColors[currentColorIndex]);
        status.setTimestamp(System.currentTimeMillis());
        status.setExpiryTime(System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // 24 hours

        saveStatusToFirestore(status);
    }

    private void createImageStatus(String content, String imageUrl) {
        Status status = new Status();
        status.setUserId(currentUserId);
        status.setUserName(currentUser.getDisplayName());
        status.setUserImageUrl(currentUser.getImageUrl());
        status.setContent(content);
        status.setImageUrl(imageUrl);
        status.setType("image");
        status.setTimestamp(System.currentTimeMillis());
        status.setExpiryTime(System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // 24 hours

        saveStatusToFirestore(status);
    }

    private void saveStatusToFirestore(Status status) {
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("userId", status.getUserId());
        statusData.put("userName", status.getUserName());
        statusData.put("userImageUrl", status.getUserImageUrl());
        statusData.put("content", status.getContent());
        statusData.put("imageUrl", status.getImageUrl());
        statusData.put("videoUrl", status.getVideoUrl());
        statusData.put("type", status.getType());
        statusData.put("backgroundColor", status.getBackgroundColor());
        statusData.put("timestamp", status.getTimestamp());
        statusData.put("expiryTime", status.getExpiryTime());
        statusData.put("viewers", new HashMap<String, Long>());

        FirebaseUtil.getStatusCollectionRef()
                .add(statusData)
                .addOnSuccessListener(documentReference -> {
                    showLoading(false);
                    Toast.makeText(this, "Status posted successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to post status: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnSend.setEnabled(!show);
        binding.btnSelectImage.setEnabled(!show);
        binding.btnChangeColor.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}