/*
package com.pingme.android.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.pingme.android.R;
import com.pingme.android.adapters.MessageSearchAdapter;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.util.ArrayList;
import java.util.List;

public class MessageSearchActivity extends AppCompatActivity implements MessageSearchAdapter.OnMessageClickListener {
    private RecyclerView recyclerViewMessages;
    private EditText searchEditText;
    private TextView emptyStateText;
    private MessageSearchAdapter adapter;
    private List<Message> allMessages = new ArrayList<>();
    private List<User> allUsers = new ArrayList<>();
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        initViews();
        setupRecyclerView();
        loadUsers();
        setupSearchListener();
    }

    private void initViews() {
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        searchEditText = findViewById(R.id.searchEditText);
        emptyStateText = findViewById(R.id.emptyStateText);
    }

    private void setupRecyclerView() {
        adapter = new MessageSearchAdapter(this, new ArrayList<>(), allUsers, this);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMessages.setAdapter(adapter);
    }

    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    adapter.updateMessages(new ArrayList<>());
                    showEmptyState(true);
                } else {
                    searchMessages(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadUsers() {
        FirebaseUtil.getUsersRef().addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                allUsers.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        allUsers.add(user);
                    }
                }
                // Now load messages after users are loaded
                loadMessages();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    private void loadMessages() {
        // Load messages from all chats
        FirebaseUtil.getChatsRef().addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                allMessages.clear();
                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId != null) {
                        loadMessagesFromChat(chatId);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    private void loadMessagesFromChat(String chatId) {
        FirebaseUtil.getChatMessagesRef(chatId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    Message message = messageSnapshot.getValue(Message.class);
                    if (message != null && !allMessages.contains(message)) {
                        allMessages.add(message);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    private void searchMessages(String query) {
        List<Message> filteredMessages = new ArrayList<>();
        
        for (Message message : allMessages) {
            if (message.getText() != null && 
                message.getText().toLowerCase().contains(query.toLowerCase())) {
                filteredMessages.add(message);
            }
        }
        
        adapter.updateMessages(filteredMessages);
        adapter.setSearchQuery(query);
        showEmptyState(filteredMessages.isEmpty());
    }

    private void showEmptyState(boolean show) {
        if (show) {
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerViewMessages.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerViewMessages.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onMessageClick(Message message) {
        // Navigate to the specific chat where this message was sent
        // You can implement this based on your navigation structure
        // For now, just show a toast or navigate back
        finish();
    }
}
*/