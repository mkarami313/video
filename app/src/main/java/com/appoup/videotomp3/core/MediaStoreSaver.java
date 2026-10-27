package com.appoup.videotomp3.core;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies a produced audio file into the public Music collection. */
public class MediaStoreSaver {

    public static boolean saveToMusic(Context ctx, File file, String mime) {
        if (file == null || !file.exists()) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
                values.put(MediaStore.Audio.Media.MIME_TYPE, mime);
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
                Uri target = ctx.getContentResolver().insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                if (target == null) return false;
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = ctx.getContentResolver().openOutputStream(target)) {
                    copy(in, out);
                }
                return true;
            } else {
                File musicDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MUSIC);
                if (!musicDir.exists()) musicDir.mkdirs();
                File dest = new File(musicDir, file.getName());
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = new FileOutputStream(dest)) {
                    copy(in, out);
                }
                MediaScannerConnection.scanFile(ctx,
                        new String[]{dest.getAbsolutePath()}, new String[]{mime}, null);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        out.flush();
    }
}
