package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseUser;
import com.pingme.android.R;
import com.pingme.android.adapters.FriendsAdapter;
import com.pingme.android.databinding.ActivityAddFriendBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class AddFriendActivity extends AppCompatActivity implements FriendsAdapter.OnFriendClickListener {

    private ActivityAddFriendBinding binding;
    private String currentUserId;
    private User foundUser;
    private User currentUser;

    private FriendsAdapter friendsAdapter;
    private final List<User> friends = new ArrayList<>();
    private final List<User> filteredFriends = new ArrayList<>();

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
        setupFriendsList();
        loadCurrentUser();
        loadFriends();
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
        binding.etFriendSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filterFriends(s.toString()); }
        });
    }

    private void setupFriendsList() {
        friendsAdapter = new FriendsAdapter(this, filteredFriends, this);
        binding.recyclerFriends.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerFriends.setAdapter(friendsAdapter);
    }

    private void loadFriends() {
        FirebaseUtil.getFriendsRef(currentUserId).get()
                .addOnSuccessListener(querySnapshot -> {
                    friends.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String friendId = doc.getId();
                        String personalName = doc.getString("personalName");
                        
                        FirebaseUtil.getUserRef(friendId).get().addOnSuccessListener(userDoc -> {
                            if (userDoc.exists()) {
                                User friend = userDoc.toObject(User.class);
                                if (friend != null) {
                                    friend.setId(friendId);
                                    friend.setPersonalName(personalName);
                                    friend.setFriendshipStatus("friend");
                                    
                                    // Load presence data respecting privacy settings
                                    loadFriendPresence(friend, () -> {
                                        friends.add(friend);
                                        filterFriends(binding.etFriendSearch.getText() != null ? binding.etFriendSearch.getText().toString() : "");
                                    });
                                }
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> Log.e("AddFriendActivity", "Failed to load friends", e));
    }
    
    private void loadFriendPresence(User friend, Runnable onComplete) {
        FirebaseUtil.getRealtimePresenceRef(friend.getId())
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            // Use utility method to safely convert online status
                            boolean isOnline = FirebaseUtil.safeBooleanValue(dataSnapshot.child("isOnline").getValue());
                            Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                            friend.setOnline(isOnline);
                            friend.setLastSeen(lastSeen != null ? lastSeen : 0);
                        }
                        onComplete.run();
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError databaseError) {
                        Log.e("AddFriendActivity", "Failed to load friend presence", databaseError.toException());
                        onComplete.run();
                    }
                });
    }

    private void filterFriends(String query) {
        filteredFriends.clear();
        if (query == null) query = "";
        String q = query.toLowerCase();
        for (User u : friends) {
            if (u.getDisplayName().toLowerCase().contains(q) || u.getEmail().toLowerCase().contains(q)) {
                filteredFriends.add(u);
            }
        }
        friendsAdapter.updateFriends(new ArrayList<>(filteredFriends));
    }

    @Override
    public void onFriendClick(User friend) {
        // Start chat with the clicked friend
        startChatWithFriend(friend);
    }
    
    private void startChatWithFriend(User friend) {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Toast.makeText(this, "User authentication error", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (friend.getId() == null || friend.getId().isEmpty()) {
            Toast.makeText(this, "Friend data error", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Generate chat ID (same logic as FriendsFragment)
        String chatId = currentUserId + "_" + friend.getId();
        if (currentUserId.compareTo(friend.getId()) > 0) {
            chatId = friend.getId() + "_" + currentUserId;
        }
        
        // Open chat activity
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("receiverId", friend.getId());
        startActivity(intent);
    }

    private void loadCurrentUser() {
        FirebaseUtil.getUserRef(currentUserId).get()
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
        String email = binding.etFriendSearch.getText().toString().trim();

        if (email.isEmpty()) {
            binding.etFriendSearch.setError("Email is required");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etFriendSearch.setError("Please enter a valid email");
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && email.equals(currentUser.getEmail())) {
            Toast.makeText(this, "You cannot add yourself as a friend", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Use the simplified search method
        FirebaseUtil.searchUserByEmail(email, new FirebaseUtil.UserSearchCallback() {
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
        FirebaseUtil.checkIfBlocked(currentUserId, userId, isBlocked -> {
            if (isBlocked) {
                Toast.makeText(this, "Cannot add this user", Toast.LENGTH_SHORT).show();
                showUserNotFound();
            } else {
                // Check if user has blocked current user
                FirebaseUtil.checkIfBlocked(userId, currentUserId, hasBlockedMe -> {
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
        if (user.isAboutEnabled() && user.getDisplayAbout() != null && !user.getDisplayAbout().isEmpty()) {
            binding.tvUserAbout.setVisibility(View.VISIBLE);
            binding.tvUserAbout.setText(user.getDisplayAbout());
        } else {
            binding.tvUserAbout.setVisibility(View.GONE);
        }

        // Load profile image respecting privacy settings
        if (user.hasProfilePhoto()) {
            binding.ivUserProfile.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(user.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.defaultprofile)
                    .error(R.drawable.defaultprofile)
                    .into(binding.ivUserProfile);
        } else {
            binding.ivUserProfile.setVisibility(View.VISIBLE);
            binding.ivUserProfile.setImageResource(R.drawable.defaultprofile);
        }

        // Load user presence if user allows it
        loadUserPresence(user);
        
        // Show additional user info based on privacy settings
        displayUserPrivacyInfo(user);
    }

    private void displayUserPrivacyInfo(User user) {
        // Privacy info is now shown in the about field if needed
        // This method is kept for compatibility but simplified
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
        // Presence info is simplified in the new layout
        // This method is kept for compatibility but simplified
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
        FirebaseUtil.checkFriendship(currentUserId, userId, areFriends -> {
            if (areFriends) {
                // Already friends
                binding.btnAddFriend.setText("Added");
                binding.btnAddFriend.setEnabled(false);
                binding.btnAddFriend.setBackgroundTintList(
                        getColorStateList(android.R.color.darker_gray));
            } else {
                // Not friends
                binding.btnAddFriend.setText("Add");
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

        // Directly add friend instead of sending request (like WhatsApp)
        FirebaseUtil.addFriend(currentUserId, foundUser.getId(), new FirebaseUtil.FriendActionCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(AddFriendActivity.this, 
                    foundUser.getDisplayName() + " added to contacts", 
                    Toast.LENGTH_SHORT).show();
                binding.btnAddFriend.setText("Added");
                binding.btnAddFriend.setEnabled(false);
                binding.btnAddFriend.setBackgroundTintList(
                        getColorStateList(android.R.color.darker_gray));
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(AddFriendActivity.this, 
                    "Failed to add friend: " + error, 
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