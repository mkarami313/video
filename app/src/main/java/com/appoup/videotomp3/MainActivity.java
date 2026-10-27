package com.appoup.videotomp3;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * VideoToMp3 — extracts the audio track of any video into a real MP3 file,
 * entirely on the device. FFmpeg (with libmp3lame) runs locally: no server,
 * no network, no upload.
 */
public class MainActivity extends AppCompatActivity {

    private TextView txtFile, txtStatus;
    private MaterialButton btnPick, btnConvert, btnShare, btnSave;
    private LinearProgressIndicator progress;
    private View resultBar;

    private Uri inputUri;
    private String inputDisplayName;
    private long totalDurationMs = 0;
    private File outputFile;
    private FFmpegSession currentSession;

    private static final int REQ_WRITE = 42;

    // --- video picker (SAF) ---
    private final androidx.activity.result.ActivityResultLauncher<Intent> pickLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) onVideoPicked(uri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtFile = findViewById(R.id.txtFile);
        txtStatus = findViewById(R.id.txtStatus);
        btnPick = findViewById(R.id.btnPick);
        btnConvert = findViewById(R.id.btnConvert);
        btnShare = findViewById(R.id.btnShare);
        btnSave = findViewById(R.id.btnSave);
        progress = findViewById(R.id.progress);
        resultBar = findViewById(R.id.resultBar);

        btnPick.setOnClickListener(v -> pickVideo());
        btnConvert.setOnClickListener(v -> startConversion());
        btnShare.setOnClickListener(v -> shareOutput());
        btnSave.setOnClickListener(v -> saveToMusic());
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*"});
        pickLauncher.launch(intent);
    }

    private void onVideoPicked(Uri uri) {
        inputUri = uri;
        inputDisplayName = queryDisplayName(uri);
        txtFile.setText(getString(R.string.selected, inputDisplayName));
        btnConvert.setEnabled(true);
        resultBar.setVisibility(View.GONE);
        txtStatus.setText("");

        // Read total duration so we can show a real percentage during encoding.
        totalDurationMs = 0;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(this, uri);
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) totalDurationMs = Long.parseLong(d);
            mmr.release();
        } catch (Exception ignored) {
        }
    }

    private int selectedBitrate() {
        int id = ((com.google.android.material.chip.ChipGroup) findViewById(R.id.chipQuality))
                .getCheckedChipId();
        if (id == R.id.chip128) return 128;
        if (id == R.id.chip320) return 320;
        return 192;
    }

    private void startConversion() {
        if (inputUri == null) return;

        int bitrate = selectedBitrate();
        String base = stripExtension(inputDisplayName == null ? "audio" : inputDisplayName);
        outputFile = new File(getExternalFilesDir(null), sanitize(base) + ".mp3");

        String inputSaf;
        try {
            inputSaf = FFmpegKitConfig.getSafParameterForRead(this, inputUri);
        } catch (Exception e) {
            toast(getString(R.string.err_open));
            return;
        }

        String[] cmd = new String[]{
                "-i", inputSaf,
                "-vn",                       // drop the video stream
                "-acodec", "libmp3lame",     // true MP3 encoder
                "-b:a", bitrate + "k",
                "-id3v2_version", "3",
                "-y", outputFile.getAbsolutePath()
        };

        setBusy(true);
        txtStatus.setText(getString(R.string.converting));

        currentSession = FFmpegKit.executeWithArgumentsAsync(cmd,
                session -> runOnUiThread(() -> {
                    setBusy(false);
                    if (ReturnCode.isSuccess(session.getReturnCode())
                            && outputFile.exists() && outputFile.length() > 0) {
                        progress.setProgress(100);
                        txtStatus.setText(getString(R.string.done, humanSize(outputFile.length())));
                        resultBar.setVisibility(View.VISIBLE);
                    } else if (ReturnCode.isCancel(session.getReturnCode())) {
                        txtStatus.setText(getString(R.string.cancelled));
                    } else {
                        txtStatus.setText(getString(R.string.failed));
                    }
                }),
                log -> { /* logs available via log.getMessage() if needed */ },
                statistics -> {
                    if (totalDurationMs > 0) {
                        int pct = (int) (statistics.getTime() / (double) totalDurationMs * 100.0);
                        if (pct < 0) pct = 0;
                        if (pct > 100) pct = 100;
                        final int p = pct;
                        runOnUiThread(() -> progress.setProgress(p));
                    }
                });
    }

    private void setBusy(boolean busy) {
        btnPick.setEnabled(!busy);
        btnConvert.setEnabled(!busy && inputUri != null);
        progress.setVisibility(busy ? View.VISIBLE : View.VISIBLE);
        if (busy) {
            progress.setIndeterminate(totalDurationMs <= 0);
            progress.setProgress(0);
            resultBar.setVisibility(View.GONE);
        }
    }

    // --- share the produced MP3 ---
    private void shareOutput() {
        if (outputFile == null || !outputFile.exists()) return;
        Uri uri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", outputFile);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("audio/mpeg");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.share)));
    }

    // --- save the produced MP3 into the public Music library ---
    private void saveToMusic() {
        if (outputFile == null || !outputFile.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, outputFile.getName());
                values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
                Uri target = getContentResolver().insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                if (target == null) { toast(getString(R.string.save_failed)); return; }
                try (InputStream in = new java.io.FileInputStream(outputFile);
                     OutputStream out = getContentResolver().openOutputStream(target)) {
                    copy(in, out);
                }
                toast(getString(R.string.saved_music));
            } catch (Exception e) {
                toast(getString(R.string.save_failed));
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
                return;
            }
            try {
                File musicDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MUSIC);
                if (!musicDir.exists()) musicDir.mkdirs();
                File dest = new File(musicDir, outputFile.getName());
                try (InputStream in = new java.io.FileInputStream(outputFile);
                     OutputStream out = new java.io.FileOutputStream(dest)) {
                    copy(in, out);
                }
                MediaScannerConnection.scanFile(this,
                        new String[]{dest.getAbsolutePath()}, new String[]{"audio/mpeg"}, null);
                toast(getString(R.string.saved_music));
            } catch (Exception e) {
                toast(getString(R.string.save_failed));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveToMusic();
        } else if (requestCode == REQ_WRITE) {
            toast(getString(R.string.need_permission));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && currentSession != null) {
            FFmpegKit.cancel(currentSession.getSessionId());
        }
    }

    // ---------- helpers ----------
    private String queryDisplayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        if (name == null) name = uri.getLastPathSegment();
        if (name == null) name = "video";
        return name;
    }

    private static String stripExtension(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static String sanitize(String n) {
        return n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        out.flush();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
