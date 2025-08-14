package com.pingme.android;

import android.app.Application;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.pingme.android.utils.NotificationUtil;
import com.pingme.android.utils.PreferenceUtils;

import java.util.HashMap;
import java.util.Map;

public class App extends Application {
    private static final String TAG = "PingMeApp";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize Cloudinary
        initializeCloudinary();

        // Create notification channels
        NotificationUtil.createNotificationChannels(this);

        // Apply saved theme
        applySavedTheme();
    }

    public static App getInstance() {
        return instance;
    }

    private void initializeCloudinary() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", BuildConfig.CLOUDINARY_CLOUD_NAME);
            config.put("api_key", BuildConfig.CLOUDINARY_API_KEY);
            config.put("api_secret", BuildConfig.CLOUDINARY_API_SECRET);

            MediaManager.init(this, config);
            Log.d(TAG, "Cloudinary initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Cloudinary", e);
        }
    }

    private void applySavedTheme() {
        try {
            String savedTheme = PreferenceUtils.getThemePreference(this);
            PreferenceUtils.applyTheme(savedTheme);
            Log.d(TAG, "Applied theme: " + savedTheme);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply saved theme", e);
            // Fallback to system theme to prevent crashes
            try {
                PreferenceUtils.applyTheme("auto");
            } catch (Exception fallbackError) {
                Log.e(TAG, "Failed to apply fallback theme", fallbackError);
                // Last resort: don't change theme at all
            }
        }
    }
}
