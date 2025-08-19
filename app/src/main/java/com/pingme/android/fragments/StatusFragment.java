package com.pingme.android.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.activities.StatusCreationActivity;
import com.pingme.android.adapters.StatusAdapter;
import com.pingme.android.adapters.StatusGroupAdapter;
import com.pingme.android.models.Status;
import com.pingme.android.models.StatusGroup;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;
import com.pingme.android.activities.StatusViewerActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatusFragment extends Fragment implements StatusGroupAdapter.OnStatusGroupClickListener {
    private RecyclerView recyclerView;
    private StatusGroupAdapter statusGroupAdapter;
    private List<StatusGroup> statusGroupList;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);
        
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return view;
        }

        initViews(view);
        setupRecyclerView();
        loadStatuses();
        
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewStatus);
        
        // Setup "My Status" section click listener
        View layoutMyStatus = view.findViewById(R.id.layoutMyStatus);
        if (layoutMyStatus != null) {
            layoutMyStatus.setOnClickListener(v -> {
                // Open status creation activity
                Intent intent = new Intent(getActivity(), StatusCreationActivity.class);
                startActivity(intent);
            });
        }
        
        // Load current user's profile photo in "My Status" section
        loadCurrentUserProfilePhoto(view);
    }

    private void setupRecyclerView() {
        statusGroupList = new ArrayList<>();
        statusGroupAdapter = new StatusGroupAdapter(getContext(), statusGroupList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(statusGroupAdapter);
    }

    private void loadStatuses() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Load statuses from friends and current user only
        db.collection("statuses")
            .whereGreaterThan("timestamp", System.currentTimeMillis() - (24 * 60 * 60 * 1000)) // Only last 24 hours
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener((value, error) -> {
                if (error != null) {
                    // Handle error
                    return;
                }

                if (value != null) {
                    List<Status> tempStatusList = new ArrayList<>();
                    List<DocumentSnapshot> documents = value.getDocuments();
                    
                    if (documents.isEmpty()) {
                        // No statuses found, clear the list
                        statusGroupList.clear();
                        statusGroupAdapter.notifyDataSetChanged();
                        return;
                    }

                    // Use atomic counter to track processed documents
                    final int[] processedCount = {0};
                    final int totalCount = documents.size();

                    for (DocumentSnapshot document : documents) {
                        Status status = document.toObject(Status.class);
                        if (status != null) {
                            status.setId(document.getId());
                            // Only show statuses from friends or current user
                            checkIfFriendOrSelf(status.getUserId(), isFriendOrSelf -> {
                                synchronized (processedCount) {
                                    if (isFriendOrSelf) {
                                        // Check if current user has viewed this status
                                        updateStatusViewedState(status);
                                        tempStatusList.add(status);
                                    }
                                    
                                    processedCount[0]++;
                                    
                                    // When all documents are processed, group and display
                                    if (processedCount[0] == totalCount) {
                                        groupStatusesAndUpdate(tempStatusList);
                                    }
                                }
                            });
                        } else {
                            synchronized (processedCount) {
                                processedCount[0]++;
                                if (processedCount[0] == totalCount) {
                                    groupStatusesAndUpdate(tempStatusList);
                                }
                            }
                        }
                    }
                } else {
                    statusGroupList.clear();
                    statusGroupAdapter.notifyDataSetChanged();
                }
            });
    }

    private void checkIfFriendOrSelf(String userId, FirebaseUtil.FriendshipStatusCallback callback) {
        if (currentUser == null) {
            callback.onResult(false);
            return;
        }
        
        String currentUserId = currentUser.getUid();
        if (userId.equals(currentUserId)) {
            callback.onResult(true);
            return;
        }
        
        FirebaseUtil.checkFriendship(currentUserId, userId, callback);
    }

    private void updateStatusViewedState(Status status) {
        if (currentUser != null && status.getViewers() != null) {
            String currentUserId = currentUser.getUid();
            status.setViewed(status.getViewers().containsKey(currentUserId));
        }
    }

    private void groupStatusesAndUpdate(List<Status> statusList) {
        // Group statuses by userId
        Map<String, StatusGroup> userStatusGroups = new HashMap<>();
        
        for (Status status : statusList) {
            String userId = status.getUserId();
            
            StatusGroup group = userStatusGroups.get(userId);
            if (group == null) {
                group = new StatusGroup(userId, status.getUserName(), status.getUserImageUrl());
                userStatusGroups.put(userId, group);
            }
            
            group.addStatus(status);
        }
        
        // Convert to list and sort by latest timestamp
        statusGroupList.clear();
        statusGroupList.addAll(userStatusGroups.values());
        
        // Sort by latest timestamp (most recent first)
        statusGroupList.sort((g1, g2) -> Long.compare(g2.getLatestTimestamp(), g1.getLatestTimestamp()));
        
        statusGroupAdapter.notifyDataSetChanged();
    }
    
    private void loadCurrentUserProfilePhoto(View view) {
        if (currentUser == null) return;
        
        android.widget.ImageView ivMyStatus = view.findViewById(R.id.ivMyStatus);
        android.widget.TextView tvMyStatusTitle = view.findViewById(R.id.textMyStatusTitle);
        android.widget.TextView tvMyStatusSubtitle = view.findViewById(R.id.textMyStatusSubtitle);
        
        if (ivMyStatus == null) return;
        
        // Set "My Status" title
        if (tvMyStatusTitle != null) {
            tvMyStatusTitle.setText("My Status");
        }
        
        // Set subtitle based on recent status
        if (tvMyStatusSubtitle != null) {
            checkRecentStatus(tvMyStatusSubtitle);
        }
        
        // Load from Firestore user data
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null && user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
                        try {
                            Glide.with(this)
                                .load(user.getImageUrl())
                                .placeholder(R.drawable.defaultprofile)
                                .error(R.drawable.defaultprofile)
                                .circleCrop()
                                .into(ivMyStatus);
                        } catch (Exception e) {
                            ivMyStatus.setImageResource(R.drawable.defaultprofile);
                        }
                    } else {
                        ivMyStatus.setImageResource(R.drawable.defaultprofile);
                    }
                } else {
                    // Fallback to Firebase Auth photo URL if Firestore user doesn't exist
                    if (currentUser.getPhotoUrl() != null) {
                        try {
                            Glide.with(this)
                                .load(currentUser.getPhotoUrl())
                                .placeholder(R.drawable.defaultprofile)
                                .error(R.drawable.defaultprofile)
                                .circleCrop()
                                .into(ivMyStatus);
                        } catch (Exception e) {
                            ivMyStatus.setImageResource(R.drawable.defaultprofile);
                        }
                    } else {
                        ivMyStatus.setImageResource(R.drawable.defaultprofile);
                    }
                }
            })
            .addOnFailureListener(e -> {
                // Fallback to Firebase Auth photo URL
                if (currentUser.getPhotoUrl() != null) {
                    try {
                        Glide.with(this)
                            .load(currentUser.getPhotoUrl())
                            .placeholder(R.drawable.defaultprofile)
                            .error(R.drawable.defaultprofile)
                            .circleCrop()
                            .into(ivMyStatus);
                    } catch (Exception ex) {
                        ivMyStatus.setImageResource(R.drawable.defaultprofile);
                    }
                } else {
                    ivMyStatus.setImageResource(R.drawable.defaultprofile);
                }
            });
    }
    
    private void checkRecentStatus(android.widget.TextView tvSubtitle) {
        if (currentUser == null) return;
        
        // Check if user has posted a status in last 24 hours
        long last24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        
        FirebaseFirestore.getInstance()
            .collection("statuses")
            .whereEqualTo("userId", currentUser.getUid())
            .whereGreaterThan("timestamp", last24Hours)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    // User has recent status
                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    Long timestamp = doc.getLong("timestamp");
                    if (timestamp != null) {
                        String timeAgo = getTimeAgo(timestamp);
                        tvSubtitle.setText("Today, " + timeAgo);
                    } else {
                        tvSubtitle.setText("Today");
                    }
                } else {
                    // No recent status
                    tvSubtitle.setText("Tap to add status update");
                }
            })
            .addOnFailureListener(e -> {
                tvSubtitle.setText("Tap to add status update");
            });
    }
    
    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long minutes = diff / (1000 * 60);
        long hours = minutes / 60;
        
        if (minutes < 60) {
            return String.format("%02d:%02d", (int)(minutes / 60), (int)(minutes % 60));
        } else {
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            return timeFormat.format(new java.util.Date(timestamp));
        }
    }

    @Override
    public void onStatusGroupClick(StatusGroup statusGroup, int position) {
        // Open status viewer activity for story-like viewing experience
        // Start with the first status of the group
        Status firstStatus = statusGroup.getLatestStatus();
        if (firstStatus != null) {
            Intent intent = StatusViewerActivity.createIntent(
                    getActivity(), 
                    firstStatus.getId(), 
                    statusGroup.getUserId(), 
                    0
            );
            startActivity(intent);
        }
    }
}