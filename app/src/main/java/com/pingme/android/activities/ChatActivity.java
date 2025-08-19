package com.pingme.android.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import androidx.core.content.FileProvider;
import java.io.File;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import com.pingme.android.utils.FirebaseUtil;
import com.pingme.android.utils.MediaPlayerUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private ValueEventListener messageStatusListener;
    private User receiver;
    private User currentUser;
    private String currentUserId;
    private boolean isTyping = false;
    private boolean isBlocked = false;
    
    // For message highlighting from search
    private String highlightMessageId = null;
    private String searchQuery = null;
    // Suppress auto-scroll to bottom when navigating from search to a specific message
    private boolean suppressAutoScroll = false;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImageSelection);

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleVideoSelection);

    private final ActivityResultLauncher<String> audioPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleAudioSelection);

    private final ActivityResultLauncher<String> documentPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleDocumentSelection);

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::handleCameraCapture);

    private Uri cameraImageUri;

    private final ActivityResultLauncher<Intent> forwardMessageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Message forwarded successfully", Toast.LENGTH_SHORT).show();
                }
            });

    public static void start(android.content.Context context, String chatId, String receiverId) {
        android.content.Intent intent = new android.content.Intent(context, ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("receiverId", receiverId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize current user ID
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            this.currentUserId = firebaseUser.getUid();
        } else {
            this.currentUserId = "";
        }

        chatId = getIntent().getStringExtra("chatId");
        receiverId = getIntent().getStringExtra("receiverId");
        
        // Handle message highlighting from search
        highlightMessageId = getIntent().getStringExtra("messageId");
        searchQuery = getIntent().getStringExtra("searchQuery");
        boolean shouldHighlight = getIntent().getBooleanExtra("highlightMessage", false);
        suppressAutoScroll = shouldHighlight && highlightMessageId != null;

        // Handle legacy intent format from FriendsLayoutActivity
        if (chatId == null || receiverId == null) {
            String userId = getIntent().getStringExtra("userId");
            if (userId != null) {
                receiverId = userId;
                // Generate chat ID using same logic as AddFriendActivity
                String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                chatId = currentUserId + "_" + receiverId;
                if (currentUserId.compareTo(receiverId) > 0) {
                    chatId = receiverId + "_" + currentUserId;
                }
                Log.d(TAG, "Using legacy intent format - generated chatId: " + chatId + " receiverId: " + receiverId);
            }
        }

        Log.d(TAG, "ChatActivity created with chatId: " + chatId + " receiverId: " + receiverId);
        Log.d(TAG, "Intent extras: " + getIntent().getExtras());

        if (chatId == null || receiverId == null) {
            Log.e(TAG, "Invalid chat data - chatId: " + chatId + " receiverId: " + receiverId);
            Toast.makeText(this, "Invalid chat data. Please try again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Cancel any existing notifications for this chat
        com.pingme.android.utils.NotificationUtil.cancelMessageNotification(this, chatId);

        setupAdapter();
        setupToolbar();
        loadCurrentUser();
        loadReceiver();
        checkBlockStatus();
        ensureChatExists();
        setupMessageListener();
        setupTypingListener();
        setupOnlineStatusListener();
        setupMessageStatusListener();
        setupClickListeners();
        markMessagesAsRead();
        
        // Handle message highlighting from search if needed
        if (shouldHighlight && highlightMessageId != null) {
            setupMessageHighlighting();
        }
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(items, receiver);
        adapter.setContext(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);
    }
    
    private void setupMessageHighlighting() {
        // Wait for messages to load, then scroll to and highlight the message
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            highlightAndScrollToMessage(highlightMessageId);
            // Keep suppressAutoScroll true to prevent auto-scrolling to bottom after search navigation
            // Only re-enable auto scroll for new messages, not when navigating from search
        }, 1500); // Delay to ensure messages are loaded
    }
    
    private void highlightAndScrollToMessage(String messageId) {
        if (messageId == null || items.isEmpty()) return;
        
        // Find message position and scroll to it
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof Message) {
                Message message = (Message) item;
                if (message.getId().equals(messageId)) {
                    // Center the message on screen instead of scrolling to top
                    centerMessageOnScreen(i);
                    
                    // Apply highlighting
                    adapter.setMessageHighlight(messageId, searchQuery);
                    
                    // Clear highlight after 5 seconds
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        adapter.clearMessageHighlight();
                        // Clear the highlight message ID to allow normal auto-scrolling for new messages
                        highlightMessageId = null;
                        // Re-enable auto scroll for new messages only
                        suppressAutoScroll = false;
                    }, 5000);
                    
                    break;
                }
            }
        }
    }
    
    private void centerMessageOnScreen(int position) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (layoutManager != null) {
            // Ensure RecyclerView is laid out before calculating center position
            binding.recyclerView.post(() -> {
                int recyclerViewHeight = binding.recyclerView.getHeight();
                if (recyclerViewHeight > 0) {
                    // Calculate the offset to center the message on screen
                    // Use a slightly higher position (around 40% from top) for better visual centering
                    // This accounts for typical message height and provides better visual balance
                    int centerOffset = (int) (recyclerViewHeight * 0.4);
                    
                    // Use scrollToPositionWithOffset to center the message
                    // The offset moves the item down from the top of the RecyclerView
                    layoutManager.scrollToPositionWithOffset(position, centerOffset);
                } else {
                    // Fallback to smooth scroll if dimensions aren't available yet
                    binding.recyclerView.smoothScrollToPosition(position);
                }
            });
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        binding.ivMore.setOnClickListener(this::showMoreOptions);
    }

    private void showMoreOptions(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String[] options;
        if (isBlocked) {
            options = new String[]{"Unblock User", "Clear Chat", "Delete Chat"};
        } else {
            options = new String[]{"Block User", "Remove Friend", "Clear Chat", "Delete Chat"};
        }

        builder.setItems(options, (dialog, which) -> {
            if (isBlocked) {
                switch (which) {
                    case 0: // Unblock
                        unblockUser();
                        break;
                    case 1: // Clear Chat
                        showClearChatConfirmation();
                        break;
                    case 2: // Delete Chat
                        showDeleteChatConfirmation();
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
                        showClearChatConfirmation();
                        break;
                    case 3: // Delete Chat
                        showDeleteChatConfirmation();
                        break;
                }
            }
        });

        builder.show();
    }

    private void showClearChatConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("🗑️ Clear Chat")
                .setMessage("Clear all messages in this chat?\n\nThis action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    clearChat();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(R.drawable.ic_delete)
                .show();
    }

    private void showDeleteChatConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("❌ Delete Chat")
                .setMessage("Delete this chat?\n\nAll messages will be removed from your device.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteChat();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(R.drawable.ic_delete)
                .show();
    }

    private void clearChat() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        
        FirebaseUtil.clearChat(chatId, currentUser.getUid());
        
        // Clear messages from local list
        messages.clear();
        updateMessagesWithDateHeaders();
        
        showLoading(false);
        Toast.makeText(this, "Chat cleared successfully", Toast.LENGTH_SHORT).show();
    }

    private void deleteChat() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        
        FirebaseUtil.deleteChat(chatId, currentUser.getUid());
        
        showLoading(false);
        Toast.makeText(this, "Chat deleted successfully", Toast.LENGTH_SHORT).show();
        
        // Close the chat activity
        finish();
    }

    private void loadCurrentUser() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        String currentUserId = firebaseUser.getUid();
        FirebaseUtil.getUserRef(currentUserId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                currentUser = snapshot.toObject(User.class);
                if (currentUser != null) {
                    currentUser.setId(snapshot.getId());
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load current user", e);
            Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadReceiver() {
        Log.d(TAG, "Loading receiver: " + receiverId);

        FirebaseUtil.getUserRef(receiverId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                receiver = snapshot.toObject(User.class);
                if (receiver != null) {
                    receiver.setId(snapshot.getId());
                    
                    // Load personal name from friendship relationship
                    loadReceiverPersonalName();
                    
                    updateToolbarWithReceiver();
                    updateAdapterPrivacySettings();

                    if (adapter != null) {
                        adapter.updateOtherUser(receiver);
                    }

                    Log.d(TAG, "Receiver loaded successfully: " + receiver.getDisplayName());
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
    
    private void loadReceiverPersonalName() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || receiver == null) {
            return;
        }
        
        String currentUserId = firebaseUser.getUid();
        
        // Load personal name from current user's friends collection
        FirebaseUtil.getFriendsRef(currentUserId)
                .document(receiverId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String personalName = doc.getString("personalName");
                        if (personalName != null && !personalName.trim().isEmpty()) {
                            receiver.setPersonalName(personalName);
                            // Update UI with the personal name
                            updateToolbarWithReceiver();
                            if (adapter != null) {
                                adapter.updateOtherUser(receiver);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load personal name for receiver", e);
                });
    }

    private void checkBlockStatus() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.e(TAG, "User not authenticated");
            return;
        }
        
        String currentUserId = firebaseUser.getUid();

        // Check both blocking directions
        FirebaseUtil.checkIfBlocked(currentUserId, receiverId, currentUserBlocked -> {
            FirebaseUtil.checkIfBlocked(receiverId, currentUserId, receiverBlocked -> {
                isBlocked = currentUserBlocked || receiverBlocked;

                runOnUiThread(() -> {
                    updateInputState();
                    if (receiverBlocked) {
                        binding.etMessage.setHint("This user has blocked you");
                    }
                });
            });
        });
    }

    private void updateInputState() {
        if (isBlocked) {
            binding.etMessage.setEnabled(false);
            binding.btnSend.setEnabled(false);
            binding.btnAttach.setEnabled(false);
            binding.etMessage.setHint("You cannot message this user");

            binding.typingIndicator.setVisibility(View.GONE);
//            binding.layoutInput.setAlpha(0.5f);
        } else {
            binding.etMessage.setEnabled(true);
            binding.btnSend.setEnabled(true);
            binding.btnAttach.setEnabled(true);
            binding.etMessage.setHint(getString(R.string.type_a_message));
//            binding.layoutInput.setAlpha(1.0f);
        }
    }

    private void updateToolbarWithReceiver() {
        if (receiver == null) return;

        // Show personal name if available, otherwise show original name
        String displayName = receiver.getPersonalName() != null && !receiver.getPersonalName().trim().isEmpty()
                ? receiver.getPersonalName()
                : receiver.getName();
        
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "Unknown User";
        }
        binding.tvUserName.setText(displayName);

        // Only show profile if not blocked and privacy allows, with current user priority
        boolean isCurrentUser = receiver.getId().equals(currentUserId);
        boolean shouldShowAvatar = isCurrentUser || (!isBlocked && receiver.isProfilePhotoEnabled());
        
        if (shouldShowAvatar && receiver.getImageUrl() != null && !receiver.getImageUrl().isEmpty()) {
            try {
                Glide.with(this)
                        .load(receiver.getImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.defaultprofile)
                        .error(R.drawable.defaultprofile)
                        .into(binding.toolbarProfileImage);
            } catch (Exception e) {
                binding.toolbarProfileImage.setImageResource(R.drawable.defaultprofile);
            }
        } else {
            binding.toolbarProfileImage.setImageResource(R.drawable.defaultprofile);
        }

        updateUserStatus();
    }

    private void updateUserStatus() {
        if (receiver == null) return;

        // Only show status if not blocked and privacy allows
        if (!isBlocked && receiver.isLastSeenEnabled()) {
            if (receiver.isOnline()) {
                binding.tvUserStatus.setText("online");
                binding.tvUserStatus.setVisibility(View.VISIBLE);
                binding.onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                long lastSeen = receiver.getLastSeen();
                if (lastSeen > 0) {
                    // Enhanced last seen formatting like WhatsApp
                    String timeAgo = getWhatsAppTimeFormat(lastSeen);
                    binding.tvUserStatus.setText("last seen " + timeAgo);
                    binding.tvUserStatus.setVisibility(View.VISIBLE);
                    binding.onlineIndicator.setVisibility(View.GONE);
                } else {
                    binding.tvUserStatus.setText("offline");
                    binding.tvUserStatus.setVisibility(View.VISIBLE);
                    binding.onlineIndicator.setVisibility(View.GONE);
                }
            }
        } else {
            // Hide status if privacy is disabled or user is blocked
            binding.tvUserStatus.setVisibility(View.GONE);
            binding.onlineIndicator.setVisibility(View.GONE);
        }
    }

    // WhatsApp-like time formatting for last seen
    private String getWhatsAppTimeFormat(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long minutes = diff / (1000 * 60);
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (minutes < 1) {
            return "just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (days == 1) {
            return "yesterday";
        } else if (days < 7) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            // For older dates, show actual date
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return "on " + dateFormat.format(new Date(timestamp));
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

        FirebaseUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "Chat doesn't exist, creating new chat: " + chatId);
                    FirebaseUtil.createNewChatInRealtime(chatId,
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
        Log.d(TAG, "=== SETTING UP MESSAGE LISTENER ===");
        Log.d(TAG, "Chat ID: " + chatId);
        Log.d(TAG, "Messages ref path: " + FirebaseUtil.getMessagesRef(chatId).toString());

        // Remove existing listener if any
        if (messageListener != null) {
            FirebaseUtil.getMessagesRef(chatId).removeEventListener(messageListener);
        }

        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                Log.d(TAG, "🔄 Message child added: " + dataSnapshot.getKey());
                Log.d(TAG, "Raw data: " + dataSnapshot.getValue());
                
                Message message = dataSnapshot.getValue(Message.class);
                if (message != null) {
                    message.setId(dataSnapshot.getKey());
                    Log.d(TAG, "✅ Parsed message: " + message.getText());

                    FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (firebaseUser == null) {
                        Log.e(TAG, "User not authenticated");
                        return;
                    }
                    
                    String currentUserId = firebaseUser.getUid();
                    
                    // Process all messages, but filter blocked users
                    if (!message.getSenderId().equals(currentUserId)) {
                        // Check if current user blocked the sender
                        FirebaseUtil.checkIfBlocked(currentUserId, message.getSenderId(), blocked -> {
                            if (blocked) {
                                Log.d(TAG, "Message filtered - sender is blocked");
                                return;
                            }
                            addMessage(message);
                        });
                    } else {
                        addMessage(message);
                    }
                } else {
                    Log.w(TAG, "❌ Failed to parse message from: " + dataSnapshot.getKey());
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                Message updatedMessage = dataSnapshot.getValue(Message.class);
                if (updatedMessage != null) {
                    updatedMessage.setId(dataSnapshot.getKey());

                    FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (firebaseUser == null) {
                        Log.e(TAG, "User not authenticated");
                        return;
                    }
                    
                    String currentUserId = firebaseUser.getUid();
                    
                    // Update message status for all messages
                    if (!updatedMessage.getSenderId().equals(currentUserId)) {
                        FirebaseUtil.checkIfBlocked(currentUserId, updatedMessage.getSenderId(), blocked -> {
                            if (!blocked) {
                                updateMessageInList(updatedMessage);
                            }
                        });
                    } else {
                        updateMessageInList(updatedMessage);
                    }
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
                Log.e(TAG, "Message listener cancelled", databaseError.toException());
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseUtil.getMessagesRef(chatId).addChildEventListener(messageListener);
    }

    private void addMessage(Message message) {
        // Check if message already exists in list (prevent duplicates)
        boolean messageExists = false;
        for (Message existingMessage : messages) {
            if (existingMessage.getId().equals(message.getId())) {
                messageExists = true;
                break;
            }
        }

        if (!messageExists) {
            messages.add(message);
            updateMessagesWithDateHeaders();

            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null && !message.getSenderId().equals(firebaseUser.getUid())) {
                // Mark message as delivered immediately when received (this is correct WhatsApp behavior)
                FirebaseUtil.markMessageAsDelivered(chatId, message.getId(), firebaseUser.getUid());
                
                // Only mark as read if chat is actively open and user is viewing
                // This ensures read receipts only trigger when user actually sees the message
                if (isChatActive()) {
                    FirebaseUtil.markMessageAsRead(chatId, message.getId(), firebaseUser.getUid());
                }
            }
        }
    }

    private boolean isChatActive() {
        // Check if the chat activity is in foreground and user is actively viewing
        // This ensures read receipts only trigger when user is actually looking at the chat
        return !isFinishing() && !isDestroyed() && hasWindowFocus();
    }

    private void updateMessageInList(Message updatedMessage) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(updatedMessage.getId())) {
                messages.set(i, updatedMessage);
                break;
            }
        }
        updateMessagesWithDateHeaders();
    }

    private void setupTypingListener() {
        typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (isBlocked) {
                    binding.typingIndicator.setVisibility(View.GONE);
                    updateUserStatus();
                    return;
                }

                // Use utility method to safely convert typing status
                boolean isOtherUserTyping = FirebaseUtil.safeBooleanValue(dataSnapshot.child(receiverId).getValue());
                
                if (isOtherUserTyping) {
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

        FirebaseUtil.getTypingRef(chatId).addValueEventListener(typingListener);
    }

    private void setupOnlineStatusListener() {
        onlineStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Use utility method to safely convert online status
                boolean isOnline = FirebaseUtil.safeBooleanValue(dataSnapshot.child("online").getValue());
                Long lastSeen = dataSnapshot.child("lastSeen").getValue(Long.class);

                if (receiver != null) {
                    receiver.setOnline(isOnline);
                    receiver.setLastSeen(lastSeen != null ? lastSeen : 0);
                    updateUserStatus();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };

        FirebaseUtil.getRealtimePresenceRef(receiverId).addValueEventListener(onlineStatusListener);
    }

    private void setupMessageStatusListener() {
        // Listen for real-time message status changes (delivered/read)
        messageStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Update message statuses in real-time
                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    String messageId = messageSnapshot.getKey();
                    if (messageId != null) {
                        Message updatedMessage = messageSnapshot.getValue(Message.class);
                        if (updatedMessage != null) {
                            updatedMessage.setId(messageId);
                            updateMessageInList(updatedMessage);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Message status listener cancelled", databaseError.toException());
            }
        };
        
        FirebaseUtil.getMessagesRef(chatId).addValueEventListener(messageStatusListener);
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
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser != null) {
                            FirebaseUtil.setTyping(chatId, currentUser.getUid(), isTyping);
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.toolbarContent.setOnClickListener(v -> viewUserProfile());
    }

    private void viewUserProfile() {
        if (receiver != null) {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_id", receiver.getId());
            startActivity(intent);
        }
    }

    private void updateMessagesWithDateHeaders() {
        MessageAdapter.addDateHeaders(items, messages);
        adapter.notifyDataSetChanged();

        // Only auto-scroll to bottom if:
        // 1. Not suppressing auto-scroll (e.g., when navigating from search)
        // 2. Not highlighting a specific message from search
        if (!items.isEmpty() && !suppressAutoScroll && highlightMessageId == null) {
            binding.recyclerView.smoothScrollToPosition(items.size() - 1);
        }
    }

    private void showAttachmentOptions() {
        String[] options = {"Image", "Video", "Audio", "Document", "Camera"};
        
        new AlertDialog.Builder(this)
                .setTitle("Choose Attachment")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Image
                            imagePickerLauncher.launch("image/*");
                            break;
                        case 1: // Video
                            videoPickerLauncher.launch("video/*");
                            break;
                        case 2: // Audio
                            audioPickerLauncher.launch("audio/*");
                            break;
                        case 3: // Document
                            // For Android 13+, we'll handle permission in the result callback
                            // For now, just launch the document picker directly
                            documentPickerLauncher.launch("*/*");
                            break;
                        case 4: // Camera
                            openCamera();
                            break;
                    }
                })
                .show();
    }

    // Enhanced message actions
    public void showMessageOptions(Message message, View anchorView) {
        if (message == null) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        String currentUserId = currentUser.getUid();
        boolean isMyMessage = message.isSentByCurrentUser(currentUserId);
        boolean canEdit = message.canBeEdited() && isMyMessage;
        boolean canDelete = message.canBeDeleted();

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // Reply option (always available for non-deleted messages)
        if (!message.isDeletedForEveryone()) {
            options.add("Reply");
            actions.add(() -> replyToMessage(message));
        }

        // Forward option (always available for non-deleted messages)
        if (!message.isDeletedForEveryone()) {
            options.add("Forward");
            actions.add(() -> forwardMessage(message));
        }

        // Edit option (only for my text messages that can be edited)
        if (canEdit) {
            options.add("Edit");
            actions.add(() -> editMessage(message));
        }

        // Delete options
        if (canDelete) {
            if (isMyMessage) {
                options.add("Delete for me");
                actions.add(() -> deleteMessageForMe(message));
                
                // Delete for everyone (only for recent messages)
                long messageAge = System.currentTimeMillis() - message.getTimestamp();
                if (messageAge < 24 * 60 * 60 * 1000) { // 24 hours
                    options.add("Delete for everyone");
                    actions.add(() -> deleteMessageForEveryone(message));
                }
            } else {
                options.add("Delete for me");
                actions.add(() -> deleteMessageForMe(message));
            }
        }

        if (options.isEmpty()) return;

        String[] optionsArray = options.toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Message Options")
                .setItems(optionsArray, (dialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    private void replyToMessage(Message message) {
        // Set reply mode in adapter
        adapter.setReplyMode(true, message);
        binding.etMessage.requestFocus();
        binding.etMessage.setHint("Reply to: " + message.getDisplayText());
    }

    private void forwardMessage(Message message) {
        // Open contact selection for forwarding
        Intent intent = new Intent(this, SelectContactsActivity.class);
        intent.putExtra("isForForward", true);
        intent.putExtra("messageId", message.getId());
        intent.putExtra("messageText", message.getText());
        intent.putExtra("messageType", message.getType());
        forwardMessageLauncher.launch(intent);
    }

    private void editMessage(Message message) {
        // Show edit dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_message, null);
        TextInputEditText etEditText = dialogView.findViewById(R.id.etEditText);
        etEditText.setText(message.getText());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("Edit Message")
                .setPositiveButton("Save", (dialogInterface, i) -> {
                    String newText = etEditText.getText().toString().trim();
                    if (!newText.isEmpty() && !newText.equals(message.getText())) {
                        FirebaseUtil.editMessage(chatId, message.getId(), newText);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    private void deleteMessageForMe(Message message) {
        String currentUserId = FirebaseAuth.getInstance().getUid();
        FirebaseUtil.deleteMessageForUser(chatId, message.getId(), currentUserId);
    }

    private void deleteMessageForEveryone(Message message) {
        String currentUserId = FirebaseAuth.getInstance().getUid();
        FirebaseUtil.deleteMessageForEveryone(chatId, message.getId(), currentUserId);
    }

    private void handleImageSelection(Uri imageUri) {
        if (imageUri != null && !isBlocked) {
            sendImageMessage(imageUri);
        }
    }

    private void handleVideoSelection(Uri videoUri) {
        if (videoUri != null && !isBlocked) {
            sendVideoMessage(videoUri);
        }
    }

    private void handleAudioSelection(Uri audioUri) {
        if (audioUri != null && !isBlocked) {
            sendAudioMessage(audioUri);
        }
    }

    private void handleDocumentSelection(Uri documentUri) {
        if (documentUri != null && !isBlocked) {
            sendDocumentMessage(documentUri);
        }
    }

    private void handleCameraCapture(Boolean success) {
        if (success && cameraImageUri != null && !isBlocked) {
            sendImageMessage(cameraImageUri);
        }
    }

    private void openCamera() {
        try {
            // Create a unique file name for the image
            String fileName = "IMG_" + System.currentTimeMillis() + ".jpg";
            File imageFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName);
            
            // Create content URI for the file
            cameraImageUri = FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".fileprovider", imageFile);
            
            // Launch camera
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendTextMessage() {
        String messageText = binding.etMessage.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isBlocked) {
            Toast.makeText(this, "Cannot send message to blocked user", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();
        
        // Clear input immediately for better UX
        binding.etMessage.setText("");
        
        // Show sending indicator
        showSendingIndicator(true);
        
        Log.d(TAG, "Sending text message: " + messageText + " to chat: " + chatId);
        
        // Use the enhanced message delivery system
        FirebaseUtil.sendMessageWithDeliveryTracking(chatId, senderId, messageText, Message.TYPE_TEXT, null)
                .addOnSuccessListener(aVoid -> {
                    showSendingIndicator(false);
                    Log.d(TAG, "Message sent successfully");
                    // Auto-scroll to new message (always scroll for sent messages)
                    binding.recyclerView.post(() -> {
                        if (messages.size() > 0) {
                            binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    showSendingIndicator(false);
                    Log.e(TAG, "Failed to send message", e);
                    Toast.makeText(this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Restore the message text if sending failed
                    binding.etMessage.setText(messageText);
                });
    }

    private void sendImageMessage(Uri imageUri) {
        if (isBlocked) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();
        showLoading(true);

        CloudinaryUtil.getInstance()
                .uploadChatImage(imageUri, this)
                .thenAccept(imageUrl -> runOnUiThread(() -> {
                    // Check if imageUrl is null
                    if (imageUrl == null) {
                        Log.e(TAG, "Image URL is null");
                        showLoading(false);
                        Toast.makeText(this, "Failed to upload image: no URL received", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Map<String, Object> mediaData = new HashMap<>();
                    mediaData.put("imageUrl", imageUrl);
                    
                    FirebaseUtil.sendMessageWithDeliveryTracking(chatId, senderId, "📷 Image", Message.TYPE_IMAGE, mediaData)
                            .addOnSuccessListener(aVoid -> {
                                showLoading(false);
                                Log.d(TAG, "Image message sent successfully");
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Log.e(TAG, "Failed to send image message", e);
                                Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show();
                            });
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void sendVideoMessage(Uri videoUri) {
        if (isBlocked) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();
        showLoading(true);

        // Validate video file
        try {
            String mimeType = getContentResolver().getType(videoUri);
            if (mimeType == null || !mimeType.startsWith("video/")) {
                showLoading(false);
                Toast.makeText(this, "Invalid video file", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking video file", e);
            showLoading(false);
            Toast.makeText(this, "Error reading video file", Toast.LENGTH_SHORT).show();
            return;
        }

        CloudinaryUtil.getInstance()
                .uploadChatVideo(videoUri, this)
                .thenAccept(videoData -> runOnUiThread(() -> {
                    try {
                        // Check if videoData is null
                        if (videoData == null) {
                            Log.e(TAG, "Video data is null");
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload video: no data received", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        Map<String, Object> mediaData = new HashMap<>();
                        
                        // Safely get values with null checks
                        Object videoUrl = videoData.get("videoUrl");
                        Object thumbnailUrl = videoData.get("thumbnailUrl");
                        Object duration = videoData.get("duration");
                        
                        // Only add non-null values to mediaData
                        if (videoUrl != null) {
                            mediaData.put("videoUrl", videoUrl);
                        }
                        if (thumbnailUrl != null) {
                            mediaData.put("thumbnailUrl", thumbnailUrl);
                        }
                        if (duration != null) {
                            mediaData.put("duration", duration);
                        }
                        
                        // Log the video data for debugging
                        Log.d(TAG, "Video data received: " + videoData);
                        Log.d(TAG, "Video URL: " + videoUrl);
                        Log.d(TAG, "Thumbnail URL: " + thumbnailUrl);
                        Log.d(TAG, "Duration: " + duration);
                        
                        // Ensure we have at least a video URL
                        if (!mediaData.containsKey("videoUrl")) {
                            Log.e(TAG, "No video URL in media data");
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload video: no URL received", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        FirebaseUtil.sendMessageWithDeliveryTracking(chatId, senderId, "🎥 Video", Message.TYPE_VIDEO, mediaData)
                                .addOnSuccessListener(aVoid -> {
                                    showLoading(false);
                                    Log.d(TAG, "Video message sent successfully");
                                    Toast.makeText(this, "Video sent successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Log.e(TAG, "Failed to send video message", e);
                                    Toast.makeText(this, "Failed to send video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in video upload callback", e);
                        showLoading(false);
                        Toast.makeText(this, "Error processing video upload", Toast.LENGTH_SHORT).show();
                    }
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Log.e(TAG, "Video upload failed", throwable);
                        Toast.makeText(this, "Failed to upload video: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void sendAudioMessage(Uri audioUri) {
        if (isBlocked) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();
        showLoading(true);

        // Validate audio file
        try {
            String mimeType = getContentResolver().getType(audioUri);
            if (mimeType == null || !mimeType.startsWith("audio/")) {
                showLoading(false);
                Toast.makeText(this, "Invalid audio file", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking audio file", e);
            showLoading(false);
            Toast.makeText(this, "Error reading audio file", Toast.LENGTH_SHORT).show();
            return;
        }

        CloudinaryUtil.getInstance()
                .uploadChatAudio(audioUri, this)
                .thenAccept(audioData -> runOnUiThread(() -> {
                    try {
                        // Check if audioData is null
                        if (audioData == null) {
                            Log.e(TAG, "Audio data is null");
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload audio: no data received", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        Map<String, Object> mediaData = new HashMap<>();
                        
                        // Safely get values with null checks
                        Object audioUrl = audioData.get("audioUrl");
                        Object duration = audioData.get("duration");
                        
                        // Only add non-null values to mediaData
                        if (audioUrl != null) {
                            mediaData.put("audioUrl", audioUrl);
                        }
                        if (duration != null) {
                            mediaData.put("duration", duration);
                        }
                        
                        // Log the audio data for debugging
                        Log.d(TAG, "Audio data received: " + audioData);
                        Log.d(TAG, "Audio URL: " + audioUrl);
                        Log.d(TAG, "Duration: " + duration);
                        
                        // Ensure we have at least an audio URL
                        if (!mediaData.containsKey("audioUrl")) {
                            Log.e(TAG, "No audio URL in media data");
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload audio: no URL received", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        FirebaseUtil.sendMessageWithDeliveryTracking(chatId, senderId, "🎤 Audio", Message.TYPE_AUDIO, mediaData)
                                .addOnSuccessListener(aVoid -> {
                                    showLoading(false);
                                    Log.d(TAG, "Audio message sent successfully");
                                    Toast.makeText(this, "Audio sent successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Log.e(TAG, "Failed to send audio message", e);
                                    Toast.makeText(this, "Failed to send audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in audio upload callback", e);
                        showLoading(false);
                        Toast.makeText(this, "Error processing audio upload", Toast.LENGTH_SHORT).show();
                    }
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Log.e(TAG, "Audio upload failed", throwable);
                        Toast.makeText(this, "Failed to upload audio: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void sendDocumentMessage(Uri documentUri) {
        if (isBlocked) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String senderId = currentUser.getUid();
        showLoading(true);

        CloudinaryUtil.getInstance()
                .uploadChatDocument(documentUri, this)
                .thenAccept(documentData -> runOnUiThread(() -> {
                    // Check if documentData is null
                    if (documentData == null) {
                        Log.e(TAG, "Document data is null");
                        showLoading(false);
                        Toast.makeText(this, "Failed to upload document: no data received", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Map<String, Object> mediaData = new HashMap<>();
                    
                    // Safely get values with null checks
                    Object fileUrl = documentData.get("fileUrl");
                    Object fileName = documentData.get("fileName");
                    Object fileSize = documentData.get("fileSize");
                    
                    // Only add non-null values to mediaData
                    if (fileUrl != null) {
                        mediaData.put("fileUrl", fileUrl);
                    }
                    if (fileName != null) {
                        mediaData.put("fileName", fileName);
                    }
                    if (fileSize != null) {
                        mediaData.put("fileSize", fileSize);
                    }
                    
                    // Ensure we have at least a file URL
                    if (!mediaData.containsKey("fileUrl")) {
                        Log.e(TAG, "No file URL in media data");
                        showLoading(false);
                        Toast.makeText(this, "Failed to upload document: no URL received", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    FirebaseUtil.sendMessageWithDeliveryTracking(chatId, senderId, "📄 Document", Message.TYPE_DOCUMENT, mediaData)
                            .addOnSuccessListener(aVoid -> {
                                showLoading(false);
                                Log.d(TAG, "Document message sent successfully");
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Log.e(TAG, "Failed to send document message", e);
                                Toast.makeText(this, "Failed to send document", Toast.LENGTH_SHORT).show();
                            });
                }))
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "Failed to upload document", Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void updateMessageStatus(String messageId, int status) {
        if (!isBlocked) {
            FirebaseUtil.getMessagesRef(chatId)
                    .child(messageId)
                    .child("status")
                    .setValue(status);
        }
    }

    private void markMessagesAsRead() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        String currentUserId = firebaseUser.getUid();
        
        // Mark all unread messages as read
        for (Message message : messages) {
            if (!message.getSenderId().equals(currentUserId)) {
                // Check if message is not already read
                if (message.getReadBy() == null || !message.getReadBy().containsKey(currentUserId)) {
                    FirebaseUtil.markMessageAsRead(chatId, message.getId(), currentUserId);
                }
            }
        }
        
        // Update the chat's unread count to 0 since all messages are now read
        updateChatUnreadCount(0);

        // Also clear per-user unreadCount in user_chats immediately (redundant safety)
        FirebaseUtil.setUserChatUnreadCount(currentUserId, chatId, 0);
    }
    
    private void updateChatUnreadCount(int newCount) {
        // Update the chat's unread count in Firebase using the utility method
        FirebaseUtil.updateChatUnreadCount(chatId, newCount);
        
        // Also reset per-user unread count like WhatsApp (only for current user)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            FirebaseUtil.setUserChatUnreadCount(currentUser.getUid(), chatId, newCount);
        }

        // Notify the chat list that this chat has been updated
        notifyChatListUpdate();
    }
    
    private void notifyChatListUpdate() {
        // Send a broadcast to notify the chat list that this chat has been updated
        Intent intent = new Intent("CHAT_UPDATED");
        intent.putExtra("chatId", chatId);
        intent.putExtra("unreadCount", 0);
        
        Log.d(TAG, "📡 Sending broadcast: CHAT_UPDATED for chat " + chatId + " with unreadCount 0");
        sendBroadcast(intent);
        Log.d(TAG, "✅ Broadcast sent successfully");
    }

    private void blockUser() {
        new AlertDialog.Builder(this)
                .setTitle("Block User")
                .setMessage("Are you sure you want to block " + receiver.getDisplayName() + "? You won't receive messages from them.")
                .setPositiveButton("Block", (dialog, which) -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        String currentUserId = currentUser.getUid();
                        FirebaseUtil.blockUser(currentUserId, receiverId);
                        isBlocked = true;
                        updateInputState();
                        Toast.makeText(this, receiver.getDisplayName() + " has been blocked", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unblockUser() {
        new AlertDialog.Builder(this)
                .setTitle("Unblock User")
                .setMessage("Are you sure you want to unblock " + receiver.getDisplayName() + "?")
                .setPositiveButton("Unblock", (dialog, which) -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        String currentUserId = currentUser.getUid();
                        FirebaseUtil.unblockUser(currentUserId, receiverId);
                        isBlocked = false;
                        updateInputState();
                        Toast.makeText(this, receiver.getDisplayName() + " has been unblocked", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeFriend() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Friend")
                .setMessage("Are you sure you want to remove " + receiver.getDisplayName() + " from your friends?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        String currentUserId = currentUser.getUid();
                        FirebaseUtil.removeFriend(currentUserId, receiverId);
                        Toast.makeText(this, receiver.getDisplayName() + " has been removed from friends", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearChatHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Chat")
                .setMessage("Are you sure you want to clear all messages in this chat?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    FirebaseUtil.clearChatHistory(chatId);
                    messages.clear();
                    items.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLoading(boolean show) {
        if (show) {
            // Show loading indicator in toolbar or use a different approach
            binding.toolbar.setEnabled(false);
        } else {
            binding.toolbar.setEnabled(true);
        }
    }

    private void showSendingIndicator(boolean show) {
        if (show) {
            binding.btnSend.setEnabled(false);
            binding.btnAttach.setEnabled(false);
            binding.etMessage.setEnabled(false);
            binding.typingIndicator.setVisibility(View.VISIBLE);
        } else {
            binding.btnSend.setEnabled(!isBlocked);
            binding.btnAttach.setEnabled(!isBlocked);
            binding.etMessage.setEnabled(!isBlocked);
            binding.typingIndicator.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Mark messages as read when chat becomes active
        if (isChatActive()) {
            markMessagesAsRead();
        }
        
        // Update user presence to online
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            FirebaseUtil.updateUserPresence(currentUser.getUid(), true);
            Log.d(TAG, "User presence set to ONLINE in ChatActivity");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Don't set user offline immediately when leaving chat - let the App class handle this
        // This allows the user to remain online when switching between chats or activities
        
        // Stop typing indicator
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (isTyping && currentUser != null) {
            isTyping = false;
            FirebaseUtil.setTyping(chatId, currentUser.getUid(), false);
            Log.d(TAG, "Stopped typing indicator in ChatActivity");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // Handle any future permission requests here if needed
        if (requestCode == 1001) {
            // Document permission result - for now, just show a message if denied
            if (grantResults.length > 0 && grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Document permission is required to share files", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "ChatActivity destroyed, removing listeners");

        if (messageListener != null) {
            FirebaseUtil.getMessagesRef(chatId).removeEventListener(messageListener);
        }
        if (typingListener != null) {
            FirebaseUtil.getTypingRef(chatId).removeEventListener(typingListener);
        }
        if (onlineStatusListener != null) {
            FirebaseUtil.getRealtimePresenceRef(receiverId).removeEventListener(onlineStatusListener);
        }
        if (messageStatusListener != null) {
            FirebaseUtil.getMessagesRef(chatId).removeEventListener(messageStatusListener);
        }

        if (isTyping) {
            FirebaseUtil.setTyping(chatId, FirebaseAuth.getInstance().getUid(), false);
        }
        
        // Release media player resources
        MediaPlayerUtil.getInstance().release();
    }
}