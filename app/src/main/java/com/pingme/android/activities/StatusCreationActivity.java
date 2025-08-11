package com.pingme.android.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityStatusCreationBinding;
import com.pingme.android.models.Status;
import com.pingme.android.utils.CloudinaryUtil;
import com.pingme.android.utils.FirestoreUtil;

public class StatusCreationActivity extends AppCompatActivity {
    private static final String TAG = "StatusCreationActivity";

    private ActivityStatusCreationBinding binding;
    private String currentUserId;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImageSelection);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStatusCreationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUI();
    }

    private void setupUI() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("New Status");
        }

        // Set up click listeners
        binding.ivAddImage.setOnClickListener(v -> selectImage());
        binding.btnPost.setOnClickListener(v -> postStatus());
        
        // Initially hide the image preview
        binding.ivPreview.setVisibility(View.GONE);
        binding.btnRemoveImage.setVisibility(View.GONE);
        
        binding.btnRemoveImage.setOnClickListener(v -> removeImage());
    }

    private void selectImage() {
        imagePickerLauncher.launch("image/*");
    }

    private void handleImageSelection(Uri imageUri) {
        if (imageUri != null) {
            selectedImageUri = imageUri;
            
            // Show preview
            binding.ivPreview.setVisibility(View.VISIBLE);
            binding.btnRemoveImage.setVisibility(View.VISIBLE);
            
            Glide.with(this)
                    .load(imageUri)
                    .into(binding.ivPreview);
        }
    }

    private void removeImage() {
        selectedImageUri = null;
        binding.ivPreview.setVisibility(View.GONE);
        binding.btnRemoveImage.setVisibility(View.GONE);
    }

    private void postStatus() {
        String statusText = binding.etStatusText.getText().toString().trim();
        
        if (statusText.isEmpty() && selectedImageUri == null) {
            Toast.makeText(this, "Please add some text or an image", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        if (selectedImageUri != null) {
            // Upload image first, then create status
            CloudinaryUtil.getInstance()
                    .uploadStatusImage(selectedImageUri, this)
                    .thenAccept(imageUrl -> runOnUiThread(() -> {
                        createStatus(statusText, imageUrl);
                    }))
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    });
        } else {
            // Create text-only status
            createStatus(statusText, null);
        }
    }

    private void createStatus(String text, String imageUrl) {
        Status status = new Status();
        status.setUserId(currentUserId);
        status.setText(text);
        status.setImageUrl(imageUrl);
        status.setTimestamp(System.currentTimeMillis());
        status.setViewers(new java.util.ArrayList<>()); // Empty viewers list initially

        FirestoreUtil.getStatusCollectionRef()
                .add(status)
                .addOnSuccessListener(documentReference -> {
                    showLoading(false);
                    Toast.makeText(this, "Status posted successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to post status", Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean show) {
        if (show) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnPost.setEnabled(false);
            binding.ivAddImage.setEnabled(false);
            binding.etStatusText.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnPost.setEnabled(true);
            binding.ivAddImage.setEnabled(true);
            binding.etStatusText.setEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}