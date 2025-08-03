package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.pingme.android.databinding.ItemChatBinding;
import com.pingme.android.models.Chat;

public class ChatListAdapter extends ListAdapter<Chat, ChatListAdapter.ChatViewHolder> {

    private OnChatClickListener onChatClickListener;

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatListAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.onChatClickListener = listener;
    }

    private static final DiffUtil.ItemCallback<Chat> DIFF_CALLBACK = new DiffUtil.ItemCallback<Chat>() {
        @Override
        public boolean areItemsTheSame(@NonNull Chat oldItem, @NonNull Chat newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Chat oldItem, @NonNull Chat newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemChatBinding binding = ItemChatBinding.inflate(inflater, parent, false);
        return new ChatViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatBinding binding;

        public ChatViewHolder(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Chat chat) {
            binding.setChat(chat);
            binding.executePendingBindings();

            // Set click listener
            binding.getRoot().setOnClickListener(v -> {
                if (onChatClickListener != null) {
                    onChatClickListener.onChatClick(chat);
                }
            });
        }
    }
}