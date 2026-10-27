package com.appoup.videotomp3.model;

import org.json.JSONException;
import org.json.JSONObject;

/** A completed conversion, persisted in the library. */
public class HistoryItem {
    public String id;
    public String name;        // file name with extension
    public String path;        // absolute path to the produced file
    public String format;      // mp3 / m4a / wav
    public long sizeBytes;
    public long durationMs;
    public long createdAt;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("path", path);
        o.put("format", format);
        o.put("size", sizeBytes);
        o.put("duration", durationMs);
        o.put("createdAt", createdAt);
        return o;
    }

    public static HistoryItem fromJson(JSONObject o) {
        HistoryItem h = new HistoryItem();
        h.id = o.optString("id");
        h.name = o.optString("name");
        h.path = o.optString("path");
        h.format = o.optString("format");
        h.sizeBytes = o.optLong("size");
        h.durationMs = o.optLong("duration");
        h.createdAt = o.optLong("createdAt");
        return h;
    }
}
