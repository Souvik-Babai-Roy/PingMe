/*
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
import com.pingme.android.utils.FirebaseUtil;

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
        recyclerView = view.findViewById(R.id.recyclerView);
        FloatingActionButton fabAddStatus = view.findViewById(R.id.fabAddStatus);
        
        fabAddStatus.setOnClickListener(v -> {
            // TODO: Implement add status functionality
            // Intent intent = new Intent(getActivity(), AddStatusActivity.class);
            // startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        statusList = new ArrayList<>();
        statusAdapter = new StatusAdapter(getContext(), statusList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(statusAdapter);
    }

    private void loadStatuses() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("statuses")
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
                            statusList.add(status);
                        }
                    }
                }
                statusAdapter.notifyDataSetChanged();
            });
    }

    @Override
    public void onStatusClick(Status status) {
        // TODO: Implement status viewing functionality
        // Intent intent = new Intent(getActivity(), ViewStatusActivity.class);
        // intent.putExtra("statusId", status.getId());
        // startActivity(intent);
    }
}
*/