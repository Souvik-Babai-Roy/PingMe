package com.pingme.android.adapters;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.pingme.android.R;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {
    
    private List<FirebaseUtil.SearchResult> searchResults;
    private OnSearchResultClickListener listener;
    private String searchQuery = "";
    private String currentUserId;

    public interface OnSearchResultClickListener {
        void onSearchResultClick(FirebaseUtil.SearchResult result);
    }

    public SearchResultAdapter(List<FirebaseUtil.SearchResult> searchResults, OnSearchResultClickListener listener) {
        this.searchResults = searchResults;
        this.listener = listener;
        // Initialize current user ID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            this.currentUserId = currentUser.getUid();
        } else {
            this.currentUserId = "";
        }
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
        FirebaseUtil.SearchResult result = searchResults.get(position);
        holder.bind(result);
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    public void updateResults(List<FirebaseUtil.SearchResult> newResults) {
        this.searchResults = newResults;
        notifyDataSetChanged();
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query;
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

        public void bind(FirebaseUtil.SearchResult result) {
            // Add logging for debugging
            Log.d("SearchResultAdapter", "Binding search result - senderId: " + result.getSenderId() + 
                  ", contactImageUrl: " + result.getContactImageUrl() + 
                  ", senderName: " + result.getSenderName());
            
            // Test ImageView with a simple drawable first
            ivContactImage.setImageResource(R.drawable.ic_person);
            Log.d("SearchResultAdapter", "Set test image - drawable: " + ivContactImage.getDrawable());
            
            // Set contact name with better fallback logic
            String displayName = result.getSenderName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = result.getContactName();
            }
            
            // Provide more informative fallbacks
            if (displayName == null || displayName.trim().isEmpty()) {
                String senderId = result.getSenderId();
                if (senderId != null && !senderId.isEmpty()) {
                    // Show partial sender ID as last resort
                    displayName = "User " + senderId.substring(0, Math.min(8, senderId.length()));
                } else {
                    displayName = "Unknown Contact";
                }
            }
            
            tvContactName.setText(displayName);

            // Set message text with search query highlighting
            String messageText = result.getMessageText();
            if (messageText != null && !messageText.isEmpty()) {
                SpannableString spannableText = highlightSearchQuery(messageText, searchQuery);
                tvMessageText.setText(spannableText);
                tvMessageText.setVisibility(View.VISIBLE);
            } else if (result.getMessage() != null && result.getMessage().getFileName() != null) {
                // Show file name if it's a file message
                String fileName = result.getMessage().getFileName();
                SpannableString spannableText = highlightSearchQuery("📎 " + fileName, searchQuery);
                tvMessageText.setText(spannableText);
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

            // Always load profile image directly from user data for consistent display
            String senderId = result.getSenderId();
            Log.d("SearchResultAdapter", "Loading avatar for senderId: " + senderId);
            
            // Debug ImageView properties
            Log.d("SearchResultAdapter", "ImageView - width: " + ivContactImage.getWidth() + 
                  ", height: " + ivContactImage.getHeight() + 
                  ", visibility: " + ivContactImage.getVisibility() + 
                  ", drawable: " + ivContactImage.getDrawable());
            
            if (senderId != null) {
                loadUserAvatarDirectly(senderId, ivContactImage);
            } else {
                Log.w("SearchResultAdapter", "No senderId, setting default avatar");
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

        private SpannableString highlightSearchQuery(String text, String searchQuery) {
            SpannableString spannableString = new SpannableString(text);
            
            if (searchQuery != null && !searchQuery.isEmpty()) {
                String lowerText = text.toLowerCase();
                String lowerQuery = searchQuery.toLowerCase();
                
                int startIndex = 0;
                while ((startIndex = lowerText.indexOf(lowerQuery, startIndex)) != -1) {
                    int endIndex = startIndex + searchQuery.length();
                    
                    // Highlight with background color
                    spannableString.setSpan(
                        new BackgroundColorSpan(ContextCompat.getColor(itemView.getContext(), R.color.search_highlight_background)),
                        startIndex, 
                        endIndex, 
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    
                    // Make text bold
                    spannableString.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        startIndex,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    
                    startIndex = endIndex;
                }
            }
            
            return spannableString;
        }

        private void loadUserAvatarDirectly(String userId, ImageView imageView) {
            // Add logging for debugging
            Log.d("SearchResultAdapter", "Loading avatar for user: " + userId + ", currentUser: " + currentUserId);
            
            FirebaseUtil.getUserRef(userId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        // Use class-level current user ID for efficiency
                        boolean isCurrentUser = userId.equals(currentUserId);
                        
                        // Apply same logic as ChatListAdapter: current user always sees their avatar
                        boolean shouldShowAvatar = isCurrentUser || user.isProfilePhotoEnabled();
                        
                        Log.d("SearchResultAdapter", "User: " + userId + 
                              ", isCurrentUser: " + isCurrentUser + 
                              ", profilePhotoEnabled: " + user.isProfilePhotoEnabled() + 
                              ", shouldShowAvatar: " + shouldShowAvatar + 
                              ", hasImageUrl: " + (user.getImageUrl() != null && !user.getImageUrl().trim().isEmpty()));
                        
                        if (shouldShowAvatar && user.getImageUrl() != null && !user.getImageUrl().trim().isEmpty()) {
                            try {
                                Log.d("SearchResultAdapter", "Loading avatar with Glide: " + user.getImageUrl());
                                
                                // Clear any previous image and set placeholder
                                imageView.setImageResource(R.drawable.ic_person);
                                
                                Glide.with(itemView.getContext())
                                        .load(user.getImageUrl())
                                        .transform(new CircleCrop())
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .listener(new RequestListener<Drawable>() {
                                            @Override
                                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                                Log.e("SearchResultAdapter", "Glide onLoadFailed for user: " + userId + ", error: " + e);
                                                // Manually set error image
                                                imageView.post(() -> imageView.setImageResource(R.drawable.ic_person));
                                                return true; // Prevent Glide from handling the error
                                            }

                                            @Override
                                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                                Log.d("SearchResultAdapter", "Glide onResourceReady for user: " + userId + ", resource: " + resource);
                                                // Manually set the loaded image
                                                imageView.post(() -> imageView.setImageDrawable(resource));
                                                return true; // Prevent Glide from handling the resource
                                            }
                                        })
                                        .into(imageView);
                                
                            } catch (Exception e) {
                                Log.e("SearchResultAdapter", "Glide failed for user: " + userId, e);
                                imageView.setImageResource(R.drawable.ic_person);
                            }
                        } else {
                            Log.d("SearchResultAdapter", "Setting default avatar for user: " + userId);
                            imageView.setImageResource(R.drawable.ic_person);
                        }
                    } else {
                        Log.w("SearchResultAdapter", "User object is null for user: " + userId);
                        imageView.setImageResource(R.drawable.ic_person);
                    }
                } else {
                    Log.w("SearchResultAdapter", "User document doesn't exist for user: " + userId);
                    imageView.setImageResource(R.drawable.ic_person);
                }
            }).addOnFailureListener(e -> {
                Log.e("SearchResultAdapter", "Failed to load user data for user: " + userId, e);
                imageView.setImageResource(R.drawable.ic_person);
            });
        }
    }
}