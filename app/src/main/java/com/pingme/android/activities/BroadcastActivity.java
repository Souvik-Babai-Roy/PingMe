package com.pingme.android.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.pingme.android.adapters.ContactSelectAdapter;
import com.pingme.android.databinding.ActivityBroadcastBinding;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.List;

public class BroadcastActivity extends AppCompatActivity {
    private ActivityBroadcastBinding binding;
    private ContactSelectAdapter adapter;
    private List<User> contactList = new ArrayList<>();
    private List<User> selectedContacts = new ArrayList<>();
    private String currentUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBroadcastBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        setupToolbar();
        setupRecyclerView();
        loadContacts();
        setupSendButton();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("New Broadcast");
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupRecyclerView() {
        adapter = new ContactSelectAdapter(contactList, user -> {
            if (selectedContacts.contains(user)) {
                selectedContacts.remove(user);
            } else {
                selectedContacts.add(user);
            }
            binding.tvSelectedCount.setText(selectedContacts.size() + " selected");
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void loadContacts() {
        if (currentUserId == null) return;
        FirestoreUtil.getFriendsRef(currentUserId).get().addOnSuccessListener(querySnapshot -> {
            contactList.clear();
            for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                User user = doc.toObject(User.class);
                if (user != null) {
                    user.setId(doc.getId());
                    contactList.add(user);
                }
            }
            adapter.notifyDataSetChanged();
        });
    }

    private void setupSendButton() {
        binding.btnSend.setOnClickListener(v -> {
            String message = binding.etMessage.getText().toString().trim();
            if (message.isEmpty()) {
                Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedContacts.isEmpty()) {
                Toast.makeText(this, "Select at least one contact", Toast.LENGTH_SHORT).show();
                return;
            }
            sendBroadcastMessage(message);
        });
    }

    private void sendBroadcastMessage(String message) {
        binding.progressBar.setVisibility(View.VISIBLE);
        for (User user : selectedContacts) {
            String chatId = FirestoreUtil.generateChatId(currentUserId, user.getId());
            FirestoreUtil.sendTextMessage(chatId, currentUserId, user.getId(), message, true);
        }
        binding.progressBar.setVisibility(View.GONE);
        Toast.makeText(this, "Broadcast sent to " + selectedContacts.size() + " contacts", Toast.LENGTH_SHORT).show();
        finish();
    }
}