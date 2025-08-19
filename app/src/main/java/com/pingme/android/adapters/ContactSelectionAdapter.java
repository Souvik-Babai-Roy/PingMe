package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
import com.pingme.android.models.User;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactSelectionAdapter extends RecyclerView.Adapter<ContactSelectionAdapter.ContactViewHolder> {
    
    private List<User> contacts;
    private OnSelectionChangedListener listener;
    private Set<String> selectedContacts = new HashSet<>();

    public interface OnSelectionChangedListener {
        void onSelectionChanged(User contact, boolean isSelected);
    }

    public ContactSelectionAdapter(List<User> contacts, OnSelectionChangedListener listener) {
        this.contacts = contacts;
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

    public class ContactViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivProfileImage;
        private TextView tvName;
        private TextView tvAbout;
        private CheckBox checkBox;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfileImage = itemView.findViewById(R.id.ivProfileImage);
            tvName = itemView.findViewById(R.id.tvName);
            tvAbout = itemView.findViewById(R.id.tvAbout);
            checkBox = itemView.findViewById(R.id.checkBox);
        }

        public void bind(User contact) {
                    tvName.setText(contact.getDisplayName());
        tvAbout.setText(contact.getDisplayAbout());

            // Load profile image
            if (contact.getImageUrl() != null && !contact.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(contact.getImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(ivProfileImage);
            } else {
                ivProfileImage.setImageResource(R.drawable.ic_person);
            }

            // Set checkbox state
            boolean isSelected = selectedContacts.contains(contact.getId());
            checkBox.setChecked(isSelected);

            // Handle item clicks
            itemView.setOnClickListener(v -> {
                boolean newSelection = !checkBox.isChecked();
                checkBox.setChecked(newSelection);
                
                if (newSelection) {
                    selectedContacts.add(contact.getId());
                } else {
                    selectedContacts.remove(contact.getId());
                }
                
                if (listener != null) {
                    listener.onSelectionChanged(contact, newSelection);
                }
            });

            // Handle checkbox clicks
            checkBox.setOnClickListener(v -> {
                boolean newSelection = checkBox.isChecked();
                
                if (newSelection) {
                    selectedContacts.add(contact.getId());
                } else {
                    selectedContacts.remove(contact.getId());
                }
                
                if (listener != null) {
                    listener.onSelectionChanged(contact, newSelection);
                }
            });
        }
    }
}