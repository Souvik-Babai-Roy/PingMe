// ChatsViewModel.java
package com.pingme.android.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.pingme.android.adapters.ChatListAdapter;
import com.pingme.android.models.Chat;
import com.pingme.android.repositories.ChatRepository;

import java.util.List;

public class ChatsViewModel extends ViewModel {
    private ChatRepository chatRepository;
    private ChatListAdapter adapter;
    private MutableLiveData<List<Chat>> chatList = new MutableLiveData<>();

    public ChatsViewModel() {
        chatRepository = new ChatRepository();
        adapter = new ChatListAdapter();
        loadChats();
    }

    public LiveData<List<Chat>> getChatList() {
        return chatList;
    }

    public ChatListAdapter getAdapter() {
        return adapter;
    }

    private void loadChats() {
        // TODO: Load chats from repository
        chatRepository.loadChats().observeForever(chats -> {
            chatList.setValue(chats);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up resources if needed
    }
}