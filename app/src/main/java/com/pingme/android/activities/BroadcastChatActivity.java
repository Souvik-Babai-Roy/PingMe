package com.pingme.android.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.pingme.android.R;
import com.pingme.android.adapters.MessageAdapter;
import com.pingme.android.databinding.ActivityBroadcastChatBinding;
import com.pingme.android.models.Broadcast;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.utils.CloudinaryUtil;
import com.pingme.android.utils.FirestoreUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BroadcastChatActivity extends AppCompatActivity {
    private static final String TAG = "BroadcastChatActivity";

    private ActivityBroadcastChatBinding binding;
    private String broadcastId;
    private String broadcastName;
    private MessageAdapter adapter;
    private List<Object> items = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private ChildEventListener messageListener;
    private Broadcast broadcast;
    private User currentUser;
    private boolean isTyping = false;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImageSelection);

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleVideoSelection);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBroadcastChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        broadcastId = getIntent().getStringExtra("broadcastId");
        broadcastName = getIntent().getStringExtra("broadcastName");

        if (broadcastId == null || broadcastName == null) {
            Toast.makeText(this, "Invalid broadcast", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupAdapter();
        setupToolbar();
        loadCurrentUser();
        loadBroadcast();
        setupMessageListener();
        setupClickListeners();
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(items, null); // No specific receiver for broadcasts
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(broadcastName);
        }
    }

    private void loadCurrentUser() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId != null) {
            FirestoreUtil.getUserRef(currentUserId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            currentUser.setId(currentUserId);
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to load current user", e));
        }
    }

    private void loadBroadcast() {
        FirestoreUtil.getBroadcastRef(broadcastId).get()
                .addOnSuccessListener(dataSnapshot -> {
                    broadcast = dataSnapshot.getValue(Broadcast.class);
                    if (broadcast != null) {
                        broadcast.setId(broadcastId);
                        updateToolbar();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load broadcast", e));
    }

    private void updateToolbar() {
        if (getSupportActionBar() != null && broadcast != null) {
            getSupportActionBar().setTitle(broadcast.getDisplayName());
            getSupportActionBar().setSubtitle(broadcast.getMemberCountText());
        }
    }

    private void setupMessageListener() {
        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                Message message = dataSnapshot.getValue(Message.class);
                if (message != null) {
                    message.setId(dataSnapshot.getKey());
                    processIncomingMessage(message);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                Message updatedMessage = dataSnapshot.getValue(Message.class);
                if (updatedMessage != null) {
                    updatedMessage.setId(dataSnapshot.getKey());
                    updateMessageInList(updatedMessage);
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                // Handle message deletion if needed
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Message listener cancelled", databaseError.toException());
            }
        };

        FirestoreUtil.getBroadcastMessagesRef(broadcastId).addChildEventListener(messageListener);
    }

    private void processIncomingMessage(Message message) {
        messages.add(message);
        items.add(message);
        adapter.notifyItemInserted(items.size() - 1);
        binding.recyclerView.scrollToPosition(items.size() - 1);
    }

    private void updateMessageInList(Message updatedMessage) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(updatedMessage.getId())) {
                messages.set(i, updatedMessage);
                items.set(i, updatedMessage);
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }

    private void setupClickListeners() {
        binding.btnSend.setOnClickListener(v -> sendTextMessage());
        binding.btnAttachment.setOnClickListener(v -> showAttachmentOptions());

        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString().trim();
                binding.btnSend.setEnabled(!text.isEmpty());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showAttachmentOptions() {
        String[] options = {"Image", "Video"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send Media")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Image
                            imagePickerLauncher.launch("image/*");
                            break;
                        case 1: // Video
                            videoPickerLauncher.launch("video/*");
                            break;
                    }
                })
                .show();
    }

    private void handleImageSelection(Uri imageUri) {
        if (imageUri != null) {
            showLoading(true);
            CloudinaryUtil.getInstance()
                    .uploadImage(imageUri, "broadcast_images/" + broadcastId, this)
                    .thenAccept(imageUrl -> runOnUiThread(() -> {
                        showLoading(false);
                        sendImageMessage(imageUrl);
                    }))
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    });
        }
    }

    private void handleVideoSelection(Uri videoUri) {
        if (videoUri != null) {
            showLoading(true);
            CloudinaryUtil.getInstance()
                    .uploadVideo(videoUri, broadcastId, this)
                    .thenAccept(videoUrl -> runOnUiThread(() -> {
                        showLoading(false);
                        sendVideoMessage(videoUrl);
                    }))
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload video", Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    });
        }
    }

    private void sendTextMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        String senderId = FirebaseAuth.getInstance().getUid();
        FirestoreUtil.sendBroadcastMessage(broadcastId, senderId, text, Message.TYPE_TEXT, null);
        binding.etMessage.setText("");
    }

    private void sendImageMessage(String imageUrl) {
        String senderId = FirebaseAuth.getInstance().getUid();
        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("imageUrl", imageUrl);
        FirestoreUtil.sendBroadcastMessage(broadcastId, senderId, "📷 Photo", Message.TYPE_IMAGE, mediaData);
    }

    private void sendVideoMessage(String videoUrl) {
        String senderId = FirebaseAuth.getInstance().getUid();
        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("videoUrl", videoUrl);
        FirestoreUtil.sendBroadcastMessage(broadcastId, senderId, "🎥 Video", Message.TYPE_VIDEO, mediaData);
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener != null) {
            FirestoreUtil.getBroadcastMessagesRef(broadcastId).removeEventListener(messageListener);
        }
    }
}