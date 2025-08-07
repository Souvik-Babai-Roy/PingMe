package com.pingme.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pingme.android.adapters.StatusAdapter;
import com.pingme.android.databinding.FragmentStatusBinding;
import com.pingme.android.models.Status;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import android.util.Log;
import com.pingme.android.R;

public class StatusFragment extends Fragment {

    private FragmentStatusBinding binding;
    private StatusAdapter statusAdapter;
    private List<Status> statusList;
    private String currentUserId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        setupRecyclerView();
        setupClickListeners();
        loadStatuses();
        loadMyStatus();
    }

    private void setupRecyclerView() {
        statusList = new ArrayList<>();
        statusAdapter = new StatusAdapter(statusList, this::onStatusClick);
        
        binding.recyclerViewStatus.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewStatus.setAdapter(statusAdapter);
    }

    private void setupClickListeners() {
        binding.layoutMyStatus.setOnClickListener(v -> {
            // TODO: Open status creation activity
            Toast.makeText(getContext(), "Create new status", Toast.LENGTH_SHORT).show();
        });
        
        binding.fabAddStatus.setOnClickListener(v -> {
            // TODO: Open status creation activity
            Toast.makeText(getContext(), "Add status", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadMyStatus() {
        // Load current user's latest status
        FirestoreUtil.getStatusCollectionRef()
                .whereEqualTo("userId", currentUserId)
                .whereGreaterThan("expiryTime", System.currentTimeMillis())
                .orderBy("expiryTime", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Status myStatus = queryDocumentSnapshots.getDocuments().get(0).toObject(Status.class);
                        if (myStatus != null) {
                            binding.tvMyStatusText.setText("Tap to view status");
                            binding.tvMyStatusTime.setText(getTimeAgo(myStatus.getTimestamp()));
                            binding.ivMyStatusIndicator.setVisibility(View.VISIBLE);
                        }
                    } else {
                        binding.tvMyStatusText.setText("Tap to add status update");
                        binding.tvMyStatusTime.setText("");
                        binding.ivMyStatusIndicator.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.tvMyStatusText.setText("Tap to add status update");
                    binding.tvMyStatusTime.setText("");
                    binding.ivMyStatusIndicator.setVisibility(View.GONE);
                });

        // Load current user info for my status
        FirestoreUtil.getUserRef(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            binding.tvMyStatusName.setText(user.getName());
                            
                            // FIXED: Load profile image using Glide
                            if (user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
                                Glide.with(this)
                                        .load(user.getImageUrl())
                                        .transform(new CircleCrop())
                                        .placeholder(R.drawable.defaultprofile)
                                        .error(R.drawable.defaultprofile)
                                        .into(binding.ivMyStatusProfile);
                            } else {
                                binding.ivMyStatusProfile.setImageResource(R.drawable.defaultprofile);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("StatusFragment", "Failed to load user info", e);
                    binding.ivMyStatusProfile.setImageResource(R.drawable.defaultprofile);
                });
    }

    private void loadStatuses() {
        // Load recent statuses from other users (not expired)
        FirestoreUtil.getStatusCollectionRef()
                .whereGreaterThan("expiryTime", System.currentTimeMillis())
                .orderBy("expiryTime", Query.Direction.DESCENDING)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e("StatusFragment", "Error loading statuses", e);
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        statusList.clear();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Status status = doc.toObject(Status.class);
                            if (status != null && !status.getUserId().equals(currentUserId)) {
                                status.setId(doc.getId());
                                statusList.add(status);
                            }
                        }
                        statusAdapter.notifyDataSetChanged();
                        
                        // Show/hide empty state
                        if (statusList.isEmpty()) {
                            binding.layoutEmptyState.setVisibility(View.VISIBLE);
                            binding.recyclerViewStatus.setVisibility(View.GONE);
                        } else {
                            binding.layoutEmptyState.setVisibility(View.GONE);
                            binding.recyclerViewStatus.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void onStatusClick(Status status) {
        // TODO: Open status viewer activity
        Toast.makeText(getContext(), "View status from " + status.getUserId(), Toast.LENGTH_SHORT).show();
    }

    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 60000) { // Less than 1 minute
            return "Just now";
        } else if (diff < 3600000) { // Less than 1 hour
            int minutes = (int) (diff / 60000);
            return minutes + " minutes ago";
        } else if (diff < 86400000) { // Less than 1 day
            int hours = (int) (diff / 3600000);
            return hours + " hours ago";
        } else {
            int days = (int) (diff / 86400000);
            return days + " days ago";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}