package com.pingme.android.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class VideoPlayerUtil {
    private static final String TAG = "VideoPlayerUtil";
    private static VideoPlayerUtil instance;
    
    public static VideoPlayerUtil getInstance() {
        if (instance == null) {
            instance = new VideoPlayerUtil();
        }
        return instance;
    }
    
    private VideoPlayerUtil() {
        // Private constructor
    }
    
    public void playVideo(String videoUrl, Context context) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(context, "Invalid video URL", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoUrl), "video/*");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error playing video", e);
            Toast.makeText(context, "Unable to play video", Toast.LENGTH_SHORT).show();
        }
    }
    
    public CompletableFuture<File> downloadVideo(String videoUrl, Context context) {
        CompletableFuture<File> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                URL url = new URL(videoUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    future.completeExceptionally(new IOException("Server returned HTTP " + connection.getResponseCode()));
                    return;
                }
                
                // Create cache directory
                File cacheDir = new File(context.getCacheDir(), "videos");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                
                // Create output file
                String fileName = "video_" + System.currentTimeMillis() + ".mp4";
                File outputFile = new File(cacheDir, fileName);
                
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(outputFile)) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
                
                future.complete(outputFile);
                
            } catch (Exception e) {
                Log.e(TAG, "Error downloading video", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    public void openVideoInExternalPlayer(String videoUrl, Context context) {
        downloadVideo(videoUrl, context)
            .thenAccept(file -> {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                context, 
                                context.getPackageName() + ".fileprovider", 
                                file
                            );
                            intent.setDataAndType(uri, "video/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Error opening video in external player", e);
                            // Fallback to direct URL
                            playVideo(videoUrl, context);
                        }
                    });
                }
            })
            .exceptionally(throwable -> {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        Log.e(TAG, "Error downloading video for external player", throwable);
                        // Fallback to direct URL
                        playVideo(videoUrl, context);
                    });
                }
                return null;
            });
    }
    
    public long getVideoFileSize(String videoUrl) {
        try {
            URL url = new URL(videoUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();
            
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return connection.getContentLength();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting video file size", e);
        }
        return -1;
    }
    
    public String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    public String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}