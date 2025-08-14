package com.pingme.android.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pingme.android.R;
import com.pingme.android.databinding.DialogPersonalNameBinding;
import com.pingme.android.models.User;

public class PersonalNameDialog extends Dialog {
    
    public interface OnPersonalNameSetListener {
        void onPersonalNameSet(String personalName);
    }
    
    private final Context context;
    private final User friend;
    private final OnPersonalNameSetListener listener;
    private DialogPersonalNameBinding binding;
    
    public PersonalNameDialog(@NonNull Context context, User friend, OnPersonalNameSetListener listener) {
        super(context);
        this.context = context;
        this.friend = friend;
        this.listener = listener;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DialogPersonalNameBinding.inflate(LayoutInflater.from(context));
        setContentView(binding.getRoot());
        
        // Setup dialog appearance
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        
        setupUI();
        setupClickListeners();
    }
    
    private void setupUI() {
        // Set current personal name if exists
        if (friend.getPersonalName() != null && !friend.getPersonalName().trim().isEmpty()) {
            binding.etPersonalName.setText(friend.getPersonalName());
        } else {
            binding.etPersonalName.setText(friend.getName());
        }
        
        // Set friend's current name as hint
        binding.tvCurrentName.setText(friend.getDisplayName());
        
        // Set friend's profile photo if available
        if (friend.getImageUrl() != null && !friend.getImageUrl().trim().isEmpty()) {
            binding.ivFriendPhoto.setVisibility(View.VISIBLE);
            // Load image with Glide (you'll need to add this dependency)
            // Glide.with(context).load(friend.getImageUrl()).into(binding.ivFriendPhoto);
        } else {
            binding.ivFriendPhoto.setVisibility(View.GONE);
        }
    }
    
    private void setupClickListeners() {
        binding.btnSave.setOnClickListener(v -> savePersonalName());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnRemove.setOnClickListener(v -> removePersonalName());
    }
    
    private void savePersonalName() {
        String personalName = binding.etPersonalName.getText() != null ? 
            binding.etPersonalName.getText().toString().trim() : "";
        
        if (personalName.isEmpty()) {
            binding.etPersonalName.setError("Please enter a name");
            return;
        }
        
        // Save personal name to Firestore
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        if (currentUserId == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Save to user's friends collection with personal name
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUserId)
            .collection("friends")
            .document(friend.getId())
            .update("personalName", personalName)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(context, "Personal name saved", Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onPersonalNameSet(personalName);
                }
                dismiss();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(context, "Failed to save personal name", Toast.LENGTH_SHORT).show();
            });
    }
    
    private void removePersonalName() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        if (currentUserId == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Remove personal name from Firestore
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUserId)
            .collection("friends")
            .document(friend.getId())
            .update("personalName", null)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(context, "Personal name removed", Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onPersonalNameSet(null);
                }
                dismiss();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(context, "Failed to remove personal name", Toast.LENGTH_SHORT).show();
            });
    }
}