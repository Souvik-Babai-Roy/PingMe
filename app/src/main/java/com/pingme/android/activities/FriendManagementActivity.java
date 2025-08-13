package com.pingme.android.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityFriendManagementBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

public class FriendManagementActivity extends AppCompatActivity {

    private ActivityFriendManagementBinding binding;
    private String currentUserId;
    private User friendUser;
    private String friendId;
    private boolean isFriend = false;
    private boolean isBlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFriendManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        // Get friend ID from intent
        friendId = getIntent().getStringExtra("FRIEND_ID");
        if (friendId == null) {
            Toast.makeText(this, "Invalid friend data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupClickListeners();
        loadFriendData();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Friend Management");
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupClickListeners() {
        binding.btnUnfriend.setOnClickListener(v -> showUnfriendDialog());
        binding.btnBlock.setOnClickListener(v -> showBlockDialog());
        binding.btnUnblock.setOnClickListener(v -> showUnblockDialog());
    }

    private void loadFriendData() {
        showLoading(true);
        
        // First check if they are friends
        checkFriendshipStatus();
        
        // Then check if blocked
        checkBlockStatus();
        
        // Load user data
        FirestoreUtil.getUserRef(friendId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        friendUser = documentSnapshot.toObject(User.class);
                        if (friendUser != null) {
                            friendUser.setId(documentSnapshot.getId());
                            displayFriendData();
                        }
                    } else {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to load user data: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void checkFriendshipStatus() {
        FirestoreUtil.checkFriendship(currentUserId, friendId, areFriends -> {
            isFriend = areFriends;
            updateButtons();
        });
    }

    private void checkBlockStatus() {
        FirestoreUtil.checkIfBlocked(currentUserId, friendId, blocked -> {
            isBlocked = blocked;
            updateButtons();
        });
    }

    private void displayFriendData() {
        if (friendUser == null) return;

        binding.tvUserName.setText(friendUser.getName());
        binding.tvUserEmail.setText(friendUser.getEmail());

        // Show about if available and enabled
        if (friendUser.getAbout() != null && !friendUser.getAbout().isEmpty() && friendUser.isAboutEnabled()) {
            binding.tvUserAbout.setVisibility(View.VISIBLE);
            binding.tvUserAbout.setText(friendUser.getAbout());
        } else {
            binding.tvUserAbout.setVisibility(View.GONE);
        }

        // Load profile image if available and user allows it
        if (friendUser.hasProfilePhoto() && friendUser.getImageUrl() != null && !friendUser.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(friendUser.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person_outline)
                    .into(binding.ivUserProfile);
        } else {
            binding.ivUserProfile.setImageResource(R.drawable.ic_person_outline);
        }

        // Load presence if user allows it
        loadPresence();
    }

    private void loadPresence() {
        if (friendUser == null || !friendUser.shouldShowLastSeen()) {
            binding.tvOnlineStatus.setVisibility(View.GONE);
            binding.onlineIndicator.setVisibility(View.GONE);
            return;
        }

        FirestoreUtil.getRealtimePresenceRef(friendId)
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
                                String lastSeenText = formatLastSeen(System.currentTimeMillis() - lastSeen);
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

    private void updateButtons() {
        if (isBlocked) {
            // User is blocked - show only unblock button
            binding.btnUnfriend.setVisibility(View.GONE);
            binding.btnBlock.setVisibility(View.GONE);
            binding.btnUnblock.setVisibility(View.VISIBLE);
        } else if (isFriend) {
            // User is a friend - show unfriend and block buttons
            binding.btnUnfriend.setVisibility(View.VISIBLE);
            binding.btnBlock.setVisibility(View.VISIBLE);
            binding.btnUnblock.setVisibility(View.GONE);
        } else {
            // User is neither friend nor blocked - hide all buttons or show add friend
            binding.btnUnfriend.setVisibility(View.GONE);
            binding.btnBlock.setVisibility(View.VISIBLE);
            binding.btnUnblock.setVisibility(View.GONE);
        }
    }

    private void showUnfriendDialog() {
        if (friendUser == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Remove Friend")
                .setMessage("Are you sure you want to remove " + friendUser.getName() + " from your friends list?")
                .setPositiveButton("Remove", (dialog, which) -> unfriendUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBlockDialog() {
        if (friendUser == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Block User")
                .setMessage("Are you sure you want to block " + friendUser.getName() + "? This will remove them from your friends list and they won't be able to message you.")
                .setPositiveButton("Block", (dialog, which) -> blockUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUnblockDialog() {
        if (friendUser == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Unblock User")
                .setMessage("Are you sure you want to unblock " + friendUser.getName() + "?")
                .setPositiveButton("Unblock", (dialog, which) -> unblockUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unfriendUser() {
        showLoading(true);
        
        FirestoreUtil.removeFriend(currentUserId, friendId, new FirestoreUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(FriendManagementActivity.this, 
                        friendUser.getName() + " has been removed from your friends list", 
                        Toast.LENGTH_SHORT).show();
                isFriend = false;
                updateButtons();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(FriendManagementActivity.this, "Failed to remove friend: " + error, 
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void blockUser() {
        showLoading(true);
        
        FirestoreUtil.blockUser(currentUserId, friendId, new FirestoreUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(FriendManagementActivity.this, 
                        friendUser.getName() + " has been blocked", 
                        Toast.LENGTH_SHORT).show();
                isBlocked = true;
                isFriend = false;
                updateButtons();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(FriendManagementActivity.this, "Failed to block user: " + error, 
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void unblockUser() {
        showLoading(true);
        
        FirestoreUtil.unblockUser(currentUserId, friendId, new FirestoreUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(FriendManagementActivity.this, 
                        friendUser.getName() + " has been unblocked", 
                        Toast.LENGTH_SHORT).show();
                isBlocked = false;
                updateButtons();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(FriendManagementActivity.this, "Failed to unblock user: " + error, 
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.layoutButtons.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}