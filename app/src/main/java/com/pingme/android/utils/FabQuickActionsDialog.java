package com.pingme.android.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.pingme.android.R;
import com.pingme.android.activities.AddFriendActivity;
import com.pingme.android.activities.BroadcastListActivity;
import com.pingme.android.activities.SelectContactsActivity;
import com.pingme.android.databinding.DialogFabQuickActionsBinding;

public class FabQuickActionsDialog {
    
    public interface OnActionSelectedListener {
        void onNewGroupSelected();
        void onNewBroadcastSelected();
        void onAddFriendSelected();
        void onNewChatSelected();
    }
    
    private final Context context;
    private Dialog dialog;
    private DialogFabQuickActionsBinding binding;
    private OnActionSelectedListener listener;
    
    public FabQuickActionsDialog(Context context) {
        this.context = context;
        createDialog();
    }
    
    public void setOnActionSelectedListener(OnActionSelectedListener listener) {
        this.listener = listener;
    }
    
    private void createDialog() {
        dialog = new Dialog(context);
        binding = DialogFabQuickActionsBinding.inflate(LayoutInflater.from(context));
        
        dialog.setContentView(binding.getRoot());
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        // Position dialog at bottom right
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.x = 16; // margin from right
            params.y = 80; // margin from bottom (above FAB)
            window.setAttributes(params);
        }
        
        setupClickListeners();
    }
    
    private void setupClickListeners() {
        binding.layoutNewGroup.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onNewGroupSelected();
            } else {
                // Default action - open contacts for group creation
                context.startActivity(new Intent(context, SelectContactsActivity.class));
            }
        });
        
        binding.layoutNewBroadcast.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onNewBroadcastSelected();
            } else {
                // Default action - open broadcast list
                context.startActivity(new Intent(context, BroadcastListActivity.class));
            }
        });
        
        binding.layoutAddFriend.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onAddFriendSelected();
            } else {
                // Default action - open add friend activity
                context.startActivity(new Intent(context, AddFriendActivity.class));
            }
        });
        
        binding.layoutNewChat.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onNewChatSelected();
            } else {
                // Default action - open contacts for new chat
                context.startActivity(new Intent(context, SelectContactsActivity.class));
            }
        });
        
        // Close dialog when clicked outside
        binding.getRoot().setOnClickListener(v -> dismiss());
    }
    
    public void show() {
        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }
    }
    
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
    
    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}