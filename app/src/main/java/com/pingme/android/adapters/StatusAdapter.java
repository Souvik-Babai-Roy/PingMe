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
        
        holder.tvUserName.setText(status.getUserName());
        holder.tvTimestamp.setText(status.getFormattedTimestamp());
        
        if (status.getImageUrl() != null && !status.getImageUrl().isEmpty()) {
            Glide.with(context)
                .load(status.getImageUrl())
                .placeholder(R.drawable.ic_person)
                .into(holder.ivStatusImage);
        } else {
            holder.ivStatusImage.setImageResource(R.drawable.ic_person);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStatusClick(status);
            }
        });
    }

    @Override
    public int getItemCount() {
        return statusList.size();
    }

    public static class StatusViewHolder extends RecyclerView.ViewHolder {
        ImageView ivStatusImage;
        TextView tvUserName;
        TextView tvTimestamp;

        public StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            ivStatusImage = itemView.findViewById(R.id.ivStatusImage);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }
}