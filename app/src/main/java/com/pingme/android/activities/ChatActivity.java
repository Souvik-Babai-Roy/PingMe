package com.pingme.android.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";

    private ActivityChatBinding binding;
    private String chatId;
    private String receiverId;
    private String currentUserId;
    private MessageAdapter adapter;
    private List<Object> items = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private ChildEventListener messageListener;
    private ValueEventListener typingListener;
    private ValueEventListener onlineStatusListener;
    private User receiver;
    private User currentUser;
    private boolean isTyping = false;
    private boolean isBlocked = false;
    private boolean isActivityActive = false;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImageSelection);

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleVideoSelection);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Validate user authentication
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        chatId = getIntent().getStringExtra("chatId");
        receiverId = getIntent().getStringExtra("receiverId");

        Log.d(TAG, "ChatActivity created with chatId: " + chatId + " receiverId: " + receiverId);

        if (chatId == null || receiverId == null) {
            Toast.makeText(this, "Invalid chat", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupAdapter();
        setupToolbar();
        loadUserData();
        setupMessageListener();
        setupClickListeners();
    }

    private void initializeViews() {
        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Set up RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setHasFixedSize(true);

        // Initialize adapter
        adapter = new MessageAdapter(items, null);
        binding.recyclerView.setAdapter(adapter);

        // Set up toolbar click listeners
        binding.toolbarContent.setOnClickListener(v -> viewUserProfile());
        binding.ivMore.setOnClickListener(v -> showMoreOptions(v));
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(items, receiver);
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadUserData() {
        // Load current user
        FirestoreUtil.getUserRef(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            currentUser.setId(documentSnapshot.getId());
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load current user", e));

        // Load receiver
        FirestoreUtil.getUserRef(receiverId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        receiver = documentSnapshot.toObject(User.class);
                        if (receiver != null) {
                            receiver.setId(documentSnapshot.getId());
                            updateToolbarWithReceiver();
                            checkBlockStatus();
                            setupOnlineStatusListener();
                            setupTypingListener();
                            ensureChatExists();
                            markMessagesAsRead();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load receiver", e);
                    Toast.makeText(this, "Failed to load user information", Toast.LENGTH_SHORT).show();
                });
    }

    private void checkBlockStatus() {
        if (currentUserId == null || receiverId == null) return;

        FirestoreUtil.checkMutualBlocking(currentUserId, receiverId, (user1BlockedUser2, user2BlockedUser1) -> {
            isBlocked = user1BlockedUser2 || user2BlockedUser1;
            updateInputState();
            
            if (adapter != null) {
                adapter.setBlocked(isBlocked);
            }
            
            if (isBlocked) {
                String statusText = user1BlockedUser2 ? "You blocked this user" : "This user blocked you";
                binding.tvUserStatus.setText(statusText);
                binding.tvUserStatus.setVisibility(View.VISIBLE);
                binding.onlineIndicator.setVisibility(View.GONE);
            }
        });
    }

    private void updateInputState() {
        if (isBlocked) {
            binding.etMessage.setEnabled(false);
            binding.btnSend.setEnabled(false);
            binding.btnAttach.setEnabled(false);
            binding.etMessage.setHint("You cannot send messages to this user");
        } else {
            binding.etMessage.setEnabled(true);
            binding.btnSend.setEnabled(true);
            binding.btnAttach.setEnabled(true);
            binding.etMessage.setHint(getString(R.string.type_a_message));
        }
    }

    private void updateToolbarWithReceiver() {
        if (receiver == null) return;

        binding.tvUserName.setText(receiver.getName());

        // Load profile picture
        if (receiver.isProfilePhotoEnabled() && receiver.getImageUrl() != null && !receiver.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(receiver.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.defaultprofile)
                    .error(R.drawable.defaultprofile)
                    .into(binding.toolbarProfileImage);
        } else {
            binding.toolbarProfileImage.setImageResource(R.drawable.defaultprofile);
        }

        updateUserStatus();
        updateAdapterPrivacySettings();
    }

    private void updateUserStatus() {
        if (receiver == null) return;

        if (receiver.isLastSeenEnabled()) {
            if (receiver.isOnline()) {
                binding.tvUserStatus.setText("online");
                binding.tvUserStatus.setVisibility(View.VISIBLE);
                binding.onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                long lastSeen = receiver.getLastSeen();
                if (lastSeen > 0) {
                    String timeAgo = DateUtils.getRelativeTimeSpanString(
                            lastSeen,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                    ).toString();
                    binding.tvUserStatus.setText("last seen " + timeAgo);
                    binding.tvUserStatus.setVisibility(View.VISIBLE);
                } else {
                    binding.tvUserStatus.setText("offline");
                    binding.tvUserStatus.setVisibility(View.VISIBLE);
                }
                binding.onlineIndicator.setVisibility(View.GONE);
            }
        } else {
            binding.tvUserStatus.setVisibility(View.GONE);
            binding.onlineIndicator.setVisibility(View.GONE);
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

    private void ensureChatExists() {
        if (chatId == null) return;

        FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "Chat doesn't exist, creating new chat: " + chatId);
                    FirestoreUtil.createNewChatInRealtime(chatId, currentUserId, receiverId);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to check chat existence", databaseError.toException());
            }
        });
    }

    private void setupMessageListener() {
        if (chatId == null) return;

        // Load initial messages
        FirestoreUtil.loadMessagesWithClearedCheck(chatId, currentUserId, new FirestoreUtil.MessagesCallback() {
            @Override
            public void onMessagesLoaded(List<Message> loadedMessages) {
                messages.clear();
                messages.addAll(loadedMessages);
                updateMessagesWithDateHeaders();
                adapter.notifyDataSetChanged();
                
                if (messages.size() > 0) {
                    binding.recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load messages: " + error);
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        });

        // Set up real-time listener
        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                if (isBlocked || !isActivityActive) return;

                Message message = dataSnapshot.getValue(Message.class);
                if (message != null) {
                    message.setId(dataSnapshot.getKey());
                    
                    // Check if message is from blocked user
                    if (message.getSenderId().equals(receiverId) && isBlocked) {
                        return;
                    }
                    
                    // Check if message is after user's cleared timestamp
                    FirestoreUtil.getUserClearedTime(chatId, currentUserId, clearedAt -> {
                        if (clearedAt == 0 || message.getTimestamp() > clearedAt) {
                            messages.add(message);
                            updateMessagesWithDateHeaders();
                            adapter.notifyDataSetChanged();
                            
                            if (binding.recyclerView.getAdapter() != null) {
                                binding.recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                            }
                            
                            // Mark as read if message is from other user
                            if (!message.getSenderId().equals(currentUserId)) {
                                updateMessageStatus(message.getId(), Message.STATUS_READ);
                            }
                        }
                    });
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                if (isBlocked || !isActivityActive) return;

                Message updatedMessage = dataSnapshot.getValue(Message.class);
                if (updatedMessage != null) {
                    updatedMessage.setId(dataSnapshot.getKey());
                    
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).getId().equals(updatedMessage.getId())) {
                            messages.set(i, updatedMessage);
                            adapter.notifyItemChanged(i);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                if (isBlocked || !isActivityActive) return;

                String messageId = dataSnapshot.getKey();
                for (int i = 0; i < messages.size(); i++) {
                    if (messages.get(i).getId().equals(messageId)) {
                        messages.remove(i);
                        updateMessagesWithDateHeaders();
                        adapter.notifyDataSetChanged();
                        break;
                    }
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Message listener cancelled", databaseError.toException());
            }
        };

        FirestoreUtil.getMessagesRef(chatId).addChildEventListener(messageListener);
    }

    private void setupTypingListener() {
        if (chatId == null) return;

        typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!isActivityActive) return;

                Boolean isReceiverTyping = dataSnapshot.child(receiverId).getValue(Boolean.class);
                if (isReceiverTyping != null && isReceiverTyping) {
                    binding.typingIndicator.setVisibility(View.VISIBLE);
                } else {
                    binding.typingIndicator.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };

        FirestoreUtil.getTypingRef(chatId).addValueEventListener(typingListener);
    }

    private void setupOnlineStatusListener() {
        if (receiverId == null) return;

        onlineStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!isActivityActive) return;

                Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                if (receiver != null) {
                    receiver.setOnline(isOnline != null ? isOnline : false);
                    updateUserStatus();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };

        FirestoreUtil.getRealtimePresenceRef(receiverId).addValueEventListener(onlineStatusListener);
    }

    private void setupClickListeners() {
        binding.btnSend.setOnClickListener(v -> sendTextMessage());
        binding.btnAttach.setOnClickListener(v -> showAttachmentOptions());

        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isTyping && s.length() > 0) {
                    isTyping = true;
                    FirestoreUtil.setTyping(chatId, currentUserId, true);
                } else if (isTyping && s.length() == 0) {
                    isTyping = false;
                    FirestoreUtil.setTyping(chatId, currentUserId, false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void viewUserProfile() {
        if (receiver != null) {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("userId", receiver.getId());
            startActivity(intent);
        }
    }

    private void showMoreOptions(View view) {
        String[] options = {"View contact", "Media, links, and docs", "Search", "Mute notifications", "Clear chat", "Block"};
        
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // View contact
                            viewUserProfile();
                            break;
                        case 1: // Media, links, and docs
                            // TODO: Implement media view
                            break;
                        case 2: // Search
                            // TODO: Implement search
                            break;
                        case 3: // Mute notifications
                            // TODO: Implement mute
                            break;
                        case 4: // Clear chat
                            clearChatHistory();
                            break;
                        case 5: // Block
                            if (isBlocked) {
                                unblockUser();
                            } else {
                                blockUser();
                            }
                            break;
                    }
                })
                .show();
    }

    private void updateMessagesWithDateHeaders() {
        items.clear();
        MessageAdapter.addDateHeaders(items, messages);
        adapter.notifyDataSetChanged();
    }

    private void showAttachmentOptions() {
        String[] options = {"Camera", "Gallery", "Document", "Location"};
        
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Camera
                            // TODO: Implement camera
                            break;
                        case 1: // Gallery
                            imagePickerLauncher.launch("image/*");
                            break;
                        case 2: // Document
                            // TODO: Implement document picker
                            break;
                        case 3: // Location
                            // TODO: Implement location sharing
                            break;
                    }
                })
                .show();
    }

    private void handleImageSelection(Uri imageUri) {
        if (imageUri == null) return;

        showLoading(true);
        
        CloudinaryUtil.uploadImage(this, imageUri)
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

    private void handleVideoSelection(Uri videoUri) {
        if (videoUri == null) return;

        showLoading(true);
        
        CloudinaryUtil.uploadVideo(this, videoUri)
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

    private void sendTextMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty() || isBlocked) {
            return;
        }

        binding.etMessage.setText("");
        
        if (isBlocked) {
            Toast.makeText(this, "You cannot send messages to this user", Toast.LENGTH_SHORT).show();
            return;
        }

        FirestoreUtil.sendMessageToRealtime(chatId, currentUserId, text, "text", null);
        
        // Stop typing indicator
        FirestoreUtil.setTyping(chatId, currentUserId, false);
        isTyping = false;
    }

    private void sendImageMessage(String imageUrl) {
        if (imageUrl == null || isBlocked) return;

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("imageUrl", imageUrl);
        
        FirestoreUtil.sendMessageToRealtime(chatId, currentUserId, "", "image", mediaData);
    }

    private void sendVideoMessage(String videoUrl) {
        if (videoUrl == null || isBlocked) return;

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("videoUrl", videoUrl);
        
        FirestoreUtil.sendMessageToRealtime(chatId, currentUserId, "", "video", mediaData);
    }

    private void updateMessageStatus(String messageId, int status) {
        if (messageId == null) return;

        FirestoreUtil.getMessagesRef(chatId).child(messageId).child("status").setValue(status);
    }

    private void markMessagesAsRead() {
        if (chatId == null || currentUserId == null) return;
        FirestoreUtil.markMessagesAsRead(chatId, currentUserId);
    }

    private void blockUser() {
        if (receiver == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Block " + receiver.getName())
                .setMessage("Are you sure you want to block this user? You won't be able to send or receive messages from them.")
                .setPositiveButton("Block", (dialog, which) -> {
                    FirestoreUtil.blockUser(currentUserId, receiverId);
                    Toast.makeText(this, "User blocked", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unblockUser() {
        if (receiver == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Unblock " + receiver.getName())
                .setMessage("Are you sure you want to unblock this user?")
                .setPositiveButton("Unblock", (dialog, which) -> {
                    FirestoreUtil.unblockUser(currentUserId, receiverId);
                    Toast.makeText(this, "User unblocked", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearChatHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Chat")
                .setMessage("Are you sure you want to clear this chat? This action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    showLoading(true);
                    
                    FirestoreUtil.clearChatHistoryForUser(chatId, currentUserId);
                    
                    messages.clear();
                    updateMessagesWithDateHeaders();
                    adapter.notifyDataSetChanged();
                    
                    showLoading(false);
                    Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLoading(boolean show) {
        if (show) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnSend.setEnabled(false);
            binding.btnAttach.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnSend.setEnabled(!isBlocked);
            binding.btnAttach.setEnabled(!isBlocked);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        
        // Update user presence to online
        FirestoreUtil.updateUserPresence(currentUserId, true);
        
        // Mark messages as read
        markMessagesAsRead();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        
        // Update user presence to offline
        FirestoreUtil.updateUserPresence(currentUserId, false);
        
        // Stop typing if user was typing
        if (isTyping) {
            FirestoreUtil.setTyping(chatId, currentUserId, false);
            isTyping = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Remove listeners
        if (messageListener != null && chatId != null) {
            FirestoreUtil.getMessagesRef(chatId).removeEventListener(messageListener);
        }
        if (typingListener != null && chatId != null) {
            FirestoreUtil.getTypingRef(chatId).removeEventListener(typingListener);
        }
        if (onlineStatusListener != null && receiverId != null) {
            FirestoreUtil.getRealtimePresenceRef(receiverId).removeEventListener(onlineStatusListener);
        }
        
        // Update user presence to offline
        FirestoreUtil.updateUserPresence(currentUserId, false);
    }
}