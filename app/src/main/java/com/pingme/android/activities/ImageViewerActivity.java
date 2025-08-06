package com.pingme.android.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

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
import com.pingme.android.utils.FirestoreUtil;

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
        
        // Make activity full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get intent data
        getIntentData();
        
        // Setup UI
        setupToolbar();
        setupPhotoView();
        setupClickListeners();
        
        // Load image
        loadImage();
        
        // Load sender info
        loadSenderInfo();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL);
        senderName = intent.getStringExtra(EXTRA_SENDER_NAME);
        senderId = intent.getStringExtra(EXTRA_SENDER_ID);
        timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        chatId = intent.getStringExtra(EXTRA_CHAT_ID);

        if (imageUrl == null || imageUrl.isEmpty()) {
            Toast.makeText(this, "Image not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupToolbar() {
        // Set sender name
        if (senderName != null && !senderName.isEmpty()) {
            binding.tvSenderName.setText(senderName);
        } else {
            binding.tvSenderName.setText("Unknown");
        }

        // Set timestamp
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault());
        binding.tvDateTime.setText(dateFormat.format(new Date(timestamp)));
    }

    private void setupPhotoView() {
        // Enable zoom and pan
        binding.photoView.setZoomable(true);
        binding.photoView.setScaleType(PhotoView.ScaleType.FIT_CENTER);
        
        // Add tap listener to toggle toolbar
        binding.photoView.setOnPhotoTapListener((view, x, y) -> toggleToolbarVisibility());
        
        // Add scale change listener
        binding.photoView.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
            // Auto-hide toolbar when zooming
            if (scaleFactor > 1.0f && isToolbarVisible) {
                hideToolbar();
            }
        });
    }

    private void setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener(v -> onBackPressed());

        // Share button
        binding.btnShare.setOnClickListener(v -> shareImage());

        // Save button
        binding.btnSave.setOnClickListener(v -> saveImageToGallery());

        // Forward button
        binding.btnForward.setOnClickListener(v -> forwardImage());

        // More options button
        binding.btnMore.setOnClickListener(v -> showMoreOptions());

        // Retry button
        binding.btnRetry.setOnClickListener(v -> loadImage());
    }

    private void loadImage() {
        showLoading(true);
        
        Glide.with(this)
                .load(imageUrl)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        showLoading(false);
                        binding.photoView.setImageDrawable(resource);
                        binding.errorContainer.setVisibility(View.GONE);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // Do nothing
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        showLoading(false);
                        showError(true);
                    }
                });
    }

    private void loadSenderInfo() {
        if (senderId != null && !senderId.isEmpty()) {
            FirestoreUtil.getUserRef(senderId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            User sender = documentSnapshot.toObject(User.class);
                            if (sender != null) {
                                binding.tvSenderName.setText(sender.getDisplayName());
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load sender info", e);
                    });
        }
    }

    private void toggleToolbarVisibility() {
        if (isToolbarVisible) {
            hideToolbar();
        } else {
            showToolbar();
        }
    }

    private void showToolbar() {
        binding.topToolbar.setVisibility(View.VISIBLE);
        binding.bottomActionBar.setVisibility(View.VISIBLE);
        isToolbarVisible = true;
        
        // Show status bar
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void hideToolbar() {
        binding.topToolbar.setVisibility(View.GONE);
        binding.bottomActionBar.setVisibility(View.GONE);
        isToolbarVisible = false;
        
        // Hide status bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.tvLoading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(boolean show) {
        binding.errorContainer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void shareImage() {
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
    }

    private void shareImageBitmap(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            
            File imageFile = new File(cachePath, "shared_image.jpg");
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
        }
    }

    private void saveImageToGallery() {
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
    public void onBackPressed() {
        super.onBackPressed();
        // Add slide transition
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}