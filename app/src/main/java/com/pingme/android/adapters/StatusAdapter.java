package com.pingme.android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.models.Status;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirebaseUtil;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.StatusViewHolder> {
    private Context context;
    private List<Status> statusList;
    private OnStatusClickListener listener;

    public interface OnStatusClickListener {
        void onStatusClick(Status status);
    }

    public StatusAdapter(Context context, List<Status> statusList, OnStatusClickListener listener) {
        this.context = context;
        this.statusList = statusList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StatusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_status, parent, false);
        return new StatusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatusViewHolder holder, int position) {
        Status status = statusList.get(position);
        holder.bind(status);
    }

    @Override
    public int getItemCount() {
        return statusList.size();
    }

    public class StatusViewHolder extends RecyclerView.ViewHolder {
        private ImageView statusImage;
        private TextView statusText;
        private TextView timestampText;
        private TextView userNameText;
        private de.hdodenhof.circleimageview.CircleImageView userProfileImage;

        public StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            statusImage = itemView.findViewById(R.id.statusImage);
            statusText = itemView.findViewById(R.id.statusText);
            timestampText = itemView.findViewById(R.id.textTime);
            userNameText = itemView.findViewById(R.id.textUserName);
            userProfileImage = itemView.findViewById(R.id.imageUserProfile);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStatusClick(statusList.get(position));
                }
            });
        }

        public void bind(Status status) {
            // Load user name with personal name support
            loadUserDisplayName(status);

            // Load user profile photo
            if (userProfileImage != null) {
                // Load from Firestore user data
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(status.getUserId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && userProfileImage != null) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null) {
                                // Get current user ID to check if this is the current user's status
                                FirebaseUser currentFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                                String currentUserId = currentFirebaseUser != null ? currentFirebaseUser.getUid() : "";
                                boolean isCurrentUser = status.getUserId().equals(currentUserId);
                                
                                // Current user always sees their own avatar, others see it only if privacy allows
                                boolean shouldShowAvatar = isCurrentUser || user.isProfilePhotoEnabled();
                                
                                if (shouldShowAvatar && user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
                                    try {
                                        Glide.with(context)
                                            .load(user.getImageUrl())
                                            .placeholder(R.drawable.defaultprofile)
                                            .error(R.drawable.defaultprofile)
                                            .circleCrop()
                                            .skipMemoryCache(false) // Allow memory caching for performance
                                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                            .into(userProfileImage);
                                    } catch (Exception e) {
                                        if (userProfileImage != null) {
                                            userProfileImage.setImageResource(R.drawable.defaultprofile);
                                        }
                                    }
                                } else {
                                    if (userProfileImage != null) {
                                        userProfileImage.setImageResource(R.drawable.defaultprofile);
                                    }
                                }
                            } else {
                                if (userProfileImage != null) {
                                    userProfileImage.setImageResource(R.drawable.defaultprofile);
                                }
                            }
                        } else {
                            if (userProfileImage != null) {
                                userProfileImage.setImageResource(R.drawable.defaultprofile);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (userProfileImage != null) {
                            userProfileImage.setImageResource(R.drawable.defaultprofile);
                        }
                    });
            }

            // Load status image if available
            if (statusImage != null) {
                if (status.getImageUrl() != null && !status.getImageUrl().isEmpty()) {
                    statusImage.setVisibility(View.VISIBLE);
                    Glide.with(context)
                        .load(status.getImageUrl())
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .into(statusImage);
                } else {
                    statusImage.setVisibility(View.GONE);
                }
            }

            // Set status text
            if (statusText != null) {
                if (status.getText() != null && !status.getText().isEmpty()) {
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText(status.getText());
                } else {
                    statusText.setVisibility(View.GONE);
                }
            }

            // Set timestamp
            if (timestampText != null) {
                if (status.getTimestamp() > 0) {
                    timestampText.setText(status.getFormattedTimestamp());
                } else {
                    timestampText.setText("");
                }
            }
        }
        
        private void loadUserDisplayName(Status status) {
            if (userNameText == null) return;
            
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                if (userNameText != null) {
                    userNameText.setText(status.getUserName() != null ? status.getUserName() : "Unknown User");
                }
                return;
            }
            
            String currentUserId = currentUser.getUid();
            String statusUserId = status.getUserId();
            
            // If it's the current user's status, show "Me"
            if (currentUserId.equals(statusUserId)) {
                if (userNameText != null) {
                    userNameText.setText("Me");
                }
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
                            // Fallback to original status user name or load from user document
                            loadOriginalUserName(status);
                        }
                    } else {
                        // Not a friend or friendship document doesn't exist, load original name
                        loadOriginalUserName(status);
                    }
                })
                .addOnFailureListener(e -> {
                    // Error loading friendship, fallback to original name
                    loadOriginalUserName(status);
                });
        }
        
        private void loadOriginalUserName(Status status) {
            if (userNameText == null) return;
            
            if (status.getUserName() != null && !status.getUserName().trim().isEmpty()) {
                userNameText.setText(status.getUserName());
            } else {
                // Load from user document if status doesn't have userName
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(status.getUserId())
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
    }
}