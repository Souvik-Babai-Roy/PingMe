package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.pingme.android.R;
import com.pingme.android.adapters.FriendsLayoutAdapter;
import com.pingme.android.databinding.ActivityFriendsLayoutBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;
import com.pingme.android.utils.PersonalNameDialog;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class FriendsLayoutActivity extends AppCompatActivity implements FriendsLayoutAdapter.OnFriendClickListener {
    private static final String TAG = "FriendsLayoutActivity";
    
    private ActivityFriendsLayoutBinding binding;
    private FriendsLayoutAdapter friendsAdapter;
    private String currentUserId;
    private List<User> friendsList = new ArrayList<>();
    private List<User> filteredFriendsList = new ArrayList<>();
    private boolean isSearchMode = false;
    private int currentTab = 0; // 0: All Friends, 1: Online Friends, 2: Recent Contacts
    private boolean isLoadingFriends = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFriendsLayoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.e(TAG, "User not authenticated");
            finish();
            return;
        }
        currentUserId = firebaseUser.getUid();
        
        setupToolbar();
        setupRecyclerView();
        setupSearchFunctionality();
        setupAddFriendButton();
        setupTabs();
        loadFriends();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("New Chat");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupRecyclerView() {
        friendsAdapter = new FriendsLayoutAdapter(this, filteredFriendsList, this);
        binding.recyclerViewFriends.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewFriends.setAdapter(friendsAdapter);
        
        // Add long press listener for personal name options
        friendsAdapter.setOnFriendLongClickListener(this::showPersonalNameOptions);
    }

    private void setupSearchFunctionality() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFriends(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupAddFriendButton() {
        binding.btnAddFriend.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddFriendActivity.class);
            startActivity(intent);
        });
    }

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                filterFriendsByTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadFriends() {
        if (isLoadingFriends) {
            Log.d(TAG, "loadFriends already in progress, skipping.");
            return;
        }
        isLoadingFriends = true;
        showLoading(true);
        
        Log.d(TAG, "Loading friends for user: " + currentUserId);
        
        FirebaseUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    friendsList.clear();
                    Log.d(TAG, "Found " + querySnapshot.size() + " friends");
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String friendId = doc.getId();
                        String personalName = doc.getString("personalName");
                        
                        // Check if friend already exists to prevent duplicates
                        boolean alreadyExists = false;
                        for (User existingFriend : friendsList) {
                            if (existingFriend.getId().equals(friendId)) {
                                alreadyExists = true;
                                break;
                            }
                        }
                        
                        if (!alreadyExists) {
                            // Get friend details from users collection
                            FirebaseUtil.getUserRef(friendId)
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            User friend = userDoc.toObject(User.class);
                                            if (friend != null) {
                                                friend.setId(friendId);
                                                friend.setPersonalName(personalName);
                                                friend.setFriendshipStatus("friend");
                                                
                                                // Load presence data respecting privacy settings
                                                loadFriendPresence(friend, () -> {
                                                    friendsList.add(friend);
                                                    filterFriends(binding.searchEditText.getText().toString());
                                                });
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error loading friend details for " + friendId, e);
                                    });
                        }
                    }
                    
                    showLoading(false);
                    isLoadingFriends = false;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading friends", e);
                    showLoading(false);
                    isLoadingFriends = false;
                    Toast.makeText(this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                });
    }

    private void filterFriends(String query) {
        isSearchMode = !query.isEmpty();
        
        // First filter by tab
        List<User> tabFilteredList = new ArrayList<>();
        switch (currentTab) {
            case 0: // All Friends
                tabFilteredList.addAll(friendsList);
                break;
            case 1: // Online Friends (respecting privacy settings)
                for (User friend : friendsList) {
                    if (friend.isLastSeenEnabled() && friend.isOnline()) {
                        tabFilteredList.add(friend);
                    }
                }
                break;
            case 2: // Recent Contacts (for now, same as all friends)
                tabFilteredList.addAll(friendsList);
                break;
        }
        
        // Then filter by search query
        filteredFriendsList.clear();
        if (query.isEmpty()) {
            filteredFriendsList.addAll(tabFilteredList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (User friend : tabFilteredList) {
                String displayName = friend.getPersonalName() != null && !friend.getPersonalName().isEmpty() 
                    ? friend.getPersonalName() 
                    : friend.getName();
                    
                if (displayName != null && displayName.toLowerCase().contains(lowerQuery) ||
                    (friend.getEmail() != null && friend.getEmail().toLowerCase().contains(lowerQuery))) {
                    filteredFriendsList.add(friend);
                }
            }
        }
        
        friendsAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void filterFriendsByTab() {
        // Apply tab filtering with current search query
        String currentQuery = binding.searchEditText.getText().toString();
        filterFriends(currentQuery);
    }

    private void loadFriendPresence(User friend, Runnable onComplete) {
        FirebaseUtil.getRealtimePresenceRef(friend.getId())
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            // Use utility method to safely convert online status
                            boolean isOnline = FirebaseUtil.safeBooleanValue(dataSnapshot.child("isOnline").getValue());
                            Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                            friend.setOnline(isOnline);
                            friend.setLastSeen(lastSeen != null ? lastSeen : 0);
                            
                            Log.d(TAG, "Loaded presence for " + friend.getDisplayName() + " - online: " + isOnline + ", lastSeenEnabled: " + friend.isLastSeenEnabled());
                        }
                        onComplete.run();
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError databaseError) {
                        Log.e(TAG, "Failed to load friend presence", databaseError.toException());
                        onComplete.run();
                    }
                });
    }

    private void updateEmptyState() {
        if (filteredFriendsList.isEmpty()) {
            if (friendsList.isEmpty()) {
                binding.emptyStateText.setText("No friends yet. Add some friends to start chatting!");
                binding.emptyStateText.setVisibility(View.VISIBLE);
            } else {
                binding.emptyStateText.setText("No friends found matching your search.");
                binding.emptyStateText.setVisibility(View.VISIBLE);
            }
        } else {
            binding.emptyStateText.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showPersonalNameOptions(User friend) {
        PersonalNameDialog dialog = new PersonalNameDialog(this, friend, newName -> {
            // Refresh the friends list to show updated personal name
            loadFriends();
        });
        dialog.show();
    }

    @Override
    public void onFriendClick(User friend) {
        // Start chat with the selected friend
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("userId", friend.getId());
        intent.putExtra("userName", friend.getPersonalName() != null && !friend.getPersonalName().isEmpty() 
            ? friend.getPersonalName() 
            : friend.getName());
        intent.putExtra("userImage", friend.getImageUrl());
        startActivity(intent);
        finish(); // Close this activity after starting chat
    }

    public void onFriendLongClick(User friend) {
        showPersonalNameOptions(friend);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.friends_layout_menu, menu);
        
        // Tint menu icons to ensure visibility
        for (int i = 0; i < menu.size(); i++) {
            MenuItem menuItem = menu.getItem(i);
            if (menuItem.getIcon() != null) {
                menuItem.getIcon().setTint(getColor(R.color.colorOnPrimary));
            }
        }
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (id == R.id.action_search_users) {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only reload friends if the list is empty to prevent unnecessary duplicate loading
        if (friendsList.isEmpty()) {
            loadFriends();
        }
    }
}