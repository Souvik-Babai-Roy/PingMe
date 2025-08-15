package com.pingme.android.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pingme.android.R;
import com.pingme.android.adapters.StatusViewerAdapter;
import com.pingme.android.databinding.ActivityStatusViewerBinding;
import com.pingme.android.models.Status;
import com.pingme.android.utils.FirebaseUtil;

import java.util.ArrayList;
import java.util.List;

public class StatusViewerActivity extends AppCompatActivity {
    private static final String TAG = "StatusViewerActivity";

    private ActivityStatusViewerBinding binding;
    private StatusViewerAdapter adapter;
    private List<Status> statusList;
    private int initialPosition;
    private String currentUserId;

    // Intent extras
    public static final String EXTRA_STATUS_ID = "status_id";
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_INITIAL_POSITION = "initial_position";

    public static Intent createIntent(Context context, String statusId, String userId, int initialPosition) {
        Intent intent = new Intent(context, StatusViewerActivity.class);
        intent.putExtra(EXTRA_STATUS_ID, statusId);
        intent.putExtra(EXTRA_USER_ID, userId);
        intent.putExtra(EXTRA_INITIAL_POSITION, initialPosition);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        binding = ActivityStatusViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUI();
        loadStatuses();
    }

    private void setupUI() {
        // Setup back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Setup ViewPager
        statusList = new ArrayList<>();
        adapter = new StatusViewerAdapter(this, statusList, new StatusViewerAdapter.StatusViewerListener() {
            @Override
            public void onStatusViewed(Status status) {
                markStatusAsViewed(status);
            }

            @Override
            public void onStatusExpired(Status status) {
                // Remove expired status from list
                int position = statusList.indexOf(status);
                if (position != -1) {
                    statusList.remove(position);
                    adapter.notifyItemRemoved(position);
                    
                    if (statusList.isEmpty()) {
                        finish();
                    }
                }
            }
        });

        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);

        // Handle page changes
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateStatusInfo(position);
            }
        });

        // Handle back press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void loadStatuses() {
        String targetUserId = getIntent().getStringExtra(EXTRA_USER_ID);
        String initialStatusId = getIntent().getStringExtra(EXTRA_STATUS_ID);
        initialPosition = getIntent().getIntExtra(EXTRA_INITIAL_POSITION, 0);

        if (targetUserId == null) {
            Toast.makeText(this, "Invalid status data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load statuses for the target user
        FirebaseFirestore.getInstance()
                .collection("statuses")
                .whereEqualTo("userId", targetUserId)
                .whereGreaterThan("timestamp", System.currentTimeMillis() - (24 * 60 * 60 * 1000))
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    statusList.clear();
                    
                    for (com.google.firebase.firestore.DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        Status status = document.toObject(Status.class);
                        if (status != null && !status.isExpired()) {
                            status.setId(document.getId());
                            statusList.add(status);
                        }
                    }

                    if (statusList.isEmpty()) {
                        Toast.makeText(this, "No active statuses found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    adapter.notifyDataSetChanged();

                    // Set initial position
                    if (initialStatusId != null) {
                        for (int i = 0; i < statusList.size(); i++) {
                            if (initialStatusId.equals(statusList.get(i).getId())) {
                                binding.viewPager.setCurrentItem(i, false);
                                break;
                            }
                        }
                    } else if (initialPosition < statusList.size()) {
                        binding.viewPager.setCurrentItem(initialPosition, false);
                    }

                    updateStatusInfo(binding.viewPager.getCurrentItem());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load statuses: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void updateStatusInfo(int position) {
        if (position >= 0 && position < statusList.size()) {
            Status status = statusList.get(position);
            
            // Update user info
            if (status.getUserName() != null) {
                binding.textUserName.setText(status.getUserName());
            }
            
            // Update timestamp
            binding.textTimestamp.setText(status.getFormattedTimeAgo());
            
            // Update progress indicator
            binding.progressIndicator.setMax(statusList.size());
            binding.progressIndicator.setProgress(position + 1);
            
            // Mark as viewed
            markStatusAsViewed(status);
        }
    }

    private void markStatusAsViewed(Status status) {
        if (currentUserId == null || status == null || status.getId() == null) {
            return;
        }

        // Add viewer to status
        FirebaseFirestore.getInstance()
                .collection("statuses")
                .document(status.getId())
                .update("viewers." + currentUserId, System.currentTimeMillis())
                .addOnFailureListener(e -> {
                    // Silently fail - not critical
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}