package com.appoup.videotomp3.core;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings (defaults for new conversions). */
public class Prefs {
    private static final String FILE = "settings";
    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String defaultFormat() { return sp.getString("format", "MP3"); }
    public void setDefaultFormat(String v) { sp.edit().putString("format", v).apply(); }

    public int defaultBitrate() { return sp.getInt("bitrate", 192); }
    public void setDefaultBitrate(int v) { sp.edit().putInt("bitrate", v).apply(); }

    public boolean autoSaveToMusic() { return sp.getBoolean("auto_save_music", false); }
    public void setAutoSaveToMusic(boolean v) { sp.edit().putBoolean("auto_save_music", v).apply(); }
}
