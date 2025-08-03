package com.pingme.android.activities;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pingme.android.adapters.MessageAdapter;
import com.pingme.android.databinding.ActivityChatBinding;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private String chatId;
    private String receiverId;
    private MessageAdapter adapter;
    private List<Message> messages = new ArrayList<>();
    private ListenerRegistration messageListener;
    private User receiver;

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
        loadReceiver();
        setupMessageListener();

        binding.btnSend.setOnClickListener(v -> sendMessage());
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(messages);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadReceiver() {
        FirestoreUtil.getUserRef(receiverId).get().addOnSuccessListener(snapshot -> {
            receiver = snapshot.toObject(User.class);
            if (receiver != null) {
                binding.toolbar.setTitle(receiver.getName());
                updateUserStatus();
            }
        });
    }

    private void updateUserStatus() {
        if (receiver != null) {
            if (receiver.isOnline()) {
                binding.toolbar.setSubtitle("online");
            } else {
                long lastSeen = receiver.getLastSeen();
                if (lastSeen > 0) {
                    String timeAgo = DateUtils.getRelativeTimeSpanString(lastSeen, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
                    binding.toolbar.setSubtitle("last seen " + timeAgo);
                } else {
                    binding.toolbar.setSubtitle("offline");
                }
            }
        }
    }

    private void setupMessageListener() {
        Query query = FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING);

        messageListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) return;

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                if (dc.getType() == DocumentChange.Type.ADDED) {
                    Message message = dc.getDocument().toObject(Message.class);
                    message.setId(dc.getDocument().getId());
                    messages.add(message);
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1);

                    // Update message status to delivered
                    if (!message.getSenderId().equals(FirebaseAuth.getInstance().getUid())) {
                        updateMessageStatus(message.getId(), Message.STATUS_DELIVERED);
                    }
                }
            }
        });
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        String senderId = FirebaseAuth.getInstance().getUid();
        Message message = new Message(
                senderId,
                text,
                System.currentTimeMillis(),
                Message.STATUS_SENT,
                Message.TYPE_TEXT
        );

        DocumentReference messageRef = FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document();

        messageRef.set(message)
                .addOnSuccessListener(aVoid -> {
                    binding.etMessage.setText("");
                    updateChatLastMessage(text);
                    message.setId(messageRef.getId());
                    updateMessageStatus(message.getId(), Message.STATUS_SENT);
                });
    }

    private void updateChatLastMessage(String text) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", text);
        updates.put("lastMessageTimestamp", System.currentTimeMillis());
        updates.put("lastMessageSenderId", FirebaseAuth.getInstance().getUid());

        FirebaseFirestore.getInstance().collection("chats")
                .document(chatId)
                .update(updates);
    }

    private void updateMessageStatus(String messageId, int status) {
        FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .update("status", status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener != null) {
            messageListener.remove();
        }
    }
}