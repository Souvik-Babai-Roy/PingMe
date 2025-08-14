package com.pingme.android.fragments;

import android.content.Intent;
import android.os.Bundle;
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
import com.pingme.android.R;
import com.pingme.android.activities.StatusCreationActivity;
import com.pingme.android.adapters.StatusAdapter;
import com.pingme.android.databinding.FragmentStatusBinding;
import com.pingme.android.models.Status;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.firebase.firestore.DocumentSnapshot;

public class StatusFragment extends Fragment implements StatusAdapter.OnStatusClickListener {
    private static final String TAG = "StatusFragment";
    
    private FragmentStatusBinding binding;
    private StatusAdapter statusAdapter;
    private String currentUserId;
    private User currentUser;
    private List<Status> statusList = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);
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
        setupClickListeners();
        loadCurrentUser();
        loadStatuses();
    }

    private void setupRecyclerView() {
        statusAdapter = new StatusAdapter(getContext(), statusList, currentUserId, this);
        binding.recyclerViewStatus.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewStatus.setAdapter(statusAdapter);
    }

    private void setupClickListeners() {
        binding.fabAddStatus.setOnClickListener(v -> openStatusCreation());
        binding.layoutMyStatus.setOnClickListener(v -> {
            if (hasMyStatus()) {
                // View my status
                viewMyStatus();
            } else {
                // Create new status
                openStatusCreation();
            }
        });
    }

    private void loadCurrentUser() {
        FirestoreUtil.getUserRef(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            currentUser.setId(currentUserId);
                            updateMyStatusUI();
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load current user", e));
    }

    private void loadStatuses() {
        showLoading(true);
        
        // Check if user is authenticated
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "User not authenticated");
            showLoading(false);
            updateUI();
            return;
        }

        Log.d(TAG, "Loading statuses for user: " + currentUserId);
        
        // First load friends to get their IDs
        FirestoreUtil.getFriendsRef(currentUserId).get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " friends");
                    
                    List<String> friendIds = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        String friendId = document.getId();
                        friendIds.add(friendId);
                    }
                    
                    // Add current user to the list to load their own status
                    friendIds.add(currentUserId);
                    
                    // Load statuses from all users (friends + current user)
                    loadStatusesFromUsers(friendIds);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load friends", e);
                    showLoading(false);
                    updateUI();
                });
    }

    private void loadStatusesFromUsers(List<String> userIds) {
        Log.d(TAG, "Loading statuses from " + userIds.size() + " users");
        
        if (userIds.isEmpty()) {
            showLoading(false);
            updateUI();
            return;
        }

        // Clear existing statuses
        statusList.clear();
        
        // Load statuses from the global statuses collection for all friend users and current user
        FirestoreUtil.getStatusCollectionRef()
                .whereIn("userId", userIds)
                .whereGreaterThan("expiryTime", System.currentTimeMillis())
                .orderBy("expiryTime")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Loaded " + querySnapshot.size() + " total statuses");
                    
                    // Keep track of how many users we need to check
                    int totalStatusesToProcess = querySnapshot.size();
                    int[] processedCount = {0};
                    
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        Status status = document.toObject(Status.class);
                        if (status != null) {
                            status.setId(document.getId());
                            
                            // Only add status if it's not expired (24 hours)
                            long statusAge = System.currentTimeMillis() - status.getTimestamp();
                            if (statusAge < 24 * 60 * 60 * 1000) { // 24 hours in milliseconds
                                
                                // Check user privacy settings before adding status
                                String statusUserId = status.getUserId();
                                if (statusUserId.equals(currentUserId)) {
                                    // Always show current user's status
                                    Log.d(TAG, "Adding current user status: " + status.getContent());
                                    statusList.add(status);
                                    processedCount[0]++;
                                    
                                    if (processedCount[0] == totalStatusesToProcess) {
                                        finishStatusLoading();
                                    }
                                } else {
                                    // Check if friend allows status visibility (using about visibility as proxy for status visibility)
                                    FirestoreUtil.getUserRef(statusUserId).get()
                                            .addOnSuccessListener(userDoc -> {
                                                processedCount[0]++;
                                                
                                                if (userDoc.exists()) {
                                                    User user = userDoc.toObject(User.class);
                                                    if (user != null && user.isAboutEnabled()) {
                                                        // User allows status visibility, add to list
                                                        Log.d(TAG, "Adding friend status: " + status.getContent() + " from " + status.getUserName());
                                                        statusList.add(status);
                                                    } else {
                                                        Log.d(TAG, "Friend " + status.getUserName() + " has disabled status visibility");
                                                    }
                                                    // If user doesn't allow status visibility, skip this status
                                                }
                                                
                                                if (processedCount[0] == totalStatusesToProcess) {
                                                    finishStatusLoading();
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                processedCount[0]++;
                                                Log.e(TAG, "Failed to load user privacy settings for " + statusUserId, e);
                                                
                                                if (processedCount[0] == totalStatusesToProcess) {
                                                    finishStatusLoading();
                                                }
                                            });
                                }
                            } else {
                                Log.d(TAG, "Status expired: " + status.getContent() + " (age: " + statusAge + "ms)");
                                processedCount[0]++;
                                if (processedCount[0] == totalStatusesToProcess) {
                                    finishStatusLoading();
                                }
                            }
                        } else {
                            processedCount[0]++;
                            if (processedCount[0] == totalStatusesToProcess) {
                                finishStatusLoading();
                            }
                        }
                    }
                    
                    // Handle case where no statuses to process
                    if (totalStatusesToProcess == 0) {
                        Log.d(TAG, "No statuses found in collection");
                        finishStatusLoading();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load statuses", e);
                    showLoading(false);
                    updateUI();
                });
    }
    
    private void finishStatusLoading() {
        // Sort statuses: current user first, then others by timestamp
        Collections.sort(statusList, (s1, s2) -> {
            // Current user's statuses first
            if (s1.getUserId().equals(currentUserId) && !s2.getUserId().equals(currentUserId)) {
                return -1;
            }
            if (!s1.getUserId().equals(currentUserId) && s2.getUserId().equals(currentUserId)) {
                return 1;
            }
            // Then by timestamp (newest first)
            return Long.compare(s2.getTimestamp(), s1.getTimestamp());
        });
        
        // Update UI
        showLoading(false);
        updateUI();
        updateMyStatusUI();
        
        Log.d(TAG, "Total visible statuses loaded: " + statusList.size());
    }

    private void updateUI() {
        if (statusList.isEmpty()) {
            binding.textNoStatus.setVisibility(View.VISIBLE);
            binding.recyclerViewStatus.setVisibility(View.GONE);
        } else {
            binding.textNoStatus.setVisibility(View.GONE);
            binding.recyclerViewStatus.setVisibility(View.VISIBLE);
            if (statusAdapter != null) {
                statusAdapter.notifyDataSetChanged();
            }
        }
        
        updateMyStatusUI();
    }

    private void updateMyStatusUI() {
        if (currentUser == null) return;
        
        // Check if current user has any active status
        FirestoreUtil.getStatusCollectionRef()
                .whereEqualTo("userId", currentUserId)
                .whereGreaterThan("expiryTime", System.currentTimeMillis())
                .orderBy("expiryTime")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // User has active status
                        Status myStatus = querySnapshot.getDocuments().get(0).toObject(Status.class);
                        if (myStatus != null) {
                            binding.textMyStatusTime.setVisibility(View.VISIBLE);
                            binding.textMyStatusTime.setText(myStatus.getFormattedTimeAgo());
                            binding.textMyStatusTitle.setText("My Status");
                            binding.textMyStatusSubtitle.setText("Tap to view");
                            
                            // Update status count
                            long activeStatusCount = querySnapshot.size();
                            if (activeStatusCount > 1) {
                                binding.textMyStatusSubtitle.setText(activeStatusCount + " statuses");
                            }
                        }
                    } else {
                        // No active status
                        binding.textMyStatusTime.setVisibility(View.GONE);
                        binding.textMyStatusTitle.setText("My Status");
                        binding.textMyStatusSubtitle.setText("Tap to add status update");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check my status", e);
                    // Set default state
                    binding.textMyStatusTime.setVisibility(View.GONE);
                    binding.textMyStatusTitle.setText("My Status");
                    binding.textMyStatusSubtitle.setText("Tap to add status update");
                });
    }

    private boolean hasMyStatus() {
        // Check if current user has any active status in the status list
        for (Status status : statusList) {
            if (status.getUserId().equals(currentUserId) && 
                status.getExpiryTime() > System.currentTimeMillis()) {
                return true;
            }
        }
        return false;
    }

    private void viewMyStatus() {
        // Load and view current user's status
        FirestoreUtil.getStatusCollectionRef()
                .whereEqualTo("userId", currentUserId)
                .whereGreaterThan("expiryTime", System.currentTimeMillis())
                .orderBy("expiryTime")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        List<Status> myStatuses = new ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Status status = doc.toObject(Status.class);
                            if (status != null) {
                                status.setId(doc.getId());
                                myStatuses.add(status);
                            }
                        }
                        
                        if (!myStatuses.isEmpty()) {
                            // Open status viewer for my statuses
                            openStatusViewer(myStatuses, 0);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load my statuses", e));
    }

    private void openStatusCreation() {
        Intent intent = new Intent(getContext(), StatusCreationActivity.class);
        startActivity(intent);
    }

    private void openStatusViewer(List<Status> statuses, int position) {
        if (position < statuses.size()) {
            Status status = statuses.get(position);
            
            // Create a simple dialog to view status content
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
            builder.setTitle(status.getUserName() != null ? status.getUserName() : "Status");
            
            // Create content based on status type
            String content;
            if ("text".equals(status.getType()) && status.getContent() != null) {
                content = status.getContent();
            } else if ("image".equals(status.getType())) {
                content = "📷 Image status";
                if (status.getContent() != null && !status.getContent().isEmpty()) {
                    content += "\n\n" + status.getContent();
                }
            } else if ("video".equals(status.getType())) {
                content = "🎥 Video status";
                if (status.getContent() != null && !status.getContent().isEmpty()) {
                    content += "\n\n" + status.getContent();
                }
            } else {
                content = "Media status";
            }
            
            // Add timestamp
            String timeAgo = getTimeAgo(status.getTimestamp());
            content += "\n\n⏰ " + timeAgo;
            
            // Add viewer count for own status
            if (status.getUserId().equals(currentUserId) && status.getViewers() != null) {
                int viewerCount = status.getViewers().size();
                if (viewerCount > 0) {
                    content += "\n👁 " + viewerCount + " view" + (viewerCount > 1 ? "s" : "");
                }
            }
            
            builder.setMessage(content);
            builder.setPositiveButton("Close", null);
            
            // Add navigation buttons if there are multiple statuses
            if (statuses.size() > 1) {
                if (position > 0) {
                    builder.setNegativeButton("Previous", (dialog, which) -> {
                        openStatusViewer(statuses, position - 1);
                    });
                }
                if (position < statuses.size() - 1) {
                    builder.setNeutralButton("Next", (dialog, which) -> {
                        openStatusViewer(statuses, position + 1);
                    });
                }
            }
            
            builder.show();
        }
    }
    
    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 60000) { // Less than 1 minute
            return "just now";
        } else if (diff < 3600000) { // Less than 1 hour
            long minutes = diff / 60000;
            return minutes + "m ago";
        } else if (diff < 86400000) { // Less than 1 day
            long hours = diff / 3600000;
            return hours + "h ago";
        } else {
            long days = diff / 86400000;
            return days + "d ago";
        }
    }

    private void showLoading(boolean show) {
        if (binding != null) {
            binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onStatusClick(Status status, int position) {
        // Mark as viewed if not already viewed
        if (!status.hasViewedBy(currentUserId)) {
            markStatusAsViewed(status);
        }
        
        // Get all statuses from this user
        String statusUserId = status.getUserId();
        List<Status> userStatuses = new ArrayList<>();
        int startPosition = 0;
        
        for (int i = 0; i < statusList.size(); i++) {
            if (statusList.get(i).getUserId().equals(statusUserId)) {
                userStatuses.add(statusList.get(i));
                if (statusList.get(i).getId().equals(status.getId())) {
                    startPosition = userStatuses.size() - 1;
                }
            }
        }
        
        // Open status viewer
        openStatusViewer(userStatuses, startPosition);
    }

    private void markStatusAsViewed(Status status) {
        if (status.getId() == null) return;
        
        status.addViewer(currentUserId);
        
        // Update in Firestore
        FirestoreUtil.getStatusCollectionRef()
                .document(status.getId())
                .update("viewers." + currentUserId, System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Status marked as viewed");
                    // Update local status
                    status.setViewed(true);
                    statusAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to mark status as viewed", e));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh statuses when fragment becomes visible
        if (currentUserId != null) {
            loadStatuses();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}