package com.pingme.android.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.github.chrisbanes.photoview.PhotoView;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityImageViewerBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageViewerActivity extends AppCompatActivity {
    private static final String TAG = "ImageViewerActivity";

    private ActivityImageViewerBinding binding;
    private String imageUrl;
    private String senderName;
    private String senderId;
    private long timestamp;
    private String chatId;
    private boolean isToolbarVisible = true;

    // Intent extras
    public static final String EXTRA_IMAGE_URL = "image_url";
    public static final String EXTRA_SENDER_NAME = "sender_name";
    public static final String EXTRA_SENDER_ID = "sender_id";
    public static final String EXTRA_TIMESTAMP = "timestamp";
    public static final String EXTRA_CHAT_ID = "chat_id";

    public static Intent createIntent(Context context, String imageUrl, String senderName,
                                      String senderId, long timestamp, String chatId) {
        Intent intent = new Intent(context, ImageViewerActivity.class);
        intent.putExtra(EXTRA_IMAGE_URL, imageUrl);
        intent.putExtra(EXTRA_SENDER_NAME, senderName);
        intent.putExtra(EXTRA_SENDER_ID, senderId);
        intent.putExtra(EXTRA_TIMESTAMP, timestamp);
        intent.putExtra(EXTRA_CHAT_ID, chatId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // FIXED: Safe window flags setup
            if (getWindow() != null) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            // Handle back with dispatcher - FIXED: Null check for dispatcher
            if (getOnBackPressedDispatcher() != null) {
                getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        finish();
                        try {
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                        } catch (Exception e) {
                            // Ignore animation errors
                        }
                    }
                });
            }

            binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            // FIXED: Validate intent data before proceeding
            if (!getIntentData()) {
                finish();
                return;
            }

            // Setup UI
            setupToolbar();
            setupPhotoView();
            setupClickListeners();

            // Load image
            loadImage();

            // Load sender info
            loadSenderInfo();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error opening image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // FIXED: Return boolean to indicate success/failure
    private boolean getIntentData() {
        try {
            Intent intent = getIntent();
            if (intent == null) {
                Log.e(TAG, "Intent is null");
                return false;
            }

            imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL);
            senderName = intent.getStringExtra(EXTRA_SENDER_NAME);
            senderId = intent.getStringExtra(EXTRA_SENDER_ID);
            timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
            chatId = intent.getStringExtra(EXTRA_CHAT_ID);

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                Log.e(TAG, "Image URL is null or empty");
                Toast.makeText(this, "Image not found", Toast.LENGTH_SHORT).show();
                return false;
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error getting intent data", e);
            return false;
        }
    }

    private void setupToolbar() {
        try {
            // Set sender name with null check
            if (senderName != null && !senderName.trim().isEmpty()) {
                binding.tvSenderName.setText(senderName);
            } else {
                binding.tvSenderName.setText("Unknown");
            }

            // Set timestamp with proper validation
            if (timestamp > 0) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault());
                    binding.tvDateTime.setText(dateFormat.format(new Date(timestamp)));
                } catch (Exception e) {
                    binding.tvDateTime.setText("Unknown time");
                }
            } else {
                binding.tvDateTime.setText("");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar", e);
            // Set default values on error
            binding.tvSenderName.setText("Unknown");
            binding.tvDateTime.setText("");
        }
    }

    private void setupPhotoView() {
        try {
            if (binding.photoView != null) {
                // Enable zoom and pan
                binding.photoView.setZoomable(true);
                binding.photoView.setScaleType(PhotoView.ScaleType.FIT_CENTER);

                // Add tap listener to toggle toolbar
                binding.photoView.setOnPhotoTapListener((view, x, y) -> toggleToolbarVisibility());

                // Add scale change listener with null checks
                binding.photoView.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
                    try {
                        // Auto-hide toolbar when zooming
                        if (scaleFactor > 1.0f && isToolbarVisible) {
                            hideToolbar();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in scale change listener", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up photo view", e);
        }
    }

    private void setupClickListeners() {
        try {
            // Back button with null check
            if (binding.btnBack != null) {
                binding.btnBack.setOnClickListener(v -> {
                    try {
                        onBackPressed();
                    } catch (Exception e) {
                        finish();
                    }
                });
            }

            // Share button
            if (binding.btnShare != null) {
                binding.btnShare.setOnClickListener(v -> shareImage());
            }

            // Save button
            if (binding.btnSave != null) {
                binding.btnSave.setOnClickListener(v -> saveImageToGallery());
            }

            // Forward button
            if (binding.btnForward != null) {
                binding.btnForward.setOnClickListener(v -> forwardImage());
            }

            // More options button
            if (binding.btnMore != null) {
                binding.btnMore.setOnClickListener(v -> showMoreOptions());
            }

            // Retry button
            if (binding.btnRetry != null) {
                binding.btnRetry.setOnClickListener(v -> loadImage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners", e);
        }
    }

    private void loadImage() {
        try {
            showLoading(true);

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                showError(true);
                return;
            }

            Glide.with(this)
                    .load(imageUrl)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            try {
                                showLoading(false);
                                if (binding.photoView != null) {
                                    binding.photoView.setImageDrawable(resource);
                                }
                                if (binding.errorContainer != null) {
                                    binding.errorContainer.setVisibility(View.GONE);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error setting image", e);
                                showError(true);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // Do nothing
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            Log.e(TAG, "Failed to load image: " + imageUrl);
                            showLoading(false);
                            showError(true);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            showLoading(false);
            showError(true);
        }
    }

    private void loadSenderInfo() {
        try {
            if (senderId != null && !senderId.trim().isEmpty()) {
                FirebaseUtil.getUserRef(senderId).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            try {
                                if (documentSnapshot != null && documentSnapshot.exists()) {
                                    User sender = documentSnapshot.toObject(User.class);
                                    if (sender != null && binding.tvSenderName != null) {
                                        String displayName = sender.getDisplayName();
                                        if (displayName != null && !displayName.trim().isEmpty()) {
                                            binding.tvSenderName.setText(displayName);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing sender info", e);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to load sender info", e);
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading sender info", e);
        }
    }

    private void toggleToolbarVisibility() {
        try {
            if (isToolbarVisible) {
                hideToolbar();
            } else {
                showToolbar();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling toolbar", e);
        }
    }

    private void showToolbar() {
        try {
            if (binding.topToolbar != null) {
                binding.topToolbar.setVisibility(View.VISIBLE);
            }
            if (binding.bottomActionBar != null) {
                binding.bottomActionBar.setVisibility(View.VISIBLE);
            }
            isToolbarVisible = true;

            // Show status bar
            if (getWindow() != null) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing toolbar", e);
        }
    }

    private void hideToolbar() {
        try {
            if (binding.topToolbar != null) {
                binding.topToolbar.setVisibility(View.GONE);
            }
            if (binding.bottomActionBar != null) {
                binding.bottomActionBar.setVisibility(View.GONE);
            }
            isToolbarVisible = false;

            // Hide status bar
            if (getWindow() != null) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding toolbar", e);
        }
    }

    private void showLoading(boolean show) {
        try {
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
            if (binding.tvLoading != null) {
                binding.tvLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing loading", e);
        }
    }

    private void showError(boolean show) {
        try {
            if (binding.errorContainer != null) {
                binding.errorContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing error state", e);
        }
    }

    private void shareImage() {
        try {
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                Toast.makeText(this, "No image to share", Toast.LENGTH_SHORT).show();
                return;
            }

            // Download image first, then share
            Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            shareImageBitmap(resource);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // Do nothing
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            Toast.makeText(ImageViewerActivity.this, "Failed to load image for sharing", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error sharing image", e);
            Toast.makeText(this, "Error sharing image", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareImageBitmap(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }

            File imageFile = new File(cachePath, "shared_image_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", imageFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Image"));

        } catch (IOException e) {
            Log.e(TAG, "Error sharing image", e);
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error sharing image", e);
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageToGallery() {
        try {
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
                return;
            }

            Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            saveImageBitmap(resource);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // Do nothing
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            Toast.makeText(ImageViewerActivity.this, "Failed to load image for saving", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            Toast.makeText(this, "Error saving image", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageBitmap(Bitmap bitmap) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "PingMe_" + timeStamp + ".jpg";

            String savedImageURL = MediaStore.Images.Media.insertImage(
                    getContentResolver(),
                    bitmap,
                    imageFileName,
                    "Image from PingMe"
            );

            if (savedImageURL != null) {
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

    private void forwardImage() {
        // TODO: Implement forward functionality
        // This would open a contact picker to forward the image
        Toast.makeText(this, "Forward feature coming soon", Toast.LENGTH_SHORT).show();
    }

    private void showMoreOptions() {
        // TODO: Implement more options (delete, set as wallpaper, etc.)
        Toast.makeText(this, "More options coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // Clear Glide to prevent memory leaks
            if (!isDestroyed() && !isFinishing()) {
                Glide.with(this).clear(binding.photoView);
            }
            binding = null;
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            super.onBackPressed();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        } catch (Exception e) {
            finish();
        }
    }
}