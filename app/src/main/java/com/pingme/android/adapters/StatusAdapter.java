package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pingme.android.R;
import com.pingme.android.models.Status;

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
            // TODO: Replace with actual user name lookup from User model
            tvName.setText(status.getUserId());
            tvContent.setText(status.getContent());
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(status.getTimestamp()));

            // TODO: Load profile image using Glide if available
            // Glide.with(itemView.getContext())
            //     .load(status.getUserImageUrl())
            //     .circleCrop()
            //     .placeholder(R.drawable.ic_profile)
            //     .into(ivProfile);

            itemView.setOnClickListener(v -> statusClickListener.onStatusClick(status));
        }
    }
}