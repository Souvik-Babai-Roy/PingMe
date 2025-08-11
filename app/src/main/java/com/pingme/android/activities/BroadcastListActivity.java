package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.pingme.android.R;
import com.pingme.android.adapters.BroadcastListAdapter;
import com.pingme.android.databinding.ActivityBroadcastListBinding;
import com.pingme.android.models.Broadcast;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

import java.util.ArrayList;
import java.util.List;

public class BroadcastListActivity extends AppCompatActivity {
    private static final String TAG = "BroadcastListActivity";

    private ActivityBroadcastListBinding binding;
    private BroadcastListAdapter adapter;
    private List<Broadcast> broadcasts = new ArrayList<>();
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBroadcastListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        loadBroadcasts();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Broadcast Lists");
        }
    }

    private void setupRecyclerView() {
        adapter = new BroadcastListAdapter(broadcasts, broadcast -> {
            // Handle broadcast selection - open broadcast chat
            Intent intent = new Intent(this, BroadcastChatActivity.class);
            intent.putExtra("broadcastId", broadcast.getId());
            intent.putExtra("broadcastName", broadcast.getName());
            startActivity(intent);
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.fabNewBroadcast.setOnClickListener(v -> showCreateBroadcastDialog());
    }

    private void loadBroadcasts() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);

        FirestoreUtil.loadUserBroadcasts(currentUserId, new FirestoreUtil.BroadcastListCallback() {
            @Override
            public void onBroadcastsLoaded(List<Broadcast> broadcastList) {
                binding.progressBar.setVisibility(View.GONE);
                broadcasts.clear();
                broadcasts.addAll(broadcastList);
                adapter.notifyDataSetChanged();

                if (broadcasts.isEmpty()) {
                    binding.emptyState.setVisibility(View.VISIBLE);
                } else {
                    binding.emptyState.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(BroadcastListActivity.this, "Failed to load broadcasts: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateBroadcastDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_broadcast, null);
        TextInputEditText etBroadcastName = dialogView.findViewById(R.id.etBroadcastName);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnCreate = dialogView.findViewById(R.id.btnCreate);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            String broadcastName = etBroadcastName.getText().toString().trim();
            if (broadcastName.isEmpty()) {
                Toast.makeText(this, "Please enter a broadcast name", Toast.LENGTH_SHORT).show();
                return;
            }

            // Open contact selection activity
            Intent intent = new Intent(this, SelectContactsActivity.class);
            intent.putExtra("broadcastName", broadcastName);
            intent.putExtra("isForBroadcast", true);
            startActivityForResult(intent, REQUEST_SELECT_CONTACTS);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createBroadcast(String name, List<String> selectedContactIds) {
        if (selectedContactIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one contact", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        FirestoreUtil.createBroadcastList(name, currentUserId, selectedContactIds, new FirestoreUtil.BroadcastCallback() {
            @Override
            public void onSuccess(String broadcastId) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(BroadcastListActivity.this, "Broadcast list created successfully", Toast.LENGTH_SHORT).show();
                loadBroadcasts(); // Reload the list
            }

            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(BroadcastListActivity.this, "Failed to create broadcast: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_CONTACTS && resultCode == RESULT_OK && data != null) {
            String broadcastName = data.getStringExtra("broadcastName");
            ArrayList<String> selectedContactIds = data.getStringArrayListExtra("selectedContactIds");
            
            if (broadcastName != null && selectedContactIds != null) {
                createBroadcast(broadcastName, selectedContactIds);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static final int REQUEST_SELECT_CONTACTS = 1001;
}