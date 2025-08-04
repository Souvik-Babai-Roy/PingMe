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
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_DATE_HEADER = 3;

    private List<Object> items; // Can contain Message or DateHeader objects
    private String currentUserId;
    private User otherUser; // For showing profile picture
    private boolean showProfilePicture = true;
    private boolean showOnlineStatus = true;

    public MessageAdapter(List<Object> items, User otherUser) {
        this.items = items;
        this.otherUser = otherUser;
        this.currentUserId = FirebaseAuth.getInstance().getUid();
    }

    public void updatePrivacySettings(boolean showProfilePicture, boolean showOnlineStatus) {
        this.showProfilePicture = showProfilePicture;
        this.showOnlineStatus = showOnlineStatus;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case VIEW_TYPE_SENT:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
            case VIEW_TYPE_RECEIVED:
                return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
            case VIEW_TYPE_DATE_HEADER:
                return new DateHeaderViewHolder(inflater.inflate(R.layout.item_date_header, parent, false));
            default:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);

        if (holder instanceof SentMessageViewHolder && item instanceof Message) {
            ((SentMessageViewHolder) holder).bind((Message) item);
        } else if (holder instanceof ReceivedMessageViewHolder && item instanceof Message) {
            ((ReceivedMessageViewHolder) holder).bind((Message) item);
        } else if (holder instanceof DateHeaderViewHolder && item instanceof DateHeader) {
            ((DateHeaderViewHolder) holder).bind((DateHeader) item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);

        if (item instanceof DateHeader) {
            return VIEW_TYPE_DATE_HEADER;
        } else if (item instanceof Message) {
            Message message = (Message) item;
            if (message.getSenderId().equals(currentUserId)) {
                return VIEW_TYPE_SENT;
            } else {
                return VIEW_TYPE_RECEIVED;
            }
        }
        return VIEW_TYPE_SENT;
    }

    // ===== VIEW HOLDERS =====

    class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        ImageView ivStatus, ivMessageImage, ivVideoThumbnail, ivPlayButton;
        View layoutImage, layoutVideo;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            ivStatus = itemView.findViewById(R.id.ivStatus);
            ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
            ivPlayButton = itemView.findViewById(R.id.ivPlayButton);
            layoutImage = itemView.findViewById(R.id.layoutImage);
            layoutVideo = itemView.findViewById(R.id.layoutVideo);
        }

        void bind(Message message) {
            // Hide all media layouts first
            if (layoutImage != null) layoutImage.setVisibility(View.GONE);
            if (layoutVideo != null) layoutVideo.setVisibility(View.GONE);

            // Set time
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(new Date(message.getTimestamp())));

            // Set status icon
            switch (message.getStatus()) {
                case Message.STATUS_SENT:
                    ivStatus.setImageResource(R.drawable.ic_sent);
                    break;
                case Message.STATUS_DELIVERED:
                    ivStatus.setImageResource(R.drawable.ic_delivered);
                    break;
                case Message.STATUS_READ:
                    ivStatus.setImageResource(R.drawable.ic_read);
                    break;
            }

            // Handle different message types
            switch (message.getType()) {
                case Message.TYPE_TEXT:
                    tvMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText(message.getText());
                    break;

                case Message.TYPE_IMAGE:
                    tvMessage.setVisibility(View.GONE);
                    if (layoutImage != null && ivMessageImage != null) {
                        layoutImage.setVisibility(View.VISIBLE);
                        Glide.with(itemView.getContext())
                                .load(message.getImageUrl())
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_image_placeholder)
                                .into(ivMessageImage);

                        // Click to view full image
                        ivMessageImage.setOnClickListener(v -> {
                            // TODO: Open full screen image viewer
                        });
                    }
                    break;

                case Message.TYPE_VIDEO:
                    tvMessage.setVisibility(View.GONE);
                    if (layoutVideo != null && ivVideoThumbnail != null) {
                        layoutVideo.setVisibility(View.VISIBLE);

                        // Load video thumbnail
                        if (message.getThumbnailUrl() != null) {
                            Glide.with(itemView.getContext())
                                    .load(message.getThumbnailUrl())
                                    .placeholder(R.drawable.ic_video_placeholder)
                                    .into(ivVideoThumbnail);
                        } else {
                            // Generate thumbnail from video URL
                            Glide.with(itemView.getContext())
                                    .load(message.getVideoUrl())
                                    .placeholder(R.drawable.ic_video_placeholder)
                                    .into(ivVideoThumbnail);
                        }

                        // Click to play video
                        layoutVideo.setOnClickListener(v -> {
                            // TODO: Open video player
                        });
                    }
                    break;

                case Message.TYPE_AUDIO:
                    tvMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText("🎵 Audio " + formatDuration(message.getDuration()));
                    // TODO: Add audio player UI
                    break;
            }
        }
    }

    class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        ImageView ivProfile, ivMessageImage, ivVideoThumbnail, ivPlayButton;
        View layoutImage, layoutVideo, layoutProfile;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            ivProfile = itemView.findViewById(R.id.ivProfile);
            ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
            ivPlayButton = itemView.findViewById(R.id.ivPlayButton);
            layoutImage = itemView.findViewById(R.id.layoutImage);
            layoutVideo = itemView.findViewById(R.id.layoutVideo);
            layoutProfile = itemView.findViewById(R.id.layoutProfile);
        }

        void bind(Message message) {
            // Hide all media layouts first
            if (layoutImage != null) layoutImage.setVisibility(View.GONE);
            if (layoutVideo != null) layoutVideo.setVisibility(View.GONE);

            // Set time
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(new Date(message.getTimestamp())));

            // Show/hide profile picture based on privacy settings
            if (ivProfile != null && layoutProfile != null) {
                if (showProfilePicture && otherUser != null && otherUser.getImageUrl() != null && !otherUser.getImageUrl().isEmpty()) {
                    layoutProfile.setVisibility(View.VISIBLE);
                    Glide.with(itemView.getContext())
                            .load(otherUser.getImageUrl())
                            .transform(new CircleCrop())
                            .placeholder(R.drawable.ic_profile)
                            .into(ivProfile);
                } else {
                    layoutProfile.setVisibility(View.VISIBLE);
                    ivProfile.setImageResource(R.drawable.ic_profile);
                }
            }

            // Handle different message types (same as sent messages)
            switch (message.getType()) {
                case Message.TYPE_TEXT:
                    tvMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText(message.getText());
                    break;

                case Message.TYPE_IMAGE:
                    tvMessage.setVisibility(View.GONE);
                    if (layoutImage != null && ivMessageImage != null) {
                        layoutImage.setVisibility(View.VISIBLE);
                        Glide.with(itemView.getContext())
                                .load(message.getImageUrl())
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_image_placeholder)
                                .into(ivMessageImage);
                    }
                    break;

                case Message.TYPE_VIDEO:
                    tvMessage.setVisibility(View.GONE);
                    if (layoutVideo != null && ivVideoThumbnail != null) {
                        layoutVideo.setVisibility(View.VISIBLE);

                        if (message.getThumbnailUrl() != null) {
                            Glide.with(itemView.getContext())
                                    .load(message.getThumbnailUrl())
                                    .placeholder(R.drawable.ic_video_placeholder)
                                    .into(ivVideoThumbnail);
                        } else {
                            Glide.with(itemView.getContext())
                                    .load(message.getVideoUrl())
                                    .placeholder(R.drawable.ic_video_placeholder)
                                    .into(ivVideoThumbnail);
                        }
                    }
                    break;

                case Message.TYPE_AUDIO:
                    tvMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText("🎵 Audio " + formatDuration(message.getDuration()));
                    break;
            }
        }
    }

    class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;

        public DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
        }

        void bind(DateHeader dateHeader) {
            tvDate.setText(dateHeader.getFormattedDate());
        }
    }

    // ===== HELPER CLASSES =====

    public static class DateHeader {
        private long timestamp;

        public DateHeader(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getFormattedDate() {
            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            Calendar messageDate = Calendar.getInstance();
            messageDate.setTimeInMillis(timestamp);

            if (isSameDay(today, messageDate)) {
                return "Today";
            } else if (isSameDay(yesterday, messageDate)) {
                return "Yesterday";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            }
        }

        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }
    }

    // ===== HELPER METHODS =====

    private String formatDuration(long durationMillis) {
        long seconds = durationMillis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    // Method to add date headers to message list
    public static void addDateHeaders(List<Object> items, List<Message> messages) {
        items.clear();

        if (messages.isEmpty()) return;

        Calendar currentDate = Calendar.getInstance();
        currentDate.setTimeInMillis(0); // Reset to ensure first date header is added

        for (Message message : messages) {
            Calendar messageDate = Calendar.getInstance();
            messageDate.setTimeInMillis(message.getTimestamp());

            // Add date header if day changed
            if (!isSameDay(currentDate, messageDate)) {
                items.add(new DateHeader(message.getTimestamp()));
                currentDate.setTimeInMillis(message.getTimestamp());
            }

            items.add(message);
        }
    }

    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}