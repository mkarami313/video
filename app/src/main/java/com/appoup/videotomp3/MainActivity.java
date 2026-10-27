package com.appoup.videotomp3;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.naman14.androidlame.AndroidLame;
import com.naman14.androidlame.LameBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * VideoToMp3 — extracts the audio track of any video into a real MP3 file,
 * entirely on the device. The audio is decoded with Android's MediaCodec and
 * re-encoded to MP3 with the native LAME encoder. No server, no network.
 */
public class MainActivity extends AppCompatActivity {

    private TextView txtFile, txtStatus;
    private MaterialButton btnPick, btnConvert, btnShare, btnSave;
    private LinearProgressIndicator progress;
    private View resultBar;

    private Uri inputUri;
    private String inputDisplayName;
    private long totalDurationUs = 0;
    private File outputFile;
    private Thread worker;
    private volatile boolean cancelRequested = false;

    private static final int REQ_WRITE = 42;
    private static final int TIMEOUT_US = 10000;

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

        totalDurationUs = 0;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(this, uri);
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) totalDurationUs = Long.parseLong(d) * 1000L;
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
        if (inputUri == null || (worker != null && worker.isAlive())) return;

        final int bitrate = selectedBitrate();
        String base = stripExtension(inputDisplayName == null ? "audio" : inputDisplayName);
        outputFile = new File(getExternalFilesDir(null), sanitize(base) + ".mp3");

        setBusy(true);
        txtStatus.setText(getString(R.string.converting));
        cancelRequested = false;

        worker = new Thread(() -> {
            boolean ok = false;
            String errMsg = null;
            try {
                ok = transcodeToMp3(inputUri, outputFile, bitrate);
            } catch (Exception e) {
                errMsg = e.getMessage();
            }
            final boolean success = ok && outputFile.exists() && outputFile.length() > 0;
            runOnUiThread(() -> {
                setBusy(false);
                if (cancelRequested) {
                    txtStatus.setText(getString(R.string.cancelled));
                } else if (success) {
                    progress.setIndeterminate(false);
                    progress.setProgress(100);
                    txtStatus.setText(getString(R.string.done, humanSize(outputFile.length())));
                    resultBar.setVisibility(View.VISIBLE);
                } else {
                    txtStatus.setText(getString(R.string.failed));
                }
            });
        });
        worker.start();
    }

    /**
     * Decodes the first audio track of {@code uri} with MediaCodec and encodes
     * the PCM to {@code out} as MP3 using LAME. Returns true on success.
     */
    private boolean transcodeToMp3(Uri uri, File out, int bitrate) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        AndroidLame lame = null;
        OutputStream mp3Stream = null;
        try {
            extractor.setDataSource(this, uri, null);
            int audioTrack = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    format = f;
                    break;
                }
            }
            if (audioTrack < 0 || format == null) return false; // no audio track
            extractor.selectTrack(audioTrack);

            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if (channels < 1) channels = 1;
            if (channels > 2) channels = 2;

            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            lame = new LameBuilder()
                    .setInSampleRate(sampleRate)
                    .setOutChannels(channels)
                    .setOutBitrate(bitrate)
                    .setOutSampleRate(sampleRate)
                    .setQuality(5)
                    .build();

            mp3Stream = new FileOutputStream(out);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (cancelRequested) return false;

                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inIndex);
                        int sampleSize = (inBuf == null) ? -1 : extractor.readSampleData(inBuf, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0);
                            extractor.advance();
                            if (totalDurationUs > 0) {
                                int pct = (int) (pts * 100 / totalDurationUs);
                                publishProgress(pct);
                            }
                        }
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIndex >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                    if (info.size > 0) {
                        ByteBuffer outBuf = decoder.getOutputBuffer(outIndex);
                        if (outBuf != null) {
                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            encodePcm(outBuf, channels, lame, mp3Stream);
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false);
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFmt = decoder.getOutputFormat();
                    // keep sampleRate/channels in sync with the decoder's real output
                    if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            && newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        int ns = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int nc = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if (nc < 1) nc = 1; if (nc > 2) nc = 2;
                        if (ns != sampleRate || nc != channels) {
                            sampleRate = ns;
                            channels = nc;
                            lame.close();
                            lame = new LameBuilder()
                                    .setInSampleRate(sampleRate)
                                    .setOutChannels(channels)
                                    .setOutBitrate(bitrate)
                                    .setOutSampleRate(sampleRate)
                                    .setQuality(5)
                                    .build();
                        }
                    }
                }
            }

            // flush remaining MP3 frames
            byte[] flushBuf = new byte[8192];
            int flushed = lame.flush(flushBuf);
            if (flushed > 0) mp3Stream.write(flushBuf, 0, flushed);
            mp3Stream.flush();
            return true;
        } finally {
            try { if (mp3Stream != null) mp3Stream.close(); } catch (Exception ignored) {}
            try { if (lame != null) lame.close(); } catch (Exception ignored) {}
            try { if (decoder != null) { decoder.stop(); decoder.release(); } } catch (Exception ignored) {}
            try { extractor.release(); } catch (Exception ignored) {}
        }
    }

    private void encodePcm(ByteBuffer pcm, int channels, AndroidLame lame, OutputStream out)
            throws Exception {
        ShortBuffer sb = pcm.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int total = sb.remaining();
        short[] samples = new short[total];
        sb.get(samples);

        int perChannel = (channels == 2) ? total / 2 : total;
        byte[] mp3Buf = new byte[(int) (perChannel * 1.25) + 7200];
        int encoded;

        if (channels == 2) {
            short[] left = new short[perChannel];
            short[] right = new short[perChannel];
            for (int i = 0; i < perChannel; i++) {
                left[i] = samples[2 * i];
                right[i] = samples[2 * i + 1];
            }
            encoded = lame.encode(left, right, perChannel, mp3Buf);
        } else {
            encoded = lame.encode(samples, samples, perChannel, mp3Buf);
        }
        if (encoded > 0) out.write(mp3Buf, 0, encoded);
    }

    private void publishProgress(int pct) {
        final int p = Math.max(0, Math.min(100, pct));
        runOnUiThread(() -> {
            if (progress.isIndeterminate()) progress.setIndeterminate(false);
            progress.setProgress(p);
        });
    }

    private void setBusy(boolean busy) {
        btnPick.setEnabled(!busy);
        btnConvert.setEnabled(!busy && inputUri != null);
        progress.setVisibility(View.VISIBLE);
        if (busy) {
            progress.setIndeterminate(totalDurationUs <= 0);
            progress.setProgress(0);
            resultBar.setVisibility(View.GONE);
        }
    }

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
                     OutputStream out = new FileOutputStream(dest)) {
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
        if (isFinishing()) cancelRequested = true;
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
