package com.appoup.videotomp3.core;

import android.content.Context;

import com.appoup.videotomp3.model.HistoryItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/** Persists the list of produced files as JSON in internal storage. */
public class HistoryStore {
    private final File file;

    public HistoryStore(Context ctx) {
        file = new File(ctx.getApplicationContext().getFilesDir(), "history.json");
    }

    public synchronized List<HistoryItem> load() {
        List<HistoryItem> list = new ArrayList<>();
        if (!file.exists()) return list;
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            byte[] data = new byte[(int) raf.length()];
            raf.readFully(data);
            raf.close();
            JSONArray arr = new JSONArray(new String(data, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(HistoryItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public synchronized void save(List<HistoryItem> list) {
        try {
            JSONArray arr = new JSONArray();
            for (HistoryItem h : list) arr.put(h.toJson());
            FileOutputStream out = new FileOutputStream(file);
            out.write(arr.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
        } catch (Exception ignored) {
        }
    }

    public synchronized void add(HistoryItem item) {
        List<HistoryItem> list = load();
        list.add(0, item);
        save(list);
    }

    public synchronized void remove(String id) {
        List<HistoryItem> list = load();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id != null && list.get(i).id.equals(id)) list.remove(i);
        }
        save(list);
    }
}
