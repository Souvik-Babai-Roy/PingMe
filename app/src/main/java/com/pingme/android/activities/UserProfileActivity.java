package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityUserProfileBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

public class UserProfileActivity extends AppCompatActivity {
    private static final String TAG = "UserProfileActivity";

    private ActivityUserProfileBinding binding;
    private String userId;
    private String currentUserId;
    private User userProfile;
    private ValueEventListener presenceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = getIntent().getStringExtra("userId");
        if (userId == null) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                       FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        setupToolbar();
        loadUserProfile();
        setupClickListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadUserProfile() {
        FirestoreUtil.getUserRef(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        userProfile = documentSnapshot.toObject(User.class);
                        if (userProfile != null) {
                            userProfile.setId(documentSnapshot.getId());
                            updateUI();
                            setupPresenceListener();
                        }
                    } else {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load user profile", e);
                    Toast.makeText(this, "Failed to load user profile", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void updateUI() {
        if (userProfile == null) return;

        // Set user name
        binding.tvUserName.setText(userProfile.getDisplayName());

        // Load profile picture respecting privacy settings
        if (userProfile.shouldShowProfilePhoto() && userProfile.getImageUrl() != null && !userProfile.getImageUrl().trim().isEmpty()) {
            Glide.with(this)
                    .load(userProfile.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.defaultprofile)
                    .error(R.drawable.defaultprofile)
                    .into(binding.ivProfileImage);
        } else {
            binding.ivProfileImage.setImageResource(R.drawable.defaultprofile);
        }

        // Set about text respecting privacy settings
        if (userProfile.shouldShowAbout() && userProfile.getAbout() != null && !userProfile.getAbout().trim().isEmpty()) {
            binding.tvAbout.setText(userProfile.getAbout());
            binding.tvAbout.setVisibility(View.VISIBLE);
        } else {
            binding.tvAbout.setVisibility(View.GONE);
        }

        // Set phone number (always show if available)
        if (userProfile.getPhoneNumber() != null && !userProfile.getPhoneNumber().trim().isEmpty()) {
            binding.tvPhoneNumber.setText(userProfile.getPhoneNumber());
            binding.tvPhoneNumber.setVisibility(View.VISIBLE);
        } else {
            binding.tvPhoneNumber.setVisibility(View.GONE);
        }

        // Set joined date
        if (userProfile.getJoinedAt() > 0) {
            String joinedDate = DateUtils.getRelativeTimeSpanString(
                    userProfile.getJoinedAt(),
                    System.currentTimeMillis(),
                    DateUtils.DAY_IN_MILLIS
            ).toString();
            binding.tvJoinedDate.setText("Joined " + joinedDate);
            binding.tvJoinedDate.setVisibility(View.VISIBLE);
        } else {
            binding.tvJoinedDate.setVisibility(View.GONE);
        }

        // Update online status
        updateOnlineStatus();
    }

    private void setupPresenceListener() {
        if (userId == null) return;

        presenceListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (userProfile != null) {
                    Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                    Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                    userProfile.setOnline(isOnline != null ? isOnline : false);
                    userProfile.setLastSeen(lastSeen != null ? lastSeen : 0);

                    updateOnlineStatus();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Presence listener cancelled", databaseError.toException());
            }
        };

        FirestoreUtil.getRealtimePresenceRef(userId).addValueEventListener(presenceListener);
    }

    private void updateOnlineStatus() {
        if (userProfile == null) return;

        if (userProfile.shouldShowLastSeen()) {
            if (userProfile.isOnline()) {
                binding.tvOnlineStatus.setText("online");
                binding.tvOnlineStatus.setTextColor(getColor(R.color.online_green));
                binding.onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                String status = userProfile.getOnlineStatus();
                binding.tvOnlineStatus.setText(status);
                binding.tvOnlineStatus.setTextColor(getColor(R.color.textColorSecondary));
                binding.onlineIndicator.setVisibility(View.GONE);
            }
            binding.tvOnlineStatus.setVisibility(View.VISIBLE);
        } else {
            binding.tvOnlineStatus.setVisibility(View.GONE);
            binding.onlineIndicator.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        // Message button
        binding.btnMessage.setOnClickListener(v -> {
            if (userProfile != null) {
                String chatId = FirestoreUtil.generateChatId(currentUserId, userProfile.getId());
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("chatId", chatId);
                intent.putExtra("receiverId", userProfile.getId());
                startActivity(intent);
                finish();
            }
        });

        // Voice call button
        binding.btnVoiceCall.setOnClickListener(v -> {
            Toast.makeText(this, "Voice call feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Video call button
        binding.btnVideoCall.setOnClickListener(v -> {
            Toast.makeText(this, "Video call feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Block button
        binding.btnBlock.setOnClickListener(v -> {
            if (userProfile != null) {
                showBlockDialog();
            }
        });
    }

    private void showBlockDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Block " + userProfile.getDisplayName())
                .setMessage("Are you sure you want to block this user? You won't be able to send or receive messages from them.")
                .setPositiveButton("Block", (dialog, which) -> {
                    if (currentUserId != null && userProfile != null) {
                        FirestoreUtil.blockUser(currentUserId, userProfile.getId());
                        Toast.makeText(this, "User blocked", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_profile_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_more) {
            showMoreOptions();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showMoreOptions() {
        if (userProfile == null) return;

        String[] options = {"View contact", "Media, links, and docs", "Search", "Mute notifications", "Clear chat", "Report"};
        
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // View contact
                            // Already in profile view
                            break;
                        case 1: // Media, links, and docs
                            Toast.makeText(this, "Media view coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 2: // Search
                            Toast.makeText(this, "Search feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 3: // Mute notifications
                            Toast.makeText(this, "Mute feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 4: // Clear chat
                            showClearChatDialog();
                            break;
                        case 5: // Report
                            Toast.makeText(this, "Report feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .show();
    }

    private void showClearChatDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Chat")
                .setMessage("Are you sure you want to clear this chat? This action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    if (currentUserId != null && userProfile != null) {
                        String chatId = FirestoreUtil.generateChatId(currentUserId, userProfile.getId());
                        FirestoreUtil.clearChatHistoryForUser(chatId, currentUserId);
                        Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenceListener != null && userId != null) {
            FirestoreUtil.getRealtimePresenceRef(userId).removeEventListener(presenceListener);
        }
    }
}