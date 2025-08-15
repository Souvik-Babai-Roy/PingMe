package com.pingme.android.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.pingme.android.adapters.BlockedUsersAdapter;
import com.pingme.android.databinding.ActivityBlockedUsersBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.util.ArrayList;
import java.util.List;

public class BlockedUsersActivity extends AppCompatActivity {
    private static final String TAG = "BlockedUsersActivity";
    
    private ActivityBlockedUsersBinding binding;
    private BlockedUsersAdapter adapter;
    private String currentUserId;
    private List<User> blockedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBlockedUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        setupToolbar();
        setupRecyclerView();
        loadBlockedUsers();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Blocked Users");
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupRecyclerView() {
        adapter = new BlockedUsersAdapter(this, blockedUsers, new BlockedUsersAdapter.OnUnblockListener() {
            @Override
            public void onUnblock(User user, int position) {
                unblockUser(user, position);
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void loadBlockedUsers() {
        showLoading(true);
        
        FirebaseUtil.getBlockedUsers(currentUserId, new FirebaseUtil.BlockedUsersCallback() {
            @Override
            public void onBlockedUsersLoaded(List<User> users) {
                blockedUsers.clear();
                blockedUsers.addAll(users);
                
                if (users.isEmpty()) {
                    showEmptyState(true);
                } else {
                    showEmptyState(false);
                    adapter.notifyDataSetChanged();
                }
                
                showLoading(false);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading blocked users: " + error);
                showLoading(false);
                showEmptyState(true);
            }
        });
    }

    private void loadBlockedUserDetails(List<String> blockedUserIds, QuerySnapshot blockingData) {
        int totalUsers = blockedUserIds.size();
        final int[] loadedCount = {0};

        for (int i = 0; i < blockedUserIds.size(); i++) {
            String userId = blockedUserIds.get(i);
            DocumentSnapshot blockDoc = blockingData.getDocuments().get(i);
            
            FirebaseUtil.getUserRef(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null) {
                                user.setId(documentSnapshot.getId());
                                
                                // Add blocked timestamp for display
                                Long blockedAt = blockDoc.getLong("blockedAt");
                                if (blockedAt != null) {
                                    user.setLastSeen(blockedAt); // Reuse this field for blocked time
                                }
                                
                                blockedUsers.add(user);
                            }
                        }
                        
                        loadedCount[0]++;
                        if (loadedCount[0] == totalUsers) {
                            // All users loaded
                            showLoading(false);
                            if (blockedUsers.isEmpty()) {
                                showEmptyState(true);
                            } else {
                                showEmptyState(false);
                                adapter.notifyDataSetChanged();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading user details", e);
                        loadedCount[0]++;
                        if (loadedCount[0] == totalUsers) {
                            showLoading(false);
                            if (blockedUsers.isEmpty()) {
                                showEmptyState(true);
                            } else {
                                showEmptyState(false);
                                adapter.notifyDataSetChanged();
                            }
                        }
                    });
        }
    }

    private void unblockUser(User user, int position) {
        FirebaseUtil.unblockUser(currentUserId, user.getId());
        
        // Remove from list
        blockedUsers.remove(position);
        adapter.notifyItemRemoved(position);
        
        // Show empty state if no more blocked users
        if (blockedUsers.isEmpty()) {
            showEmptyState(true);
        }
        
        Log.d(TAG, "User unblocked: " + user.getDisplayName());
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        binding.emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}