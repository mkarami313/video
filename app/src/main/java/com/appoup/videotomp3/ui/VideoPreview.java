package com.appoup.videotomp3.ui;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

/**
 * Plays a video into a TextureView (so it clips to rounded card corners),
 * with a center play/pause overlay. Preview only — audio plays too.
 */
public class VideoPreview implements TextureView.SurfaceTextureListener {

    private final Context ctx;
    private final TextureView textureView;
    private final View playButton;

    private MediaPlayer player;
    private Surface surface;
    private Uri pending;
    private boolean prepared = false;

    public VideoPreview(Context ctx, TextureView textureView, View playButton) {
        this.ctx = ctx;
        this.textureView = textureView;
        this.playButton = playButton;
        textureView.setSurfaceTextureListener(this);
        playButton.setOnClickListener(x -> toggle());
        textureView.setOnClickListener(x -> toggle());
    }

    public void setSource(Uri uri) {
        this.pending = uri;
        this.prepared = false;
        if (surface != null) prepare();
    }

    private void prepare() {
        release();
        if (pending == null || surface == null) return;
        try {
            player = new MediaPlayer();
            player.setSurface(surface);
            player.setDataSource(ctx, pending);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.seekTo(1);
                showPlayIcon(true);
            });
            player.setOnCompletionListener(mp -> {
                showPlayIcon(true);
                try { mp.seekTo(1); } catch (Exception ignored) {}
            });
            player.prepareAsync();
        } catch (Exception e) {
            release();
        }
    }

    private void toggle() {
        if (player == null || !prepared) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                showPlayIcon(true);
            } else {
                player.start();
                showPlayIcon(false);
            }
        } catch (Exception ignored) {
        }
    }

    public void pause() {
        try {
            if (player != null && player.isPlaying()) {
                player.pause();
                showPlayIcon(true);
            }
        } catch (Exception ignored) {
        }
    }

    public void release() {
        prepared = false;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void showPlayIcon(boolean show) {
        playButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // --- SurfaceTextureListener ---
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        surface = new Surface(st);
        if (pending != null) prepare();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) { }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        release();
        if (surface != null) { surface.release(); surface = null; }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) { }
}
