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
import com.pingme.android.adapters.StatusAdapter;
import com.pingme.android.models.Status;
import com.pingme.android.models.User;

import java.util.ArrayList;
import java.util.List;

public class StatusFragment extends Fragment {
    private RecyclerView recyclerView;
    private StatusAdapter adapter;
    private List<Status> statusList;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerViewStatus);
        FloatingActionButton fabAddStatus = view.findViewById(R.id.fabAddStatus);
        
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        statusList = new ArrayList<>();
        adapter = new StatusAdapter(getContext(), statusList, new StatusAdapter.OnStatusClickListener() {
            @Override
            public void onStatusClick(Status status) {
                // Handle status click - open status viewer
                // TODO: Implement status viewer activity
            }
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        fabAddStatus.setOnClickListener(v -> {
            // TODO: Implement add status functionality
            // Intent intent = new Intent(getContext(), AddStatusActivity.class);
            // startActivity(intent);
        });
        
        loadStatuses();
        
        return view;
    }
    
    private void loadStatuses() {
        if (currentUser == null) return;
        
        db.collection("statuses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener((value, error) -> {
                if (error != null) {
                    return;
                }
                
                statusList.clear();
                if (value != null) {
                    for (DocumentSnapshot document : value.getDocuments()) {
                        Status status = document.toObject(Status.class);
                        if (status != null) {
                            statusList.add(status);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            });
    }
}