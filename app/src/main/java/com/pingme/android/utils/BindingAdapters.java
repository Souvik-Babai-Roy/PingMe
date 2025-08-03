package com.pingme.android.utils;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BindingAdapters {

    // Load circular profile image with default placeholder
    @BindingAdapter("imageUrl")
    public static void loadCircularImage(ImageView imageView, String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(imageView.getContext())
                    .load(imageUrl)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_profile)
                    .into(imageView);
        } else {
            imageView.setImageResource(R.drawable.ic_profile);
        }
    }

    // Load image with optional placeholder (no transformation)
    @BindingAdapter({"imageUrl", "placeholder"})
    public static void loadImage(ImageView imageView, String imageUrl, Drawable placeholder) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(imageView.getContext())
                    .load(imageUrl)
                    .placeholder(placeholder)
                    .into(imageView);
        } else {
            imageView.setImageDrawable(placeholder);
        }
    }

    // Bind formatted timestamp to TextView
    @BindingAdapter("timestamp")
    public static void setTimestamp(TextView textView, long timestamp) {
        if (timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            textView.setText(sdf.format(new Date(timestamp)));
        } else {
            textView.setText("");
        }
    }

    // Show unread message count
    @BindingAdapter("unreadCount")
    public static void setUnreadCount(TextView textView, int count) {
        if (count > 0) {
            textView.setText(String.valueOf(count));
            textView.setVisibility(android.view.View.VISIBLE);
        } else {
            textView.setVisibility(android.view.View.GONE);
        }
    }
}
