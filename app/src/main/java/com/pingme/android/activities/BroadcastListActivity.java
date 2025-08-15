package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.pingme.android.utils.FirebaseUtil;

import java.util.ArrayList;
import java.util.List;

public class BroadcastListActivity extends AppCompatActivity {
    private static final String TAG = "BroadcastListActivity";

    private ActivityBroadcastListBinding binding;
    private BroadcastListAdapter adapter;
    private List<Broadcast> broadcasts = new ArrayList<>();
    private String currentUserId;

    private final ActivityResultLauncher<Intent> selectContactsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String broadcastName = result.getData().getStringExtra("broadcastName");
                    String[] selectedContactIds = result.getData().getStringArrayExtra("selectedContactIds");
                    
                    if (broadcastName != null && selectedContactIds != null) {
                        List<String> contactIdList = new ArrayList<>();
                        for (String id : selectedContactIds) {
                            contactIdList.add(id);
                        }
                        createBroadcast(broadcastName, contactIdList);
                    }
                }
            });

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
        adapter = new BroadcastListAdapter(broadcasts, new BroadcastListAdapter.OnBroadcastClickListener() {
            @Override
            public void onBroadcastClick(Broadcast broadcast) {
                // For now, show a toast. This can be enhanced later with a dedicated broadcast chat
                Toast.makeText(BroadcastListActivity.this, "Broadcast: " + broadcast.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBroadcastLongClick(Broadcast broadcast) {
                // Show options for broadcast (edit, delete, etc.)
                showBroadcastOptions(broadcast);
            }
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

        FirebaseUtil.loadUserBroadcasts(currentUserId, new FirebaseUtil.BroadcastListCallback() {
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
            selectContactsLauncher.launch(intent);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showBroadcastOptions(Broadcast broadcast) {
        String[] options = {"Edit", "Delete"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Broadcast Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Edit
                            // TODO: Implement edit broadcast functionality
                            Toast.makeText(this, "Edit broadcast coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 1: // Delete
                            deleteBroadcast(broadcast);
                            break;
                    }
                })
                .show();
    }

    private void deleteBroadcast(Broadcast broadcast) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Broadcast")
                .setMessage("Are you sure you want to delete '" + broadcast.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Delete broadcast from database
                    FirebaseUtil.getBroadcastRef(broadcast.getId())
                            .child("isActive")
                            .setValue(false)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Broadcast deleted", Toast.LENGTH_SHORT).show();
                                loadBroadcasts(); // Reload the list
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to delete broadcast", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createBroadcast(String name, List<String> selectedContactIds) {
        if (selectedContactIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one contact", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        FirebaseUtil.createBroadcastList(name, currentUserId, selectedContactIds, new FirebaseUtil.BroadcastCallback() {
            @Override
            public void onBroadcastCreated(Broadcast broadcast) {
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}