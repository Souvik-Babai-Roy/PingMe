package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityUserProfileBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

public class UserProfileActivity extends AppCompatActivity {
    private ActivityUserProfileBinding binding;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String targetUserId;
    private User targetUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Get target user ID from intent
        targetUserId = getIntent().getStringExtra("user_id");
        if (targetUserId == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        loadUserProfile();
        setupClickListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Profile");
        }
    }

    private void loadUserProfile() {
        if (currentUser == null || targetUserId == null) return;

        // Show loading
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.contentLayout.setVisibility(View.GONE);

        // Load user data from Firestore
        FirebaseUtil.getUserRef(targetUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        targetUser = documentSnapshot.toObject(User.class);
                        if (targetUser != null) {
                            displayUserProfile();
                        } else {
                            showError("Failed to load user data");
                        }
                    } else {
                        showError("User not found");
                    }
                })
                .addOnFailureListener(e -> {
                    showError("Failed to load profile: " + e.getMessage());
                });
    }

    private void displayUserProfile() {
        if (targetUser == null) return;

        // Hide loading
        binding.progressBar.setVisibility(View.GONE);
        binding.contentLayout.setVisibility(View.VISIBLE);

        // Display name (always shown)
        binding.tvUserName.setText(targetUser.getDisplayName());

        // Profile photo (based on privacy setting)
        if (targetUser.shouldShowProfilePhoto() && targetUser.getImageUrl() != null && !targetUser.getImageUrl().isEmpty()) {
            binding.ivProfilePhoto.setVisibility(View.VISIBLE);
            binding.ivDefaultProfile.setVisibility(View.GONE);
            
            Glide.with(this)
                    .load(targetUser.getImageUrl())
                    .placeholder(R.drawable.defaultprofile)
                    .error(R.drawable.defaultprofile)
                    .circleCrop()
                    .into(binding.ivProfilePhoto);
        } else {
            binding.ivProfilePhoto.setVisibility(View.GONE);
            binding.ivDefaultProfile.setVisibility(View.VISIBLE);
        }

        // About section (based on privacy setting)
        if (targetUser.isAboutEnabled()) {
            binding.tvAbout.setVisibility(View.VISIBLE);
            binding.tvAbout.setText(targetUser.getDisplayAbout());
        } else {
            binding.tvAbout.setVisibility(View.GONE);
        }

        // Last seen section (based on privacy setting)
        if (targetUser.isLastSeenEnabled()) {
            binding.tvLastSeen.setVisibility(View.VISIBLE);
            binding.tvLastSeen.setText(targetUser.getOnlineStatus());
        } else {
            binding.tvLastSeen.setVisibility(View.GONE);
        }

        // Phone number (if available and user is friend)
        checkFriendshipAndShowDetails();
    }

    private void checkFriendshipAndShowDetails() {
        if (currentUser == null || targetUserId == null) return;

        // Check if users are friends
        FirebaseUtil.getUserRef(currentUser.getUid())
                .collection("friends")
                .document(targetUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Users are friends, show additional details
                        showFriendDetails();
                    } else {
                        // Not friends, hide sensitive information
                        hideSensitiveInfo();
                    }
                })
                .addOnFailureListener(e -> {
                    // On error, hide sensitive info for safety
                    hideSensitiveInfo();
                });
    }

    private void showFriendDetails() {
        // Show phone number if available
        if (targetUser.getPhoneNumber() != null && !targetUser.getPhoneNumber().isEmpty()) {
            binding.tvPhoneNumber.setVisibility(View.VISIBLE);
            binding.tvPhoneNumber.setText(targetUser.getPhoneNumber());
        } else {
            binding.tvPhoneNumber.setVisibility(View.GONE);
        }

        // Show email if available
        if (targetUser.getEmail() != null && !targetUser.getEmail().isEmpty()) {
            binding.tvEmail.setVisibility(View.VISIBLE);
            binding.tvEmail.setText(targetUser.getEmail());
        } else {
            binding.tvEmail.setVisibility(View.GONE);
        }

        // Show joined date
        if (targetUser.getJoinedAt() > 0) {
            binding.tvJoinedDate.setVisibility(View.VISIBLE);
            String joinedDate = "Joined " + formatDate(targetUser.getJoinedAt());
            binding.tvJoinedDate.setText(joinedDate);
        } else {
            binding.tvJoinedDate.setVisibility(View.GONE);
        }
    }

    private void hideSensitiveInfo() {
        binding.tvPhoneNumber.setVisibility(View.GONE);
        binding.tvEmail.setVisibility(View.GONE);
        binding.tvJoinedDate.setVisibility(View.GONE);
        
        // Also hide the block button if not friends (can't block someone you're not friends with)
        binding.btnBlock.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        // Message button
        binding.btnMessage.setOnClickListener(v -> {
            if (targetUser != null) {
                // Start chat with this user
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("user_id", targetUserId);
                intent.putExtra("user_name", targetUser.getDisplayName());
                startActivity(intent);
                finish();
            }
        });

        // Call button (placeholder)
        binding.btnCall.setOnClickListener(v -> {
            Toast.makeText(this, "Call feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Video call button (placeholder)
        binding.btnVideoCall.setOnClickListener(v -> {
            Toast.makeText(this, "Video call feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Block button (if not already blocked)
        binding.btnBlock.setOnClickListener(v -> {
            if (targetUser != null) {
                showBlockConfirmation();
            }
        });
    }

    private void showBlockConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Block User")
                .setMessage("Are you sure you want to block " + targetUser.getDisplayName() + "?")
                .setPositiveButton("Block", (dialog, which) -> {
                    blockUser();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void blockUser() {
        if (currentUser == null || targetUserId == null) return;

        // Add to blocked users
        FirebaseUtil.getUserRef(currentUser.getUid())
                .collection("blocked_users")
                .document(targetUserId)
                .set(targetUser)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "User blocked successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to block user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String formatDate(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    private void showError(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.contentLayout.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.VISIBLE);
        binding.tvErrorMessage.setText(message);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}