package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
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

    public void updateResults(List<FirestoreUtil.SearchResult> newResults) {
        this.searchResults = newResults;
        notifyDataSetChanged();
    }

    public class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivContactImage;
        private TextView tvContactName;
        private TextView tvMessageText;
        private TextView tvTimestamp;
        private TextView tvChatName;

        public SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            ivContactImage = itemView.findViewById(R.id.ivContactImage);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvChatName = itemView.findViewById(R.id.tvChatName);
        }

        public void bind(FirestoreUtil.SearchResult result) {
            // Set contact name
            if (result.getContactName() != null) {
                tvContactName.setText(result.getContactName());
            } else {
                tvContactName.setText("Unknown Contact");
            }

            // Set message text (highlight search terms if needed)
            if (result.getMessageText() != null) {
                tvMessageText.setText(result.getMessageText());
                tvMessageText.setVisibility(View.VISIBLE);
            } else {
                tvMessageText.setVisibility(View.GONE);
            }

            // Set timestamp
            if (result.getTimestamp() > 0) {
                String timeText = formatTimestamp(result.getTimestamp());
                tvTimestamp.setText(timeText);
                tvTimestamp.setVisibility(View.VISIBLE);
            } else {
                tvTimestamp.setVisibility(View.GONE);
            }

            // Set chat name (if different from contact name)
            if (result.getChatName() != null && !result.getChatName().equals(result.getContactName())) {
                tvChatName.setText("in " + result.getChatName());
                tvChatName.setVisibility(View.VISIBLE);
            } else {
                tvChatName.setVisibility(View.GONE);
            }

            // Load profile image
            if (result.getContactImageUrl() != null && !result.getContactImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(result.getContactImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(ivContactImage);
            } else {
                ivContactImage.setImageResource(R.drawable.ic_person);
            }

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSearchResultClick(result);
                }
            });
        }

        private String formatTimestamp(long timestamp) {
            Date date = new Date(timestamp);
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 24 * 60 * 60 * 1000) { // Less than 24 hours
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return timeFormat.format(date);
            } else if (diff < 7 * 24 * 60 * 60 * 1000) { // Less than 7 days
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                return dayFormat.format(date);
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                return dateFormat.format(date);
            }
        }
    }
}