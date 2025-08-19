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
import com.pingme.android.fragments.StatusFragment;
import com.pingme.android.utils.FirebaseUtil;
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

		// Start notification listener for this user
		FirebaseUtil.startNotificationListener(currentUserId);
		
		// Update user presence to online
		FirebaseUtil.updatePresence(currentUserId, true);

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
		// Set popup theme based on current theme
		binding.toolbar.setPopupTheme(R.style.PopupMenuStyle);
		
		// Ensure title text color is white for both themes
		binding.toolbar.setTitleTextColor(getColor(R.color.white));
		
		// Ensure menu icons are visible with proper tinting
		binding.toolbar.setOverflowIcon(getDrawable(R.drawable.ic_more_vert_24));
	}

	private void setupViewPager() {
		ViewPagerAdapter adapter = new ViewPagerAdapter(this);
		adapter.addFragment(new ChatsFragment(), "CHATS");
		adapter.addFragment(new StatusFragment(), "STATUS");
		adapter.addFragment(new CallsFragment(), "CALLS");

		binding.viewPager.setAdapter(adapter);
		// Reduce offscreen limit to improve memory usage and tab switching
		binding.viewPager.setOffscreenPageLimit(1);

		new TabLayoutMediator(binding.tabLayout, binding.viewPager,
				(tab, position) -> tab.setText(adapter.getTabTitle(position))
		).attach();

		// Add page change callback to update FAB
		binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				updateFABForCurrentTab(position);
			}
		});

		// Set initial FAB state
		updateFABForCurrentTab(0);
	}

	private void updateFABForCurrentTab(int position) {
		switch (position) {
			case 0: // Chats - Simple flow button to open friends
				binding.fab.setImageResource(R.drawable.ic_chat_add);
				binding.fab.setContentDescription("New Chat");
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

	private void setupFAB() {
		binding.fab.setOnClickListener(v -> {
			// Add bounce animation for better user feedback
			v.animate()
				.scaleX(0.9f)
				.scaleY(0.9f)
				.setDuration(100)
				.withEndAction(() -> {
					v.animate()
						.scaleX(1.0f)
						.scaleY(1.0f)
						.setDuration(100)
						.start();
				})
				.start();
			
			int currentTab = binding.viewPager.getCurrentItem();
			switch (currentTab) {
				case 0: // Chats tab - open friends layout
					openFriendsLayout();
					break;
				case 1: // Status tab - open status creation activity
					startActivity(new Intent(this, StatusCreationActivity.class));
					break;
				case 2: // Calls tab - open contacts for calling
					startActivity(new Intent(this, SelectContactsActivity.class));
					break;
			}
		});
	}

	private void openFriendsLayout() {
		// Open friends layout with search users and add friend functionality
		Intent intent = new Intent(this, FriendsLayoutActivity.class);
		startActivity(intent);
	}

	private void updateUserPresence() {
		if (currentUserId != null) {
			FirebaseUtil.updatePresence(currentUserId, true);
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
						FirebaseUtil.getUserRef(currentUserId)
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
		
		// Tint menu icons to ensure visibility
		for (int i = 0; i < menu.size(); i++) {
			MenuItem menuItem = menu.getItem(i);
			if (menuItem.getIcon() != null) {
				menuItem.getIcon().setTint(getColor(R.color.colorOnPrimary));
			}
		}
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			settingsLauncher.launch(new Intent(this, SettingsActivity.class));
			return true;
		}
		if (id == R.id.action_profile) {
			profileLauncher.launch(new Intent(this, EditProfileActivity.class));
			return true;
		}
		if (id == R.id.action_search_messages) {
			// Open search activity for searching messages, contacts, and chats
			Intent intent = new Intent(this, SearchActivity.class);
			startActivity(intent);
			return true;
		}
		if (id == R.id.action_blocked_users) {
			Intent intent = new Intent(this, BlockedUsersActivity.class);
			startActivity(intent);
			return true;
		}
		if (id == R.id.action_logout) {
			showLogoutDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showLogoutDialog() {
		new androidx.appcompat.app.AlertDialog.Builder(this)
			.setTitle("Logout")
			.setMessage("Are you sure you want to logout?")
			.setPositiveButton("Logout", (dialog, which) -> {
				logout();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void logout() {
		// Update user presence to offline
		if (currentUserId != null) {
			FirebaseUtil.updatePresence(currentUserId, false);
		}

		// Sign out from Firebase Auth
		FirebaseAuth.getInstance().signOut();

		// Clear any stored preferences
		PreferenceUtils.clearUserData(this);

		// Navigate to AuthActivity
		Intent intent = new Intent(this, AuthActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		finish();
	}
}