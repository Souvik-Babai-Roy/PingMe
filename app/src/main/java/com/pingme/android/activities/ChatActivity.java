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
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        String currentUserId = firebaseUser.getUid();
        FirestoreUtil.getUserRef(currentUserId).get().addOnSuccessListener(snapshot -> {
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

        FirestoreUtil.getUserRef(receiverId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                receiver = snapshot.toObject(User.class);
                if (receiver != null) {
                    receiver.setId(snapshot.getId());
                    updateToolbarWithReceiver();
                    updateAdapterPrivacySettings();

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
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.e(TAG, "User not authenticated");
            return;
        }
        
        String currentUserId = firebaseUser.getUid();

        // Check both blocking directions
        FirestoreUtil.checkIfBlocked(currentUserId, receiverId, currentUserBlocked -> {
            FirestoreUtil.checkIfBlocked(receiverId, currentUserId, receiverBlocked -> {
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

        binding.tvUserName.setText(receiver.getName());

        // Only show profile if not blocked and privacy allows
        if (!isBlocked && receiver.isProfilePhotoEnabled() &&
                receiver.getImageUrl() != null && !receiver.getImageUrl().isEmpty()) {

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

        FirestoreUtil.getChatRef(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "Chat doesn't exist, creating new chat: " + chatId);
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

        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                Message message = dataSnapshot.getValue(Message.class);
                if (message != null) {
                    message.setId(dataSnapshot.getKey());

                    FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (firebaseUser == null) {
                        Log.e(TAG, "User not authenticated");
                        return;
                    }
                    
                    String currentUserId = firebaseUser.getUid();
                    if (!message.getSenderId().equals(currentUserId)) {
                        // Check if current user blocked the sender
                        FirestoreUtil.checkIfBlocked(currentUserId, message.getSenderId(), blocked -> {
                            if (blocked) {
                                Log.d(TAG, "Message filtered - sender is blocked");
                                return;
                            }
                            processIncomingMessage(message);
                        });
                    } else {
                        processIncomingMessage(message);
                    }
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
                    if (!updatedMessage.getSenderId().equals(currentUserId)) {
                        FirestoreUtil.checkIfBlocked(currentUserId, updatedMessage.getSenderId(), blocked -> {
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

        FirestoreUtil.getMessagesRef(chatId).addChildEventListener(messageListener);
    }

    private void processIncomingMessage(Message message) {
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
                updateMessageStatus(message.getId(), Message.STATUS_DELIVERED);
            }
        }
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
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser != null) {
                            FirestoreUtil.setTyping(chatId, currentUser.getUid(), isTyping);
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

        String[] options = {"Image", "Video", "Audio", "Document", "Camera"};

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
                        case 2: // Audio
                            audioPickerLauncher.launch("audio/*");
                            break;
                        case 3: // Document
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
                        FirestoreUtil.editMessage(chatId, message.getId(), newText);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    private void deleteMessageForMe(Message message) {
        String currentUserId = FirebaseAuth.getInstance().getUid();
        FirestoreUtil.deleteMessageForUser(chatId, message.getId(), currentUserId);
    }

    private void deleteMessageForEveryone(Message message) {
        String currentUserId = FirebaseAuth.getInstance().getUid();
        FirestoreUtil.deleteMessageForEveryone(chatId, message.getId(), currentUserId);
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

    private void handleAudioSelection(Uri audioUri) {
        if (audioUri != null && !isBlocked) {
            showLoading(true);
            CloudinaryUtil.getInstance()
                    .uploadAudio(audioUri, chatId, this)
                    .thenAccept(audioUrl -> runOnUiThread(() -> {
                        showLoading(false);
                        sendAudioMessage(audioUrl);
                    }))
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload audio", Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    });
        }
    }

    private void handleDocumentSelection(Uri documentUri) {
        if (documentUri != null && !isBlocked) {
            showLoading(true);
            CloudinaryUtil.getInstance()
                    .uploadDocument(documentUri, "chat_documents/" + chatId, this)
                    .thenAccept(documentUrl -> runOnUiThread(() -> {
                        showLoading(false);
                        sendDocumentMessage(documentUrl);
                    }))
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload document", Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    });
        }
    }

    private void handleCameraCapture(Boolean success) {
        if (success && cameraImageUri != null && !isBlocked) {
            showLoading(true);
            CloudinaryUtil.getInstance()
                    .uploadImage(cameraImageUri, "chat_images/" + chatId, this)
                    .thenAccept(imageUrl -> runOnUiThread(() -> {
                        showLoading(false);
                        sendImageMessage(imageUrl);
                    }))
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Failed to upload camera image", Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    });
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
        if (isBlocked) {
            Toast.makeText(this, "You cannot send messages to blocked users", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        Log.d(TAG, "Sending text message: " + text);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();
        FirestoreUtil.sendMessageToRealtime(chatId, senderId, text, "text", null);
        binding.etMessage.setText("");
        FirestoreUtil.setTyping(chatId, senderId, false);
        isTyping = false;
    }

    private void sendImageMessage(String imageUrl) {
        if (isBlocked) return;

        Log.d(TAG, "Sending image message: " + imageUrl);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("imageUrl", imageUrl);

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "📷 Photo", "image", mediaData);
    }

    private void sendVideoMessage(String videoUrl) {
        if (isBlocked) return;

        Log.d(TAG, "Sending video message: " + videoUrl);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("videoUrl", videoUrl);
        mediaData.put("thumbnailUrl", "");

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "🎥 Video", "video", mediaData);
    }

    private void sendAudioMessage(String audioUrl) {
        if (isBlocked) return;

        Log.d(TAG, "Sending audio message: " + audioUrl);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("audioUrl", audioUrl);

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "🎵 Audio", "audio", mediaData);
    }

    private void sendDocumentMessage(String documentUrl) {
        if (isBlocked) return;

        Log.d(TAG, "Sending document message: " + documentUrl);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderId = currentUser.getUid();

        Map<String, Object> mediaData = new HashMap<>();
        mediaData.put("fileUrl", documentUrl);
        mediaData.put("fileName", "Document");

        FirestoreUtil.sendMessageToRealtime(chatId, senderId, "📄 Document", "document", mediaData);
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
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                FirestoreUtil.markMessagesAsRead(chatId, currentUser.getUid());
            }
        }
    }

    private void blockUser() {
        new AlertDialog.Builder(this)
                .setTitle("Block User")
                .setMessage("Are you sure you want to block " + receiver.getName() + "? You won't receive messages from them.")
                .setPositiveButton("Block", (dialog, which) -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        String currentUserId = currentUser.getUid();
                        FirestoreUtil.blockUser(currentUserId, receiverId);
                        isBlocked = true;
                        updateInputState();
                        Toast.makeText(this, receiver.getName() + " has been blocked", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unblockUser() {
        new AlertDialog.Builder(this)
                .setTitle("Unblock User")
                .setMessage("Are you sure you want to unblock " + receiver.getName() + "?")
                .setPositiveButton("Unblock", (dialog, which) -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        String currentUserId = currentUser.getUid();
                        FirestoreUtil.unblockUser(currentUserId, receiverId);
                        isBlocked = false;
                        updateInputState();
                        Toast.makeText(this, receiver.getName() + " has been unblocked", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeFriend() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Friend")
                .setMessage("Are you sure you want to remove " + receiver.getName() + " from your friends?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        String currentUserId = currentUser.getUid();
                        FirestoreUtil.removeFriend(currentUserId, receiverId);
                        Toast.makeText(this, receiver.getName() + " has been removed from friends", Toast.LENGTH_SHORT).show();
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
                    FirestoreUtil.clearChatHistory(chatId);
                    messages.clear();
                    items.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLoading(boolean show) {
        binding.btnSend.setEnabled(!show && !isBlocked);
        binding.btnAttach.setEnabled(!show && !isBlocked);
        binding.etMessage.setEnabled(!show && !isBlocked);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String currentUserId = currentUser.getUid();
            FirestoreUtil.updateUserPresence(currentUserId, true);
        }
        markMessagesAsRead();
    }

    @Override
    protected void onPause() {
        super.onPause();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String currentUserId = currentUser.getUid();
            if (isTyping) {
                FirestoreUtil.setTyping(chatId, currentUserId, false);
                isTyping = false;
            }
            FirestoreUtil.updateUserPresence(currentUserId, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "ChatActivity destroyed, removing listeners");

        if (messageListener != null) {
            FirestoreUtil.getMessagesRef(chatId).removeEventListener(messageListener);
        }
        if (typingListener != null) {
            FirestoreUtil.getTypingRef(chatId).removeEventListener(typingListener);
        }
        if (onlineStatusListener != null) {
            FirestoreUtil.getRealtimePresenceRef(receiverId).removeEventListener(onlineStatusListener);
        }

        if (isTyping) {
            FirestoreUtil.setTyping(chatId, FirebaseAuth.getInstance().getUid(), false);
        }
    }
}