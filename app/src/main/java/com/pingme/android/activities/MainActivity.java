package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.pingme.android.fragments.StatusFragment;
import com.pingme.android.utils.FirestoreUtil;
import com.pingme.android.utils.PreferenceUtils;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private String currentUserId;
    private ViewPagerAdapter adapter;

    // FIXED: Add result launcher for settings/profile activities
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
        
        try {
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            mAuth = FirebaseAuth.getInstance();
            
            // FIXED: Check for null user to prevent crash
            if (mAuth.getCurrentUser() == null) {
                Log.w(TAG, "User not authenticated, redirecting to auth");
                // User not authenticated, redirect to auth
                startActivity(new Intent(this, AuthActivity.class));
                finish();
                return;
            }
            
            currentUserId = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "MainActivity created for user: " + currentUserId);

            // FIXED: Apply theme and sync preferences before setting up UI
            applyCurrentTheme();
            syncUserPreferences();

            setupToolbar();
            setupViewPager();
            setupFAB();
            updateUserPresence();
            updateFCMToken();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            // If there's any error, redirect to auth
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }
    }

    // FIXED: Apply current theme
    private void applyCurrentTheme() {
        try {
            String savedTheme = PreferenceUtils.getThemePreference(this);
            PreferenceUtils.applyTheme(savedTheme);
        } catch (Exception e) {
            Log.e(TAG, "Error applying theme", e);
        }
    }

    // FIXED: Sync user preferences from Firestore
    private void syncUserPreferences() {
        try {
            PreferenceUtils.syncPreferencesFromFirestore(this);
        } catch (Exception e) {
            Log.e(TAG, "Error syncing preferences", e);
        }
    }

    private void setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("PingMe");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar", e);
        }
    }

    private void setupViewPager() {
        try {
            adapter = new ViewPagerAdapter(this);
            
            // FIXED: Create fragments with proper error handling
            ChatsFragment chatsFragment = new ChatsFragment();
            StatusFragment statusFragment = new StatusFragment();
            CallsFragment callsFragment = new CallsFragment();
            
            adapter.addFragment(chatsFragment, "CHATS");
            adapter.addFragment(statusFragment, "STATUS");
            adapter.addFragment(callsFragment, "CALLS");

            binding.viewPager.setAdapter(adapter);
            // FIXED: Reduce offscreen limit to improve memory usage and tab switching
            binding.viewPager.setOffscreenPageLimit(1);

            new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                    (tab, position) -> {
                        try {
                            tab.setText(adapter.getPageTitle(position));
                        } catch (Exception e) {
                            Log.e(TAG, "Error setting tab title", e);
                        }
                    }
            ).attach();

            // FIXED: Add page change callback to refresh data when switching tabs
            binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    
                    try {
                        // Refresh fragment data when switching to it
                        if (position == 0) { // Chats fragment
                            // Get the fragment and refresh if it exists
                            ChatsFragment chatsFragment = (ChatsFragment) adapter.getFragment(0);
                            if (chatsFragment != null && chatsFragment.isAdded()) {
                                chatsFragment.refreshChats();
                            }
                        }
                        
                        // Update FAB based on current tab (existing logic)
                        switch (position) {
                            case 0: // Chats
                                binding.fab.setImageResource(R.drawable.ic_chat_add);
                                binding.fab.setContentDescription("Add Friend");
                                break;
                            case 1: // Status
                                binding.fab.setImageResource(R.drawable.ic_camera);
                                binding.fab.setContentDescription("Add Status");
                                break;
                            case 2: // Calls
                                binding.fab.setImageResource(R.drawable.ic_baseline_person_24);
                                binding.fab.setContentDescription("New Call");
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in page change callback", e);
                    }
                }
            });

            binding.viewPager.setCurrentItem(0);
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up ViewPager", e);
        }
    }

    private void setupFAB() {
        try {
            // FAB for adding friends (visible on Chats tab)
            binding.fab.setOnClickListener(v -> {
                try {
                    int currentTab = binding.viewPager.getCurrentItem();
                    switch (currentTab) {
                        case 0: // Chats tab
                            startActivity(new Intent(this, AddFriendActivity.class));
                            break;
                        case 1: // Status tab
                            // TODO: Add status creation
                            break;
                        case 2: // Calls tab
                            // TODO: Start new call
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in FAB click", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up FAB", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            getMenuInflater().inflate(R.menu.main_menu, menu);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error creating options menu", e);
            return false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            int itemId = item.getItemId();

            if (itemId == R.id.menu_account) {
                // FIXED: Use result launcher instead of direct startActivity
                Intent intent = new Intent(this, EditProfileActivity.class);
                profileLauncher.launch(intent);
                return true;
            } else if (itemId == R.id.menu_settings) {
                // FIXED: Use result launcher for settings
                Intent intent = new Intent(this, SettingsActivity.class);
                settingsLauncher.launch(intent);
                return true;
            } else if (itemId == R.id.menu_logout) {
                logout();
                return true;
            }

            return super.onOptionsItemSelected(item);
        } catch (Exception e) {
            Log.e(TAG, "Error in options item selected", e);
            return false;
        }
    }

    private void logout() {
        try {
            // Set user offline before logout
            if (currentUserId != null) {
                FirestoreUtil.updateUserPresence(currentUserId, false);
            }

            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error during logout", e);
            // Force logout even if there's an error
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }
    }

    private void updateUserPresence() {
        try {
            if (currentUserId != null) {
                FirestoreUtil.updateUserPresence(currentUserId, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating user presence", e);
        }
    }

    private void setUserOffline() {
        try {
            if (currentUserId != null) {
                FirestoreUtil.updateUserPresence(currentUserId, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting user offline", e);
        }
    }

    private void updateFCMToken() {
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            String token = task.getResult();
                            if (currentUserId != null) {
                                FirestoreUtil.updateFCMToken(currentUserId, token);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error updating FCM token", e);
        }
    }

    private void recreateIfNeeded() {
        try {
            // Recreate activity if theme or preferences changed
            recreate();
        } catch (Exception e) {
            Log.e(TAG, "Error recreating activity", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Update user presence to online
            if (currentUserId != null) {
                FirestoreUtil.updateUserPresence(currentUserId, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Update user presence to offline
            setUserOffline();
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // Set user offline before destroying
            setUserOffline();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
}