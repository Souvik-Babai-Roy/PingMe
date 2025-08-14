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
import com.google.firebase.firestore.QuerySnapshot;
import com.pingme.android.R;
import com.pingme.android.activities.ChatActivity;
import com.pingme.android.adapters.FriendsAdapter;
import com.pingme.android.databinding.FragmentFriendsBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
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
        
        FirestoreUtil.getFriendsRef(currentUserId)
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
        FirestoreUtil.getUserRef(friendId).get()
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
                            
                            friendsList.add(friend);
                            
                            // Check if all friends are loaded
                            if (friendsList.size() == friendDoc.getReference().getParent().get().get().size()) {
                                // Sort friends by display name (personal name or regular name)
                                friendsList.sort((f1, f2) -> f1.getDisplayName().compareToIgnoreCase(f2.getDisplayName()));
                                
                                // Initially show all friends
                                filteredFriendsList.clear();
                                filteredFriendsList.addAll(friendsList);
                                
                                showLoading(false);
                                updateUI();
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load friend info for: " + friendId, e);
                    // Continue loading other friends
                    if (friendsList.size() == friendDoc.getReference().getParent().get().get().size()) {
                        showLoading(false);
                        updateUI();
                    }
                });
    }

    private void filterFriends(String query) {
        filteredFriendsList.clear();
        
        if (query.isEmpty()) {
            filteredFriendsList.addAll(friendsList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (User friend : friendsList) {
                        if (friend.getDisplayName().toLowerCase().contains(lowerCaseQuery) ||
            (friend.getEmail() != null && friend.getEmail().toLowerCase().contains(lowerCaseQuery))) {
                    filteredFriendsList.add(friend);
                }
            }
        }
        
        friendsAdapter.notifyDataSetChanged();
        updateUI();
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
        
        // Generate chat ID
        String chatId = currentUserId + "_" + friend.getId();
        if (currentUserId.compareTo(friend.getId()) > 0) {
            chatId = friend.getId() + "_" + currentUserId;
        }
        
        // Open chat activity
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("receiverId", friend.getId());
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