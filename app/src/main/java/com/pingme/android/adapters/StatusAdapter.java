package com.pingme.android.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
import com.pingme.android.databinding.ItemStatusBinding;
import com.pingme.android.models.Status;

import java.util.List;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.StatusViewHolder> {
    private final Context context;
    private final List<Status> statusList;
    private final String currentUserId;
    private final OnStatusClickListener listener;

    public interface OnStatusClickListener {
        void onStatusClick(Status status, int position);
    }

    public StatusAdapter(Context context, List<Status> statusList, String currentUserId, OnStatusClickListener listener) {
        this.context = context;
        this.statusList = statusList;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StatusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStatusBinding binding = ItemStatusBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new StatusViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull StatusViewHolder holder, int position) {
        Status status = statusList.get(position);
        holder.bind(status, position);
    }

    @Override
    public int getItemCount() {
        return statusList.size();
    }

    public class StatusViewHolder extends RecyclerView.ViewHolder {
        private final ItemStatusBinding binding;

        public StatusViewHolder(ItemStatusBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Status status, int position) {
            // Set user name
            binding.textUserName.setText(status.getUserName() != null ? status.getUserName() : "Unknown User");

            // Set timestamp
            binding.textTime.setText(status.getFormattedTimeAgo());

            // Load user profile image
            if (status.getUserImageUrl() != null && !status.getUserImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(status.getUserImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person_outline)
                        .into(binding.imageUserProfile);
            } else {
                binding.imageUserProfile.setImageResource(R.drawable.ic_person_outline);
            }

            // Set status indicator based on viewed status
            if (status.isViewed()) {
                // User has viewed this status
                binding.statusIndicator.setBorderWidth(2);
                binding.statusIndicator.setBorderColor(context.getColor(R.color.gray_light));
            } else {
                // User hasn't viewed this status
                binding.statusIndicator.setBorderWidth(6);
                binding.statusIndicator.setBorderColor(context.getColor(R.color.colorPrimary));
            }

            // Handle different status types
            if (status.isTextStatus()) {
                bindTextStatus(status);
            } else if (status.isImageStatus()) {
                bindImageStatus(status);
            } else if (status.isVideoStatus()) {
                bindVideoStatus(status);
            }

            // Set click listener
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStatusClick(status, position);
                }
            });

            // Show viewer count for own statuses
            if (status.getUserId().equals(currentUserId)) {
                binding.textViewerCount.setVisibility(View.VISIBLE);
                int viewerCount = status.getViewerCount();
                if (viewerCount > 0) {
                    binding.textViewerCount.setText(viewerCount + " view" + (viewerCount > 1 ? "s" : ""));
                } else {
                    binding.textViewerCount.setText("No views");
                }
            } else {
                binding.textViewerCount.setVisibility(View.GONE);
            }
        }

        private void bindTextStatus(Status status) {
            binding.statusPreview.setVisibility(View.VISIBLE);
            binding.statusText.setVisibility(View.VISIBLE);
            binding.statusImage.setVisibility(View.GONE);
            binding.playIcon.setVisibility(View.GONE);

            // Set text content
            binding.statusText.setText(status.getContent());

            // Set background color
            if (status.getBackgroundColor() != null && !status.getBackgroundColor().isEmpty()) {
                try {
                    binding.statusPreview.setBackgroundColor(Color.parseColor(status.getBackgroundColor()));
                } catch (IllegalArgumentException e) {
                    binding.statusPreview.setBackgroundColor(context.getColor(R.color.colorPrimary));
                }
            } else {
                binding.statusPreview.setBackgroundColor(context.getColor(R.color.colorPrimary));
            }
        }

        private void bindImageStatus(Status status) {
            binding.statusPreview.setVisibility(View.VISIBLE);
            binding.statusText.setVisibility(View.GONE);
            binding.statusImage.setVisibility(View.VISIBLE);
            binding.playIcon.setVisibility(View.GONE);

            // Load status image
            if (status.getImageUrl() != null && !status.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(status.getImageUrl())
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(binding.statusImage);
            } else {
                binding.statusImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Show caption if available
            if (status.getContent() != null && !status.getContent().trim().isEmpty()) {
                binding.statusText.setVisibility(View.VISIBLE);
                binding.statusText.setText(status.getContent());
                binding.statusText.setBackgroundColor(Color.parseColor("#80000000")); // Semi-transparent background
                binding.statusText.setTextColor(Color.WHITE);
            }
        }

        private void bindVideoStatus(Status status) {
            binding.statusPreview.setVisibility(View.VISIBLE);
            binding.statusText.setVisibility(View.GONE);
            binding.statusImage.setVisibility(View.VISIBLE);
            binding.playIcon.setVisibility(View.VISIBLE);

            // Load video thumbnail (for now, use placeholder)
            // TODO: Implement video thumbnail loading
            binding.statusImage.setImageResource(R.drawable.ic_video_placeholder);

            // Show caption if available
            if (status.getContent() != null && !status.getContent().trim().isEmpty()) {
                binding.statusText.setVisibility(View.VISIBLE);
                binding.statusText.setText(status.getContent());
                binding.statusText.setBackgroundColor(Color.parseColor("#80000000")); // Semi-transparent background
                binding.statusText.setTextColor(Color.WHITE);
            }
        }
    }
}