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
import com.pingme.android.models.Status;
import com.pingme.android.models.User;
import com.pingme.android.utils.FirestoreUtil;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.StatusViewHolder> {
    private final List<Status> statuses;
    private final OnStatusClickListener statusClickListener;

    public interface OnStatusClickListener {
        void onStatusClick(Status status);
    }

    public StatusAdapter(List<Status> statuses, OnStatusClickListener statusClickListener) {
        this.statuses = statuses;
        this.statusClickListener = statusClickListener;
    }

    @NonNull
    @Override
    public StatusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_status, parent, false);
        return new StatusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatusViewHolder holder, int position) {
        holder.bind(statuses.get(position));
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    class StatusViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvContent;
        ImageView ivProfile;

        public StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvContent = itemView.findViewById(R.id.tvContent);
            ivProfile = itemView.findViewById(R.id.ivProfile);
        }

        void bind(Status status) {
            // Load user details to show name and profile picture
            FirestoreUtil.getUserRef(status.getUserId())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null) {
                                tvName.setText(user.getName());
                                
                                // FIXED: Load profile image using Glide with privacy check
                                if (user.shouldShowProfilePhoto() && user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
                                    Glide.with(itemView.getContext())
                                            .load(user.getImageUrl())
                                            .transform(new CircleCrop())
                                            .placeholder(R.drawable.defaultprofile)
                                            .error(R.drawable.defaultprofile)
                                            .into(ivProfile);
                                } else {
                                    ivProfile.setImageResource(R.drawable.defaultprofile);
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Fallback to user ID if user details can't be loaded
                        tvName.setText(status.getUserId());
                        ivProfile.setImageResource(R.drawable.defaultprofile);
                    });

            tvContent.setText(status.getContent());
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(status.getTimestamp()));

            itemView.setOnClickListener(v -> statusClickListener.onStatusClick(status));
        }
    }
}