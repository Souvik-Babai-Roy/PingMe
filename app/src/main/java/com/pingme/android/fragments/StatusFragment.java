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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pingme.android.R;
import com.pingme.android.activities.StatusCreationActivity;
import com.pingme.android.adapters.StatusAdapter;
import com.pingme.android.models.Status;
import com.pingme.android.utils.FirebaseUtil;
import com.pingme.android.activities.StatusViewerActivity;

import java.util.ArrayList;
import java.util.List;

public class StatusFragment extends Fragment implements StatusAdapter.OnStatusClickListener {
    private RecyclerView recyclerView;
    private StatusAdapter statusAdapter;
    private List<Status> statusList;
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
        FloatingActionButton fabAddStatus = view.findViewById(R.id.fabAddStatus);
        
        if (fabAddStatus != null) {
            fabAddStatus.setOnClickListener(v -> {
                // Open status creation activity
                Intent intent = new Intent(getActivity(), StatusCreationActivity.class);
                startActivity(intent);
            });
        }
    }

    private void setupRecyclerView() {
        statusList = new ArrayList<>();
        statusAdapter = new StatusAdapter(getContext(), statusList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(statusAdapter);
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

                statusList.clear();
                if (value != null) {
                    for (DocumentSnapshot document : value.getDocuments()) {
                        Status status = document.toObject(Status.class);
                        if (status != null) {
                            status.setId(document.getId());
                            // Only show statuses from friends or current user
                            checkIfFriendOrSelf(status.getUserId(), isFriendOrSelf -> {
                                if (isFriendOrSelf) {
                                    statusList.add(status);
                                    statusAdapter.notifyDataSetChanged();
                                }
                            });
                        }
                    }
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

    @Override
    public void onStatusClick(Status status) {
        // Open status viewer activity for story-like viewing experience
        Intent intent = StatusViewerActivity.createIntent(
                getActivity(), 
                status.getId(), 
                status.getUserId(), 
                0
        );
        startActivity(intent);
    }
}