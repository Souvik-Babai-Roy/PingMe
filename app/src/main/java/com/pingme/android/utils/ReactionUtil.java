package com.pingme.android.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.pingme.android.R;
import com.pingme.android.models.Message;

import java.util.Map;

public class ReactionUtil {
    
    public interface ReactionListener {
        void onReactionSelected(String emoji);
        void onReactionRemoved();
    }

    /**
     * Show reaction picker dialog
     */
    public static void showReactionPicker(Context context, View anchorView, Message message, String currentUserId, ReactionListener listener) {
        Dialog dialog = new Dialog(context, R.style.ReactionPickerDialog);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_message_reactions, null);
        
        // Check if user already has a reaction
        String currentReaction = message.getUserReaction(currentUserId);
        
        // Set up reaction buttons
        setupReactionButton(dialogView, R.id.btnReaction1, "👍", currentReaction, emoji -> {
            handleReaction(message, currentUserId, emoji, currentReaction, listener);
            dialog.dismiss();
        });
        
        setupReactionButton(dialogView, R.id.btnReaction2, "❤️", currentReaction, emoji -> {
            handleReaction(message, currentUserId, emoji, currentReaction, listener);
            dialog.dismiss();
        });
        
        setupReactionButton(dialogView, R.id.btnReaction3, "😂", currentReaction, emoji -> {
            handleReaction(message, currentUserId, emoji, currentReaction, listener);
            dialog.dismiss();
        });
        
        setupReactionButton(dialogView, R.id.btnReaction4, "😮", currentReaction, emoji -> {
            handleReaction(message, currentUserId, emoji, currentReaction, listener);
            dialog.dismiss();
        });
        
        setupReactionButton(dialogView, R.id.btnReaction5, "😢", currentReaction, emoji -> {
            handleReaction(message, currentUserId, emoji, currentReaction, listener);
            dialog.dismiss();
        });
        
        setupReactionButton(dialogView, R.id.btnReaction6, "😡", currentReaction, emoji -> {
            handleReaction(message, currentUserId, emoji, currentReaction, listener);
            dialog.dismiss();
        });

        dialog.setContentView(dialogView);
        
        // Position dialog above the anchor view
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            
            // Get anchor view location
            int[] location = new int[2];
            anchorView.getLocationInWindow(location);
            
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = 100; // Offset from bottom
            window.setAttributes(params);
        }
        
        dialog.show();
    }
    
    private static void setupReactionButton(View dialogView, int buttonId, String emoji, String currentReaction, ReactionListener listener) {
        TextView button = dialogView.findViewById(buttonId);
        button.setText(emoji);
        
        // Highlight if this is the current reaction
        if (emoji.equals(currentReaction)) {
            button.setBackgroundResource(R.drawable.reaction_selected_background);
        }
        
        button.setOnClickListener(v -> listener.onReactionSelected(emoji));
    }
    
    private static void handleReaction(Message message, String userId, String newReaction, String currentReaction, ReactionListener listener) {
        if (newReaction.equals(currentReaction)) {
            // Remove reaction if same emoji is selected
            message.removeReaction(userId);
            listener.onReactionRemoved();
        } else {
            // Add new reaction
            message.addReaction(userId, newReaction);
            listener.onReactionSelected(newReaction);
        }
    }
    
    /**
     * Format reactions for display
     */
    public static String formatReactionsForDisplay(Message message) {
        if (message.getReactions() == null || message.getReactions().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> userReactions : message.getReactions().values()) {
            if (!userReactions.isEmpty()) {
                sb.append(userReactions.values().iterator().next());
            }
        }
        return sb.toString();
    }
    
    /**
     * Get reaction count for display
     */
    public static int getReactionCount(Message message) {
        if (message.getReactions() == null) return 0;
        return message.getReactions().size();
    }
}