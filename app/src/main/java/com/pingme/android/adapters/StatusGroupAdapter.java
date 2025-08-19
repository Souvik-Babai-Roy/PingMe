package com.pingme.android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.models.Status;
import com.pingme.android.models.StatusGroup;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class StatusGroupAdapter extends RecyclerView.Adapter<StatusGroupAdapter.StatusGroupViewHolder> {
    private Context context;
    private List<StatusGroup> statusGroups;
    private OnStatusGroupClickListener listener;

    public interface OnStatusGroupClickListener {
        void onStatusGroupClick(StatusGroup statusGroup, int position);
    }

    public StatusGroupAdapter(Context context, List<StatusGroup> statusGroups, OnStatusGroupClickListener listener) {
        this.context = context;
        this.statusGroups = statusGroups;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StatusGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_status, parent, false);
        return new StatusGroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatusGroupViewHolder holder, int position) {
        StatusGroup statusGroup = statusGroups.get(position);
        holder.bind(statusGroup, position);
    }

    @Override
    public int getItemCount() {
        return statusGroups.size();
    }

    public class StatusGroupViewHolder extends RecyclerView.ViewHolder {
        private de.hdodenhof.circleimageview.CircleImageView statusIndicator;
        private de.hdodenhof.circleimageview.CircleImageView userProfileImage;
        private TextView userNameText;
        private TextView statusCountText;
        private TextView statusPreviewText;
        private TextView viewerCountText;
        private TextView timeText;
        private FrameLayout previewLayout;
        private ImageView previewImage;
        private ImageView videoPlayIcon;

        public StatusGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            userProfileImage = itemView.findViewById(R.id.imageUserProfile);
            userNameText = itemView.findViewById(R.id.textUserName);
            statusCountText = itemView.findViewById(R.id.textStatusCount);
            statusPreviewText = itemView.findViewById(R.id.statusText);
            viewerCountText = itemView.findViewById(R.id.textViewerCount);
            timeText = itemView.findViewById(R.id.textTime);
            previewLayout = itemView.findViewById(R.id.layoutPreview);
            previewImage = itemView.findViewById(R.id.imagePreview);
            videoPlayIcon = itemView.findViewById(R.id.iconVideoPlay);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStatusGroupClick(statusGroups.get(position), position);
                }
            });
        }

        public void bind(StatusGroup statusGroup, int position) {
            // Load user display name
            loadUserDisplayName(statusGroup);

            // Load user profile photo
            loadUserProfilePhoto(statusGroup);

            // Set status ring color based on viewed status
            setStatusRingColor(statusGroup);

            // Set status count (only show if more than 1 status)
            if (statusGroup.getTotalCount() > 1) {
                statusCountText.setVisibility(View.VISIBLE);
                statusCountText.setText(statusGroup.getStatusCountText());
            } else {
                statusCountText.setVisibility(View.GONE);
            }

            // Set status preview text
            String previewText = statusGroup.getPreviewText();
            if (previewText != null && !previewText.isEmpty()) {
                statusPreviewText.setVisibility(View.VISIBLE);
                statusPreviewText.setText(previewText);
            } else {
                statusPreviewText.setVisibility(View.GONE);
            }

            // Set time
            timeText.setText(statusGroup.getFormattedTimeAgo());

            // Handle viewer count for own statuses
            showViewerCountIfOwn(statusGroup);

            // Show preview thumbnail for image/video statuses
            showPreviewThumbnail(statusGroup);
        }

        private void loadUserDisplayName(StatusGroup statusGroup) {
            if (userNameText == null) return;

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                userNameText.setText(statusGroup.getUserName() != null ? statusGroup.getUserName() : "Unknown User");
                return;
            }

            String currentUserId = currentUser.getUid();
            String statusUserId = statusGroup.getUserId();

            // If it's the current user's status, show "My status"
            if (currentUserId.equals(statusUserId)) {
                userNameText.setText("My status");
                return;
            }

            // For other users, try to load personal name from friendship
            FirebaseUtil.getFriendsRef(currentUserId)
                .document(statusUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && userNameText != null) {
                        String personalName = doc.getString("personalName");
                        if (personalName != null && !personalName.trim().isEmpty()) {
                            userNameText.setText(personalName);
                        } else {
                            loadOriginalUserName(statusGroup);
                        }
                    } else {
                        loadOriginalUserName(statusGroup);
                    }
                })
                .addOnFailureListener(e -> loadOriginalUserName(statusGroup));
        }

        private void loadOriginalUserName(StatusGroup statusGroup) {
            if (userNameText == null) return;

            if (statusGroup.getUserName() != null && !statusGroup.getUserName().trim().isEmpty()) {
                userNameText.setText(statusGroup.getUserName());
            } else {
                // Load from user document if statusGroup doesn't have userName
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(statusGroup.getUserId())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists() && userNameText != null) {
                            User user = doc.toObject(User.class);
                            if (user != null) {
                                String displayName = user.getDisplayNameForUser();
                                userNameText.setText(displayName);
                            } else {
                                userNameText.setText("Unknown User");
                            }
                        } else {
                            if (userNameText != null) {
                                userNameText.setText("Unknown User");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (userNameText != null) {
                            userNameText.setText("Unknown User");
                        }
                    });
            }
        }

        private void loadUserProfilePhoto(StatusGroup statusGroup) {
            if (userProfileImage == null) return;

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(statusGroup.getUserId())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && userProfileImage != null) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            // Get current user ID to check if this is the current user's status group
                            com.google.firebase.auth.FirebaseUser currentFirebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                            String currentUserId = currentFirebaseUser != null ? currentFirebaseUser.getUid() : "";
                            boolean isCurrentUser = statusGroup.getUserId().equals(currentUserId);
                            
                            // Current user always sees their own avatar, others see it only if privacy allows
                            boolean shouldShowAvatar = isCurrentUser || user.isProfilePhotoEnabled();
                            
                            if (shouldShowAvatar && user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
                                try {
                                    Glide.with(context)
                                        .load(user.getImageUrl())
                                        .placeholder(R.drawable.defaultprofile)
                                        .error(R.drawable.defaultprofile)
                                        .circleCrop()
                                        .into(userProfileImage);
                                } catch (Exception e) {
                                    userProfileImage.setImageResource(R.drawable.defaultprofile);
                                }
                            } else {
                                userProfileImage.setImageResource(R.drawable.defaultprofile);
                            }
                        } else {
                            userProfileImage.setImageResource(R.drawable.defaultprofile);
                        }
                    } else {
                        userProfileImage.setImageResource(R.drawable.defaultprofile);
                    }
                })
                .addOnFailureListener(e -> {
                    if (userProfileImage != null) {
                        userProfileImage.setImageResource(R.drawable.defaultprofile);
                    }
                });
        }

        private void setStatusRingColor(StatusGroup statusGroup) {
            if (statusIndicator == null) return;

            int borderColor;
            if (statusGroup.hasUnviewedStatus()) {
                // Green ring for unviewed statuses
                borderColor = ContextCompat.getColor(context, R.color.colorPrimary);
            } else {
                // Gray ring for viewed statuses
                borderColor = ContextCompat.getColor(context, R.color.outline_variant);
            }

            // Apply the border color
            statusIndicator.setBorderColor(borderColor);
        }

        private void showViewerCountIfOwn(StatusGroup statusGroup) {
            if (viewerCountText == null) return;

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getUid().equals(statusGroup.getUserId())) {
                // Show viewer count for own statuses
                Status latestStatus = statusGroup.getLatestStatus();
                if (latestStatus != null && latestStatus.getViewers() != null) {
                    int viewerCount = latestStatus.getViewers().size();
                    if (viewerCount > 0) {
                        viewerCountText.setVisibility(View.VISIBLE);
                        String viewText = viewerCount == 1 ? "1 view" : viewerCount + " views";
                        viewerCountText.setText("👁 " + viewText);
                    } else {
                        viewerCountText.setVisibility(View.GONE);
                    }
                } else {
                    viewerCountText.setVisibility(View.GONE);
                }
            } else {
                viewerCountText.setVisibility(View.GONE);
            }
        }

        private void showPreviewThumbnail(StatusGroup statusGroup) {
            if (previewLayout == null || previewImage == null) return;

            Status latestStatus = statusGroup.getLatestStatus();
            if (latestStatus == null) {
                previewLayout.setVisibility(View.GONE);
                return;
            }

            // Show preview for image/video statuses
            if (latestStatus.isImageStatus() && latestStatus.getImageUrl() != null) {
                previewLayout.setVisibility(View.VISIBLE);
                videoPlayIcon.setVisibility(View.GONE);
                
                Glide.with(context)
                    .load(latestStatus.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .centerCrop()
                    .into(previewImage);
                    
            } else if (latestStatus.isVideoStatus() && latestStatus.getVideoUrl() != null) {
                previewLayout.setVisibility(View.VISIBLE);
                videoPlayIcon.setVisibility(View.VISIBLE);
                
                // For video, we could show a thumbnail or just a placeholder
                previewImage.setImageResource(R.drawable.ic_image_placeholder);
                
            } else {
                previewLayout.setVisibility(View.GONE);
            }
        }
    }
}