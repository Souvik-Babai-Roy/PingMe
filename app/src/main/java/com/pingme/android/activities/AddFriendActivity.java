package com.pingme.android.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseUser;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityAddFriendBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;

public class AddFriendActivity extends AppCompatActivity {

    private ActivityAddFriendBinding binding;
    private String currentUserId;
    private User foundUser;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddFriendBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        setupToolbar();
        setupClickListeners();
        loadCurrentUser();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Friend");
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupClickListeners() {
        binding.btnSearch.setOnClickListener(v -> searchUserByEmail());
        binding.btnAddFriend.setOnClickListener(v -> addFriend());
    }

    private void loadCurrentUser() {
        FirestoreUtil.getUserRef(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            currentUser.setId(documentSnapshot.getId());
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                });
    }

    private void searchUserByEmail() {
        String email = binding.etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            binding.etEmail.setError("Email is required");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Please enter a valid email");
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && email.equals(currentUser.getEmail())) {
            Toast.makeText(this, "You cannot add yourself as a friend", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Use the simplified search method
        FirestoreUtil.searchUserByEmail(email, new FirestoreUtil.UserSearchCallback() {
            @Override
            public void onUserFound(User user) {
                showLoading(false);
                foundUser = user;

                // Check if blocked before displaying user
                checkIfBlocked(user.getId());
            }

            @Override
            public void onUserNotFound() {
                showLoading(false);
                showUserNotFound();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(AddFriendActivity.this, "Search failed: " + error, Toast.LENGTH_SHORT).show();
                Log.e("AddFriendActivity", "Search error: " + error);
            }
        });
    }

    private void checkIfBlocked(String userId) {
        FirestoreUtil.checkIfBlocked(currentUserId, userId, isBlocked -> {
            if (isBlocked) {
                Toast.makeText(this, "Cannot add this user", Toast.LENGTH_SHORT).show();
                showUserNotFound();
            } else {
                // Check if user has blocked current user
                FirestoreUtil.checkIfBlocked(userId, currentUserId, hasBlockedMe -> {
                    if (hasBlockedMe) {
                        Toast.makeText(this, "Cannot add this user", Toast.LENGTH_SHORT).show();
                        showUserNotFound();
                    } else {
                        displayFoundUser(foundUser);
                        checkFriendshipStatus(foundUser.getId());
                    }
                });
            }
        });
    }

    private void displayFoundUser(User user) {
        binding.layoutUserFound.setVisibility(View.VISIBLE);
        
        // Always show name (required field)
        binding.tvUserName.setText(user.getDisplayName());
        binding.tvUserEmail.setText(user.getEmail());

        // Show about if available and user allows it
        if (user.getAbout() != null && !user.getAbout().isEmpty() && user.isAboutEnabled()) {
            binding.tvUserAbout.setVisibility(View.VISIBLE);
            binding.tvUserAbout.setText(user.getAbout());
        } else {
            binding.tvUserAbout.setVisibility(View.GONE);
        }

        // Load profile image if available and user allows it
        if (user.hasProfilePhoto() && user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
            binding.ivUserProfile.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(user.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person_outline)
                    .error(R.drawable.ic_person_outline)
                    .into(binding.ivUserProfile);
        } else {
            binding.ivUserProfile.setVisibility(View.VISIBLE);
            binding.ivUserProfile.setImageResource(R.drawable.ic_person_outline);
        }

        // Load user presence if user allows it
        loadUserPresence(user);
        
        // Show additional user info based on privacy settings
        displayUserPrivacyInfo(user);
    }

    private void displayUserPrivacyInfo(User user) {
        // Show phone number if available and user allows it
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
            binding.tvUserPhone.setVisibility(View.VISIBLE);
            binding.tvUserPhone.setText("📱 " + user.getPhoneNumber());
        } else {
            binding.tvUserPhone.setVisibility(View.GONE);
        }

        // Show join date if available
        if (user.getJoinedAt() > 0) {
            binding.tvUserJoined.setVisibility(View.VISIBLE);
            String joinDate = formatJoinDate(user.getJoinedAt());
            binding.tvUserJoined.setText("📅 Joined " + joinDate);
        } else {
            binding.tvUserJoined.setVisibility(View.GONE);
        }

        // Show privacy indicators
        StringBuilder privacyInfo = new StringBuilder();
        if (!user.isProfilePhotoEnabled()) {
            privacyInfo.append("🔒 Profile photo hidden\n");
        }
        if (!user.isLastSeenEnabled()) {
            privacyInfo.append("🔒 Last seen hidden\n");
        }
        if (!user.isAboutEnabled()) {
            privacyInfo.append("🔒 About hidden\n");
        }
        if (!user.isReadReceiptsEnabled()) {
            privacyInfo.append("🔒 Read receipts disabled\n");
        }

        if (privacyInfo.length() > 0) {
            binding.tvPrivacyInfo.setVisibility(View.VISIBLE);
            binding.tvPrivacyInfo.setText(privacyInfo.toString().trim());
        } else {
            binding.tvPrivacyInfo.setVisibility(View.GONE);
        }
    }

    private String formatJoinDate(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long days = diff / (1000 * 60 * 60 * 24);
        long months = days / 30;
        long years = months / 12;

        if (years > 0) {
            return years + " year" + (years > 1 ? "s" : "") + " ago";
        } else if (months > 0) {
            return months + " month" + (months > 1 ? "s" : "") + " ago";
        } else if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            return "today";
        }
    }

    private void loadUserPresence(User user) {
        if (!user.shouldShowLastSeen()) {
            binding.tvOnlineStatus.setVisibility(View.GONE);
            binding.onlineIndicator.setVisibility(View.GONE);
            return;
        }

        FirestoreUtil.getRealtimePresenceRef(user.getId())
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                            Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                            if (isOnline != null && isOnline) {
                                binding.tvOnlineStatus.setText("online");
                                binding.tvOnlineStatus.setVisibility(View.VISIBLE);
                                binding.onlineIndicator.setVisibility(View.VISIBLE);
                            } else if (lastSeen != null && lastSeen > 0) {
                                long diff = System.currentTimeMillis() - lastSeen;
                                String lastSeenText = formatLastSeen(diff);
                                binding.tvOnlineStatus.setText(lastSeenText);
                                binding.tvOnlineStatus.setVisibility(View.VISIBLE);
                                binding.onlineIndicator.setVisibility(View.GONE);
                            } else {
                                binding.tvOnlineStatus.setText("offline");
                                binding.tvOnlineStatus.setVisibility(View.VISIBLE);
                                binding.onlineIndicator.setVisibility(View.GONE);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError databaseError) {
                        binding.tvOnlineStatus.setVisibility(View.GONE);
                        binding.onlineIndicator.setVisibility(View.GONE);
                    }
                });
    }

    private String formatLastSeen(long diffMs) {
        long minutes = diffMs / (1000 * 60);
        long hours = minutes / 60;
        long days = hours / 24;

        if (minutes < 1) return "last seen just now";
        if (minutes < 60) return "last seen " + minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        if (hours < 24) return "last seen " + hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        if (days < 7) return "last seen " + days + " day" + (days > 1 ? "s" : "") + " ago";
        return "last seen a long time ago";
    }

    private void checkFriendshipStatus(String userId) {
        FirestoreUtil.checkFriendship(currentUserId, userId, areFriends -> {
            if (areFriends) {
                // Already friends
                binding.btnAddFriend.setText("Already Friends");
                binding.btnAddFriend.setEnabled(false);
                binding.btnAddFriend.setBackgroundTintList(
                        getColorStateList(android.R.color.darker_gray));
            } else {
                // Not friends
                binding.btnAddFriend.setText("Add Friend");
                binding.btnAddFriend.setEnabled(true);
                binding.btnAddFriend.setBackgroundTintList(
                        getColorStateList(R.color.colorPrimary));
            }
        });
    }

    private void addFriend() {
        if (foundUser == null || currentUser == null) {
            Toast.makeText(this, "Unable to send friend request. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Send friend request instead of directly adding friend
        FirestoreUtil.sendFriendRequest(currentUserId, foundUser.getEmail());
        
        showLoading(false);
        Toast.makeText(AddFriendActivity.this, 
            "Friend request sent to " + foundUser.getName(), 
            Toast.LENGTH_SHORT).show();
        binding.btnAddFriend.setText("Request Sent");
        binding.btnAddFriend.setEnabled(false);
        binding.btnAddFriend.setBackgroundTintList(
                getColorStateList(android.R.color.darker_gray));
    }

    private void showUserNotFound() {
        binding.layoutUserFound.setVisibility(View.GONE);
        Toast.makeText(this, "No user found with this email", Toast.LENGTH_SHORT).show();
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnSearch.setEnabled(!show);

        if (foundUser != null && binding.btnAddFriend.isEnabled()) {
            binding.btnAddFriend.setEnabled(!show);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}