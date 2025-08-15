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

        public StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            statusImage = itemView.findViewById(R.id.statusImage);
            statusText = itemView.findViewById(R.id.statusText);
            timestampText = itemView.findViewById(R.id.timestampText);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStatusClick(statusList.get(position));
                }
            });
        }

        public void bind(Status status) {
            // Load status image if available
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

            // Set status text
            if (status.getText() != null && !status.getText().isEmpty()) {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText(status.getText());
            } else {
                statusText.setVisibility(View.GONE);
            }

            // Set timestamp
            if (status.getTimestamp() > 0) {
                timestampText.setText(status.getFormattedTimestamp());
            } else {
                timestampText.setText("");
            }
        }
    }
}