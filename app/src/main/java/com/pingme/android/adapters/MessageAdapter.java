package com.pingme.android.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.pingme.android.R;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_DATE_HEADER = 0;
    private static final int VIEW_TYPE_MESSAGE_SENT = 1;
    private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;

    private Context context;
    private List<Object> items;
    private String currentUserId;
    private User otherUser;
    private boolean otherUserShowsProfilePhoto = true;
    private boolean otherUserShowsLastSeen = true;

    public MessageAdapter(List<Object> items, User otherUser) {
        this.items = items != null ? items : new ArrayList<>();
        this.otherUser = otherUser;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public void updateOtherUser(User otherUser) {
        this.otherUser = otherUser;
        if (otherUser != null) {
            this.otherUserShowsProfilePhoto = otherUser.isProfilePhotoEnabled();
            this.otherUserShowsLastSeen = otherUser.isLastSeenEnabled();
        }
        notifyDataSetChanged();
    }

    public void updatePrivacySettings(boolean showsProfilePhoto, boolean showsLastSeen) {
        this.otherUserShowsProfilePhoto = showsProfilePhoto;
        this.otherUserShowsLastSeen = showsLastSeen;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof String) {
            return VIEW_TYPE_DATE_HEADER;
        } else if (item instanceof Message) {
            Message message = (Message) item;
            if (message.getSenderId().equals(currentUserId)) {
                return VIEW_TYPE_MESSAGE_SENT;
            } else {
                return VIEW_TYPE_MESSAGE_RECEIVED;
            }
        }
        return VIEW_TYPE_MESSAGE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        switch (viewType) {
            case VIEW_TYPE_DATE_HEADER:
                View dateView = inflater.inflate(R.layout.item_date_header, parent, false);
                return new DateHeaderViewHolder(dateView);
            case VIEW_TYPE_MESSAGE_SENT:
                View sentView = inflater.inflate(R.layout.item_message_sent, parent, false);
                return new SentMessageViewHolder(sentView);
            case VIEW_TYPE_MESSAGE_RECEIVED:
                View receivedView = inflater.inflate(R.layout.item_message_received, parent, false);
                return new ReceivedMessageViewHolder(receivedView);
            default:
                View defaultView = inflater.inflate(R.layout.item_message_received, parent, false);
                return new ReceivedMessageViewHolder(defaultView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);

        if (holder instanceof DateHeaderViewHolder && item instanceof String) {
            ((DateHeaderViewHolder) holder).bind((String) item);
        } else if (holder instanceof SentMessageViewHolder && item instanceof Message) {
            ((SentMessageViewHolder) holder).bind((Message) item);
        } else if (holder instanceof ReceivedMessageViewHolder && item instanceof Message) {
            ((ReceivedMessageViewHolder) holder).bind((Message) item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Static method to add date headers to messages
    public static void addDateHeaders(List<Object> items, List<Message> messages) {
        items.clear();

        // Sort messages by timestamp
        Collections.sort(messages, (m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));

        String currentDateHeader = null;

        for (Message message : messages) {
            String messageDate = getDateHeader(message.getTimestamp());

            if (!messageDate.equals(currentDateHeader)) {
                items.add(messageDate);
                currentDateHeader = messageDate;
            }

            items.add(message);
        }
    }

    private static String getDateHeader(long timestamp) {
        Calendar today = Calendar.getInstance();
        Calendar messageDate = Calendar.getInstance();
        messageDate.setTimeInMillis(timestamp);

        // Check if it's today
        if (isSameDay(today, messageDate)) {
            return "Today";
        }

        // Check if it's yesterday
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, messageDate)) {
            return "Yesterday";
        }

        // Check if it's this week
        Calendar weekAgo = Calendar.getInstance();
        weekAgo.add(Calendar.DAY_OF_YEAR, -7);
        if (messageDate.after(weekAgo)) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            return dayFormat.format(new Date(timestamp));
        }

        // Check if it's this year
        if (today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd", Locale.getDefault());
            return dateFormat.format(new Date(timestamp));
        }

        // Older than this year
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        return dateFormat.format(new Date(timestamp));
    }

    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    // Date Header ViewHolder
    class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;

        public DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
        }

        public void bind(String date) {
            tvDate.setText(date);
        }
    }

    // Sent Message ViewHolder
    class SentMessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutTextMessage, layoutImage, layoutVideo, layoutAudio, layoutDocument;
        TextView tvMessage, tvTime, tvImageCaption, tvImageTime, tvVideoTime, tvAudioTime, tvDocumentTime;
        TextView tvAudioDuration, tvDocumentName, tvDocumentSize, tvVideoDuration;
        ImageView ivStatus, ivImageStatus, ivVideoStatus, ivAudioStatus, ivDocumentStatus;
        ImageView ivMessageImage, ivVideoThumbnail, ivPlayButton, ivPlayAudio;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutTextMessage = itemView.findViewById(R.id.layoutTextMessage);
            layoutImage = itemView.findViewById(R.id.layoutImage);
            layoutVideo = itemView.findViewById(R.id.layoutVideo);
            layoutAudio = itemView.findViewById(R.id.layoutAudio);
            layoutDocument = itemView.findViewById(R.id.layoutDocument);

            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvImageCaption = itemView.findViewById(R.id.tvImageCaption);
            tvImageTime = itemView.findViewById(R.id.tvImageTime);
            tvVideoTime = itemView.findViewById(R.id.tvVideoTime);
            tvAudioTime = itemView.findViewById(R.id.tvAudioTime);
            tvDocumentTime = itemView.findViewById(R.id.tvDocumentTime);

            tvAudioDuration = itemView.findViewById(R.id.tvAudioDuration);
            tvDocumentName = itemView.findViewById(R.id.tvDocumentName);
            tvDocumentSize = itemView.findViewById(R.id.tvDocumentSize);
            tvVideoDuration = itemView.findViewById(R.id.tvVideoDuration);

            ivStatus = itemView.findViewById(R.id.ivStatus);
            ivImageStatus = itemView.findViewById(R.id.ivImageStatus);
            ivVideoStatus = itemView.findViewById(R.id.ivVideoStatus);
            ivAudioStatus = itemView.findViewById(R.id.ivAudioStatus);
            ivDocumentStatus = itemView.findViewById(R.id.ivDocumentStatus);

            ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
            ivPlayButton = itemView.findViewById(R.id.ivPlayButton);
            ivPlayAudio = itemView.findViewById(R.id.ivPlayAudio);
        }

        public void bind(Message message) {
            hideAllLayouts();

            String messageType = message.getType();
            String timeText = getFormattedTime(message.getTimestamp());

            switch (messageType) {
                case Message.TYPE_TEXT:
                    layoutTextMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText(message.getText());
                    tvTime.setText(timeText);
                    setMessageStatus(ivStatus, message.getStatus());
                    break;

                case Message.TYPE_IMAGE:
                    layoutImage.setVisibility(View.VISIBLE);
                    loadMessageImage(message.getImageUrl());
                    tvImageTime.setText(timeText);
                    setMessageStatus(ivImageStatus, message.getStatus());

                    // Set click listener to view full image in ImageViewerActivity
                    ivMessageImage.setOnClickListener(v -> {
                        Intent intent = com.pingme.android.activities.ImageViewerActivity.createIntent(
                                context,
                                message.getImageUrl(),
                                "You", // Sender name for sent messages
                                currentUserId,
                                message.getTimestamp(),
                                "" // Chat ID if needed
                        );
                        context.startActivity(intent);
                    });
                    break;

                case Message.TYPE_VIDEO:
                    layoutVideo.setVisibility(View.VISIBLE);
                    loadVideoThumbnail(message.getThumbnailUrl());
                    tvVideoTime.setText(timeText);
                    setMessageStatus(ivVideoStatus, message.getStatus());

                    if (message.getDuration() > 0) {
                        tvVideoDuration.setText(formatDuration(message.getDuration()));
                        tvVideoDuration.setVisibility(View.VISIBLE);
                    }

                    // Set click listener to play video
                    ivPlayButton.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(message.getVideoUrl()), "video/*");
                        context.startActivity(intent);
                    });
                    break;

                case Message.TYPE_AUDIO:
                    layoutAudio.setVisibility(View.VISIBLE);
                    tvAudioTime.setText(timeText);
                    setMessageStatus(ivAudioStatus, message.getStatus());

                    if (message.getDuration() > 0) {
                        tvAudioDuration.setText(formatDuration(message.getDuration()));
                    }
                    break;
            }
        }

        private void hideAllLayouts() {
            layoutTextMessage.setVisibility(View.GONE);
            layoutImage.setVisibility(View.GONE);
            layoutVideo.setVisibility(View.GONE);
            layoutAudio.setVisibility(View.GONE);
            layoutDocument.setVisibility(View.GONE);
        }

        private void loadMessageImage(String imageUrl) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(context)
                        .load(imageUrl)
                        .transform(new RoundedCorners(24))
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(ivMessageImage);
            }
        }

        private void loadVideoThumbnail(String thumbnailUrl) {
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Glide.with(context)
                        .load(thumbnailUrl)
                        .transform(new RoundedCorners(24))
                        .placeholder(R.drawable.ic_video_placeholder)
                        .into(ivVideoThumbnail);
            }
        }

        private void setMessageStatus(ImageView statusIcon, int status) {
            switch (status) {
                case Message.STATUS_SENT:
                    statusIcon.setImageResource(R.drawable.ic_sent);
                    break;
                case Message.STATUS_DELIVERED:
                    statusIcon.setImageResource(R.drawable.ic_delivered);
                    break;
                case Message.STATUS_READ:
                    statusIcon.setImageResource(R.drawable.ic_read);
                    break;
            }
        }
    }

    // Received Message ViewHolder
    class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutProfile, layoutTextMessage, layoutImage, layoutVideo, layoutAudio, layoutDocument;
        TextView tvMessage, tvTime, tvImageCaption, tvImageTime, tvVideoTime, tvAudioTime, tvDocumentTime;
        TextView tvAudioDuration, tvDocumentName, tvDocumentSize, tvVideoDuration;
        ImageView ivProfile, ivMessageImage, ivVideoThumbnail, ivPlayButton, ivPlayAudio;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutProfile = itemView.findViewById(R.id.layoutProfile);
            layoutTextMessage = itemView.findViewById(R.id.layoutTextMessage);
            layoutImage = itemView.findViewById(R.id.layoutImage);
            layoutVideo = itemView.findViewById(R.id.layoutVideo);
            layoutAudio = itemView.findViewById(R.id.layoutAudio);
            layoutDocument = itemView.findViewById(R.id.layoutDocument);

            ivProfile = itemView.findViewById(R.id.ivProfile);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvImageCaption = itemView.findViewById(R.id.tvImageCaption);
            tvImageTime = itemView.findViewById(R.id.tvImageTime);
            tvVideoTime = itemView.findViewById(R.id.tvVideoTime);
            tvAudioTime = itemView.findViewById(R.id.tvAudioTime);
            tvDocumentTime = itemView.findViewById(R.id.tvDocumentTime);

            tvAudioDuration = itemView.findViewById(R.id.tvAudioDuration);
            tvDocumentName = itemView.findViewById(R.id.tvDocumentName);
            tvDocumentSize = itemView.findViewById(R.id.tvDocumentSize);
            tvVideoDuration = itemView.findViewById(R.id.tvVideoDuration);

            ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
            ivPlayButton = itemView.findViewById(R.id.ivPlayButton);
            ivPlayAudio = itemView.findViewById(R.id.ivPlayAudio);
        }

        public void bind(Message message) {
            hideAllLayouts();

            // Load profile image based on privacy settings
            if (otherUserShowsProfilePhoto && otherUser != null &&
                    otherUser.getImageUrl() != null && !otherUser.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(otherUser.getImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.defaultprofile)
                        .into(ivProfile);
            } else {
                ivProfile.setImageResource(R.drawable.defaultprofile);
            }

            String messageType = message.getType();
            String timeText = getFormattedTime(message.getTimestamp());

            switch (messageType) {
                case Message.TYPE_TEXT:
                    layoutTextMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText(message.getText());
                    tvTime.setText(timeText);
                    break;

                case Message.TYPE_IMAGE:
                    layoutImage.setVisibility(View.VISIBLE);
                    loadMessageImage(message.getImageUrl());
                    tvImageTime.setText(timeText);

                    // Set click listener to view full image in ImageViewerActivity
                    ivMessageImage.setOnClickListener(v -> {
                        String senderName = otherUser != null ? otherUser.getDisplayName() : "Unknown";
                        Intent intent = com.pingme.android.activities.ImageViewerActivity.createIntent(
                                context,
                                message.getImageUrl(),
                                senderName,
                                message.getSenderId(),
                                message.getTimestamp(),
                                "" // Chat ID if needed
                        );
                        context.startActivity(intent);
                    });
                    break;

                case Message.TYPE_VIDEO:
                    layoutVideo.setVisibility(View.VISIBLE);
                    loadVideoThumbnail(message.getThumbnailUrl());
                    tvVideoTime.setText(timeText);

                    if (message.getDuration() > 0) {
                        tvVideoDuration.setText(formatDuration(message.getDuration()));
                        tvVideoDuration.setVisibility(View.VISIBLE);
                    }

                    // Set click listener to play video
                    ivPlayButton.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(message.getVideoUrl()), "video/*");
                        context.startActivity(intent);
                    });
                    break;

                case Message.TYPE_AUDIO:
                    layoutAudio.setVisibility(View.VISIBLE);
                    tvAudioTime.setText(timeText);

                    if (message.getDuration() > 0) {
                        tvAudioDuration.setText(formatDuration(message.getDuration()));
                    }
                    break;
            }
        }

        private void hideAllLayouts() {
            layoutTextMessage.setVisibility(View.GONE);
            layoutImage.setVisibility(View.GONE);
            layoutVideo.setVisibility(View.GONE);
            layoutAudio.setVisibility(View.GONE);
            layoutDocument.setVisibility(View.GONE);
        }

        private void loadMessageImage(String imageUrl) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(context)
                        .load(imageUrl)
                        .transform(new RoundedCorners(24))
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(ivMessageImage);
            }
        }

        private void loadVideoThumbnail(String thumbnailUrl) {
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Glide.with(context)
                        .load(thumbnailUrl)
                        .transform(new RoundedCorners(24))
                        .placeholder(R.drawable.ic_video_placeholder)
                        .into(ivVideoThumbnail);
            }
        }
    }

    private String getFormattedTime(long timestamp) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return timeFormat.format(new Date(timestamp));
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    // Reply mode functionality
    private boolean isReplyMode = false;
    private Message replyToMessage = null;

    public void setReplyMode(boolean replyMode, Message message) {
        this.isReplyMode = replyMode;
        this.replyToMessage = message;
        notifyDataSetChanged();
    }

    public boolean isReplyMode() {
        return isReplyMode;
    }

    public Message getReplyToMessage() {
        return replyToMessage;
    }
}