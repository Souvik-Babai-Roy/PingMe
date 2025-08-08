package com.pingme.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
import com.pingme.android.databinding.ItemContactSelectBinding;
import com.pingme.android.models.User;
import java.util.List;

public class ContactSelectAdapter extends RecyclerView.Adapter<ContactSelectAdapter.ContactViewHolder> {
    private List<User> contacts;
    private OnContactSelectedListener listener;

    public interface OnContactSelectedListener {
        void onContactSelected(User user);
    }

    public ContactSelectAdapter(List<User> contacts, OnContactSelectedListener listener) {
        this.contacts = contacts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactSelectBinding binding = ItemContactSelectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ContactViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(contacts.get(position));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {
        private ItemContactSelectBinding binding;
        public ContactViewHolder(ItemContactSelectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        public void bind(User user) {
            binding.tvName.setText(user.getDisplayName());
            binding.tvAbout.setText(user.getAbout());
            if (user.shouldShowProfilePhoto() && user.getImageUrl() != null && !user.getImageUrl().trim().isEmpty()) {
                Glide.with(binding.getRoot().getContext())
                        .load(user.getImageUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.defaultprofile)
                        .error(R.drawable.defaultprofile)
                        .into(binding.ivProfile);
            } else {
                binding.ivProfile.setImageResource(R.drawable.defaultprofile);
            }
            binding.checkBox.setOnCheckedChangeListener(null);
            binding.checkBox.setChecked(false);
            binding.getRoot().setOnClickListener(v -> {
                binding.checkBox.setChecked(!binding.checkBox.isChecked());
                listener.onContactSelected(user);
            });
            binding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                listener.onContactSelected(user);
            });
        }
    }
}