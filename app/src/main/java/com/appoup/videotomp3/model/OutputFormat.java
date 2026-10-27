package com.appoup.videotomp3.model;

/** Supported on-device output formats (all encoded natively, no server). */
public enum OutputFormat {
    MP3("mp3", "audio/mpeg"),
    M4A("m4a", "audio/mp4"),
    WAV("wav", "audio/wav");

    public final String ext;
    public final String mime;

    OutputFormat(String ext, String mime) {
        this.ext = ext;
        this.mime = mime;
    }

    public static OutputFormat fromName(String name) {
        if (name == null) return MP3;
        for (OutputFormat f : values()) {
            if (f.name().equalsIgnoreCase(name) || f.ext.equalsIgnoreCase(name)) return f;
        }
        return MP3;
    }
}
