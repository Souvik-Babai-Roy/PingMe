/*
package com.pingme.android.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.Observer;

import com.pingme.android.adapters.ChatListAdapter;
import com.pingme.android.models.Chat;
import com.pingme.android.repositories.ChatRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class ChatsViewModel extends ViewModel {
    private ChatRepository chatRepository;
    private ChatListAdapter adapter;
    private MutableLiveData<List<Chat>> chatList = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private Observer<List<Chat>> chatsObserver;
    private Observer<List<Chat>> searchObserver;
    private String currentUserId;

    public ChatsViewModel() {
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        this.chatRepository = new ChatRepository();

        // Initialize loading state
        isLoading.setValue(false);

        chatRepository.getChatsLiveData().observeForever(chats -> {
            if (chats != null) {
                chatList.setValue(chats);
                if (adapter != null) adapter.updateChats(chats);
            }
        });
    }

    // Set adapter from Fragment/Activity (where Context is available)
    public void setAdapter(ChatListAdapter adapter) {
        this.adapter = adapter;
    }

    public LiveData<List<Chat>> getChatList() {
        return chatList;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public ChatListAdapter getAdapter() {
        return adapter;
    }

    public void loadChats() {
        if (currentUserId == null) {
            errorMessage.setValue("User not authenticated");
            return;
        }

        isLoading.setValue(true);
        chatRepository.loadChats();

        // Remove previous observer if exists
        if (chatsObserver != null) {
            chatRepository.getChatsLiveData().removeObserver(chatsObserver);
        }

        // Create new observer
        chatsObserver = chats -> {
            isLoading.setValue(false);
            if (chats != null) {
                chatList.setValue(chats);
                if (adapter != null) {
                    adapter.updateChats(chats);
                }
                errorMessage.setValue(null);
            } else {
                errorMessage.setValue("Failed to load chats");
            }
        };

        // Observe repository data
        chatRepository.getChatsLiveData().observeForever(chatsObserver);
        chatRepository.loadChats(currentUserId);
    }

    public void refreshChats() {
        loadChats();
    }

    // Method to handle real-time chat updates
    public void onChatUpdated(Chat updatedChat) {
        if (adapter != null) {
            adapter.addOrUpdateChat(updatedChat);
        }

        // Update the LiveData as well
        List<Chat> currentChats = chatList.getValue();
        if (currentChats != null) {
            // Find and update the existing chat or add new one
            boolean found = false;
            for (int i = 0; i < currentChats.size(); i++) {
                if (currentChats.get(i).getId().equals(updatedChat.getId())) {
                    currentChats.set(i, updatedChat);
                    found = true;
                    break;
                }
            }
            if (!found) {
                currentChats.add(0, updatedChat);
            }
            chatList.setValue(currentChats);
        }
    }

    // Method to handle chat deletion
    public void onChatDeleted(String chatId) {
        if (adapter != null) {
            adapter.removeChat(chatId);
        }

        // Update LiveData
        List<Chat> currentChats = chatList.getValue();
        if (currentChats != null) {
            currentChats.removeIf(chat -> chat.getId().equals(chatId));
            chatList.setValue(currentChats);
        }
    }

    // Method to mark all chats as read for a specific user
    public void markChatAsRead(String chatId) {
        List<Chat> currentChats = chatList.getValue();
        if (currentChats != null) {
            for (Chat chat : currentChats) {
                if (chat.getId().equals(chatId)) {
                    chat.setUnreadCount(0);
                    break;
                }
            }
            chatList.setValue(currentChats);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    // Method to update typing status
    public void updateTypingStatus(String chatId, boolean isTyping) {
        List<Chat> currentChats = chatList.getValue();
        if (currentChats != null) {
            for (Chat chat : currentChats) {
                if (chat.getId().equals(chatId)) {
                    chat.setTyping(isTyping);
                    break;
                }
            }
            chatList.setValue(currentChats);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    // Method to search chats
    public void searchChats(String query) {
        if (chatRepository != null) {
            // Remove previous search observer if exists
            if (searchObserver != null) {
                // We need to keep track of the previous search LiveData to remove observer
                // For now, we'll create a new observer each time
            }

            LiveData<List<Chat>> searchResults = chatRepository.searchChats(query, currentUserId);

            searchObserver = filteredChats -> {
                if (filteredChats != null) {
                    chatList.setValue(filteredChats);
                    if (adapter != null) {
                        adapter.updateChats(filteredChats);
                    }
                }
            };

            searchResults.observeForever(searchObserver);
        }
    }

    // Method to clear search and reload all chats
    public void clearSearch() {
        // Remove search observer if exists
        if (searchObserver != null) {
            // Note: This is a limitation - we can't easily remove the observer without keeping reference to the LiveData
            // Consider refactoring to use a single LiveData source with transformation
            searchObserver = null;
        }
        loadChats();
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Remove observers to prevent memory leaks
        if (chatsObserver != null && chatRepository != null) {
            chatRepository.getChatsLiveData().removeObserver(chatsObserver);
        }

        if (searchObserver != null && chatRepository != null) {
            // Note: This is a limitation in the current design
            // Ideally, we should keep reference to the search LiveData to remove the observer
        }

        // Clear references
        adapter = null;
        chatRepository = null;
        chatsObserver = null;
        searchObserver = null;
    }
}
*/