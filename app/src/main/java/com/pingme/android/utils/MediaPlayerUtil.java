package com.pingme.android.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MediaPlayerUtil {
    private static final String TAG = "MediaPlayerUtil";
    private static MediaPlayerUtil instance;
    private MediaPlayer currentPlayer;
    private String currentPlayingUrl;
    private MediaPlayerListener currentListener;
    private final Map<String, MediaPlayer> playerCache = new HashMap<>();
    
    public interface MediaPlayerListener {
        void onPrepared();
        void onCompletion();
        void onError(String error);
        void onProgress(int currentPosition, int duration);
    }
    
    public static MediaPlayerUtil getInstance() {
        if (instance == null) {
            instance = new MediaPlayerUtil();
        }
        return instance;
    }
    
    private MediaPlayerUtil() {
        // Private constructor
    }
    
    public void playAudio(String audioUrl, Context context, MediaPlayerListener listener) {
        try {
            // Stop current playback if any
            stopCurrentPlayback();
            
            currentPlayingUrl = audioUrl;
            currentListener = listener;
            
            // Check if we have a cached player for this URL
            if (playerCache.containsKey(audioUrl)) {
                currentPlayer = playerCache.get(audioUrl);
                if (currentPlayer != null) {
                    currentPlayer.seekTo(0);
                    currentPlayer.start();
                    if (listener != null) {
                        listener.onPrepared();
                    }
                    startProgressUpdates();
                    return;
                }
            }
            
            // Create new player
            currentPlayer = new MediaPlayer();
            currentPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            
            currentPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "Audio prepared: " + audioUrl);
                mp.start();
                if (listener != null) {
                    listener.onPrepared();
                }
                startProgressUpdates();
                
                // Cache the player
                playerCache.put(audioUrl, mp);
            });
            
            currentPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Audio completed: " + audioUrl);
                if (listener != null) {
                    listener.onCompletion();
                }
                currentPlayingUrl = null;
                currentListener = null;
            });
            
            currentPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Audio error: " + audioUrl + ", what: " + what + ", extra: " + extra);
                if (listener != null) {
                    listener.onError("Audio playback error: " + what);
                }
                currentPlayingUrl = null;
                currentListener = null;
                return true;
            });
            
            currentPlayer.setDataSource(context, Uri.parse(audioUrl));
            currentPlayer.prepareAsync();
            
        } catch (IOException e) {
            Log.e(TAG, "Error playing audio: " + audioUrl, e);
            if (listener != null) {
                listener.onError("Failed to play audio: " + e.getMessage());
            }
        }
    }
    
    public void pauseAudio() {
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            currentPlayer.pause();
            Log.d(TAG, "Audio paused: " + currentPlayingUrl);
        }
    }
    
    public void resumeAudio() {
        if (currentPlayer != null && !currentPlayer.isPlaying()) {
            currentPlayer.start();
            Log.d(TAG, "Audio resumed: " + currentPlayingUrl);
            startProgressUpdates();
        }
    }
    
    public void stopCurrentPlayback() {
        if (currentPlayer != null) {
            try {
                if (currentPlayer.isPlaying()) {
                    currentPlayer.stop();
                }
                currentPlayer.reset();
                Log.d(TAG, "Audio stopped: " + currentPlayingUrl);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Error stopping audio", e);
            }
        }
        currentPlayingUrl = null;
        currentListener = null;
    }
    
    public void seekTo(int position) {
        if (currentPlayer != null) {
            currentPlayer.seekTo(position);
        }
    }
    
    public boolean isPlaying() {
        return currentPlayer != null && currentPlayer.isPlaying();
    }
    
    public boolean isPlayingUrl(String url) {
        return currentPlayingUrl != null && currentPlayingUrl.equals(url) && isPlaying();
    }
    
    public boolean isCurrentUrl(String url) {
        return currentPlayingUrl != null && currentPlayingUrl.equals(url);
    }
    
    public int getCurrentPosition() {
        if (currentPlayer != null) {
            try {
                return currentPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Error getting current position", e);
            }
        }
        return 0;
    }
    
    public int getDuration() {
        if (currentPlayer != null) {
            try {
                return currentPlayer.getDuration();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Error getting duration", e);
            }
        }
        return 0;
    }
    
    private void startProgressUpdates() {
        if (currentListener == null) return;
        
        new Thread(() -> {
            while (currentPlayer != null && currentPlayer.isPlaying() && currentListener != null) {
                try {
                    int currentPosition = getCurrentPosition();
                    int duration = getDuration();
                    currentListener.onProgress(currentPosition, duration);
                    Thread.sleep(100); // Update every 100ms
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "Error in progress updates", e);
                    break;
                }
            }
        }).start();
    }
    
    public void release() {
        stopCurrentPlayback();
        
        // Release all cached players
        for (MediaPlayer player : playerCache.values()) {
            try {
                if (player != null) {
                    player.release();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error releasing cached player", e);
            }
        }
        playerCache.clear();
        
        if (currentPlayer != null) {
            try {
                currentPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing current player", e);
            }
            currentPlayer = null;
        }
    }
}