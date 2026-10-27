package com.appoup.videotomp3.model;

import android.net.Uri;

/** Everything needed to convert one source video into one audio file. */
public class ConversionOptions {
    public Uri sourceUri;
    public String sourceName = "audio";
    public String outputName;              // base name without extension (null -> derived)
    public OutputFormat format = OutputFormat.MP3;
    public int bitrateKbps = 192;          // MP3/M4A
    public int sampleRate = 0;             // 0 = keep source
    public int channels = 0;               // 0 = keep source, 1 = mono, 2 = stereo
    public float gain = 1.0f;              // volume multiplier
    public int fadeInMs = 0;
    public int fadeOutMs = 0;
    public long trimStartMs = 0;           // 0 = from start
    public long trimEndMs = 0;             // 0 = to end
    public long sourceDurationMs = 0;

    // Optional ID3 tags (MP3 only)
    public String tagTitle;
    public String tagArtist;
    public String tagAlbum;
    public String tagGenre;
    public byte[] coverImage;              // JPEG/PNG bytes
    public String coverMime;               // image/jpeg or image/png

    public boolean hasTrim() {
        return trimStartMs > 0 || (trimEndMs > 0 && trimEndMs > trimStartMs);
    }
}
