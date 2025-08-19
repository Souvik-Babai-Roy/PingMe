package com.pingme.android.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PreferenceUtils {
    // Preference keys
    private static final String PREF_THEME = "theme";
    private static final String PREF_LAST_SEEN = "last_seen";
    private static final String PREF_READ_RECEIPTS = "read_receipts";
    private static final String PREF_PROFILE_PHOTO = "profile_photo";
    private static final String PREF_ABOUT = "about";
    private static final String PREF_MESSAGE_NOTIFS = "message_notifications";

    private static final String PREF_STATUS_NOTIFS = "status_notifications";

    // Default values
    private static final boolean DEFAULT_LAST_SEEN = true;
    private static final boolean DEFAULT_READ_RECEIPTS = true;
    private static final boolean DEFAULT_PROFILE_PHOTO = true;
    private static final boolean DEFAULT_ABOUT = true;
    private static final boolean DEFAULT_MESSAGE_NOTIFS = true;

    private static final boolean DEFAULT_STATUS_NOTIFS = false;

    // FIXED: Apply selected theme with immediate activity recreation
    public static void applyTheme(String themeValue) {
        try {
            switch (themeValue) {
                case "light":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case "dark":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                case "auto":
                default:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    break;
            }
        } catch (Exception e) {
            // Fallback to system theme if there's an error
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static String getAppliedTheme() {
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        switch (currentMode) {
            case AppCompatDelegate.MODE_NIGHT_NO:
                return "light";
            case AppCompatDelegate.MODE_NIGHT_YES:
                return "dark";
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
            default:
                return "auto";
        }
    }

    public static void clearUserData(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().apply();
    }

    // FIXED: Save theme selection with immediate UI update
    public static void setThemePreference(Context context, String themeValue) {
        savePreference(context, PREF_THEME, themeValue);
        updateFirestore("theme", themeValue);

        // Apply theme immediately
        applyTheme(themeValue);

        // FIXED: Recreate current activity to apply theme safely
        if (context instanceof Activity) {
            try {
                ((Activity) context).recreate();
            } catch (Exception e) {
                // If recreation fails, just apply the theme for next launch
                Log.w("PreferenceUtils", "Failed to recreate activity, theme will apply on next launch");
            }
        }
    }

    // ⏳ Last Seen settings
    public static void setLastSeenEnabled(Context context, boolean enabled) {
        savePreference(context, PREF_LAST_SEEN, enabled);
        updateFirestore("last_seen_enabled", enabled);
    }

    // ✅ Read Receipts
    public static void setReadReceiptsEnabled(Context context, boolean enabled) {
        savePreference(context, PREF_READ_RECEIPTS, enabled);
        updateFirestore("read_receipts_enabled", enabled);
    }

    // 👤 Profile Photo visibility
    public static void setProfilePhotoEnabled(Context context, boolean enabled) {
        savePreference(context, PREF_PROFILE_PHOTO, enabled);
        updateFirestore("profile_photo_enabled", enabled);
    }

    // ℹ️ About visibility
    public static void setAboutEnabled(Context context, boolean enabled) {
        savePreference(context, PREF_ABOUT, enabled);
        updateFirestore("about_enabled", enabled);
    }

    // 🔔 Message notifications
    public static void setMessageNotificationsEnabled(Context context, boolean enabled) {
        savePreference(context, PREF_MESSAGE_NOTIFS, enabled);
        // WhatsApp-like: Toggle notification channels
    }



    // 📢 Status notifications
    public static void setStatusNotificationsEnabled(Context context, boolean enabled) {
        savePreference(context, PREF_STATUS_NOTIFS, enabled);
        // WhatsApp-like: Toggle notification channels
    }

    // 🔄 Generic save method
    private static void savePreference(Context context, String key, Object value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        }
        editor.apply();
    }

    // 🔥 Update Firestore with preference changes
    private static void updateFirestore(String key, Object value) {
        String userId = getCurrentUserId();
        if (userId != null) {
            // FIXED: Map preference keys to proper User model fields
            String userField = mapToUserField(key);
            
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .update(userField, value)
                    .addOnFailureListener(e -> {
                        // If update fails, try to set the field with a Map
                        Map<String, Object> data = new HashMap<>();
                        data.put(userField, value);
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(userId)
                                .set(data, com.google.firebase.firestore.SetOptions.merge());
                    });
        }
    }

    // FIXED: Map preference keys to User model fields
    private static String mapToUserField(String prefKey) {
        switch (prefKey) {
            case "last_seen_enabled":
                return "lastSeenEnabled";
            case "read_receipts_enabled":
                return "readReceiptsEnabled";
            case "profile_photo_enabled":
                return "profilePhotoEnabled";
            case "about_enabled":
                return "aboutEnabled";
            case "theme":
                return "theme";
            default:
                return prefKey;
        }
    }

    // FIXED: Sync preferences from Firestore with error handling
    public static void syncPreferencesFromFirestore(Context context) {
        String userId = getCurrentUserId();
        if (userId == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        updateLocalPreferences(context, snapshot);
                    }
                })
                .addOnFailureListener(e -> {
                    // Try backup preferences collection
                    FirebaseFirestore.getInstance()
                            .collection("user_preferences")
                            .document(userId)
                            .get()
                            .addOnSuccessListener(backupSnapshot -> {
                                if (backupSnapshot.exists()) {
                                    updateLocalPreferences(context, backupSnapshot);
                                }
                            });
                });
    }

    // 📥 Update local preferences from Firestore snapshot
    private static void updateLocalPreferences(Context context, DocumentSnapshot snapshot) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        // Theme - FIXED: Apply immediately if changed
        String currentTheme = prefs.getString(PREF_THEME, "auto");
        if (snapshot.contains("theme")) {
            String newTheme = snapshot.getString("theme");
            if (newTheme != null && !newTheme.equals(currentTheme)) {
                editor.putString(PREF_THEME, newTheme);
                editor.apply(); // Apply immediately
                applyTheme(newTheme);

                // Recreate activity if context is Activity
                if (context instanceof Activity) {
                    ((Activity) context).recreate();
                }
                return; // Exit early to avoid double apply
            }
        }

        // FIXED: Privacy settings using proper User model field names
        updateBooleanPref(editor, snapshot, "lastSeenEnabled", PREF_LAST_SEEN, DEFAULT_LAST_SEEN);
        updateBooleanPref(editor, snapshot, "readReceiptsEnabled", PREF_READ_RECEIPTS, DEFAULT_READ_RECEIPTS);
        updateBooleanPref(editor, snapshot, "profilePhotoEnabled", PREF_PROFILE_PHOTO, DEFAULT_PROFILE_PHOTO);
        updateBooleanPref(editor, snapshot, "aboutEnabled", PREF_ABOUT, DEFAULT_ABOUT);

        editor.apply();
    }

    // 🔧 Helper: Update boolean preference
    private static void updateBooleanPref(SharedPreferences.Editor editor, DocumentSnapshot snapshot,
                                          String firestoreKey, String prefKey, boolean defaultValue) {
        if (snapshot.contains(firestoreKey)) {
            editor.putBoolean(prefKey, snapshot.getBoolean(firestoreKey));
        } else {
            editor.putBoolean(prefKey, defaultValue);
        }
    }

    // 🔐 Get current user's UID
    private static String getCurrentUserId() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return null;
    }

    // ===== GETTERS =====
    public static String getThemePreference(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_THEME, "auto");
    }

    public static boolean isLastSeenEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_LAST_SEEN, DEFAULT_LAST_SEEN);
    }

    public static boolean isReadReceiptsEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_READ_RECEIPTS, DEFAULT_READ_RECEIPTS);
    }

    public static boolean isProfilePhotoEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_PROFILE_PHOTO, DEFAULT_PROFILE_PHOTO);
    }

    public static boolean isAboutEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_ABOUT, DEFAULT_ABOUT);
    }

    public static boolean areMessageNotificationsEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_MESSAGE_NOTIFS, DEFAULT_MESSAGE_NOTIFS);
    }



    public static boolean areStatusNotificationsEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_STATUS_NOTIFS, DEFAULT_STATUS_NOTIFS);
    }
}