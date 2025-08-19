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
import android.widget.ProgressBar;
import android.util.Log;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.firebase.auth.FirebaseUser;
import com.pingme.android.R;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;
import com.pingme.android.utils.MediaPlayerUtil;
import com.pingme.android.utils.VideoPlayerUtil;
import com.pingme.android.utils.DocumentViewerUtil;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.Toast;

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
    
    // For message highlighting from search
    private String highlightMessageId = null;
    private String searchQuery = null;

    public MessageAdapter(List<Object> items, User otherUser) {
        this.items = items != null ? items : new ArrayList<>();
        this.otherUser = otherUser;
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        this.currentUserId = currentUser != null ? currentUser.getUid() : "";
    }
    
    public void setContext(Context context) {
        this.context = context;
    }

    public void updateOtherUser(User otherUser) {
        this.otherUser = otherUser;
        // Refresh the adapter to reflect any privacy setting changes
        notifyDataSetChanged();
    }

    public void updatePrivacySettings(boolean showsProfilePhoto, boolean showsLastSeen) {
        this.otherUserShowsProfilePhoto = showsProfilePhoto;
        this.otherUserShowsLastSeen = showsLastSeen;
        notifyDataSetChanged();
    }
    
    public void setMessageHighlight(String messageId, String searchQuery) {
        this.highlightMessageId = messageId;
        this.searchQuery = searchQuery;
        notifyDataSetChanged();
    }
    
    public void clearMessageHighlight() {
        this.highlightMessageId = null;
        this.searchQuery = null;
        notifyDataSetChanged();
    }
    
    private CharSequence getHighlightedText(String text, String messageId) {
        if (text == null || text.isEmpty()) return text;
        
        // Check if this message should be highlighted
        boolean shouldHighlight = messageId != null && messageId.equals(highlightMessageId);
        
        if (shouldHighlight && searchQuery != null && !searchQuery.isEmpty() && context != null) {
            SpannableString spannableString = new SpannableString(text);
            String lowerText = text.toLowerCase();
            String lowerQuery = searchQuery.toLowerCase();
            
            int startIndex = 0;
            while ((startIndex = lowerText.indexOf(lowerQuery, startIndex)) != -1) {
                int endIndex = startIndex + searchQuery.length();
                
                // Highlight with professional yellow background like WhatsApp
                spannableString.setSpan(
                    new BackgroundColorSpan(ContextCompat.getColor(context, R.color.search_highlight_background)),
                    startIndex, 
                    endIndex, 
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                
                // Make text bold for better visibility
                spannableString.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                
                startIndex = endIndex;
            }
            
            return spannableString;
        }
        
        return text;
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
            Message message = (Message) item;
            // Check if message is visible for current user
            if (message.isVisibleForUser(currentUserId)) {
                ((SentMessageViewHolder) holder).bind(message);
            } else {
                // Hide deleted/cleared messages
                holder.itemView.setVisibility(View.GONE);
            }
        } else if (holder instanceof ReceivedMessageViewHolder && item instanceof Message) {
            Message message = (Message) item;
            // Check if message is visible for current user
            if (message.isVisibleForUser(currentUserId)) {
                ((ReceivedMessageViewHolder) holder).bind(message);
            } else {
                // Hide deleted/cleared messages
                holder.itemView.setVisibility(View.GONE);
            }
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
        ProgressBar progressAudio;

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
            progressAudio = itemView.findViewById(R.id.progressAudio);
        }

        public void bind(Message message) {
            hideAllLayouts();

            String messageType = message.getType();
            String timeText = getFormattedTime(message.getTimestamp());

            switch (messageType) {
                case Message.TYPE_TEXT:
                    layoutTextMessage.setVisibility(View.VISIBLE);
                    tvMessage.setText(getHighlightedText(message.getText(), message.getId()));
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
                        VideoPlayerUtil.getInstance().playVideo(message.getVideoUrl(), context);
                    });
                    break;

                case Message.TYPE_AUDIO:
                    layoutAudio.setVisibility(View.VISIBLE);
                    tvAudioTime.setText(timeText);
                    setMessageStatus(ivAudioStatus, message.getStatus());

                    if (message.getDuration() > 0) {
                        tvAudioDuration.setText(formatDuration(message.getDuration()));
                    }

                    // Reset progress bar and icon
                    if (progressAudio != null) {
                        progressAudio.setProgress(0);
                        if (message.getDuration() > 0) {
                            progressAudio.setMax((int) message.getDuration());
                        }
                    }

                    // Play/Pause toggle with progress updates
                    ivPlayAudio.setOnClickListener(v -> {
                        MediaPlayerUtil mediaPlayer = MediaPlayerUtil.getInstance();
                        String audioUrl = message.getAudioUrl();

                        if (mediaPlayer.isPlayingUrl(audioUrl)) {
                            mediaPlayer.pauseAudio();
                            ivPlayAudio.setImageResource(R.drawable.ic_play_circle);
                            return;
                        }

                        if (mediaPlayer.isCurrentUrl(audioUrl) && !mediaPlayer.isPlaying()) {
                            mediaPlayer.resumeAudio();
                            ivPlayAudio.setImageResource(R.drawable.ic_pause_circle);
                            return;
                        }

                        ivPlayAudio.setImageResource(R.drawable.ic_pause_circle);

                        mediaPlayer.playAudio(audioUrl, context, new MediaPlayerUtil.MediaPlayerListener() {
                            @Override
                            public void onPrepared() {
                                // Initialize progress max from real duration if missing
                                if (progressAudio != null) {
                                    int duration = mediaPlayer.getDuration();
                                    if (duration > 0) progressAudio.setMax(duration);
                                }
                            }

                            @Override
                            public void onCompletion() {
                                itemView.post(() -> {
                                    ivPlayAudio.setImageResource(R.drawable.ic_play_circle);
                                    if (progressAudio != null) progressAudio.setProgress(0);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                Log.e("MessageAdapter", "Audio playback error: " + error);
                                itemView.post(() -> {
                                    Toast.makeText(context, "Audio playback error", Toast.LENGTH_SHORT).show();
                                    ivPlayAudio.setImageResource(R.drawable.ic_play_circle);
                                    if (progressAudio != null) progressAudio.setProgress(0);
                                });
                            }

                            @Override
                            public void onProgress(int currentPosition, int duration) {
                                if (progressAudio != null) {
                                    itemView.post(() -> {
                                        if (progressAudio.getMax() != duration && duration > 0) {
                                            progressAudio.setMax(duration);
                                        }
                                        progressAudio.setProgress(currentPosition);
                                    });
                                }
                            }
                        });
                    });
                    break;

                case Message.TYPE_DOCUMENT:
                    layoutDocument.setVisibility(View.VISIBLE);
                    tvDocumentTime.setText(timeText);
                    setMessageStatus(ivDocumentStatus, message.getStatus());

                    if (tvDocumentName != null) {
                        String name = message.getFileName() != null ? message.getFileName() : "Document";
                        tvDocumentName.setText(name);
                    }
                    if (tvDocumentSize != null) {
                        if (message.getFileSize() > 0) {
                            tvDocumentSize.setText(DocumentViewerUtil.getInstance().formatFileSize(message.getFileSize()));
                            tvDocumentSize.setVisibility(View.VISIBLE);
                        } else {
                            tvDocumentSize.setVisibility(View.GONE);
                        }
                    }

                    layoutDocument.setOnClickListener(v -> {
                        DocumentViewerUtil.getInstance().openDocument(message.getFileUrl(), message.getFileName(), context);
                    });
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
            // Use the enhanced delivery status for better accuracy
            if (statusIcon == null) return;
            
            // Get the current message from the holder's position
            int position = getAdapterPosition();
            if (position >= 0 && position < items.size()) {
                Object item = items.get(position);
                if (item instanceof Message) {
                    Message message = (Message) item;
                    
                    // Use enhanced status calculation with recipient ID for accuracy
                    String recipientId = otherUser != null ? otherUser.getId() : null;
                    int deliveryStatus = message.getDeliveryStatus(currentUserId, recipientId);
                    
                    // Respect receiver's read receipts privacy setting
                    boolean receiverAllowsReadReceipts = otherUser != null && otherUser.isReadReceiptsEnabled();
                    
                    // If receiver has disabled read receipts, don't show blue ticks even if read
                    if (deliveryStatus == Message.STATUS_READ && !receiverAllowsReadReceipts) {
                        deliveryStatus = Message.STATUS_DELIVERED; // Show gray double tick instead
                    }
                    
                    switch (deliveryStatus) {
                        case Message.STATUS_SENT:
                            statusIcon.setImageResource(R.drawable.ic_sent); // Single gray tick
                            break;
                        case Message.STATUS_DELIVERED:
                            statusIcon.setImageResource(R.drawable.ic_delivered); // Double gray tick
                            break;
                        case Message.STATUS_READ:
                            statusIcon.setImageResource(R.drawable.ic_read); // Double blue tick
                            break;
                        default:
                            statusIcon.setImageResource(R.drawable.ic_sent);
                            break;
                    }
                    // Clear any color filter since colors are defined in drawable
                    statusIcon.clearColorFilter();
                }
            }
        }

        private void setMessageStatus(TextView statusText, Message message) {
            if (statusText == null) return;
            
            if (message.isSentByCurrentUser(currentUserId)) {
                String status = message.getStatusText(currentUserId);
                statusText.setText(status);
                
                // Use enhanced status calculation with recipient ID for accuracy
                String recipientId = otherUser != null ? otherUser.getId() : null;
                int deliveryStatus = message.getDeliveryStatus(currentUserId, recipientId);
                
                // Respect receiver's read receipts privacy setting
                boolean receiverAllowsReadReceipts = otherUser != null && otherUser.isReadReceiptsEnabled();
                
                // If receiver has disabled read receipts, don't show blue ticks even if read
                if (deliveryStatus == Message.STATUS_READ && !receiverAllowsReadReceipts) {
                    deliveryStatus = Message.STATUS_DELIVERED; // Show gray double tick instead
                }
                
                switch (deliveryStatus) {
                    case Message.STATUS_SENT:
                        statusText.setTextColor(context.getResources().getColor(R.color.status_sent)); // Gray
                        break;
                    case Message.STATUS_DELIVERED:
                        statusText.setTextColor(context.getResources().getColor(R.color.status_delivered)); // Gray
                        break;
                    case Message.STATUS_READ:
                        statusText.setTextColor(context.getResources().getColor(R.color.status_read)); // Blue
                        break;
                    default:
                        statusText.setTextColor(context.getResources().getColor(R.color.status_sent));
                        break;
                }
            } else {
                statusText.setText("");
            }
        }
    }

    // Received Message ViewHolder
    class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutProfile, layoutTextMessage, layoutImage, layoutVideo, layoutAudio, layoutDocument;
        TextView tvMessage, tvTime, tvImageCaption, tvImageTime, tvVideoTime, tvAudioTime, tvDocumentTime;
        TextView tvAudioDuration, tvDocumentName, tvDocumentSize, tvVideoDuration;
        ImageView ivProfile, ivMessageImage, ivVideoThumbnail, ivPlayButton, ivPlayAudio;
        ProgressBar progressAudio;

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
            progressAudio = itemView.findViewById(R.id.progressAudio);
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
                    tvMessage.setText(getHighlightedText(message.getText(), message.getId()));
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
                        VideoPlayerUtil.getInstance().playVideo(message.getVideoUrl(), context);
                    });
                    break;

                case Message.TYPE_AUDIO:
                    layoutAudio.setVisibility(View.VISIBLE);
                    tvAudioTime.setText(timeText);

                    if (message.getDuration() > 0) {
                        tvAudioDuration.setText(formatDuration(message.getDuration()));
                    }

                    // Reset progress bar and icon
                    if (progressAudio != null) {
                        progressAudio.setProgress(0);
                        if (message.getDuration() > 0) {
                            progressAudio.setMax((int) message.getDuration());
                        }
                    }

                    ivPlayAudio.setOnClickListener(v -> {
                        MediaPlayerUtil mediaPlayer = MediaPlayerUtil.getInstance();
                        String audioUrl = message.getAudioUrl();

                        if (mediaPlayer.isPlayingUrl(audioUrl)) {
                            mediaPlayer.pauseAudio();
                            ivPlayAudio.setImageResource(R.drawable.ic_play_circle);
                            return;
                        }

                        if (mediaPlayer.isCurrentUrl(audioUrl) && !mediaPlayer.isPlaying()) {
                            mediaPlayer.resumeAudio();
                            ivPlayAudio.setImageResource(R.drawable.ic_pause_circle);
                            return;
                        }

                        ivPlayAudio.setImageResource(R.drawable.ic_pause_circle);

                        mediaPlayer.playAudio(audioUrl, context, new MediaPlayerUtil.MediaPlayerListener() {
                            @Override
                            public void onPrepared() {
                                if (progressAudio != null) {
                                    int duration = mediaPlayer.getDuration();
                                    if (duration > 0) progressAudio.setMax(duration);
                                }
                            }

                            @Override
                            public void onCompletion() {
                                itemView.post(() -> {
                                    ivPlayAudio.setImageResource(R.drawable.ic_play_circle);
                                    if (progressAudio != null) progressAudio.setProgress(0);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                Log.e("MessageAdapter", "Audio playback error: " + error);
                                itemView.post(() -> {
                                    Toast.makeText(context, "Audio playback error", Toast.LENGTH_SHORT).show();
                                    ivPlayAudio.setImageResource(R.drawable.ic_play_circle);
                                    if (progressAudio != null) progressAudio.setProgress(0);
                                });
                            }

                            @Override
                            public void onProgress(int currentPosition, int duration) {
                                if (progressAudio != null) {
                                    itemView.post(() -> {
                                        if (progressAudio.getMax() != duration && duration > 0) {
                                            progressAudio.setMax(duration);
                                        }
                                        progressAudio.setProgress(currentPosition);
                                    });
                                }
                            }
                        });
                    });
                    break;

                case Message.TYPE_DOCUMENT:
                    layoutDocument.setVisibility(View.VISIBLE);
                    tvDocumentTime.setText(timeText);

                    if (tvDocumentName != null) {
                        String name = message.getFileName() != null ? message.getFileName() : "Document";
                        tvDocumentName.setText(name);
                    }
                    if (tvDocumentSize != null) {
                        if (message.getFileSize() > 0) {
                            tvDocumentSize.setText(DocumentViewerUtil.getInstance().formatFileSize(message.getFileSize()));
                            tvDocumentSize.setVisibility(View.VISIBLE);
                        } else {
                            tvDocumentSize.setVisibility(View.GONE);
                        }
                    }

                    layoutDocument.setOnClickListener(v -> {
                        DocumentViewerUtil.getInstance().openDocument(message.getFileUrl(), message.getFileName(), context);
                    });
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