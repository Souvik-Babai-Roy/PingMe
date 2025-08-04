package com.pingme.android.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.pingme.android.R;
import com.pingme.android.adapters.MessageAdapter;
import com.pingme.android.databinding.ActivityChatBinding;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.utils.CloudinaryUtil;
import com.pingme.android.utils.FirestoreUtil;
import com.pingme.android.utils.PreferenceUtils;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private String chatId;
    private String receiverId;
    private MessageAdapter adapter;
    private List<Object> items = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private ChildEventListener messageListener;
    private ValueEventListener typingListener;
    private User receiver;
    private User currentUser;
    private boolean isTyping = false;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImageSelection);

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleVideoSelection);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatId = getIntent().getStringExtra("chatId");
        receiverId = getIntent().getStringExtra("receiverId");

        if (chatId == null || receiverId == null) {
            Toast.makeText(this, "Invalid chat", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupAdapter();
        setupToolbar();
        loadCurrentUser();
        loadReceiver();
        setupMessageListener();
        setupTypingListener();
        setupClickListeners();
        markMessagesAsRead();
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(items, receiver);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadCurrentUser() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirestoreUtil.getUserRef(currentUserId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                currentUser = snapshot.toObject(User.class);
                if (currentUser != null) {
                    currentUser.setId(snapshot.getId());
                }
            }
        });
    }

    private void loadReceiver() {
        FirestoreUtil.getUserRef(receiverId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                receiver = snapshot.toObject(User.class);
                if (receiver != null) {
                    receiver.setId(snapshot.getId());
                    updateToolbarWithReceiver();
                    updateAdapterPrivacySettings();
                }
            }
        });
    }

    private void updateToolbarWithReceiver() {
        if (receiver == null) return;

        binding.toolbar.setTitle(receiver.getName());
        updateUserStatus();

        // Load profile picture in toolbar if privacy allows
        if (receiver.isProfilePhotoEnabled() && receiver.getImageUrl() != null && !receiver.getImageUrl().isEmpty()) {
            // Implementation for profile image in toolbar
        }
    }

    private void updateUserStatus() {
        if (receiver == null) return;

        if (receiver.isLastSeenEnabled()) {
            if (receiver.isOnline()) {
                binding.toolbar.setSubtitle("online");
            } else {
                long lastSeen = receiver.getLastSeen();
                if (lastSeen > 0) {
                    String timeAgo = DateUtils.getRelativeTimeSpanString(
                            lastSeen,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                    ).toString();
                    binding.toolbar.setSubtitle("last seen " + timeAgo);
                } else {
                    binding.toolbar.setSubtitle("offline");
                }
            }
        } else {
            binding.toolbar.setSubtitle("");
        }
    }

    private void updateAdapterPrivacySettings() {
        if (receiver != null && adapter != null) {
            adapter.updatePrivacySettings(
                    receiver.isProfilePhotoEnabled(),
                    receiver.isLastSeenEnabled()
            );
        }
    }

    private void setupMessageListener() {
        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                // FIX: Use getValue() instead of toObject() for Realtime Database
                Message message = dataSnapshot.getValue(Message.class);
                if (message != null) {
                    message.setId(dataSnapshot.getKey());
                    messages.add(message);
                    updateMessagesWithDateHeaders();

                    if (!message.getSenderId().equals(FirebaseAuth.getInstance().getUid())) {
                        updateMessageStatus(message.getId(), Message.STATUS_DELIVERED);
                    }
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                // FIX: Use getValue() instead of toObject() for Realtime Database
                Message updatedMessage = dataSnapshot.getValue(Message.class);
                if (updatedMessage != null) {
                    updatedMessage.setId(dataSnapshot.getKey());

                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).getId().equals(updatedMessage.getId())) {
                            messages.set(i, updatedMessage);
                            break;
                        }
                    }
                    updateMessagesWithDateHeaders();
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String messageId = dataSnapshot.getKey();
                messages.removeIf(message -> message.getId().equals(messageId));
                updateMessagesWithDateHeaders();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        FirestoreUtil.getMessagesRef(chatId).addChildEventListener(messageListener);
    }

    private void setupTypingListener() {
        typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Boolean isOtherUserTyping = dataSnapshot.child(receiverId).getValue(Boolean.class);
                if (isOtherUserTyping != null && isOtherUserTyping) {
                    binding.toolbar.setSubtitle("typing...");
                } else {
                    updateUserStatus();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };

        FirestoreUtil.getTypingRef(chatId).addValueEventListener(typingListener);
    }

    private void setupClickListeners() {
        binding.btnSend.setOnClickListener(v -> sendTextMessage());
        binding.btnAttach.setOnClickListener(v -> showAttachmentOptions());

        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean shouldShowTyping = s.length() > 0;
                if (shouldShowTyping != isTyping) {
                    isTyping = shouldShowTyping;
                    FirestoreUtil.setTyping(chatId, FirebaseAuth.getInstance().getUid(), isTyping);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateMessagesWithDateHeaders() {
        MessageAdapter.addDateHeaders(items, messages);
        adapter.notifyDataSetChanged();

        if (!items.isEmpty()) {
            binding.recyclerView.smoothScrollToPosition(items.size() - 1);
        }
    }

    private void showAttachmentOptions() {
        String[] options = {"Image", "Video", "Audio"};

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Send Media")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            imagePickerLauncher.launch("image/*");
                            break;
                        case 1:
                            videoPickerLauncher.launch("video/*");
                            break;
                        case 2:
                            Toast.makeText(this, "Audio recording coming soon", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .show();
    }

    private void handleImageSelection(Uri imageUri) {
        if (imageUri != null) {
            showLoading(true);
            CloudinaryUtil.getInstance()
                    .uploadImage(imageUri, "chat_images/" + chatId, this)
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
                    .uploadVideo(videoUri, chatId, this)
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
        FirestoreUtil.sendMessageToRealtime(chatId, senderId, text, "text", null);
        binding.etMessage.setText("");
        FirestoreUtil.setTyping(chatId, senderId, false);
        isTyping = false;
    }

    private void sendImageMessage(String imageUrl) {
        String senderId = FirebaseAuth.getInstance().getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("imageUrl", imageUrl);

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "📷 Image", "image", mediaData);
    }

    private void sendVideoMessage(String videoUrl) {
        String senderId = FirebaseAuth.getInstance().getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("videoUrl", videoUrl);

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "🎥 Video", "video", mediaData);
    }

    private void sendAudioMessage(String audioUrl, long duration) {
        String senderId = FirebaseAuth.getInstance().getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("audioUrl", audioUrl);
        mediaData.put("duration", duration);

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "🎤 Audio", "audio", mediaData);
    }

    private void updateMessageStatus(String messageId, int status) {
        FirestoreUtil.getMessagesRef(chatId)
                .child(messageId)
                .child("status")
                .setValue(status);
    }

    private void markMessagesAsRead() {
        FirestoreUtil.markMessagesAsRead(chatId, FirebaseAuth.getInstance().getUid());
    }

    private void showLoading(boolean show) {
        binding.btnSend.setEnabled(!show);
        binding.btnAttach.setEnabled(!show);
        binding.etMessage.setEnabled(!show);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isTyping) {
            FirestoreUtil.setTyping(chatId, FirebaseAuth.getInstance().getUid(), false);
            isTyping = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (messageListener != null) {
            FirestoreUtil.getMessagesRef(chatId).removeEventListener(messageListener);
        }
        if (typingListener != null) {
            FirestoreUtil.getTypingRef(chatId).removeEventListener(typingListener);
        }

        if (isTyping) {
            FirestoreUtil.setTyping(chatId, FirebaseAuth.getInstance().getUid(), false);
        }
    }
}