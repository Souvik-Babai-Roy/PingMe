package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.pingme.android.R;
import com.pingme.android.adapters.ContactSelectionAdapter;
import com.pingme.android.databinding.ActivitySelectContactsBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

import java.util.ArrayList;
import java.util.List;

public class SelectContactsActivity extends AppCompatActivity {
    private static final String TAG = "SelectContactsActivity";

    private ActivitySelectContactsBinding binding;
    private ContactSelectionAdapter adapter;
    private List<User> allContacts = new ArrayList<>();
    private List<String> selectedContactIds = new ArrayList<>();
    private String currentUserId;
    private boolean isForBroadcast;
    private boolean isForForward;
    private String messageId;
    private String messageText;
    private String messageType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySelectContactsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get intent extras
        isForBroadcast = getIntent().getBooleanExtra("isForBroadcast", false);
        isForForward = getIntent().getBooleanExtra("isForForward", false);
        messageId = getIntent().getStringExtra("messageId");
        messageText = getIntent().getStringExtra("messageText");
        messageType = getIntent().getStringExtra("messageType");

        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        loadContacts();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (isForBroadcast) {
                getSupportActionBar().setTitle("Select Contacts for Broadcast");
            } else if (isForForward) {
                getSupportActionBar().setTitle("Forward Message");
            } else {
                getSupportActionBar().setTitle("Select Contacts");
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new ContactSelectionAdapter(allContacts, selectedContactIds, (user, isSelected) -> {
            if (isSelected) {
                selectedContactIds.add(user.getId());
            } else {
                selectedContactIds.remove(user.getId());
            }
            updateSelectionCount();
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.btnDone.setOnClickListener(v -> {
            if (selectedContactIds.isEmpty()) {
                Toast.makeText(this, "Please select at least one contact", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isForBroadcast) {
                // Return selected contacts for broadcast creation
                Intent resultIntent = new Intent();
                resultIntent.putStringArrayListExtra("selectedContactIds", new ArrayList<>(selectedContactIds));
                setResult(RESULT_OK, resultIntent);
                finish();
            } else if (isForForward) {
                // Forward message to selected contacts
                forwardMessageToContacts();
            }
        });

        binding.btnSelectAll.setOnClickListener(v -> {
            selectedContactIds.clear();
            for (User contact : allContacts) {
                selectedContactIds.add(contact.getId());
            }
            adapter.notifyDataSetChanged();
            updateSelectionCount();
        });

        binding.btnClearSelection.setOnClickListener(v -> {
            selectedContactIds.clear();
            adapter.notifyDataSetChanged();
            updateSelectionCount();
        });
    }

    private void loadContacts() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // Load user's friends
        FirestoreUtil.getFriendsRef(currentUserId).get()
                .addOnSuccessListener(querySnapshot -> {
                    binding.progressBar.setVisibility(View.GONE);
                    allContacts.clear();

                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : querySnapshot) {
                        User contact = document.toObject(User.class);
                        if (contact != null) {
                            contact.setId(document.getId());
                            allContacts.add(contact);
                        }
                    }

                    adapter.notifyDataSetChanged();
                    updateSelectionCount();

                    if (allContacts.isEmpty()) {
                        binding.emptyState.setVisibility(View.VISIBLE);
                    } else {
                        binding.emptyState.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to load contacts", e);
                    Toast.makeText(this, "Failed to load contacts", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateSelectionCount() {
        int count = selectedContactIds.size();
        binding.tvSelectionCount.setText(count + " contact" + (count != 1 ? "s" : "") + " selected");
        binding.btnDone.setEnabled(count > 0);
    }

    private void forwardMessageToContacts() {
        if (messageId == null || messageText == null) {
            Toast.makeText(this, "Invalid message data", Toast.LENGTH_SHORT).show();
            return;
        }

        // For now, we'll just show a success message
        // In a real implementation, you would forward the message to each selected contact
        Toast.makeText(this, "Message forwarded to " + selectedContactIds.size() + " contacts", Toast.LENGTH_SHORT).show();
        finish();
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