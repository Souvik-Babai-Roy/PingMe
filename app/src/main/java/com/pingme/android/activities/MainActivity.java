package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.pingme.android.R;
import com.pingme.android.adapters.ViewPagerAdapter;
import com.pingme.android.databinding.ActivityMainBinding;
import com.pingme.android.fragments.CallsFragment;
import com.pingme.android.fragments.ChatsFragment;
import com.pingme.android.utils.FirestoreUtil;
import com.pingme.android.utils.PreferenceUtils;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private String currentUserId;

    // Add result launcher for settings/profile activities
    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // Handle result from settings - theme might have changed
                        recreateIfNeeded();
                    });

    private final ActivityResultLauncher<Intent> profileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            // Profile updated - refresh if needed
                            recreateIfNeeded();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        // Check for null user to prevent crash
        if (mAuth.getCurrentUser() == null) {
            // User not authenticated, redirect to auth
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        currentUserId = mAuth.getCurrentUser().getUid();

        // Apply theme and sync preferences before setting up UI
        applyCurrentTheme();
        syncUserPreferences();

        setupToolbar();
        setupViewPager();
        setupFAB();
        updateUserPresence();
        updateFCMToken();
    }

    // Apply current theme
    private void applyCurrentTheme() {
        String savedTheme = PreferenceUtils.getThemePreference(this);
        PreferenceUtils.applyTheme(savedTheme);
    }

    // Sync user preferences from Firestore
    private void syncUserPreferences() {
        PreferenceUtils.syncPreferencesFromFirestore(this);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("PingMe");
        }
    }

    private void setupViewPager() {
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        adapter.addFragment(new ChatsFragment(), "CHATS");
        adapter.addFragment(new CallsFragment(), "CALLS");

        binding.viewPager.setAdapter(adapter);
        // Reduce offscreen limit to improve memory usage and tab switching
        binding.viewPager.setOffscreenPageLimit(1);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(adapter.getTabTitle(position))
        ).attach();
    }

    private void setupFAB() {
        binding.fab.setOnClickListener(v -> {
            int currentTab = binding.viewPager.getCurrentItem();
            if (currentTab == 0) { // Chats tab
                startActivity(new Intent(this, AddFriendActivity.class));
            } else if (currentTab == 1) { // Calls tab
                // Open contacts for calling
                startActivity(new Intent(this, SelectContactsActivity.class));
            }
        });
    }

    private void updateUserPresence() {
        if (currentUserId != null) {
            FirestoreUtil.updatePresence(currentUserId, true);
        }
    }

    private void updateFCMToken() {
        if (currentUserId == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    if (token != null) {
                        // Update token in user profile
                        FirestoreUtil.getUserRef(currentUserId)
                                .update("fcmToken", token);
                    }
                });
    }

    // Check if recreation is needed (theme change, etc.)
    private void recreateIfNeeded() {
        String currentTheme = PreferenceUtils.getThemePreference(this);
        String appliedTheme = PreferenceUtils.getAppliedTheme();
        
        if (!currentTheme.equals(appliedTheme)) {
            recreate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_search) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            settingsLauncher.launch(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_profile) {
            profileLauncher.launch(new Intent(this, EditProfileActivity.class));
            return true;
        } else if (id == R.id.action_blocked_users) {
            startActivity(new Intent(this, BlockedUsersActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUserPresence();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentUserId != null) {
            FirestoreUtil.updatePresence(currentUserId, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentUserId != null) {
            FirestoreUtil.updatePresence(currentUserId, false);
        }
        binding = null;
    }
}