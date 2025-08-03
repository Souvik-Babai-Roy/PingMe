package com.pingme.android.fragments;

import android.content.Context;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import com.pingme.android.R;
import com.pingme.android.utils.PreferenceUtils;

public class SettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);

        // Initialize all preferences
        bindThemePreference();
        bindSwitchPreference("last_seen");
        bindSwitchPreference("read_receipts");
        bindSwitchPreference("profile_photo");
        bindSwitchPreference("about");
        bindSwitchPreference("message_notifications");
        bindSwitchPreference("group_notifications");
        bindSwitchPreference("status_notifications");

        // Set up About section
        setupAboutSection();
    }

    private void bindThemePreference() {
        ListPreference themePref = findPreference("theme");
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener(this);
            // Set current value summary
            themePref.setSummaryProvider(preference -> {
                String value = ((ListPreference) preference).getValue();
                String[] entries = getResources().getStringArray(R.array.theme_entries);
                String[] values = getResources().getStringArray(R.array.theme_values);

                for (int i = 0; i < values.length; i++) {
                    if (values[i].equals(value)) {
                        return entries[i];
                    }
                }
                return "System default";
            });
        }
    }

    private void bindSwitchPreference(String key) {
        SwitchPreferenceCompat switchPref = findPreference(key);
        if (switchPref != null) {
            switchPref.setOnPreferenceChangeListener(this);
        }
    }

    private void setupAboutSection() {
        // Set app version
        Preference versionPref = findPreference("app_version");
        if (versionPref != null) {
            try {
                String versionName = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0)
                        .versionName;
                versionPref.setSummary(versionName);
            } catch (Exception e) {
                versionPref.setSummary("1.0.0");
            }
        }

        // Set click listeners
        setupPreferenceClick("storage_usage", () -> openStorageSettings());
        setupPreferenceClick("network_usage", () -> openNetworkUsage());
        setupPreferenceClick("terms_of_service", () -> openWebPage("https://example.com/terms"));
        setupPreferenceClick("privacy_policy", () -> openWebPage("https://example.com/privacy"));
    }

    private void setupPreferenceClick(String key, Runnable action) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                action.run();
                return true;
            });
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        Context context = getContext();

        if (context == null) return false;

        switch (key) {
            case "theme":
                PreferenceUtils.setThemePreference(context, (String) newValue);
                return true;

            case "last_seen":
                PreferenceUtils.setLastSeenEnabled(context, (boolean) newValue);
                return true;

            case "read_receipts":
                PreferenceUtils.setReadReceiptsEnabled(context, (boolean) newValue);
                return true;

            case "profile_photo":
                PreferenceUtils.setProfilePhotoEnabled(context, (boolean) newValue);
                return true;

            case "about":
                PreferenceUtils.setAboutEnabled(context, (boolean) newValue);
                return true;

            case "message_notifications":
                PreferenceUtils.setMessageNotificationsEnabled(context, (boolean) newValue);
                return true;

            case "group_notifications":
                PreferenceUtils.setGroupNotificationsEnabled(context, (boolean) newValue);
                return true;

            case "status_notifications":
                PreferenceUtils.setStatusNotificationsEnabled(context, (boolean) newValue);
                return true;

            default:
                return false;
        }
    }

    // ===== WhatsApp-like actions =====

    private void openStorageSettings() {
        // Implement storage management screen
        // Example: startActivity(new Intent(getActivity(), StorageSettingsActivity.class));
    }

    private void openNetworkUsage() {
        // Implement network usage screen
        // Example: startActivity(new Intent(getActivity(), NetworkUsageActivity.class));
    }

    private void openWebPage(String url) {
        // Implement web view
        // Example:
        //   Intent intent = new Intent(requireContext(), WebViewActivity.class);
        //   intent.putExtra("url", url);
        //   startActivity(intent);
    }
}