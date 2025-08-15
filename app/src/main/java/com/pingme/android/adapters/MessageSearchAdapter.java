/*
package com.pingme.android.adapters;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pingme.android.R;
import com.pingme.android.models.Message;
import com.pingme.android.models.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageSearchAdapter extends RecyclerView.Adapter<MessageSearchAdapter.MessageViewHolder> {
    private Context context;
    private List<Message> messagesList;
    private List<User> usersList;
    private OnMessageClickListener listener;
    private String searchQuery = "";

    public interface OnMessageClickListener {
        void onMessageClick(Message message);
    }

    public MessageSearchAdapter(Context context, List<Message> messagesList, List<User> usersList, OnMessageClickListener listener) {
        this.context = context;
        this.messagesList = messagesList;
        this.usersList = usersList;
        this.listener = listener;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_message_search, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messagesList.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messagesList.size();
    }

    public class MessageViewHolder extends RecyclerView.ViewHolder {
        private TextView senderNameText;
        private TextView messageText;
        private TextView timestampText;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            senderNameText = itemView.findViewById(R.id.senderNameText);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMessageClick(messagesList.get(position));
                }
            });
        }

        public void bind(Message message) {
            // Get sender name
            String senderName = getSenderName(message.getSenderId());
            senderNameText.setText(senderName);

            // Highlight search query in message text
            String messageContent = message.getText() != null ? message.getText() : "";
            SpannableString spannableString = new SpannableString(messageContent);
            
            if (!searchQuery.isEmpty() && messageContent.toLowerCase().contains(searchQuery.toLowerCase())) {
                String lowerMessage = messageContent.toLowerCase();
                String lowerQuery = searchQuery.toLowerCase();
                int start = lowerMessage.indexOf(lowerQuery);
                if (start >= 0) {
                    int end = start + searchQuery.length();
                    spannableString.setSpan(
                        new BackgroundColorSpan(context.getColor(R.color.highlight_yellow)),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }
            
            messageText.setText(spannableString);

            // Set timestamp
            if (message.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault());
                String timestamp = sdf.format(new Date(message.getTimestamp()));
                timestampText.setText(timestamp);
            } else {
                timestampText.setText("");
            }
        }

        private String getSenderName(String senderId) {
            for (User user : usersList) {
                if (user.getId().equals(senderId)) {
                    return user.getDisplayName();
                }
            }
            return "Unknown User";
        }
    }
}
*/