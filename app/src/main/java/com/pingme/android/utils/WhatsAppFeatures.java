package com.pingme.android.utils;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.Log;

import com.pingme.android.models.Message;
import com.pingme.android.models.User;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class WhatsAppFeatures {
    private static final String TAG = "WhatsAppFeatures";

    // Message reaction emojis (WhatsApp style)
    public static final String[] REACTION_EMOJIS = {"👍", "❤️", "😂", "😮", "😢", "😡"};

    // Message status constants
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_READ = 3;

    /**
     * Format timestamp in WhatsApp style
     */
    public static String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        // If it's today
        if (DateUtils.isToday(timestamp)) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            return timeFormat.format(new Date(timestamp));
        }
        // If it's yesterday
        else if (diff < DateUtils.DAY_IN_MILLIS * 2) {
            return "Yesterday";
        }
        // If it's within a week
        else if (diff < DateUtils.WEEK_IN_MILLIS) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            return dayFormat.format(new Date(timestamp));
        }
        // If it's this year
        else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return dateFormat.format(new Date(timestamp));
        }
    }

    /**
     * Format last seen in WhatsApp style
     */
    public static String formatLastSeen(long lastSeen) {
        if (lastSeen == 0) return "last seen recently";
        
        long now = System.currentTimeMillis();
        long diff = now - lastSeen;

        if (diff < DateUtils.MINUTE_IN_MILLIS) {
            return "last seen just now";
        } else if (diff < DateUtils.HOUR_IN_MILLIS) {
            long minutes = diff / DateUtils.MINUTE_IN_MILLIS;
            return "last seen " + minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else if (diff < DateUtils.DAY_IN_MILLIS) {
            long hours = diff / DateUtils.HOUR_IN_MILLIS;
            return "last seen " + hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (diff < DateUtils.WEEK_IN_MILLIS) {
            long days = diff / DateUtils.DAY_IN_MILLIS;
            return "last seen " + days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return "last seen " + dateFormat.format(new Date(lastSeen));
        }
    }

    /**
     * Get message status icon based on status
     */
    public static String getMessageStatusIcon(int status) {
        switch (status) {
            case STATUS_SENT:
                return "✓";
            case STATUS_DELIVERED:
                return "✓✓";
            case STATUS_READ:
                return "✓✓";
            default:
                return "⏳";
        }
    }

    /**
     * Get message status color based on status
     */
    public static int getMessageStatusColor(Context context, int status) {
        switch (status) {
            case STATUS_SENT:
                return context.getColor(android.R.color.darker_gray);
            case STATUS_DELIVERED:
            case STATUS_READ:
                return context.getColor(com.pingme.android.R.color.message_delivered);
            default:
                return context.getColor(android.R.color.darker_gray);
        }
    }

    /**
     * Check if message is read by user
     */
    public static boolean isMessageReadByUser(Message message, String userId) {
        if (message.getReadBy() == null) return false;
        return message.getReadBy().containsKey(userId);
    }

    /**
     * Check if message is delivered to user
     */
    public static boolean isMessageDeliveredToUser(Message message, String userId) {
        if (message.getDeliveredTo() == null) return false;
        return message.getDeliveredTo().containsKey(userId);
    }

    /**
     * Get message reaction count
     */
    public static int getMessageReactionCount(Message message) {
        if (message.getReactions() == null) return 0;
        int total = 0;
        for (Map<String, String> reaction : message.getReactions().values()) {
            total += reaction.size();
        }
        return total;
    }

    /**
     * Get user's reaction to message
     */
    public static String getUserReaction(Message message, String userId) {
        if (message.getReactions() == null) return null;
        Map<String, String> userReactions = message.getReactions().get(userId);
        if (userReactions == null || userReactions.isEmpty()) return null;
        // Return the first reaction (users can only have one reaction per message in WhatsApp)
        return userReactions.values().iterator().next();
    }

    /**
     * Format file size in WhatsApp style
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Format duration in WhatsApp style (for audio/video)
     */
    public static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds % 60);
        }
    }

    /**
     * Check if user is online (WhatsApp logic)
     */
    public static boolean isUserOnline(User user) {
        if (!user.isLastSeenEnabled()) return false;
        
        long now = System.currentTimeMillis();
        long lastSeen = user.getLastSeen();
        
        // Consider online if last seen within 5 minutes
        return (now - lastSeen) < (5 * 60 * 1000);
    }

    /**
     * Get typing indicator text
     */
    public static String getTypingText(String userName) {
        return userName + " is typing...";
    }

    /**
     * Get recording indicator text
     */
    public static String getRecordingText(String userName) {
        return userName + " is recording a voice message...";
    }

    /**
     * Check if message can be edited (within 15 minutes)
     */
    public static boolean canEditMessage(Message message) {
        long now = System.currentTimeMillis();
        long messageTime = message.getTimestamp();
        long diff = now - messageTime;
        
        // WhatsApp allows editing within 15 minutes
        return diff <= (15 * 60 * 1000);
    }

    /**
     * Check if message can be deleted for everyone (within 1 hour)
     */
    public static boolean canDeleteForEveryone(Message message) {
        long now = System.currentTimeMillis();
        long messageTime = message.getTimestamp();
        long diff = now - messageTime;
        
        // WhatsApp allows delete for everyone within 1 hour
        return diff <= (60 * 60 * 1000);
    }
}