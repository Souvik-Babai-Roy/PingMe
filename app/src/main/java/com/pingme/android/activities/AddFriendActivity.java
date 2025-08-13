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
        binding.tvUserName.setText(user.getName());
        binding.tvUserEmail.setText(user.getEmail());

        // Show about if available (privacy is handled in the data retrieval)
        if (user.getAbout() != null && !user.getAbout().isEmpty() && user.isAboutEnabled()) {
            binding.tvUserAbout.setVisibility(View.VISIBLE);
            binding.tvUserAbout.setText(user.getAbout());
        } else {
            binding.tvUserAbout.setVisibility(View.GONE);
        }

        // Load profile image if available and user allows it
        if (user.hasProfilePhoto() && user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(user.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person_outline)
                    .into(binding.ivUserProfile);
        } else {
            binding.ivUserProfile.setImageResource(R.drawable.ic_person_outline);
        }

        // Load user presence if user allows it
        loadUserPresence(user);
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
            Toast.makeText(this, "Unable to add friend. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Use the simplified add friend method
        FirestoreUtil.addFriend(currentUserId, foundUser, new FirestoreUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AddFriendActivity.this, 
                    "Friend added successfully! You can now chat with " + foundUser.getName(), 
                    Toast.LENGTH_SHORT).show();
                binding.btnAddFriend.setText("Added");
                binding.btnAddFriend.setEnabled(false);
                binding.btnAddFriend.setBackgroundTintList(
                        getColorStateList(android.R.color.darker_gray));
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(AddFriendActivity.this, "Failed to add friend: " + error, 
                        Toast.LENGTH_SHORT).show();
            }
        });
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