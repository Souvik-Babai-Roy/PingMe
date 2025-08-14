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
        
        // Load statuses from friends only
        FirestoreUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> friendIds = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        friendIds.add(doc.getId());
                    }
                    
                    if (friendIds.isEmpty()) {
                        showLoading(false);
                        updateUI();
                        return;
                    }
                    
                    // Load statuses from these friends
                    loadStatusesFromFriends(friendIds);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to load friends", e);
                    Toast.makeText(getContext(), "Failed to load statuses", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadStatusesFromFriends(List<String> friendIds) {
        Log.d(TAG, "Loading statuses from " + friendIds.size() + " friends");
        
        FirestoreUtil.getStatusCollectionRef()
                .whereIn("userId", friendIds)
                .whereGreaterThan("expiryTime", System.currentTimeMillis()) // Only non-expired
                .orderBy("expiryTime")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    statusList.clear();
                    Log.d(TAG, "Found " + querySnapshot.size() + " statuses");
                    
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Status status = doc.toObject(Status.class);
                        if (status != null) {
                            status.setId(doc.getId());
                            
                            // Set viewed status
                            if (status.getViewers() != null && status.getViewers().containsKey(currentUserId)) {
                                status.setViewed(true);
                            }
                            
                            statusList.add(status);
                        }
                    }
                    
                    showLoading(false);
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to load statuses", e);
                    Toast.makeText(getContext(), "Failed to load statuses", Toast.LENGTH_SHORT).show();
                });
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
                    // Set default state on error
                    binding.textMyStatusTime.setVisibility(View.GONE);
                    binding.textMyStatusTitle.setText("My Status");
                    binding.textMyStatusSubtitle.setText("Tap to add status update");
                });
    }

    private boolean hasMyStatus() {
        // This will be determined by the async call above
        // For now, return false to default to creation
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
        // TODO: Implement status viewer activity
        // For now, just show a toast
        if (position < statuses.size()) {
            Status status = statuses.get(position);
            String message = "Viewing status: " + (status.getContent() != null ? status.getContent() : "Media");
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
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