package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.pingme.android.adapters.ContactSelectionAdapter;
import com.pingme.android.databinding.ActivitySelectContactsBinding;
import com.pingme.android.models.Broadcast;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectContactsActivity extends AppCompatActivity {
    private static final String TAG = "SelectContactsActivity";

    private ActivitySelectContactsBinding binding;
    private ContactSelectionAdapter adapter;
    private List<User> contacts = new ArrayList<>();
    private List<User> selectedContacts = new ArrayList<>();
    private String currentUserId;
    
    // For forwarding
    private boolean isForForward = false;
    private String messageId;
    private String messageText;
    private String messageType;
    
    // For broadcast creation
    private boolean isForBroadcast = false;
    private String broadcastName;

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
        Intent intent = getIntent();
        isForForward = intent.getBooleanExtra("isForForward", false);
        isForBroadcast = intent.getBooleanExtra("isForBroadcast", false);
        messageId = intent.getStringExtra("messageId");
        messageText = intent.getStringExtra("messageText");
        messageType = intent.getStringExtra("messageType");
        broadcastName = intent.getStringExtra("broadcastName");

        setupUI();
        setupRecyclerView();
        loadContacts();
    }

    private void setupUI() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (isForForward) {
                getSupportActionBar().setTitle("Forward to...");
            } else if (isForBroadcast) {
                getSupportActionBar().setTitle("Select Recipients");
            } else {
                getSupportActionBar().setTitle("Select Contacts");
            }
        }

        binding.fabDone.setOnClickListener(v -> {
            if (selectedContacts.isEmpty()) {
                Toast.makeText(this, "Please select at least one contact", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isForForward) {
                forwardMessage();
            } else if (isForBroadcast) {
                createBroadcast();
            } else {
                // Return selected contacts
                Intent resultIntent = new Intent();
                resultIntent.putExtra("selectedContactIds", getUserIds(selectedContacts));
                if (isForBroadcast && broadcastName != null) {
                    resultIntent.putExtra("broadcastName", broadcastName);
                }
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ContactSelectionAdapter(contacts, this::onContactSelectionChanged);
        binding.recyclerViewContacts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewContacts.setAdapter(adapter);
    }

    private void loadContacts() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerViewContacts.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.GONE);

        FirebaseUtil.getFriendsRef(currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    contacts.clear();
                    
                    if (querySnapshot.isEmpty()) {
                        showEmptyState();
                        return;
                    }

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String userId = doc.getId();
                        
                        // Load user details
                        FirebaseUtil.getUserRef(userId)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc.exists()) {
                                        User user = userDoc.toObject(User.class);
                                        if (user != null) {
                                            user.setId(userDoc.getId());
                                            contacts.add(user);
                                            adapter.notifyDataSetChanged();
                                            
                                            if (contacts.size() == 1) {
                                                binding.progressBar.setVisibility(View.GONE);
                                                binding.recyclerViewContacts.setVisibility(View.VISIBLE);
                                            }
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    showEmptyState();
                                });
                    }
                    
                    if (querySnapshot.size() == 0) {
                        binding.progressBar.setVisibility(View.GONE);
                        showEmptyState();
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showEmptyState();
                    Toast.makeText(this, "Failed to load contacts", Toast.LENGTH_SHORT).show();
                });
    }

    private void showEmptyState() {
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerViewContacts.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
    }

    private void onContactSelectionChanged(User contact, boolean isSelected) {
        if (isSelected) {
            if (!selectedContacts.contains(contact)) {
                selectedContacts.add(contact);
            }
        } else {
            selectedContacts.remove(contact);
        }

        // Update FAB visibility
        binding.fabDone.setVisibility(selectedContacts.isEmpty() ? View.GONE : View.VISIBLE);
        
        // Update toolbar subtitle
        if (getSupportActionBar() != null) {
            if (selectedContacts.isEmpty()) {
                getSupportActionBar().setSubtitle(null);
            } else {
                getSupportActionBar().setSubtitle(selectedContacts.size() + " selected");
            }
        }
    }

    private void forwardMessage() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.fabDone.setEnabled(false);

        int totalContacts = selectedContacts.size();
        int[] forwardedCount = {0};

        for (User contact : selectedContacts) {
            // Create or get chat with this contact
            String chatId = FirebaseUtil.generateChatId(currentUserId, contact.getId());
            
            // Forward the message
            Map<String, Object> mediaData = null;
            if (!messageType.equals("text")) {
                mediaData = new HashMap<>();
                // Add appropriate media data based on message type
                // This would need to be passed from the original message
            }
            
            FirebaseUtil.sendMessageWithDeliveryTracking(chatId, currentUserId, messageText, messageType, mediaData)
                    .addOnCompleteListener(task -> {
                        forwardedCount[0]++;
                        if (forwardedCount[0] == totalContacts) {
                            runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Message forwarded to " + totalContacts + " contact(s)", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    });
        }
    }

    private void createBroadcast() {
        if (broadcastName == null || broadcastName.isEmpty()) {
            Toast.makeText(this, "Broadcast name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.fabDone.setEnabled(false);

        List<String> selectedContactIds = new ArrayList<>();
        for (User contact : selectedContacts) {
            selectedContactIds.add(contact.getId());
        }

        FirebaseUtil.createBroadcastList(broadcastName, currentUserId, selectedContactIds, new FirebaseUtil.BroadcastCallback() {
            @Override
            public void onBroadcastCreated(Broadcast broadcast) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(SelectContactsActivity.this, "Broadcast list created successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.fabDone.setEnabled(true);
                    Toast.makeText(SelectContactsActivity.this, "Failed to create broadcast: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String[] getUserIds(List<User> users) {
        String[] ids = new String[users.size()];
        for (int i = 0; i < users.size(); i++) {
            ids[i] = users.get(i).getId();
        }
        return ids;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}