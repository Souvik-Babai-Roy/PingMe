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

    private void loadFriends() {
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
                                            
                                            friendsList.add(friend);
                                            filterFriends(binding.searchEditText.getText().toString());
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error loading friend details for " + friendId, e);
                                });
                    }
                    
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading friends", e);
                    showLoading(false);
                    Toast.makeText(this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                });
    }

    private void filterFriends(String query) {
        filteredFriendsList.clear();
        
        if (query.isEmpty()) {
            filteredFriendsList.addAll(friendsList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (User friend : friendsList) {
                String displayName = friend.getPersonalName() != null && !friend.getPersonalName().isEmpty() 
                    ? friend.getPersonalName() 
                    : friend.getName();
                    

            }
        }
        
        friendsAdapter.notifyDataSetChanged();
        updateEmptyState();
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
        // Refresh friends list when returning to this activity
        loadFriends();
    }
}