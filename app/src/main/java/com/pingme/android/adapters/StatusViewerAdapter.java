package com.pingme.android.adapters;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
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

public class StatusViewerAdapter extends RecyclerView.Adapter<StatusViewerAdapter.StatusViewHolder> {
    private Context context;
    private List<Status> statusList;
    private StatusViewerListener listener;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final long STATUS_DURATION = 5000; // 5 seconds per status

    public interface StatusViewerListener {
        void onStatusViewed(Status status);
        void onStatusExpired(Status status);
    }

    public StatusViewerAdapter(Context context, List<Status> statusList, StatusViewerListener listener) {
        this.context = context;
        this.statusList = statusList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StatusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_status_viewer, parent, false);
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
        private View backgroundView;

        public StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            statusImage = itemView.findViewById(R.id.statusImage);
            statusText = itemView.findViewById(R.id.statusText);
            backgroundView = itemView.findViewById(R.id.backgroundView);
        }

        public void bind(Status status) {
            // Set background color for text status
            if (status.isTextStatus() && status.getBackgroundColor() != null) {
                try {
                    int color = Color.parseColor(status.getBackgroundColor());
                    backgroundView.setBackgroundColor(color);
                    backgroundView.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    backgroundView.setBackgroundColor(Color.BLACK);
                }
            } else {
                backgroundView.setVisibility(View.GONE);
            }

            // Handle image status
            if (status.isImageStatus() && status.getImageUrl() != null && !status.getImageUrl().isEmpty()) {
                statusImage.setVisibility(View.VISIBLE);
                Glide.with(context)
                    .load(status.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(statusImage);
            } else {
                statusImage.setVisibility(View.GONE);
            }

            // Handle text status
            if (status.isTextStatus() && status.getContent() != null && !status.getContent().isEmpty()) {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText(status.getContent());
                
                // Set text color based on background
                if (status.getBackgroundColor() != null) {
                    try {
                        int bgColor = Color.parseColor(status.getBackgroundColor());
                        // Determine if background is dark or light
                        double luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255;
                        if (luminance > 0.5) {
                            statusText.setTextColor(Color.BLACK);
                        } else {
                            statusText.setTextColor(Color.WHITE);
                        }
                    } catch (Exception e) {
                        statusText.setTextColor(Color.WHITE);
                    }
                } else {
                    statusText.setTextColor(Color.WHITE);
                }
            } else {
                statusText.setVisibility(View.GONE);
            }

            // Notify that status is being viewed
            if (listener != null) {
                listener.onStatusViewed(status);
            }

            // Auto-advance after duration (for automatic progression)
            handler.postDelayed(() -> {
                if (listener != null && status.isExpired()) {
                    listener.onStatusExpired(status);
                }
            }, STATUS_DURATION);
        }
    }
}