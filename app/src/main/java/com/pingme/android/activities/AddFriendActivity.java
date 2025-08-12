package com.pingme.android.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityAddFriendBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

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

        FirestoreUtil.getUsersCollectionRef()
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    showLoading(false);

                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        foundUser = doc.toObject(User.class);

                        if (foundUser != null) {
                            foundUser.setId(doc.getId());

                            // Check if user is blocked
                            checkIfBlocked(foundUser.getId());
                        }
                    } else {
                        showUserNotFound();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

        // Show about only if user allows it in privacy settings
        if (user.isAboutEnabled()) {
            binding.tvUserAbout.setVisibility(View.VISIBLE);
            binding.tvUserAbout.setText(user.getAbout());
        } else {
            binding.tvUserAbout.setVisibility(View.GONE);
        }

        // Load profile image only if user allows it in privacy settings
        if (user.isProfilePhotoEnabled() && user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(user.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_profile)
                    .into(binding.ivUserProfile);
        } else {
            binding.ivUserProfile.setImageResource(R.drawable.ic_profile);
        }

        // Show online status only if user allows it
        if (user.isLastSeenEnabled()) {
            loadUserPresence(user);
        } else {
            binding.tvOnlineStatus.setVisibility(View.GONE);
            binding.onlineIndicator.setVisibility(View.GONE);
        }
    }

    private void loadUserPresence(User user) {
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
        FirestoreUtil.getFriendsRef(currentUserId)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
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
                })
                .addOnFailureListener(e -> {
                    // Default to allowing add if check fails
                    binding.btnAddFriend.setText("Add Friend");
                    binding.btnAddFriend.setEnabled(true);
                });
    }

    private void addFriend() {
        if (foundUser == null || currentUser == null) {
            Toast.makeText(this, "Unable to add friend. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Add friend to current user's friends list
        FirestoreUtil.getFriendsRef(currentUserId)
                .document(foundUser.getId())
                .set(foundUser)
                .addOnSuccessListener(aVoid -> {
                    // Add current user to found user's friends list
                    FirestoreUtil.getFriendsRef(foundUser.getId())
                            .document(currentUserId)
                            .set(currentUser)
                            .addOnSuccessListener(aVoid1 -> {
                                // Create empty chat between users
                                createChatBetweenUsers(currentUser, foundUser);

                                showLoading(false);
                                Toast.makeText(this, "Friend added successfully!", Toast.LENGTH_SHORT).show();
                                binding.btnAddFriend.setText("Added");
                                binding.btnAddFriend.setEnabled(false);
                                binding.btnAddFriend.setBackgroundTintList(
                                        getColorStateList(android.R.color.darker_gray));
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Toast.makeText(this, "Failed to complete friend request: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to add friend: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void createChatBetweenUsers(User currentUser, User otherUser) {
        String chatId = FirestoreUtil.generateChatId(currentUser.getId(), otherUser.getId());

        // Create empty chat for friends (will appear in chat list)
        FirestoreUtil.createEmptyFriendChat(currentUser.getId(), otherUser.getId());

        Toast.makeText(this, "You can now chat with " + otherUser.getName(), Toast.LENGTH_SHORT).show();
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