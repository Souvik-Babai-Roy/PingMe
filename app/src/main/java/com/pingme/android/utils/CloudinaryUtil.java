package com.pingme.android.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CloudinaryUtil {
    private static final String TAG = "CloudinaryUtil";
    private static CloudinaryUtil instance;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000; // 2 seconds
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);

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
                            Log.d(TAG, "Upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Upload successful - requestId: " + (requestId != null ? requestId : "null") + ", resultData: " + resultData);
                            
                            // Check if resultData is null
                            if (resultData == null) {
                                Log.e(TAG, "Upload resultData is null");
                                future.completeExceptionally(new Exception("Upload completed but result data is null"));
                                return;
                            }
                            
                            String imageUrl = (String) resultData.get("secure_url");
                            if (imageUrl != null) {
                                future.complete(imageUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned from Cloudinary"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            future.completeExceptionally(new Exception("Upload failed: " + (error != null ? error.getDescription() : "Unknown error")));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
        return uploadChatVideoWithRetry(videoUri, context, 0);
    }
    
    private CompletableFuture<Map<String, Object>> uploadChatVideoWithRetry(Uri videoUri, Context context, int attemptCount) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        
        // Add null checks for parameters
        if (videoUri == null) {
            Log.e(TAG, "Video URI is null");
            return CompletableFuture.failedFuture(new Exception("Video URI is null"));
        }
        
        if (context == null) {
            Log.e(TAG, "Context is null");
            return CompletableFuture.failedFuture(new Exception("Context is null"));
        }
        
        // Check file size (max 100MB for videos)
        try {
            long fileSize = getFileSize(context, videoUri);
            if (fileSize > 100 * 1024 * 1024) { // 100MB
                return CompletableFuture.failedFuture(new Exception("Video file too large. Maximum size is 100MB"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not check file size", e);
        }
        
        try {
            // Get original filename and preserve extension for videos
            String originalFilename = getFileName(context, videoUri);
            String extension = getFileExtension(originalFilename);
            String filename = "video_" + System.currentTimeMillis();
            
            Log.d(TAG, "Video upload - Original filename: " + originalFilename);
            Log.d(TAG, "Video upload - Extracted extension: " + extension);
            
            // Preserve video extension if available
            if (extension != null && !extension.isEmpty()) {
                // Common video extensions
                if (extension.matches("(?i)(mp4|avi|mov|wmv|flv|webm|mkv|m4v|3gp)")) {
                    filename += "." + extension;
                }
            }
            
            Log.d(TAG, "Starting video upload for URI: " + videoUri);
            Log.d(TAG, "Video upload - Final filename: " + filename);
            
            // Create a final reference to avoid potential null pointer issues
            final CompletableFuture<Map<String, Object>> finalFuture = future;
            
            // Add null check for the future
            if (finalFuture == null) {
                Log.e(TAG, "Future is null, cannot proceed with video upload");
                return CompletableFuture.failedFuture(new Exception("Future is null"));
            }
            
            MediaManager.get().upload(videoUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_videos/" + filename)
                    .option("resource_type", "video")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Video upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Video upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Video upload successful - requestId: " + (requestId != null ? requestId : "null") + ", resultData: " + resultData);
                            
                            // Use a synchronized block to prevent race conditions
                            synchronized (finalFuture) {
                                try {
                                    // Check if future is already completed
                                    if (finalFuture.isDone()) {
                                        Log.w(TAG, "Video upload future already completed, ignoring onSuccess");
                                        return;
                                    }
                                    
                                    // Check if resultData is null
                                    if (resultData == null) {
                                        Log.e(TAG, "Video upload resultData is null");
                                        finalFuture.completeExceptionally(new Exception("Video upload completed but result data is null"));
                                        return;
                                    }
                                    
                                    Log.d(TAG, "Available fields in resultData: " + resultData.keySet());
                                    
                                    // Create a HashMap to handle null values safely
                                    Map<String, Object> result = new HashMap<>();
                                    
                                    // Get secure_url, fallback to url if secure_url is null
                                    Object videoUrl = resultData.get("secure_url");
                                    Log.d(TAG, "secure_url from resultData: " + videoUrl);
                                    if (videoUrl == null) {
                                        videoUrl = resultData.get("url");
                                        Log.d(TAG, "url from resultData (fallback): " + videoUrl);
                                    }
                                    
                                    // Only add videoUrl if it's not null
                                    if (videoUrl != null) {
                                        result.put("videoUrl", videoUrl.toString());
                                    }
                                    
                                    // Get thumbnail_url, only add if not null
                                    Object thumbnailUrl = resultData.get("thumbnail_url");
                                    Log.d(TAG, "thumbnail_url from resultData: " + thumbnailUrl);
                                    if (thumbnailUrl != null) {
                                        result.put("thumbnailUrl", thumbnailUrl.toString());
                                    }
                                    
                                                                    // Get duration, only add if not null
                                Object duration = resultData.get("duration");
                                Log.d(TAG, "duration from resultData: " + duration);
                                if (duration != null) {
                                    try {
                                        // Convert duration to long (milliseconds)
                                        double durationSeconds = Double.parseDouble(duration.toString());
                                        long durationMillis = (long) (durationSeconds * 1000);
                                        result.put("duration", durationMillis);
                                    } catch (NumberFormatException e) {
                                        Log.w(TAG, "Could not parse duration: " + duration, e);
                                        // Store as 0 if parsing fails
                                        result.put("duration", 0L);
                                    }
                                }
                                    
                                    // Log the result for debugging
                                    Log.d(TAG, "Video result map: " + result);
                                    
                                    // Ensure we have at least a video URL before completing
                                    if (!result.containsKey("videoUrl") || result.get("videoUrl") == null) {
                                        Log.e(TAG, "Video URL is null, completing with error");
                                        finalFuture.completeExceptionally(new Exception("Video upload completed but no URL received"));
                                    } else {
                                        // Create a new HashMap to ensure thread safety
                                        Map<String, Object> finalResult = new HashMap<>(result);
                                        finalFuture.complete(finalResult);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in onSuccess callback", e);
                                    if (!finalFuture.isDone()) {
                                        finalFuture.completeExceptionally(e);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Video upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            synchronized (finalFuture) {
                                if (!finalFuture.isDone()) {
                                    // Implement retry logic
                                    if (attemptCount < MAX_RETRY_ATTEMPTS - 1) {
                                        Log.d(TAG, "Retrying video upload, attempt " + (attemptCount + 1) + "/" + MAX_RETRY_ATTEMPTS);
                                        retryExecutor.schedule(() -> {
                                            uploadChatVideoWithRetry(videoUri, context, attemptCount + 1)
                                                .thenAccept(result -> {
                                                    if (!finalFuture.isDone()) {
                                                        finalFuture.complete(result);
                                                    }
                                                })
                                                .exceptionally(retryError -> {
                                                    if (!finalFuture.isDone()) {
                                                        finalFuture.completeExceptionally(retryError);
                                                    }
                                                    return null;
                                                });
                                        }, RETRY_DELAY_MS * (attemptCount + 1), TimeUnit.MILLISECONDS);
                                    } else {
                                        finalFuture.completeExceptionally(new Exception("Video upload failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + (error != null ? error.getDescription() : "Unknown error")));
                                    }
                                }
                            }
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Video upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
        return uploadChatAudioWithRetry(audioUri, context, 0);
    }
    
    private CompletableFuture<Map<String, Object>> uploadChatAudioWithRetry(Uri audioUri, Context context, int attemptCount) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        
        // Add null checks for parameters
        if (audioUri == null) {
            Log.e(TAG, "Audio URI is null");
            return CompletableFuture.failedFuture(new Exception("Audio URI is null"));
        }
        
        if (context == null) {
            Log.e(TAG, "Context is null");
            return CompletableFuture.failedFuture(new Exception("Context is null"));
        }
        
        // Check file size (max 50MB for audio)
        try {
            long fileSize = getFileSize(context, audioUri);
            if (fileSize > 50 * 1024 * 1024) { // 50MB
                return CompletableFuture.failedFuture(new Exception("Audio file too large. Maximum size is 50MB"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not check file size", e);
        }
        
        try {
            // Get original filename and preserve extension for audio
            String originalFilename = getFileName(context, audioUri);
            String extension = getFileExtension(originalFilename);
            String filename = "audio_" + System.currentTimeMillis();
            
            Log.d(TAG, "Audio upload - Original filename: " + originalFilename);
            Log.d(TAG, "Audio upload - Extracted extension: " + extension);
            
            // Preserve audio extension if available
            if (extension != null && !extension.isEmpty()) {
                // Common audio extensions
                if (extension.matches("(?i)(mp3|wav|aac|ogg|m4a|flac|wma|amr)")) {
                    filename += "." + extension;
                }
            }
            
            Log.d(TAG, "Audio upload - Final filename: " + filename);
            
            // Create a final reference to avoid potential null pointer issues
            final CompletableFuture<Map<String, Object>> finalFuture = future;
            
            MediaManager.get().upload(audioUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_audio/" + filename)
                    .option("resource_type", "video")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Audio upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Audio upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Audio upload successful - requestId: " + (requestId != null ? requestId : "null") + ", resultData: " + resultData);
                            
                            try {
                                // Check if resultData is null
                                if (resultData == null) {
                                    Log.e(TAG, "Audio upload resultData is null");
                                    if (!finalFuture.isDone()) {
                                        finalFuture.completeExceptionally(new Exception("Audio upload completed but result data is null"));
                                    }
                                    return;
                                }
                                
                                // Create a HashMap to handle null values safely
                                Map<String, Object> result = new HashMap<>();
                                
                                // Get secure_url, fallback to url if secure_url is null
                                Object audioUrl = resultData.get("secure_url");
                                if (audioUrl == null) {
                                    audioUrl = resultData.get("url");
                                }
                                
                                // Only add audioUrl if it's not null
                                if (audioUrl != null) {
                                    result.put("audioUrl", audioUrl.toString());
                                }
                                
                                // Get duration, only add if not null
                                Object duration = resultData.get("duration");
                                if (duration != null) {
                                    try {
                                        // Convert duration to long (milliseconds)
                                        double durationSeconds = Double.parseDouble(duration.toString());
                                        long durationMillis = (long) (durationSeconds * 1000);
                                        result.put("duration", durationMillis);
                                    } catch (NumberFormatException e) {
                                        Log.w(TAG, "Could not parse duration: " + duration, e);
                                        // Store as 0 if parsing fails
                                        result.put("duration", 0L);
                                    }
                                }
                                
                                // Ensure we have at least an audio URL before completing
                                if (!result.containsKey("audioUrl") || result.get("audioUrl") == null) {
                                    Log.e(TAG, "Audio URL is null, completing with error");
                                    if (!finalFuture.isDone()) {
                                        finalFuture.completeExceptionally(new Exception("Audio upload completed but no URL received"));
                                    }
                                } else {
                                    if (!finalFuture.isDone()) {
                                        // Create a new HashMap to ensure thread safety
                                        Map<String, Object> finalResult = new HashMap<>(result);
                                        finalFuture.complete(finalResult);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in audio onSuccess callback", e);
                                if (!finalFuture.isDone()) {
                                    finalFuture.completeExceptionally(e);
                                }
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Audio upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            if (!finalFuture.isDone()) {
                                // Implement retry logic
                                if (attemptCount < MAX_RETRY_ATTEMPTS - 1) {
                                    Log.d(TAG, "Retrying audio upload, attempt " + (attemptCount + 1) + "/" + MAX_RETRY_ATTEMPTS);
                                    retryExecutor.schedule(() -> {
                                        uploadChatAudioWithRetry(audioUri, context, attemptCount + 1)
                                            .thenAccept(result -> {
                                                if (!finalFuture.isDone()) {
                                                    finalFuture.complete(result);
                                                }
                                            })
                                            .exceptionally(retryError -> {
                                                if (!finalFuture.isDone()) {
                                                    finalFuture.completeExceptionally(retryError);
                                                }
                                                return null;
                                            });
                                    }, RETRY_DELAY_MS * (attemptCount + 1), TimeUnit.MILLISECONDS);
                                } else {
                                    finalFuture.completeExceptionally(new Exception("Audio upload failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + (error != null ? error.getDescription() : "Unknown error")));
                                }
                            }
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Audio upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
            // Get original filename and preserve extension
            String originalFilename = getFileName(context, documentUri);
            String extension = getFileExtension(originalFilename);
            String filename = "doc_" + System.currentTimeMillis();
            
            Log.d(TAG, "Document upload - Original filename: " + originalFilename);
            Log.d(TAG, "Document upload - Extracted extension: " + extension);
            
            // Preserve the original extension if available
            if (extension != null && !extension.isEmpty()) {
                filename += "." + extension;
            }
            
            Log.d(TAG, "Document upload - Final filename: " + filename);
            
            // Create a final reference to avoid potential null pointer issues
            final CompletableFuture<Map<String, Object>> finalFuture = future;
            final String finalOriginalFilename = originalFilename; // Store for callback
            final String finalExtension = extension; // Store extension for callback
            
            MediaManager.get().upload(documentUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_documents/" + filename)
                    .option("resource_type", "raw")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Document upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            double progress = (double) bytes / totalBytes * 100;
                            Log.d(TAG, "Document upload progress: " + String.format("%.1f%%", progress));
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Document upload successful - requestId: " + (requestId != null ? requestId : "null") + ", resultData: " + resultData);
                            
                            try {
                                // Check if resultData is null
                                if (resultData == null) {
                                    Log.e(TAG, "Document upload resultData is null");
                                    if (!finalFuture.isDone()) {
                                        finalFuture.completeExceptionally(new Exception("Document upload completed but result data is null"));
                                    }
                                    return;
                                }
                                
                                // Create a HashMap to handle null values safely
                                Map<String, Object> result = new HashMap<>();
                                
                                // Get secure_url, fallback to url if secure_url is null
                                Object fileUrl = resultData.get("secure_url");
                                if (fileUrl == null) {
                                    fileUrl = resultData.get("url");
                                }
                                
                                // Only add fileUrl if it's not null
                                if (fileUrl != null) {
                                    result.put("fileUrl", fileUrl.toString());
                                }
                                
                                // Get fileName, fallback to our stored original filename
                                Object fileName = resultData.get("original_filename");
                                if (fileName != null) {
                                    result.put("fileName", fileName.toString());
                                } else if (finalOriginalFilename != null) {
                                    // Fallback to the filename we extracted locally
                                    result.put("fileName", finalOriginalFilename);
                                } else {
                                    // Last fallback to generic name with extension
                                    String fallbackName = "document";
                                    if (finalExtension != null && !finalExtension.isEmpty()) {
                                        fallbackName += "." + finalExtension;
                                    }
                                    result.put("fileName", fallbackName);
                                }
                                
                                // Get fileSize, only add if not null
                                Object fileSize = resultData.get("bytes");
                                if (fileSize != null) {
                                    try {
                                        // Convert fileSize to long
                                        long fileSizeBytes = Long.parseLong(fileSize.toString());
                                        result.put("fileSize", fileSizeBytes);
                                    } catch (NumberFormatException e) {
                                        Log.w(TAG, "Could not parse file size: " + fileSize, e);
                                        // Store as 0 if parsing fails
                                        result.put("fileSize", 0L);
                                    }
                                }
                                
                                // Ensure we have at least a file URL before completing
                                if (!result.containsKey("fileUrl") || result.get("fileUrl") == null) {
                                    Log.e(TAG, "File URL is null, completing with error");
                                    if (!finalFuture.isDone()) {
                                        finalFuture.completeExceptionally(new Exception("Document upload completed but no URL received"));
                                    }
                                } else {
                                    if (!finalFuture.isDone()) {
                                        // Create a new HashMap to ensure thread safety
                                        Map<String, Object> finalResult = new HashMap<>(result);
                                        finalFuture.complete(finalResult);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in document onSuccess callback", e);
                                if (!finalFuture.isDone()) {
                                    finalFuture.completeExceptionally(e);
                                }
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Document upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            if (!finalFuture.isDone()) {
                                finalFuture.completeExceptionally(new Exception("Document upload failed: " + (error != null ? error.getDescription() : "Unknown error")));
                            }
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Document upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
            // Get original filename and preserve extension for audio
            String originalFilename = getFileName(context, audioUri);
            String extension = getFileExtension(originalFilename);
            String filename = "audio_" + System.currentTimeMillis();
            
            // Preserve audio extension if available
            if (extension != null && !extension.isEmpty()) {
                // Common audio extensions
                if (extension.matches("(?i)(mp3|wav|aac|ogg|m4a|flac|wma|amr)")) {
                    filename += "." + extension;
                }
            }

            MediaManager.get().upload(audioUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_audio/" + chatId + "/" + filename)
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Audio upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Audio upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Audio upload successful - requestId: " + (requestId != null ? requestId : "null") + ", resultData: " + resultData);
                            
                            // Check if resultData is null
                            if (resultData == null) {
                                Log.e(TAG, "Audio upload resultData is null");
                                future.completeExceptionally(new Exception("Audio upload completed but result data is null"));
                                return;
                            }
                            
                            String audioUrl = (String) resultData.get("secure_url");
                            if (audioUrl != null) {
                                future.complete(audioUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Audio upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            future.completeExceptionally(new Exception(error != null ? error.getDescription() : "Unknown error"));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Audio upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
            // Get original filename and preserve extension for videos
            String originalFilename = getFileName(context, videoUri);
            String extension = getFileExtension(originalFilename);
            String filename = "video_" + System.currentTimeMillis();
            
            // Preserve video extension if available
            if (extension != null && !extension.isEmpty()) {
                // Common video extensions
                if (extension.matches("(?i)(mp4|avi|mov|wmv|flv|webm|mkv|m4v|3gp)")) {
                    filename += "." + extension;
                }
            }

            MediaManager.get().upload(videoUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", "chat_video/" + chatId + "/" + filename)
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Video upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Video upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Video upload successful - requestId: " + (requestId != null ? requestId : "null") + ", resultData: " + resultData);
                            
                            // Check if resultData is null
                            if (resultData == null) {
                                Log.e(TAG, "Video upload resultData is null");
                                future.completeExceptionally(new Exception("Video upload completed but result data is null"));
                                return;
                            }
                            
                            String videoUrl = (String) resultData.get("secure_url");
                            if (videoUrl != null) {
                                future.complete(videoUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Video upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            future.completeExceptionally(new Exception(error != null ? error.getDescription() : "Unknown error"));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Video upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
            // Get original filename and preserve extension
            String originalFilename = getFileName(context, documentUri);
            String extension = getFileExtension(originalFilename);
            String filename = "document_" + System.currentTimeMillis();
            
            // Preserve the original extension if available
            if (extension != null && !extension.isEmpty()) {
                filename += "." + extension;
            }

            MediaManager.get().upload(documentUri)
                    .unsigned("pingme_upload_preset")
                    .option("public_id", folder + "/" + filename)
                    .option("resource_type", "auto") // Auto detect resource type
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Document upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Document upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Document upload successful - requestId: " + (requestId != null ? requestId : "null") + ", resultData: " + resultData);
                            
                            // Check if resultData is null
                            if (resultData == null) {
                                Log.e(TAG, "Document upload resultData is null");
                                future.completeExceptionally(new Exception("Document upload completed but result data is null"));
                                return;
                            }
                            
                            String documentUrl = (String) resultData.get("secure_url");
                            if (documentUrl != null) {
                                future.complete(documentUrl);
                            } else {
                                future.completeExceptionally(new Exception("No URL returned"));
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e(TAG, "Document upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            future.completeExceptionally(new Exception(error != null ? error.getDescription() : "Unknown error"));
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Document upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
                            Log.d(TAG, "Upload started: " + (requestId != null ? requestId : "null"));
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            Log.d(TAG, "Upload progress: " + bytes + "/" + totalBytes);
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            // Check if resultData is null
                            if (resultData == null) {
                                Log.e(TAG, "Upload resultData is null");
                                callback.onUploadError("Upload completed but result data is null");
                                return;
                            }
                            
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
                            Log.e(TAG, "Upload failed - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
                            callback.onUploadError(error != null ? error.getDescription() : "Unknown error");
                        }

                        @Override
                        public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                            Log.w(TAG, "Upload rescheduled - requestId: " + (requestId != null ? requestId : "null") + ", error: " + (error != null ? error.getDescription() : "null"));
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
    
    private long getFileSize(Context context, Uri uri) throws Exception {
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE);
                if (sizeIndex != -1) {
                    return cursor.getLong(sizeIndex);
                }
            }
        }
        
        // Fallback: try to get file size from file path
        try {
            java.io.File file = new java.io.File(uri.getPath());
            if (file.exists()) {
                return file.length();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get file size from path", e);
        }
        
        throw new Exception("Could not determine file size");
    }
    
    private String getFileName(Context context, Uri uri) {
        String fileName = null;
        try {
            // Try to get filename from content resolver
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
            
            // Fallback: try to get filename from URI path
            if (fileName == null || fileName.isEmpty()) {
                String path = uri.getPath();
                if (path != null && path.contains("/")) {
                    fileName = path.substring(path.lastIndexOf("/") + 1);
                }
            }
            
            // Fallback: try to get filename from URI last path segment
            if (fileName == null || fileName.isEmpty()) {
                fileName = uri.getLastPathSegment();
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error getting filename from URI", e);
        }
        
        return fileName;
    }
    
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return null;
    }
}