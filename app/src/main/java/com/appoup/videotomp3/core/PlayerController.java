package com.appoup.videotomp3.core;

import android.media.MediaPlayer;

/** Simple single-stream audio player for the library. */
public class PlayerController {

    public interface Listener { void onPlayerStateChanged(); }

    private MediaPlayer player;
    private String currentId;
    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    public String currentId() { return currentId; }

    public boolean isPlaying() {
        try { return player != null && player.isPlaying(); } catch (Exception e) { return false; }
    }

    public void toggle(String id, String path) {
        if (id.equals(currentId) && player != null) {
            try {
                if (player.isPlaying()) player.pause();
                else player.start();
            } catch (Exception ignored) {}
            notifyChanged();
            return;
        }
        stop();
        try {
            player = new MediaPlayer();
            player.setDataSource(path);
            player.setOnCompletionListener(mp -> { stop(); notifyChanged(); });
            player.prepare();
            player.start();
            currentId = id;
        } catch (Exception e) {
            stop();
        }
        notifyChanged();
    }

    public float fraction() {
        try {
            if (player == null) return 0;
            int dur = player.getDuration();
            return dur > 0 ? (float) player.getCurrentPosition() / dur : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void seekToFraction(float f) {
        try {
            if (player != null) {
                int dur = player.getDuration();
                player.seekTo((int) (dur * f));
            }
        } catch (Exception ignored) {}
    }

    public void stop() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        currentId = null;
    }

    private void notifyChanged() {
        if (listener != null) listener.onPlayerStateChanged();
    }
}
