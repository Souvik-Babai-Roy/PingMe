package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pingme.android.R;
import com.pingme.android.models.Message;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private List<Message> messages;
    private String currentUserId;

    public MessageAdapter(List<Message> messages) {
        this.messages = messages;
        this.currentUserId = FirebaseAuth.getInstance().getUid();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SENT) {
            return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
        } else {
            return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (messages.get(position).getSenderId().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    abstract static class MessageViewHolder extends RecyclerView.ViewHolder {
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(Message message);
    }

    static class SentMessageViewHolder extends MessageViewHolder {
        TextView tvMessage, tvTime;
        ImageView ivStatus;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            ivStatus = itemView.findViewById(R.id.ivStatus);
        }

        @Override
        void bind(Message message) {
            tvMessage.setText(message.getText());

            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(new Date(message.getTimestamp())));

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
        }
    }

    static class ReceivedMessageViewHolder extends MessageViewHolder {
        TextView tvMessage, tvTime;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        @Override
        void bind(Message message) {
            tvMessage.setText(message.getText());

            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(new Date(message.getTimestamp())));
        }
    }
}