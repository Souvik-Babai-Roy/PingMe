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

        // Last seen section (based on privacy setting) - Enhanced with real-time data
        loadAndDisplayLastSeen();

        // Phone number (if available and user is friend)
        checkFriendshipAndShowDetails();
    }

    private void loadAndDisplayLastSeen() {
        if (targetUser == null || targetUserId == null) {
            binding.tvLastSeen.setVisibility(View.GONE);
            return;
        }

        // Check if current user is viewing their own profile
        boolean isOwnProfile = currentUser != null && currentUser.getUid().equals(targetUserId);
        
        // Always show online status for own profile
        if (isOwnProfile) {
            binding.tvLastSeen.setVisibility(View.VISIBLE);
            binding.tvLastSeen.setText("online");
            binding.tvLastSeen.setTextColor(getColor(R.color.online_green));
            return;
        }

        // For other users, check privacy settings first
        if (!targetUser.isLastSeenEnabled()) {
            binding.tvLastSeen.setVisibility(View.GONE);
            return;
        }

        // Check if users are friends for enhanced privacy
        FirebaseUtil.getFriendsRef(currentUser.getUid()).document(targetUserId)
                .get()
                .addOnSuccessListener(friendDoc -> {
                    boolean areFriends = friendDoc.exists();
                    
                    // If last seen is enabled, show it (friends get more detailed info)
                    loadRealTimePresence(areFriends);
                })
                .addOnFailureListener(e -> {
                    // On error, assume not friends and show basic info
                    loadRealTimePresence(false);
                });
    }

    private void loadRealTimePresence(boolean areFriends) {
        // Load real-time presence data from Firebase Realtime Database
        FirebaseUtil.getPresenceRef(targetUserId)
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            boolean isOnline = FirebaseUtil.safeBooleanValue(dataSnapshot.child("isOnline").getValue());
                            Long lastSeenTimestamp = dataSnapshot.child("lastSeen").getValue(Long.class);
                            
                            displayLastSeenStatus(isOnline, lastSeenTimestamp, areFriends);
                        } else {
                            // No presence data available
                            displayLastSeenStatus(false, null, areFriends);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError databaseError) {
                        // On error, show basic offline status
                        displayLastSeenStatus(false, null, areFriends);
                    }
                });
    }

    private void displayLastSeenStatus(boolean isOnline, Long lastSeenTimestamp, boolean areFriends) {
        binding.tvLastSeen.setVisibility(View.VISIBLE);
        
        if (isOnline) {
            // User is currently online
            binding.tvLastSeen.setText("online");
            binding.tvLastSeen.setTextColor(getColor(R.color.online_green));
        } else if (lastSeenTimestamp != null && lastSeenTimestamp > 0) {
            // User is offline, show last seen time
            String lastSeenText = formatLastSeenTime(lastSeenTimestamp, areFriends);
            binding.tvLastSeen.setText(lastSeenText);
            binding.tvLastSeen.setTextColor(getColor(R.color.textColorSecondary));
        } else {
            // No last seen data available
            if (areFriends) {
                binding.tvLastSeen.setText("last seen recently");
            } else {
                binding.tvLastSeen.setText("last seen recently");
            }
            binding.tvLastSeen.setTextColor(getColor(R.color.textColorSecondary));
        }
    }

    private String formatLastSeenTime(long lastSeenTimestamp, boolean areFriends) {
        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - lastSeenTimestamp;

        // Convert to different time units
        long seconds = timeDifference / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        // WhatsApp-like formatting with privacy levels
        if (seconds < 60) {
            return areFriends ? "last seen just now" : "last seen recently";
        } else if (minutes < 60) {
            if (areFriends) {
                return minutes == 1 ? "last seen 1 minute ago" : "last seen " + minutes + " minutes ago";
            } else {
                return "last seen recently";
            }
        } else if (hours < 24) {
            if (areFriends) {
                return hours == 1 ? "last seen 1 hour ago" : "last seen " + hours + " hours ago";
            } else {
                return "last seen recently";
            }
        } else if (days == 1) {
            return "last seen yesterday";
        } else if (days < 7) {
            return areFriends ? "last seen " + days + " days ago" : "last seen this week";
        } else if (weeks < 4) {
            return areFriends ? "last seen " + weeks + " week" + (weeks > 1 ? "s" : "") + " ago" : "last seen recently";
        } else if (months < 12) {
            return areFriends ? "last seen " + months + " month" + (months > 1 ? "s" : "") + " ago" : "last seen a while ago";
        } else {
            return areFriends ? "last seen " + years + " year" + (years > 1 ? "s" : "") + " ago" : "last seen a long time ago";
        }
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
        binding.tvEmail.setVisibility(View.GONE);
        binding.tvAbout.setVisibility(View.GONE);
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