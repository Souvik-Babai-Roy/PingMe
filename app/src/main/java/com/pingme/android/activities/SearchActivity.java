package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.pingme.android.adapters.SearchResultAdapter;
import com.pingme.android.databinding.ActivitySearchBinding;
import com.pingme.android.models.Message;
import com.pingme.android.utils.FirebaseUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";

    private ActivitySearchBinding binding;
    private SearchResultAdapter adapter;
    private List<FirebaseUtil.SearchResult> searchResults = new ArrayList<>();
    private String currentUserId;
    private long startDate = 0;
    private long endDate = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        setupSearchInput();
        setupDateFilters();
        setupClickListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Search");
        }
    }

    private void setupRecyclerView() {
        adapter = new SearchResultAdapter(searchResults, result -> {
            // Handle search result click - open the chat and navigate to specific message
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("chatId", result.getChatId());

            // Determine the other participant from chatId instead of using senderId (which may be current user)
            String otherUserId = null;
            if (result.getChatId() != null && result.getChatId().contains("_")) {
                String[] parts = result.getChatId().split("_");
                if (parts.length == 2) {
                    String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                    if (uid != null) {
                        otherUserId = parts[0].equals(uid) ? parts[1] : (parts[1].equals(uid) ? parts[0] : parts[0]);
                    }
                }
            }
            if (otherUserId == null && result.getMessage() != null) {
                // Fallback: if parsing failed, use the opposite of sender where possible
                String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                String sender = result.getMessage().getSenderId();
                otherUserId = (uid != null && sender != null && sender.equals(uid)) ? uid : sender;
            }
            intent.putExtra("receiverId", otherUserId);

            intent.putExtra("messageId", result.getMessageId());
            intent.putExtra("highlightMessage", true);
            intent.putExtra("searchQuery", binding.etSearch.getText().toString().trim());
            startActivity(intent);
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupSearchInput() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    performSearch(query);
                } else {
                    clearResults();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupDateFilters() {
        // Set up date filter chips
        binding.chipToday.setOnClickListener(v -> setDateFilter(0)); // Today
        binding.chipYesterday.setOnClickListener(v -> setDateFilter(1)); // Yesterday
        binding.chipWeek.setOnClickListener(v -> setDateFilter(7)); // This week
        binding.chipMonth.setOnClickListener(v -> setDateFilter(30)); // This month
        binding.chipAll.setOnClickListener(v -> setDateFilter(-1)); // All time
    }

    private void setupClickListeners() {
        binding.btnSearch.setOnClickListener(v -> {
            String query = binding.etSearch.getText().toString().trim();
            if (!query.isEmpty()) {
                performSearch(query);
            }
        });

        binding.btnClear.setOnClickListener(v -> {
            binding.etSearch.setText("");
            clearResults();
        });
    }

    private void setDateFilter(int days) {
        Calendar calendar = Calendar.getInstance();
        endDate = calendar.getTimeInMillis();

        if (days == 0) {
            // Today
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            startDate = calendar.getTimeInMillis();
        } else if (days == 1) {
            // Yesterday
            calendar.add(Calendar.DAY_OF_YEAR, -1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            startDate = calendar.getTimeInMillis();
            
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            calendar.add(Calendar.MILLISECOND, -1);
            endDate = calendar.getTimeInMillis();
        } else if (days > 0) {
            // Specific number of days
            calendar.add(Calendar.DAY_OF_YEAR, -days);
            startDate = calendar.getTimeInMillis();
        } else {
            // All time
            startDate = 0;
        }

        // Update chip states
        updateChipStates(days);
        
        // Re-search if there's a current query
        String query = binding.etSearch.getText().toString().trim();
        if (!query.isEmpty()) {
            performSearch(query);
        }
    }

    private void updateChipStates(int selectedDays) {
        binding.chipToday.setChecked(selectedDays == 0);
        binding.chipYesterday.setChecked(selectedDays == 1);
        binding.chipWeek.setChecked(selectedDays == 7);
        binding.chipMonth.setChecked(selectedDays == 30);
        binding.chipAll.setChecked(selectedDays == -1);
    }

    private void performSearch(String query) {
        if (query.length() < 2) return;

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);

        FirebaseUtil.searchAllChats(currentUserId, query, new FirebaseUtil.SearchCallback() {
            @Override
            public void onSearchComplete(List<FirebaseUtil.SearchResult> results) {
                binding.progressBar.setVisibility(View.GONE);
                
                // Filter results by date range
                List<FirebaseUtil.SearchResult> filteredResults = new ArrayList<>();
                for (FirebaseUtil.SearchResult result : results) {
                    Message message = result.getMessage();
                    if (message.getTimestamp() >= startDate && message.getTimestamp() <= endDate) {
                        filteredResults.add(result);
                    }
                }

                searchResults.clear();
                searchResults.addAll(filteredResults);
                adapter.setSearchQuery(query);
                adapter.notifyDataSetChanged();

                if (searchResults.isEmpty()) {
                    binding.emptyState.setVisibility(View.VISIBLE);
                    binding.emptyStateText.setText("No messages found for \"" + query + "\"");
                } else {
                    binding.emptyState.setVisibility(View.GONE);
                }

                updateSearchStats(filteredResults.size());
            }

            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(SearchActivity.this, "Search failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearResults() {
        searchResults.clear();
        adapter.notifyDataSetChanged();
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyStateText.setText("Enter a search term to find messages");
        binding.searchStats.setVisibility(View.GONE);
    }

    private void updateSearchStats(int resultCount) {
        binding.searchStats.setVisibility(View.VISIBLE);
        binding.tvResultCount.setText(resultCount + " result" + (resultCount != 1 ? "s" : ""));
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String dateRange = "";
        if (startDate > 0) {
            dateRange = " from " + dateFormat.format(new Date(startDate));
            if (endDate < System.currentTimeMillis()) {
                dateRange += " to " + dateFormat.format(new Date(endDate));
            }
        }
        binding.tvDateRange.setText(dateRange);
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