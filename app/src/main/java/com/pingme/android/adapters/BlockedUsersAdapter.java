package com.pingme.android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.pingme.android.R;
import com.pingme.android.databinding.ItemBlockedUserBinding;
import com.pingme.android.models.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BlockedUsersAdapter extends RecyclerView.Adapter<BlockedUsersAdapter.BlockedUserViewHolder> {

    private Context context;
    private List<User> blockedUsers;
    private OnUnblockListener onUnblockListener;
    private String currentUserId;

    public interface OnUnblockListener {
        void onUnblock(User user, int position);
    }

    public BlockedUsersAdapter(Context context, List<User> blockedUsers, OnUnblockListener listener) {
        this.context = context;
        this.blockedUsers = blockedUsers;
        this.onUnblockListener = listener;
        // Initialize current user ID
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            this.currentUserId = "";
        }
    }

    @NonNull
    @Override
    public BlockedUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBlockedUserBinding binding = ItemBlockedUserBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new BlockedUserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BlockedUserViewHolder holder, int position) {
        User user = blockedUsers.get(position);
        holder.bind(user, position);
    }

    @Override
    public int getItemCount() {
        return blockedUsers.size();
    }

    class BlockedUserViewHolder extends RecyclerView.ViewHolder {
        private ItemBlockedUserBinding binding;

        public BlockedUserViewHolder(ItemBlockedUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User user, int position) {
            // Set user name - display appropriate name based on privacy settings
            String displayName = user.getDisplayName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Unknown User";
            }
            binding.tvName.setText(displayName);

            // Format blocked date - use blocked timestamp properly
            if (user.getLastSeen() > 0) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault());
                String blockedDate = "Blocked on " + dateFormat.format(new Date(user.getLastSeen()));
                binding.tvBlockedDate.setText(blockedDate);
            } else {
                binding.tvBlockedDate.setText("Blocked recently");
            }

            // Load profile image with current user priority logic
            boolean isCurrentUser = user.getId().equals(currentUserId);
            boolean shouldShowAvatar = isCurrentUser || user.isProfilePhotoEnabled();
            
            if (shouldShowAvatar && user.getImageUrl() != null && !user.getImageUrl().trim().isEmpty()) {
                try {
                    Glide.with(context)
                            .load(user.getImageUrl())
                            .transform(new CircleCrop())
                            .placeholder(R.drawable.defaultprofile)
                            .error(R.drawable.defaultprofile)
                            .into(binding.ivProfile);
                } catch (Exception e) {
                    binding.ivProfile.setImageResource(R.drawable.defaultprofile);
                }
            } else {
                binding.ivProfile.setImageResource(R.drawable.defaultprofile);
            }

            // Unblock button click with confirmation
            binding.btnUnblock.setOnClickListener(v -> {
                if (onUnblockListener != null) {
                    onUnblockListener.onUnblock(user, position);
                }
            });
        }
    }
}