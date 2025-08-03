package com.pingme.android.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pingme.android.activities.ChatActivity;
import com.pingme.android.databinding.FragmentChatsBinding;
import com.pingme.android.viewmodels.ChatsViewModel;

public class ChatsFragment extends Fragment {

    private FragmentChatsBinding binding;
    private ChatsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ChatsViewModel.class);

        setupRecyclerView();
        setupObservers();
    }

    private void setupRecyclerView() {
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(viewModel.getAdapter());
    }

    private void setupObservers() {
        viewModel.getChatList().observe(getViewLifecycleOwner(), chats -> {
            viewModel.getAdapter().submitList(chats);
            binding.emptyView.setVisibility(chats.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getAdapter().setOnChatClickListener(chat -> {
            Intent intent = new Intent(getContext(), ChatActivity.class);
            intent.putExtra("chatId", chat.getId());
            intent.putExtra("receiverId", chat.getOtherUser().getId());
            startActivity(intent);
        });
    }
}