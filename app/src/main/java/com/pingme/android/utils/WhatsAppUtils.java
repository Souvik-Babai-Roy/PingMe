package com.pingme.android.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class WhatsAppUtils {
    
    /**
     * Format timestamp like WhatsApp for chat list
     */
    public static String formatChatListTime(long timestamp) {
        if (timestamp <= 0) return "";

        Calendar messageTime = Calendar.getInstance();
        messageTime.setTimeInMillis(timestamp);
        
        Calendar now = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        
        // Same day - show time only
        if (isSameDay(messageTime, now)) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return timeFormat.format(new Date(timestamp));
        }
        // Yesterday - show "Yesterday"
        else if (isSameDay(messageTime, yesterday)) {
            return "Yesterday";
        }
        // This week - show day name
        else if (isThisWeek(messageTime, now)) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            return dayFormat.format(new Date(timestamp));
        }
        // Older - show date
        else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            return dateFormat.format(new Date(timestamp));
        }
    }
    
    /**
     * Format last seen like WhatsApp
     */
    public static String formatLastSeen(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long minutes = diff / (1000 * 60);
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (minutes < 1) {
            return "just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (days == 1) {
            return "yesterday";
        } else if (days < 7) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            // For older dates, show actual date
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return "on " + dateFormat.format(new Date(timestamp));
        }
    }
    
    /**
     * Get WhatsApp-like message preview with sender prefix
     */
    public static String getMessagePreview(String message, String messageType, String senderId, String currentUserId, int maxLength) {
        if (message == null) message = "";
        
        // Add sender prefix
        String senderPrefix = "";
        if (senderId != null && senderId.equals(currentUserId)) {
            senderPrefix = "You: ";
        }
        
        // Handle different message types
        switch (messageType != null ? messageType.toLowerCase() : "text") {
            case "image":
                return senderPrefix + "📷 Photo";
            case "video":
                return senderPrefix + "🎥 Video";
            case "audio":
                return senderPrefix + "🎤 Audio";
            case "document":
                return senderPrefix + "📄 Document";
            case "location":
                return senderPrefix + "📍 Location";
            case "contact":
                return senderPrefix + "👤 Contact";
            case "sticker":
                return senderPrefix + "🎭 Sticker";
            case "gif":
                return senderPrefix + "🎬 GIF";
            default:
                // For text messages, truncate if too long
                if (!message.trim().isEmpty()) {
                    String text = message.trim();
                    if (text.length() > maxLength) {
                        text = text.substring(0, maxLength - 3) + "...";
                    }
                    return senderPrefix + text;
                }
                return senderPrefix + "Message";
        }
    }
    
    /**
     * Get display name with priority for personal names
     */
    public static String getDisplayName(String personalName, String originalName, String email) {
        if (personalName != null && !personalName.trim().isEmpty()) {
            return personalName;
        } else if (originalName != null && !originalName.trim().isEmpty()) {
            return originalName;
        } else if (email != null && !email.trim().isEmpty() && email.contains("@")) {
            return email.split("@")[0];
        }
        return "Unknown User";
    }
    
    /**
     * Format unread count like WhatsApp
     */
    public static String formatUnreadCount(int count) {
        if (count <= 0) return "";
        if (count > 999) return "999+";
        if (count > 99) return "99+";
        return String.valueOf(count);
    }
    
    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    private static boolean isThisWeek(Calendar messageTime, Calendar now) {
        Calendar weekStart = Calendar.getInstance();
        weekStart.setTime(now.getTime());
        weekStart.add(Calendar.DAY_OF_YEAR, -7);
        
        return messageTime.after(weekStart) && messageTime.before(now);
    }
}