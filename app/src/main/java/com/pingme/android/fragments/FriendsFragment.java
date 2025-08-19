package com.pingme.android.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.pingme.android.activities.ChatActivity;
import com.pingme.android.adapters.FriendsAdapter;
import com.pingme.android.databinding.FragmentFriendsBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;
import com.pingme.android.utils.PersonalNameDialog;

import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends Fragment implements FriendsAdapter.OnFriendClickListener {
    private static final String TAG = "FriendsFragment";
    
    private FragmentFriendsBinding binding;
    private FriendsAdapter friendsAdapter;
    private String currentUserId;
    private List<User> friendsList = new ArrayList<>();
    private List<User> filteredFriendsList = new ArrayList<>();
    private boolean isSearchMode = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFriendsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.e(TAG, "User not authenticated");
            return;
        }
        currentUserId = firebaseUser.getUid();
        
        setupRecyclerView();
        setupSearchFunctionality();
        loadFriends();
    }

    private void setupRecyclerView() {
        friendsAdapter = new FriendsAdapter(getContext(), filteredFriendsList, this);
        binding.recyclerViewFriends.setLayoutManager(new LinearLayoutManager(getContext()));
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
                        
                        // Load friend's user info and personal name
                        loadFriendWithPersonalName(friendId, doc);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to load friends", e);
                    Toast.makeText(getContext(), "Failed to load friends", Toast.LENGTH_SHORT).show();
                    updateUI();
                });
    }
    
    private void loadFriendWithPersonalName(String friendId, DocumentSnapshot friendDoc) {
        // First, get the friend's user info
        FirebaseUtil.getUserRef(friendId).get()
                .addOnSuccessListener(userSnapshot -> {
                    if (userSnapshot.exists()) {
                        User friend = userSnapshot.toObject(User.class);
                        if (friend != null) {
                            friend.setId(friendId);
                            
                            // Check if there's a personal name set for this friend
                            if (friendDoc.contains("personalName") && friendDoc.get("personalName") != null) {
                                String personalName = friendDoc.getString("personalName");
                                friend.setPersonalName(personalName);
                            }
                            
                            // FIXED: Check for duplicates before adding
                            boolean alreadyExists = false;
                            for (User existingFriend : friendsList) {
                                if (existingFriend.getId().equals(friendId)) {
                                    alreadyExists = true;
                                    break;
                                }
                            }
                            
                            if (!alreadyExists) {
                                // Load presence data respecting privacy settings
                                loadFriendPresence(friend, () -> {
                                    friendsList.add(friend);
                                    Log.d(TAG, "Added friend: " + friend.getDisplayName() + " (Online: " + friend.isOnline() + ", Last seen enabled: " + friend.isLastSeenEnabled() + ")");
                                    
                                    // Check if all friends are loaded by getting the total count
                                    friendDoc.getReference().getParent().get()
                                        .addOnSuccessListener(querySnapshot -> {
                                            if (friendsList.size() == querySnapshot.size()) {
                                                // Sort friends by display name (personal name or regular name)
                                                friendsList.sort((f1, f2) -> f1.getDisplayName().compareToIgnoreCase(f2.getDisplayName()));
                                                
                                                // Initially show all friends
                                                filteredFriendsList.clear();
                                                filteredFriendsList.addAll(friendsList);
                                                
                                                showLoading(false);
                                                updateUI();
                                            }
                                        });
                                });
                            } else {
                                // Check if all friends are loaded (for duplicate case)
                                friendDoc.getReference().getParent().get()
                                    .addOnSuccessListener(querySnapshot -> {
                                        if (friendsList.size() == querySnapshot.size()) {
                                            showLoading(false);
                                            updateUI();
                                        }
                                    });
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load friend info for: " + friendId, e);
                    // Continue loading other friends - get total count to check completion
                    friendDoc.getReference().getParent().get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (friendsList.size() == querySnapshot.size()) {
                                showLoading(false);
                                updateUI();
                            }
                        });
                });
    }

    private void filterFriends(String query) {
        isSearchMode = !query.isEmpty();
        
        filteredFriendsList.clear();
        if (query.isEmpty()) {
            filteredFriendsList.addAll(friendsList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (User friend : friendsList) {
                // Enhanced search - check personal name, original name, and email
                String personalName = friend.getPersonalName();
                String originalName = friend.getName();
                String email = friend.getEmail();
                
                boolean matches = false;
                
                // Check personal name first (priority)
                if (personalName != null && !personalName.trim().isEmpty()) {
                    if (personalName.toLowerCase().contains(lowerQuery)) {
                        matches = true;
                    }
                }
                
                // Check original name
                if (!matches && originalName != null && !originalName.trim().isEmpty()) {
                    if (originalName.toLowerCase().contains(lowerQuery)) {
                        matches = true;
                    }
                }
                
                // Check email
                if (!matches && email != null && !email.trim().isEmpty()) {
                    if (email.toLowerCase().contains(lowerQuery)) {
                        matches = true;
                    }
                }
                
                if (matches) {
                    filteredFriendsList.add(friend);
                }
            }
        }
        
        // Sort friends list like WhatsApp (online first, then alphabetical)
        sortFriendsList();
        
        friendsAdapter.notifyDataSetChanged();
        updateUI();
    }
    
    private void sortFriendsList() {
        filteredFriendsList.sort((friend1, friend2) -> {
            // First priority: Online status (if privacy allows)
            boolean friend1Online = friend1.isLastSeenEnabled() && friend1.isOnline();
            boolean friend2Online = friend2.isLastSeenEnabled() && friend2.isOnline();
            
            if (friend1Online && !friend2Online) {
                return -1; // friend1 comes first
            } else if (!friend1Online && friend2Online) {
                return 1; // friend2 comes first
            }
            
            // Second priority: Alphabetical by display name
            String name1 = friend1.getPersonalName() != null && !friend1.getPersonalName().trim().isEmpty()
                    ? friend1.getPersonalName()
                    : friend1.getName();
            String name2 = friend2.getPersonalName() != null && !friend2.getPersonalName().trim().isEmpty()
                    ? friend2.getPersonalName()
                    : friend2.getName();
            
            if (name1 == null) name1 = "";
            if (name2 == null) name2 = "";
            
            return name1.compareToIgnoreCase(name2);
        });
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

    private void updateUI() {
        if (filteredFriendsList.isEmpty()) {
            binding.textNoFriends.setVisibility(View.VISIBLE);
            binding.recyclerViewFriends.setVisibility(View.GONE);
        } else {
            binding.textNoFriends.setVisibility(View.GONE);
            binding.recyclerViewFriends.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean show) {
        if (show) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onFriendClick(User friend) {
        // Start chat with friend
        startChatWithFriend(friend);
    }

    private void startChatWithFriend(User friend) {
        Log.d(TAG, "Starting chat with friend: " + friend.getDisplayName());
        Log.d(TAG, "Current user ID: " + currentUserId);
        Log.d(TAG, "Friend ID: " + friend.getId());
        
        // Validate required data
        if (currentUserId == null || currentUserId.isEmpty()) {
            Toast.makeText(getContext(), "User authentication error. Please restart app.", Toast.LENGTH_LONG).show();
            return;
        }
        
        if (friend.getId() == null || friend.getId().isEmpty()) {
            // Try to get friend ID from email as fallback
            String friendEmail = friend.getEmail();
            if (friendEmail != null && !friendEmail.isEmpty()) {
                // Search for friend by email to get proper ID
                FirebaseUtil.searchUserByEmail(friendEmail, new FirebaseUtil.UserSearchCallback() {
                    @Override
                    public void onUserFound(User foundUser) {
                        foundUser.setPersonalName(friend.getPersonalName()); // Keep personal name
                        startChatWithFriend(foundUser); // Retry with proper ID
                    }
                    
                    @Override
                    public void onUserNotFound() {
                        Toast.makeText(getContext(), "Could not find friend data. Please remove and re-add this friend.", Toast.LENGTH_LONG).show();
                    }
                    
                    @Override
                    public void onError(String error) {
                        Toast.makeText(getContext(), "Error searching for friend: " + error, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Error searching for friend by email: " + error);
                    }
                });
                return;
            } else {
                Toast.makeText(getContext(), "Friend data error. Friend ID is missing.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Friend object: " + friend.toString());
                return;
            }
        }
        
        // Generate chat ID
        String chatId = currentUserId + "_" + friend.getId();
        if (currentUserId.compareTo(friend.getId()) > 0) {
            chatId = friend.getId() + "_" + currentUserId;
        }
        
        Log.d(TAG, "Generated chat ID: " + chatId);
        
        // Open chat activity
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("receiverId", friend.getId());
        
        Log.d(TAG, "Starting ChatActivity with chatId: " + chatId + " and receiverId: " + friend.getId());
        startActivity(intent);
    }
    
    private void showPersonalNameOptions(User friend) {
        String[] options = {"Set Personal Name", "Remove Personal Name", "Cancel"};
        
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Friend Options")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // Set Personal Name
                        showPersonalNameDialog(friend);
                        break;
                    case 1: // Remove Personal Name
                        removePersonalName(friend);
                        break;
                    case 2: // Cancel
                        dialog.dismiss();
                        break;
                }
            })
            .show();
    }
    
    private void showPersonalNameDialog(User friend) {
        PersonalNameDialog dialog = new PersonalNameDialog(requireContext(), friend, personalName -> {
            // Refresh friends list to show updated personal name
            loadFriends();
        });
        dialog.show();
    }
    
    private void removePersonalName(User friend) {
        // Remove personal name from Firestore
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUserId)
            .collection("friends")
            .document(friend.getId())
            .update("personalName", null)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(requireContext(), "Personal name removed", Toast.LENGTH_SHORT).show();
                loadFriends(); // Refresh list
            })
            .addOnFailureListener(e -> {
                Toast.makeText(requireContext(), "Failed to remove personal name", Toast.LENGTH_SHORT).show();
            });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh friends list when fragment becomes visible
        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadFriends();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}