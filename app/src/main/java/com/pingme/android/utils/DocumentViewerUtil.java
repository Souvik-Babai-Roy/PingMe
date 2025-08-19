package com.pingme.android.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class DocumentViewerUtil {
    private static final String TAG = "DocumentViewerUtil";
    private static DocumentViewerUtil instance;
    
    public static DocumentViewerUtil getInstance() {
        if (instance == null) {
            instance = new DocumentViewerUtil();
        }
        return instance;
    }
    
    private DocumentViewerUtil() {
        // Private constructor
    }
    
    public void openDocument(String documentUrl, String fileName, Context context) {
        if (documentUrl == null || documentUrl.isEmpty()) {
            Toast.makeText(context, "Invalid document URL", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading toast
        Toast.makeText(context, "Opening document...", Toast.LENGTH_SHORT).show();
        
        downloadDocument(documentUrl, fileName, context)
            .thenAccept(file -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        context, 
                        context.getPackageName() + ".fileprovider", 
                        file
                    );
                    
                    String mimeType = getMimeType(file.getName());
                    intent.setDataAndType(uri, mimeType);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error opening document", e);
                    // Fallback to browser
                    openInBrowser(documentUrl, context);
                }
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "Error downloading document", throwable);
                Toast.makeText(context, "Failed to download document", Toast.LENGTH_SHORT).show();
                // Fallback to browser
                openInBrowser(documentUrl, context);
                return null;
            });
    }
    
    public CompletableFuture<File> downloadDocument(String documentUrl, String fileName, Context context) {
        CompletableFuture<File> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                URL url = new URL(documentUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    future.completeExceptionally(new IOException("Server returned HTTP " + connection.getResponseCode()));
                    return;
                }
                
                // Create documents directory
                File docsDir = new File(context.getCacheDir(), "documents");
                if (!docsDir.exists()) {
                    docsDir.mkdirs();
                }
                
                // Use provided filename or generate one
                String outputFileName = fileName;
                if (outputFileName == null || outputFileName.isEmpty()) {
                    outputFileName = "document_" + System.currentTimeMillis();
                    
                    // Try to get extension from URL
                    String extension = getExtensionFromUrl(documentUrl);
                    if (extension != null) {
                        outputFileName += "." + extension;
                    }
                }
                
                File outputFile = new File(docsDir, outputFileName);
                
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
                Log.e(TAG, "Error downloading document", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    private void openInBrowser(String url, Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening in browser", e);
            Toast.makeText(context, "Unable to open document", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getMimeType(String fileName) {
        String extension = getExtension(fileName);
        if (extension != null) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mimeType != null) {
                return mimeType;
            }
        }
        return "application/octet-stream"; // Default MIME type
    }
    
    private String getExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return null;
    }
    
    private String getExtensionFromUrl(String url) {
        try {
            // Remove query parameters
            String cleanUrl = url.split("\\?")[0];
            
            if (cleanUrl.contains(".")) {
                String extension = cleanUrl.substring(cleanUrl.lastIndexOf(".") + 1);
                // Common document extensions
                if (extension.matches("(?i)(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|rtf|odt|ods|odp)")) {
                    return extension.toLowerCase();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting extension from URL", e);
        }
        return null;
    }
    
    public String getDocumentTypeIcon(String fileName) {
        String extension = getExtension(fileName);
        if (extension != null) {
            extension = extension.toLowerCase();
            switch (extension) {
                case "pdf":
                    return "📄";
                case "doc":
                case "docx":
                    return "📝";
                case "xls":
                case "xlsx":
                    return "📊";
                case "ppt":
                case "pptx":
                    return "📋";
                case "txt":
                    return "📃";
                case "zip":
                case "rar":
                case "7z":
                    return "🗜️";
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                    return "🖼️";
                case "mp4":
                case "avi":
                case "mov":
                    return "🎥";
                case "mp3":
                case "wav":
                case "aac":
                    return "🎵";
                default:
                    return "📄";
            }
        }
        return "📄";
    }
    
    public String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}