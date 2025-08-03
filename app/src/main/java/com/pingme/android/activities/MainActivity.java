package com.pingme.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

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
    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser().getUid();

        setupToolbar();
        setupViewPager();
        setupFAB();
        updateUserPresence();
        updateFCMToken();

        PreferenceUtils.applyTheme(PreferenceUtils.getThemePreference(this));
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
        adapter.addFragment(new StatusFragment(), "STATUS");
        adapter.addFragment(new CallsFragment(), "CALLS");

        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setOffscreenPageLimit(3);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(adapter.getPageTitle(position))
        ).attach();

        binding.viewPager.setCurrentItem(0);
    }

    private void setupFAB() {
        // FAB for adding friends (visible on Chats tab)
        binding.fab.setOnClickListener(v -> {
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
        });

        // Update FAB icon based on current tab
        binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
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
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_account) {
            startActivity(new Intent(this, EditProfileActivity.class));
            return true;
        } else if (itemId == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (itemId == R.id.menu_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        // Set user offline before logout
        FirestoreUtil.updateUserPresence(currentUserId, false);

        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, AuthActivity.class));
        finish();
    }

    private void updateUserPresence() {
        if (currentUserId != null) {
            FirestoreUtil.updateUserPresence(currentUserId, true);
        }
    }

    private void setUserOffline() {
        if (currentUserId != null) {
            FirestoreUtil.updateUserPresence(currentUserId, false);
        }
    }

    private void updateFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String token = task.getResult();
                        FirestoreUtil.updateFCMToken(currentUserId, token);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUserPresence();
    }

    @Override
    protected void onPause() {
        super.onPause();
        setUserOffline();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setUserOffline();
    }
}