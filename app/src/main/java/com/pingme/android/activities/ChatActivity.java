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
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";

    private ActivityChatBinding binding;
    private String chatId;
    private String receiverId;
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

        Log.d(TAG, "ChatActivity created with chatId: " + chatId + " receiverId: " + receiverId);

        if (chatId == null || receiverId == null) {
            Toast.makeText(this, "Invalid chat", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupAdapter();
        setupToolbar();
        loadCurrentUser();
        loadReceiver();
        checkBlockStatus();
        ensureChatExists();
        setupMessageListener();
        setupTypingListener();
        setupOnlineStatusListener();
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

        // Add menu for block/unblock/unfriend options
        binding.ivMore.setOnClickListener(this::showMoreOptions);
    }

    private void showMoreOptions(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String[] options;
        if (isBlocked) {
            options = new String[]{"Unblock User", "Clear Chat"};
        } else {
            options = new String[]{"Block User", "Remove Friend", "Clear Chat"};
        }

        builder.setItems(options, (dialog, which) -> {
            if (isBlocked) {
                switch (which) {
                    case 0: // Unblock
                        unblockUser();
                        break;
                    case 1: // Clear Chat
                        clearChatHistory();
                        break;
                }
            } else {
                switch (which) {
                    case 0: // Block
                        blockUser();
                        break;
                    case 1: // Remove Friend
                        removeFriend();
                        break;
                    case 2: // Clear Chat
                        clearChatHistory();
                        break;
                }
            }
        });

        builder.show();
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
        Log.d(TAG, "Loading receiver: " + receiverId);

        FirestoreUtil.getUserRef(receiverId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                receiver = snapshot.toObject(User.class);
                if (receiver != null) {
                    receiver.setId(snapshot.getId());
                    updateToolbarWithReceiver();
                    updateAdapterPrivacySettings();

                    // Update adapter reference
                    if (adapter != null) {
                        adapter.updateOtherUser(receiver);
                    }

                    Log.d(TAG, "Receiver loaded successfully: " + receiver.getName());
                }
            } else {
                Log.e(TAG, "Receiver not found: " + receiverId);
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load receiver", e);
            Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void checkBlockStatus() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirestoreUtil.checkMutualBlocking(currentUserId, receiverId, (user1BlockedUser2, user2BlockedUser1) -> {
            isBlocked = user1BlockedUser2 || user2BlockedUser1;
            updateInputState();
            
            // FIXED: Update adapter with blocked status
            if (adapter != null) {
                adapter.setBlocked(isBlocked);
            }
            
            if (isBlocked) {
                // Show blocked status in toolbar
                binding.tvUserStatus.setText(user1BlockedUser2 ? "You blocked this user" : "This user blocked you");
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

        // Set user name
        binding.tvUserName.setText(receiver.getName());

        // Load profile picture in toolbar if privacy allows
        if (receiver.isProfilePhotoEnabled() && receiver.getImageUrl() != null && !receiver.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(receiver.getImageUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.defaultprofile)
                    .into(binding.toolbarProfileImage);
        } else {
            binding.toolbarProfileImage.setImageResource(R.drawable.defaultprofile);
        }

        updateUserStatus();
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
        Log.d(TAG, "Ensuring chat exists: " + chatId);

        // Check if chat exists, if not create it
        FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "Chat doesn't exist, creating new chat: " + chatId);
                    // Create new chat
                    FirestoreUtil.createNewChatInRealtime(chatId,
                            FirebaseAuth.getInstance().getUid(), receiverId);
                } else {
                    Log.d(TAG, "Chat already exists: " + chatId);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to check chat existence", databaseError.toException());
            }
        });
    }

    private void setupMessageListener() {
        Log.d(TAG, "Setting up message listener for chat: " + chatId);

        // FIXED: Use improved message loading with cleared chat support
        FirestoreUtil.loadMessagesWithClearedCheck(chatId, currentUserId, new FirestoreUtil.MessagesCallback() {
            @Override
            public void onMessagesLoaded(List<Message> loadedMessages) {
                messages.clear();
                messages.addAll(loadedMessages);
                updateMessagesWithDateHeaders();
                adapter.notifyDataSetChanged();
                
                // Scroll to bottom if there are messages
                if (messages.size() > 0) {
                    binding.recyclerViewMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load messages: " + error);
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        });

        // Set up real-time listener for new messages
        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                Log.d(TAG, "Message added: " + dataSnapshot.getKey());
                
                // FIXED: Check if user is blocked before processing messages
                if (isBlocked) {
                    Log.d(TAG, "Skipping message processing - user is blocked");
                    return;
                }

                Message message = dataSnapshot.getValue(Message.class);
                if (message != null) {
                    message.setId(dataSnapshot.getKey());
                    
                    // FIXED: Check if message is from blocked user
                    if (message.getSenderId().equals(receiverId) && isBlocked) {
                        Log.d(TAG, "Skipping message from blocked user");
                        return;
                    }
                    
                    // Check if message is after user's cleared timestamp
                    FirestoreUtil.getUserClearedTime(chatId, currentUserId, clearedAt -> {
                        if (clearedAt == 0 || message.getTimestamp() > clearedAt) {
                            messages.add(message);
                            updateMessagesWithDateHeaders();
                            adapter.notifyDataSetChanged();
                            
                            // Scroll to bottom for new messages
                            if (binding.recyclerViewMessages.getAdapter() != null) {
                                binding.recyclerViewMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
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
                Log.d(TAG, "Message changed: " + dataSnapshot.getKey());
                
                // FIXED: Check if user is blocked before processing message updates
                if (isBlocked) {
                    return;
                }

                Message updatedMessage = dataSnapshot.getValue(Message.class);
                if (updatedMessage != null) {
                    updatedMessage.setId(dataSnapshot.getKey());
                    
                    // Update existing message
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
                Log.d(TAG, "Message removed: " + dataSnapshot.getKey());
                
                // FIXED: Check if user is blocked before processing message removal
                if (isBlocked) {
                    return;
                }

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
        typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Boolean isOtherUserTyping = dataSnapshot.child(receiverId).getValue(Boolean.class);
                if (isOtherUserTyping != null && isOtherUserTyping) {
                    binding.tvUserStatus.setText("typing...");
                    binding.tvUserStatus.setVisibility(View.VISIBLE);
                    binding.typingIndicator.setVisibility(View.VISIBLE);
                } else {
                    binding.typingIndicator.setVisibility(View.GONE);
                    updateUserStatus();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };

        FirestoreUtil.getTypingRef(chatId).addValueEventListener(typingListener);
    }

    private void setupOnlineStatusListener() {
        onlineStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Boolean isOnline = dataSnapshot.child("online").getValue(Boolean.class);
                Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                if (receiver != null) {
                    receiver.setOnline(isOnline != null ? isOnline : false);
                    receiver.setLastSeen(lastSeen != null ? lastSeen : 0);
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
                if (!isBlocked) {
                    boolean shouldShowTyping = s.length() > 0;
                    if (shouldShowTyping != isTyping) {
                        isTyping = shouldShowTyping;
                        FirestoreUtil.setTyping(chatId, FirebaseAuth.getInstance().getUid(), isTyping);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Click on toolbar to view user profile
        binding.toolbarContent.setOnClickListener(v -> viewUserProfile());
    }

    private void viewUserProfile() {
        if (receiver != null) {
            // You can implement a user profile activity here
            // Intent intent = new Intent(this, UserProfileActivity.class);
            // intent.putExtra("userId", receiver.getId());
            // startActivity(intent);
            Toast.makeText(this, "Profile: " + receiver.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMessagesWithDateHeaders() {
        MessageAdapter.addDateHeaders(items, messages);
        adapter.notifyDataSetChanged();

        if (!items.isEmpty()) {
            binding.recyclerView.smoothScrollToPosition(items.size() - 1);
        }
    }

    private void showAttachmentOptions() {
        if (isBlocked) {
            Toast.makeText(this, "You cannot send attachments to blocked users", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {"Image", "Video", "Camera", "Document"};

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
                        case 2: // Camera
                            // TODO: Implement camera capture
                            Toast.makeText(this, "Camera feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 3: // Document
                            // TODO: Implement document picker
                            Toast.makeText(this, "Document feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .show();
    }

    private void handleImageSelection(Uri imageUri) {
        if (imageUri != null && !isBlocked) {
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
        if (videoUri != null && !isBlocked) {
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
        if (text.isEmpty() || isBlocked) {
            return;
        }

        binding.etMessage.setText("");
        
        // FIXED: Check block status before sending
        if (isBlocked) {
            Toast.makeText(this, "You cannot send messages to this user", Toast.LENGTH_SHORT).show();
            return;
        }

        // FIXED: Use improved message sending with block check
        FirestoreUtil.sendMessageToRealtime(chatId, currentUserId, text, "text", null);
        
        // Stop typing indicator
        FirestoreUtil.setTyping(chatId, currentUserId, false);
        isTyping = false;
    }

    private void sendImageMessage(String imageUrl) {
        if (isBlocked) return;

        Log.d(TAG, "Sending image message: " + imageUrl);

        String senderId = FirebaseAuth.getInstance().getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("imageUrl", imageUrl);

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "📷 Photo", "image", mediaData);
    }

    private void sendVideoMessage(String videoUrl) {
        if (isBlocked) return;

        Log.d(TAG, "Sending video message: " + videoUrl);

        String senderId = FirebaseAuth.getInstance().getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("videoUrl", videoUrl);
        // TODO: Generate thumbnail for video
        mediaData.put("thumbnailUrl", "");

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "🎥 Video", "video", mediaData);
    }

    private void updateMessageStatus(String messageId, int status) {
        if (!isBlocked) {
            FirestoreUtil.getMessagesRef(chatId)
                    .child(messageId)
                    .child("status")
                    .setValue(status);
        }
    }

    private void markMessagesAsRead() {
        if (!isBlocked) {
            FirestoreUtil.markMessagesAsRead(chatId, FirebaseAuth.getInstance().getUid());
        }
    }

    private void blockUser() {
        new AlertDialog.Builder(this)
                .setTitle("Block User")
                .setMessage("Are you sure you want to block " + receiver.getName() + "? You won't receive messages from them.")
                .setPositiveButton("Block", (dialog, which) -> {
                    String currentUserId = FirebaseAuth.getInstance().getUid();
                    FirestoreUtil.blockUser(currentUserId, receiverId);
                    isBlocked = true;
                    updateInputState();
                    Toast.makeText(this, receiver.getName() + " has been blocked", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unblockUser() {
        new AlertDialog.Builder(this)
                .setTitle("Unblock User")
                .setMessage("Are you sure you want to unblock " + receiver.getName() + "?")
                .setPositiveButton("Unblock", (dialog, which) -> {
                    String currentUserId = FirebaseAuth.getInstance().getUid();
                    FirestoreUtil.unblockUser(currentUserId, receiverId);
                    isBlocked = false;
                    updateInputState();
                    Toast.makeText(this, receiver.getName() + " has been unblocked", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeFriend() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Friend")
                .setMessage("Are you sure you want to remove " + receiver.getName() + " from your friends?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    String currentUserId = FirebaseAuth.getInstance().getUid();
                    FirestoreUtil.removeFriend(currentUserId, receiverId);
                    Toast.makeText(this, receiver.getName() + " has been removed from friends", Toast.LENGTH_SHORT).show();
                    finish(); // Close chat since they're no longer friends
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
                    
                    // FIXED: Use one-sided chat clearing
                    FirestoreUtil.clearChatHistoryForUser(chatId, currentUserId);
                    
                    // Clear local messages
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
        
        // Update user presence to online
        String currentUserId = FirebaseAuth.getInstance().getUid();
        FirestoreUtil.updateUserPresence(currentUserId, true);
        
        // Mark messages as read
        markMessagesAsRead();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Update user presence to offline
        String currentUserId = FirebaseAuth.getInstance().getUid();
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
        if (messageListener != null) {
            FirestoreUtil.getMessagesRef(chatId).removeEventListener(messageListener);
        }
        if (typingListener != null) {
            FirestoreUtil.getTypingRef(chatId).removeEventListener(typingListener);
        }
        if (onlineStatusListener != null) {
            FirestoreUtil.getRealtimePresenceRef(receiverId).removeEventListener(onlineStatusListener);
        }
        
        // Update user presence to offline
        String currentUserId = FirebaseAuth.getInstance().getUid();
        FirestoreUtil.updateUserPresence(currentUserId, false);
    }
}