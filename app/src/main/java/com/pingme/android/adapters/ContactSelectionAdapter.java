package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pingme.android.R;
import com.pingme.android.models.User;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class ContactSelectionAdapter extends RecyclerView.Adapter<ContactSelectionAdapter.ContactViewHolder> {
    private List<User> contacts;
    private List<String> selectedContactIds;
    private OnContactSelectionListener listener;

    public interface OnContactSelectionListener {
        void onContactSelectionChanged(User contact, boolean isSelected);
    }

    public ContactSelectionAdapter(List<User> contacts, List<String> selectedContactIds, OnContactSelectionListener listener) {
        this.contacts = contacts;
        this.selectedContactIds = selectedContactIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_selection, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        User contact = contacts.get(position);
        holder.bind(contact);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {
        private CircleImageView ivProfilePic;
        private TextView tvName;
        private TextView tvStatus;
        private CheckBox checkBox;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePic = itemView.findViewById(R.id.ivProfilePic);
            tvName = itemView.findViewById(R.id.tvName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            checkBox = itemView.findViewById(R.id.checkBox);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    User contact = contacts.get(position);
                    boolean isSelected = selectedContactIds.contains(contact.getId());
                    if (listener != null) {
                        listener.onContactSelectionChanged(contact, !isSelected);
                    }
                }
            });

            checkBox.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    User contact = contacts.get(position);
                    boolean isSelected = checkBox.isChecked();
                    if (listener != null) {
                        listener.onContactSelectionChanged(contact, isSelected);
                    }
                }
            });
        }

        public void bind(User contact) {
            tvName.setText(contact.getDisplayName());
            tvStatus.setText(contact.getDisplayAbout());

            // Set checkbox state
            boolean isSelected = selectedContactIds.contains(contact.getId());
            checkBox.setChecked(isSelected);

            // Load profile picture
            if (contact.hasProfilePhoto()) {
                Glide.with(ivProfilePic.getContext())
                        .load(contact.getImageUrl())
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                        .into(ivProfilePic);
            } else {
                ivProfilePic.setImageResource(R.drawable.default_avatar);
            }
        }
    }
}