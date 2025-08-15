package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.pingme.android.R;
import com.pingme.android.adapters.MessageSearchAdapter;
import com.pingme.android.databinding.ActivityMessageSearchBinding;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

import java.util.ArrayList;
import java.util.List;

public class MessageSearchActivity extends AppCompatActivity implements MessageSearchAdapter.OnMessageClickListener {
    private static final String TAG = "MessageSearchActivity";
    
    private ActivityMessageSearchBinding binding;
    private MessageSearchAdapter adapter;
    private String currentUserId;
    private List<Message> searchResults = new ArrayList<>();
    private List<User> usersList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMessageSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.e(TAG, "User not authenticated");
            finish();
            return;
        }
        currentUserId = firebaseUser.getUid();
        
        setupToolbar();
        setupRecyclerView();
        setupSearchFunctionality();
        loadUsers();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Search Messages");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupRecyclerView() {
        adapter = new MessageSearchAdapter(this, searchResults, usersList, this);
        binding.recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewMessages.setAdapter(adapter);
    }

    private void setupSearchFunctionality() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 2) {
                    searchMessages(s.toString());
                } else {
                    clearSearchResults();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadUsers() {
        // Load all users to get names for search results
        FirestoreUtil.getUsersRef()
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    usersList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setId(doc.getId());
                            usersList.add(user);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading users", e);
                });
    }

    private void searchMessages(String query) {
        showLoading(true);
        
        // Search in all chats for the current user
        FirestoreUtil.getUserChatsRef(currentUserId)
                .get()
                .addOnSuccessListener(chatsSnapshot -> {
                    searchResults.clear();
                    
                    for (DocumentSnapshot chatDoc : chatsSnapshot.getDocuments()) {
                        String chatId = chatDoc.getId();
                        String otherUserId = chatDoc.getString("otherUserId");
                        
                        // Search messages in this chat
                        FirestoreUtil.getChatMessagesRef(chatId)
                                .whereGreaterThanOrEqualTo("text", query)
                                .whereLessThanOrEqualTo("text", query + '\uf8ff')
                                .limit(10)
                                .get()
                                .addOnSuccessListener(messagesSnapshot -> {
                                    for (DocumentSnapshot messageDoc : messagesSnapshot.getDocuments()) {
                                        Message message = messageDoc.toObject(Message.class);
                                        if (message != null) {
                                            message.setId(messageDoc.getId());
                                            message.setChatId(chatId);
                                            message.setOtherUserId(otherUserId);
                                            searchResults.add(message);
                                        }
                                    }
                                    
                                    // Also search for case-insensitive matches
                                    searchCaseInsensitive(query, chatId, otherUserId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error searching messages in chat " + chatId, e);
                                });
                    }
                    
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user chats", e);
                    showLoading(false);
                });
    }

    private void searchCaseInsensitive(String query, String chatId, String otherUserId) {
        String lowerQuery = query.toLowerCase();
        
        FirestoreUtil.getChatMessagesRef(chatId)
                .get()
                .addOnSuccessListener(messagesSnapshot -> {
                    for (DocumentSnapshot messageDoc : messagesSnapshot.getDocuments()) {
                        Message message = messageDoc.toObject(Message.class);
                        if (message != null && message.getText() != null && 
                            message.getText().toLowerCase().contains(lowerQuery)) {
                            
                            // Check if this message is already in results
                            boolean alreadyExists = false;
                            for (Message existingMessage : searchResults) {
                                if (existingMessage.getId().equals(message.getId())) {
                                    alreadyExists = true;
                                    break;
                                }
                            }
                            
                            if (!alreadyExists) {
                                message.setId(messageDoc.getId());
                                message.setChatId(chatId);
                                message.setOtherUserId(otherUserId);
                                searchResults.add(message);
                            }
                        }
                    }
                    
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });
    }

    private void clearSearchResults() {
        searchResults.clear();
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (searchResults.isEmpty()) {
            binding.emptyStateText.setText("No messages found matching your search.");
            binding.emptyStateText.setVisibility(View.VISIBLE);
        } else {
            binding.emptyStateText.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onMessageClick(Message message) {
        // Open the chat with this message
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("userId", message.getOtherUserId());
        intent.putExtra("messageId", message.getId());
        startActivity(intent);
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