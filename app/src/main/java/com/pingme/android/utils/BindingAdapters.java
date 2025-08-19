package com.pingme.android.utils;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.databinding.BindingAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.pingme.android.R;
import de.hdodenhof.circleimageview.CircleImageView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BindingAdapters {

    // Unified adapter for CircleImageView
    @BindingAdapter({"circleImageUrl", "circlePlaceholder"})
    public static void loadCircleImage(CircleImageView view, String imageUrl, Object placeholder) {
        Drawable placeholderDrawable = null;

        if (placeholder instanceof Integer) {
            placeholderDrawable = ContextCompat.getDrawable(view.getContext(), (Integer) placeholder);
        } else if (placeholder instanceof Drawable) {
            placeholderDrawable = (Drawable) placeholder;
        }

        if (placeholderDrawable == null) {
            placeholderDrawable = ContextCompat.getDrawable(view.getContext(), R.drawable.defaultprofile);
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(view.getContext())
                    .load(imageUrl)
                    .transform(new CircleCrop())
                    .placeholder(placeholderDrawable)
                    .error(placeholderDrawable)
                    .into(view);
        } else {
            view.setImageDrawable(placeholderDrawable);
        }
    }

    // CircleImageView with just URL
    @BindingAdapter("circleImageUrl")
    public static void loadCircleImage(CircleImageView view, String imageUrl) {
        loadCircleImage(view, imageUrl, R.drawable.defaultprofile);
    }

    // Regular ImageView with URL and placeholder
    @BindingAdapter({"imageUrl", "placeholder"})
    public static void loadImage(ImageView view, String imageUrl, Object placeholder) {
        Drawable placeholderDrawable = null;

        if (placeholder instanceof Integer) {
            placeholderDrawable = ContextCompat.getDrawable(view.getContext(), (Integer) placeholder);
        } else if (placeholder instanceof Drawable) {
            placeholderDrawable = (Drawable) placeholder;
        }

        if (placeholderDrawable == null) {
            placeholderDrawable = ContextCompat.getDrawable(view.getContext(), R.drawable.defaultprofile);
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(view.getContext())
                    .load(imageUrl)
                    .placeholder(placeholderDrawable)
                    .error(placeholderDrawable)
                    .into(view);
        } else {
            view.setImageDrawable(placeholderDrawable);
        }
    }

    // Regular ImageView with just URL
    @BindingAdapter("imageUrl")
    public static void loadImage(ImageView view, String imageUrl) {
        loadImage(view, imageUrl, R.drawable.defaultprofile);
    }

    // Adapter for placeholderDrawable attribute
    @BindingAdapter("placeholderDrawable")
    public static void setPlaceholderDrawable(ImageView view, Drawable placeholder) {
        view.setImageDrawable(placeholder);
    }

    // Timestamp formatting
    @BindingAdapter("timestamp")
    public static void setTimestamp(TextView textView, long timestamp) {
        if (timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            textView.setText(sdf.format(new Date(timestamp)));
        } else {
            textView.setText("");
        }
    }

    // Text style handling
    @BindingAdapter("android:textStyle")
    public static void setTextStyle(TextView textView, int style) {
        textView.setTypeface(textView.getTypeface(), style);
    }

    @BindingAdapter("textStyle")
    public static void setCustomTextStyle(TextView textView, int style) {
        textView.setTypeface(textView.getTypeface(), style);
    }

    @BindingAdapter("isItalic")
    public static void setItalicStyle(TextView textView, boolean isItalic) {
        textView.setTypeface(textView.getTypeface(), isItalic ? Typeface.ITALIC : Typeface.NORMAL);
    }

    // Text color based on condition
    @BindingAdapter({"textColorPrimary", "textColorSecondary", "useSecondary"})
    public static void setConditionalTextColor(
            TextView textView,
            int primaryColor,
            int secondaryColor,
            boolean useSecondary
    ) {
        int colorRes = useSecondary ? secondaryColor : primaryColor;
        textView.setTextColor(ContextCompat.getColor(textView.getContext(), colorRes));
    }
}