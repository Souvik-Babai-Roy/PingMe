package com.pingme.android.utils;

import android.util.Log;
import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.pingme.android.models.ChatHistory;
import com.pingme.android.models.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for admin-level access to chat history and deleted content.
 * This provides compliance and audit capabilities for deleted messages and chats.
 */
public class AdminAccessUtil {
    private static final String TAG = "AdminAccessUtil";
    
    public interface AdminAccessCallback {
        void onAdminAccessGranted();
        void onAdminAccessDenied(String reason);
        void onChatHistoryRetrieved(List<ChatHistory> chatHistories);
        void onDeletedMessagesRetrieved(List<Message> deletedMessages);
        void onError(String error);
    }
    
    /**
     * Check if current user has admin access
     */
    public static void checkAdminAccess(AdminAccessCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onAdminAccessDenied("User not authenticated");
            return;
        }
        
        String userId = currentUser.getUid();
        
        // Check if user is admin
        FirebaseFirestore.getInstance()
            .collection("admin_users")
            .document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists() && "admin".equals(documentSnapshot.getString("role"))) {
                    callback.onAdminAccessGranted();
                } else {
                    // Check if user is system user
                    checkSystemUserAccess(userId, callback);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to check admin access", e);
                callback.onAdminAccessDenied("Failed to verify admin status");
            });
    }
    
    private static void checkSystemUserAccess(String userId, AdminAccessCallback callback) {
        FirebaseFirestore.getInstance()
            .collection("system_users")
            .document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists() && "system".equals(documentSnapshot.getString("role"))) {
                    callback.onAdminAccessGranted();
                } else {
                    callback.onAdminAccessDenied("User does not have admin privileges");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to check system user access", e);
                callback.onAdminAccessDenied("Failed to verify system user status");
            });
    }
    
    /**
     * Get all chat history for admin review
     */
    public static void getAllChatHistory(AdminAccessCallback callback) {
        checkAdminAccess(new AdminAccessCallback() {
            @Override
            public void onAdminAccessGranted() {
                FirebaseFirestore.getInstance()
                    .collection("chat_history")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<ChatHistory> chatHistories = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            ChatHistory chatHistory = doc.toObject(ChatHistory.class);
                            if (chatHistory != null) {
                                chatHistory.setChatId(doc.getId());
                                chatHistories.add(chatHistory);
                            }
                        }
                        callback.onChatHistoryRetrieved(chatHistories);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to retrieve chat history", e);
                        callback.onError("Failed to retrieve chat history: " + e.getMessage());
                    });
            }
            
            @Override
            public void onAdminAccessDenied(String reason) {
                callback.onAdminAccessDenied(reason);
            }
            
            @Override
            public void onChatHistoryRetrieved(List<ChatHistory> chatHistories) {}
            
            @Override
            public void onDeletedMessagesRetrieved(List<Message> deletedMessages) {}
            
            @Override
            public void onError(String error) {}
        });
    }
    
    /**
     * Get chat history for a specific chat
     */
    public static void getChatHistory(String chatId, AdminAccessCallback callback) {
        checkAdminAccess(new AdminAccessCallback() {
            @Override
            public void onAdminAccessGranted() {
                FirebaseFirestore.getInstance()
                    .collection("chat_history")
                    .document(chatId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            ChatHistory chatHistory = documentSnapshot.toObject(ChatHistory.class);
                            if (chatHistory != null) {
                                chatHistory.setChatId(chatId);
                                List<ChatHistory> histories = new ArrayList<>();
                                histories.add(chatHistory);
                                callback.onChatHistoryRetrieved(histories);
                            } else {
                                callback.onError("Failed to parse chat history");
                            }
                        } else {
                            callback.onChatHistoryRetrieved(new ArrayList<>());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to retrieve chat history for: " + chatId, e);
                        callback.onError("Failed to retrieve chat history: " + e.getMessage());
                    });
            }
            
            @Override
            public void onAdminAccessDenied(String reason) {
                callback.onAdminAccessDenied(reason);
            }
            
            @Override
            public void onChatHistoryRetrieved(List<ChatHistory> chatHistories) {}
            
            @Override
            public void onDeletedMessagesRetrieved(List<Message> deletedMessages) {}
            
            @Override
            public void onError(String error) {}
        });
    }
    
    /**
     * Get all deleted messages from a specific chat
     */
    public static void getDeletedMessages(String chatId, AdminAccessCallback callback) {
        checkAdminAccess(new AdminAccessCallback() {
            @Override
            public void onAdminAccessGranted() {
                FirebaseFirestore.getInstance()
                    .collection("chat_history")
                    .document(chatId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            ChatHistory chatHistory = documentSnapshot.toObject(ChatHistory.class);
                            if (chatHistory != null && chatHistory.getDeletedMessages() != null) {
                                List<Message> deletedMessages = new ArrayList<>();
                                for (ChatHistory.DeletedMessage deletedMsg : chatHistory.getDeletedMessages().values()) {
                                    deletedMessages.add(deletedMsg.getOriginalMessage());
                                }
                                callback.onDeletedMessagesRetrieved(deletedMessages);
                            } else {
                                callback.onDeletedMessagesRetrieved(new ArrayList<>());
                            }
                        } else {
                            callback.onDeletedMessagesRetrieved(new ArrayList<>());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to retrieve deleted messages for: " + chatId, e);
                        callback.onError("Failed to retrieve deleted messages: " + e.getMessage());
                    });
            }
            
            @Override
            public void onAdminAccessDenied(String reason) {
                callback.onAdminAccessDenied(reason);
            }
            
            @Override
            public void onChatHistoryRetrieved(List<ChatHistory> chatHistories) {}
            
            @Override
            public void onDeletedMessagesRetrieved(List<Message> deletedMessages) {}
            
            @Override
            public void onError(String error) {}
        });
    }
    
    /**
     * Search chat history by user ID (for compliance purposes)
     */
    public static void searchChatHistoryByUser(String userId, AdminAccessCallback callback) {
        checkAdminAccess(new AdminAccessCallback() {
            @Override
            public void onAdminAccessGranted() {
                // This would require a more complex query structure
                // For now, we'll get all chat history and filter client-side
                getAllChatHistory(new AdminAccessCallback() {
                    @Override
                    public void onAdminAccessGranted() {}
                    
                    @Override
                    public void onAdminAccessDenied(String reason) {
                        callback.onAdminAccessDenied(reason);
                    }
                    
                    @Override
                    public void onChatHistoryRetrieved(List<ChatHistory> chatHistories) {
                        List<ChatHistory> filteredHistories = new ArrayList<>();
                        for (ChatHistory history : chatHistories) {
                            if (history.getDeletedMessages() != null) {
                                for (ChatHistory.DeletedMessage deletedMsg : history.getDeletedMessages().values()) {
                                    if (userId.equals(deletedMsg.getDeletedBy()) || 
                                        (deletedMsg.getOriginalMessage() != null && 
                                         userId.equals(deletedMsg.getOriginalMessage().getSenderId()))) {
                                        filteredHistories.add(history);
                                        break;
                                    }
                                }
                            }
                        }
                        callback.onChatHistoryRetrieved(filteredHistories);
                    }
                    
                    @Override
                    public void onDeletedMessagesRetrieved(List<Message> deletedMessages) {}
                    
                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
            }
            
            @Override
            public void onAdminAccessDenied(String reason) {
                callback.onAdminAccessDenied(reason);
            }
            
            @Override
            public void onChatHistoryRetrieved(List<ChatHistory> chatHistories) {}
            
            @Override
            public void onDeletedMessagesRetrieved(List<Message> deletedMessages) {}
            
            @Override
            public void onError(String error) {}
        });
    }
    
    /**
     * Export chat history for compliance/audit purposes
     */
    public static void exportChatHistory(String chatId, AdminAccessCallback callback) {
        getChatHistory(chatId, new AdminAccessCallback() {
            @Override
            public void onAdminAccessGranted() {}
            
            @Override
            public void onAdminAccessDenied(String reason) {
                callback.onAdminAccessDenied(reason);
            }
            
            @Override
            public void onChatHistoryRetrieved(List<ChatHistory> chatHistories) {
                if (!chatHistories.isEmpty()) {
                    ChatHistory history = chatHistories.get(0);
                    // Here you would implement the export logic
                    // For now, we'll just return the history
                    callback.onChatHistoryRetrieved(chatHistories);
                } else {
                    callback.onChatHistoryRetrieved(new ArrayList<>());
                }
            }
            
            @Override
            public void onDeletedMessagesRetrieved(List<Message> deletedMessages) {}
            
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}