package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pingme.android.R;
import com.pingme.android.models.Message;
import com.pingme.android.utils.FirestoreUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {
    private List<FirestoreUtil.SearchResult> searchResults;
    private OnSearchResultClickListener listener;

    public interface OnSearchResultClickListener {
        void onSearchResultClick(FirestoreUtil.SearchResult result);
    }

    public SearchResultAdapter(List<FirestoreUtil.SearchResult> searchResults, OnSearchResultClickListener listener) {
        this.searchResults = searchResults;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new SearchResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        FirestoreUtil.SearchResult result = searchResults.get(position);
        holder.bind(result);
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    public void updateSearchResults(List<FirestoreUtil.SearchResult> newResults) {
        this.searchResults = newResults;
        notifyDataSetChanged();
    }

    class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private TextView tvMessageText;
        private TextView tvMessageTime;
        private TextView tvChatInfo;

        public SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            tvMessageTime = itemView.findViewById(R.id.tvMessageTime);
            tvChatInfo = itemView.findViewById(R.id.tvChatInfo);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSearchResultClick(searchResults.get(position));
                }
            });
        }

        public void bind(FirestoreUtil.SearchResult result) {
            Message message = result.getMessage();
            
            tvMessageText.setText(message.getDisplayText());
            tvMessageTime.setText(formatTime(message.getTimestamp()));
            tvChatInfo.setText("Chat: " + result.getChatId()); // This could be enhanced to show chat name
        }

        private String formatTime(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}