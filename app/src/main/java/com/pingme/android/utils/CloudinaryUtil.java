package com.pingme.android.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CloudinaryUtil {
    private static final String TAG = "CloudinaryUtil";
    private static CloudinaryUtil instance;

    public static CloudinaryUtil getInstance() {
        if (instance == null) {
            instance = new CloudinaryUtil();
        }
        return instance;
    }

    public CompletableFuture<String> uploadImage(Uri imageUri, String folder, Context context) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            // Get bitmap from URI
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);

            // Compress bitmap
            Bitmap compressedBitmap = compressBitmap(bitmap, 80);

            // Upload options
            Map<String, Object> options = new HashMap<>();
            options.put("folder", folder);
            options.put("resource_type", "image");
            options.put("format", "jpg");
            options.put("transformation", "c_fill,w_300,h_300,q_auto");

            // Generate unique filename
            String filename = "profile_" + System.currentTimeMillis();

            MediaManager.get().upload(compressedBitmap.getNinePatchChunk())
                    .unsigned("pingme_upload_preset") // You need to create this preset in Cloudinary
                    .option("public_id", folder + "/" + filename)
                    .option("resource_type", "image")
                    .option("format", "jpg")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Upload successful: " + resultData);
                            String imageUrl = (String) resultData.get("secure_url");
                            future.complete(imageUrl);
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception(error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();

        } catch (IOException e) {
            Log.e(TAG, "Error processing image", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private Bitmap compressBitmap(Bitmap bitmap, int quality) {
        // Resize if too large
        int maxSize = 1024;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width > maxSize || height > maxSize) {
            float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
            int newWidth = Math.round(width * ratio);
            int newHeight = Math.round(height * ratio);
            bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }

        return bitmap;
    }

    public CompletableFuture<String> uploadStatusImage(Uri imageUri, Context context) {
        return uploadImage(imageUri, "status_images", context);
    }

    public CompletableFuture<String> uploadChatMedia(Uri mediaUri, String chatId, Context context) {
        return uploadImage(mediaUri, "chat_media/" + chatId, context);
    }
}