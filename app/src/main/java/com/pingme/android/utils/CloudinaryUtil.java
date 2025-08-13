package com.pingme.android.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

            // Compress bitmap to byte array
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            byte[] byteArray = stream.toByteArray();

            // Generate unique filename
            String filename = "img_" + System.currentTimeMillis();

            // FIXED: Removed transformation parameter for unsigned upload
            MediaManager.get().upload(byteArray)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", folder + "/" + filename)
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Upload successful: " + resultData);
                            String imageUrl = (String) resultData.get("secure_url");
                            if (imageUrl != null) {
                                future.complete(imageUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned from Cloudinary"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception("Upload failed: " + error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();

            // Clean up
            stream.close();

        } catch (IOException e) {
            Log.e(TAG, "Error processing image", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    public CompletableFuture<String> uploadStatusImage(Uri imageUri, Context context) {
        return uploadImage(imageUri, "status_images", context);
    }

    public CompletableFuture<String> uploadChatImage(Uri imageUri, Context context) {
        return uploadImage(imageUri, "chat_images", context);
    }

    public CompletableFuture<Map<String, Object>> uploadChatVideo(Uri videoUri, Context context) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        
        try {
            String filename = "video_" + System.currentTimeMillis();
            
            MediaManager.get().upload(videoUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_videos/" + filename)
                    .option("resource_type", "video")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Video upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Video upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Video upload successful: " + resultData);
                            Map<String, Object> result = Map.of(
                                "videoUrl", resultData.get("secure_url"),
                                "thumbnailUrl", resultData.get("thumbnail_url"),
                                "duration", resultData.get("duration")
                            );
                            future.complete(result);
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Video upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception("Video upload failed: " + error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Video upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();
                    
        } catch (Exception e) {
            Log.e(TAG, "Error processing video", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    public CompletableFuture<Map<String, Object>> uploadChatAudio(Uri audioUri, Context context) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        
        try {
            String filename = "audio_" + System.currentTimeMillis();
            
            MediaManager.get().upload(audioUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_audio/" + filename)
                    .option("resource_type", "video")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Audio upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Audio upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Audio upload successful: " + resultData);
                            Map<String, Object> result = Map.of(
                                "audioUrl", resultData.get("secure_url"),
                                "duration", resultData.get("duration")
                            );
                            future.complete(result);
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Audio upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception("Audio upload failed: " + error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Audio upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();
                    
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    public CompletableFuture<Map<String, Object>> uploadChatDocument(Uri documentUri, Context context) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        
        try {
            String filename = "doc_" + System.currentTimeMillis();
            
            MediaManager.get().upload(documentUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_documents/" + filename)
                    .option("resource_type", "raw")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Document upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Document upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Document upload successful: " + resultData);
                            Map<String, Object> result = Map.of(
                                "fileUrl", resultData.get("secure_url"),
                                "fileName", resultData.get("original_filename"),
                                "fileSize", resultData.get("bytes")
                            );
                            future.complete(result);
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Document upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception("Document upload failed: " + error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Document upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();
                    
        } catch (Exception e) {
            Log.e(TAG, "Error processing document", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    public CompletableFuture<String> uploadChatMedia(Uri mediaUri, String chatId, Context context) {
        return uploadImage(mediaUri, "chat_media/" + chatId, context);
    }

    public CompletableFuture<String> uploadAudio(Uri audioUri, String chatId, Context context) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            String filename = "audio_" + System.currentTimeMillis();

            MediaManager.get().upload(audioUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_audio/" + chatId + "/" + filename)
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Audio upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Audio upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Audio upload successful: " + resultData);
                            String audioUrl = (String) resultData.get("secure_url");
                            if (audioUrl != null) {
                                future.complete(audioUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Audio upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception(error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Audio upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();

        } catch (Exception e) {
            Log.e(TAG, "Error uploading audio", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    public CompletableFuture<String> uploadVideo(Uri videoUri, String chatId, Context context) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            String filename = "video_" + System.currentTimeMillis();

            MediaManager.get().upload(videoUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_video/" + chatId + "/" + filename)
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Video upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Video upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Video upload successful: " + resultData);
                            String videoUrl = (String) resultData.get("secure_url");
                            if (videoUrl != null) {
                                future.complete(videoUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Video upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception(error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Video upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();

        } catch (Exception e) {
            Log.e(TAG, "Error uploading video", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    public CompletableFuture<String> uploadDocument(Uri documentUri, String folder, Context context) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            String filename = "document_" + System.currentTimeMillis();

            MediaManager.get().upload(documentUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", folder + "/" + filename)
                    .option("resource_type", "auto") // Auto detect resource type
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Document upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Document upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Document upload successful: " + resultData);
                            String documentUrl = (String) resultData.get("secure_url");
                            if (documentUrl != null) {
                                future.complete(documentUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Document upload failed: " + error.getDescription());
                            future.completeExceptionally(new Exception(error.getDescription()));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Document upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();

        } catch (Exception e) {
            Log.e(TAG, "Error uploading document", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    // ===== CALLBACK-BASED UPLOAD METHODS =====

    public static void uploadImage(android.net.Uri imageUri, String folder, android.content.Context context, ImageUploadCallback callback) {
        try {
            MediaManager.get().upload(imageUri)
                    .option("folder", folder)
                    .callback(new com.cloudinary.android.callback.UploadCallback() {
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
                            String imageUrl = (String) resultData.get("secure_url");
                            if (imageUrl != null) {
                                Log.d(TAG, "Upload successful: " + imageUrl);
                                callback.onUploadSuccess(imageUrl);
                            } else {
                                callback.onUploadError("No URL returned from upload");
                            }
                        }

                        @Override
                        public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                            Log.e(TAG, "Upload failed: " + error.getDescription());
                            callback.onUploadError(error.getDescription());
                        }

                        @Override
                        public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                            Log.w(TAG, "Upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();
        } catch (Exception e) {
            Log.e(TAG, "Error starting upload", e);
            callback.onUploadError(e.getMessage());
        }
    }

    // Simplified overload for status creation
    public static void uploadImage(android.net.Uri imageUri, ImageUploadCallback callback) {
        uploadImage(imageUri, "status", null, callback);
    }

    // ===== CALLBACK INTERFACES =====

    public interface ImageUploadCallback {
        void onUploadSuccess(String imageUrl);
        void onUploadError(String error);
    }
}