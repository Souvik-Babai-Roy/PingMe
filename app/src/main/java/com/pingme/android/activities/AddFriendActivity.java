package com.pingme.android.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddFriendBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupToolbar();
        setupClickListeners();
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

        if (email.equals(FirebaseAuth.getInstance().getCurrentUser().getEmail())) {
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
                            displayFoundUser(foundUser);
                            checkFriendshipStatus(foundUser.getId());
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

    private void displayFoundUser(User user) {
        binding.layoutUserFound.setVisibility(View.VISIBLE);
        binding.tvUserName.setText(user.getName());
        binding.tvUserEmail.setText(user.getEmail());
        binding.tvUserAbout.setText(user.getAbout());

        // Load profile image
        if (user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(user.getImageUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(binding.ivUserProfile);
        } else {
            binding.ivUserProfile.setImageResource(R.drawable.ic_profile);
        }
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
                    } else {
                        // Not friends
                        binding.btnAddFriend.setText("Add Friend");
                        binding.btnAddFriend.setEnabled(true);
                    }
                });
    }

    private void addFriend() {
        if (foundUser == null) return;

        showLoading(true);

        // Add friend to current user's friends list
        FirestoreUtil.getFriendsRef(currentUserId)
                .document(foundUser.getId())
                .set(foundUser)
                .addOnSuccessListener(aVoid -> {
                    // Add current user to found user's friends list
                    FirestoreUtil.getUserRef(currentUserId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                User currentUser = documentSnapshot.toObject(User.class);
                                if (currentUser != null) {
                                    FirestoreUtil.getFriendsRef(foundUser.getId())
                                            .document(currentUserId)
                                            .set(currentUser)
                                            .addOnSuccessListener(aVoid1 -> {
                                                showLoading(false);
                                                Toast.makeText(this, "Friend added successfully!", Toast.LENGTH_SHORT).show();
                                                binding.btnAddFriend.setText("Added");
                                                binding.btnAddFriend.setEnabled(false);

                                                // Create chat between users
                                                createChatBetweenUsers(currentUser, foundUser);
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to add friend: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void createChatBetweenUsers(User currentUser, User otherUser) {
        String chatId = FirestoreUtil.generateChatId(currentUser.getId(), otherUser.getId());

        // Create chat document
        FirestoreUtil.getChatRef(chatId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        // Chat doesn't exist, create it
                        FirestoreUtil.createNewChat(chatId, currentUser.getId(), otherUser.getId());
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
        if (binding.btnAddFriend.isEnabled()) {
            binding.btnAddFriend.setEnabled(!show);
        }
    }
}