package com.appoup.videotomp3.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.appoup.videotomp3.R;
import com.appoup.videotomp3.core.ConversionManager;
import com.appoup.videotomp3.core.ConversionService;
import com.appoup.videotomp3.model.ConversionOptions;
import com.appoup.videotomp3.model.OutputFormat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ConvertFragment extends Fragment {

    private final List<Uri> sources = new ArrayList<>();
    private byte[] coverBytes;
    private String coverMime;

    private TextView txtFile, txtGain;
    private View advancedBox, bitrateBox, emptyState, previewCard;
    private VideoView videoPreview;
    private ImageView imgCover;
    private MaterialButtonToggleGroup toggleFormat, toggleBitrate, toggleChannels, toggleRate;
    private Slider sliderGain;
    private MaterialButton btnConvert, btnAddQueue;
    private TextInputEditText editTrimStart, editTrimEnd, editFadeIn, editFadeOut,
            editTitle, editArtist, editAlbum;

    private final ActivityResultLauncher<Intent> pickVideos =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    sources.clear();
                    Intent data = r.getData();
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                            sources.add(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        sources.add(data.getData());
                    }
                    onSourcesPicked();
                }
            });

    private final ActivityResultLauncher<Intent> pickCover =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null
                        && r.getData().getData() != null) {
                    loadCover(r.getData().getData());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_convert, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        txtFile = v.findViewById(R.id.txtFile);
        txtGain = v.findViewById(R.id.txtGain);
        advancedBox = v.findViewById(R.id.advancedBox);
        bitrateBox = v.findViewById(R.id.bitrateBox);
        emptyState = v.findViewById(R.id.emptyState);
        previewCard = v.findViewById(R.id.previewCard);
        videoPreview = v.findViewById(R.id.videoPreview);
        imgCover = v.findViewById(R.id.imgCover);
        toggleFormat = v.findViewById(R.id.toggleFormat);
        toggleBitrate = v.findViewById(R.id.toggleBitrate);
        toggleChannels = v.findViewById(R.id.toggleChannels);
        toggleRate = v.findViewById(R.id.toggleRate);
        sliderGain = v.findViewById(R.id.sliderGain);
        editTrimStart = v.findViewById(R.id.editTrimStart);
        editTrimEnd = v.findViewById(R.id.editTrimEnd);
        editFadeIn = v.findViewById(R.id.editFadeIn);
        editFadeOut = v.findViewById(R.id.editFadeOut);
        editTitle = v.findViewById(R.id.editTitle);
        editArtist = v.findViewById(R.id.editArtist);
        editAlbum = v.findViewById(R.id.editAlbum);
        btnConvert = v.findViewById(R.id.btnConvert);
        btnAddQueue = v.findViewById(R.id.btnAddQueue);

        v.findViewById(R.id.btnPick).setOnClickListener(x -> pickVideo());
        v.findViewById(R.id.btnCover).setOnClickListener(x -> pickCoverImage());
        btnConvert.setOnClickListener(x -> enqueue());
        btnAddQueue.setOnClickListener(x -> enqueue());

        ((MaterialSwitch) v.findViewById(R.id.switchAdvanced)).setOnCheckedChangeListener(
                (b, checked) -> advancedBox.setVisibility(checked ? View.VISIBLE : View.GONE));

        toggleFormat.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean wav = toggleFormat.getCheckedButtonId() == R.id.fmtWav;
            bitrateBox.setVisibility(wav ? View.GONE : View.VISIBLE);
        });

        sliderGain.addOnChangeListener((slider, value, fromUser) ->
                txtGain.setText(String.format(java.util.Locale.US, "%.1f×", value)));
    }

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pickVideos.launch(i);
    }

    private void pickCoverImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        pickCover.launch(i);
    }

    private void onSourcesPicked() {
        boolean has = !sources.isEmpty();
        btnConvert.setEnabled(has);
        btnAddQueue.setEnabled(has);
        emptyState.setVisibility(has ? View.GONE : View.VISIBLE);
        txtFile.setVisibility(has ? View.VISIBLE : View.GONE);
        previewCard.setVisibility(has ? View.VISIBLE : View.GONE);

        if (!has) {
            stopPreview();
            return;
        }
        if (sources.size() == 1) {
            txtFile.setText(getString(R.string.selected, queryName(sources.get(0))));
        } else {
            txtFile.setText(sources.size() + " videos selected — previewing first");
        }
        setupPreview(sources.get(0));
    }

    private void setupPreview(Uri uri) {
        try {
            MediaController mc = new MediaController(requireContext());
            mc.setAnchorView(videoPreview);
            videoPreview.setMediaController(mc);
            videoPreview.setVideoURI(uri);
            videoPreview.setOnPreparedListener(mp -> videoPreview.seekTo(1));
            videoPreview.requestFocus();
        } catch (Exception ignored) {
        }
    }

    private void stopPreview() {
        try {
            if (videoPreview.isPlaying()) videoPreview.stopPlayback();
        } catch (Exception ignored) {
        }
    }

    private void loadCover(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while (in != null && (r = in.read(buf)) != -1) bos.write(buf, 0, r);
            coverBytes = bos.toByteArray();
            coverMime = requireContext().getContentResolver().getType(uri);
            if (coverMime == null) coverMime = "image/jpeg";
            Bitmap bmp = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length);
            if (bmp != null) {
                imgCover.setImageBitmap(bmp);
                imgCover.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            coverBytes = null;
        }
    }

    private void enqueue() {
        if (sources.isEmpty()) return;
        OutputFormat fmt = selectedFormat();
        int bitrate = selectedBitrate();
        int channels = selectedChannels();
        int rate = selectedRate();
        float gain = sliderGain.getValue();
        int fadeIn = parseInt(editFadeIn);
        int fadeOut = parseInt(editFadeOut);
        long trimStart = parseTime(editTrimStart);
        long trimEnd = parseTime(editTrimEnd);

        ConversionManager manager = ConversionManager.get(requireContext());
        for (Uri uri : sources) {
            ConversionOptions opt = new ConversionOptions();
            opt.sourceUri = uri;
            opt.sourceName = stripExt(queryName(uri));
            opt.format = fmt;
            opt.bitrateKbps = bitrate;
            opt.channels = channels;
            opt.sampleRate = rate;
            opt.gain = gain;
            opt.fadeInMs = fadeIn;
            opt.fadeOutMs = fadeOut;
            opt.trimStartMs = trimStart;
            opt.trimEndMs = trimEnd;
            opt.sourceDurationMs = probeDuration(uri);
            if (fmt == OutputFormat.MP3) {
                opt.tagTitle = text(editTitle);
                opt.tagArtist = text(editArtist);
                opt.tagAlbum = text(editAlbum);
                opt.coverImage = coverBytes;
                opt.coverMime = coverMime;
            }
            manager.enqueue(opt);
        }
        ConversionService.start(requireContext());
        Snackbar.make(requireView(), getString(R.string.enqueued), Snackbar.LENGTH_LONG).show();
    }

    private OutputFormat selectedFormat() {
        int id = toggleFormat.getCheckedButtonId();
        if (id == R.id.fmtM4a) return OutputFormat.M4A;
        if (id == R.id.fmtWav) return OutputFormat.WAV;
        return OutputFormat.MP3;
    }

    private int selectedBitrate() {
        int id = toggleBitrate.getCheckedButtonId();
        if (id == R.id.br128) return 128;
        if (id == R.id.br256) return 256;
        if (id == R.id.br320) return 320;
        return 192;
    }

    private int selectedChannels() {
        int id = toggleChannels.getCheckedButtonId();
        if (id == R.id.chMono) return 1;
        if (id == R.id.chStereo) return 2;
        return 0;
    }

    private int selectedRate() {
        int id = toggleRate.getCheckedButtonId();
        if (id == R.id.rate44) return 44100;
        if (id == R.id.rate22) return 22050;
        if (id == R.id.rate16) return 16000;
        return 0;
    }

    private String queryName(Uri uri) {
        String name = null;
        try (Cursor c = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
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

    private long probeDuration(Uri uri) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(requireContext(), uri);
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mmr.release();
            return d != null ? Long.parseLong(d) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPreview();
    }

    private static String stripExt(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static String text(TextInputEditText e) {
        String s = e.getText() == null ? "" : e.getText().toString().trim();
        return TextUtils.isEmpty(s) ? null : s;
    }

    private static int parseInt(TextInputEditText e) {
        try {
            String s = e.getText() == null ? "" : e.getText().toString().trim();
            return s.isEmpty() ? 0 : Integer.parseInt(s);
        } catch (Exception ex) {
            return 0;
        }
    }

    /** Parses "mm:ss", "hh:mm:ss" or plain seconds into milliseconds. */
    private static long parseTime(TextInputEditText e) {
        String s = e.getText() == null ? "" : e.getText().toString().trim();
        if (s.isEmpty()) return 0;
        try {
            if (s.contains(":")) {
                String[] p = s.split(":");
                long sec = 0;
                for (String part : p) sec = sec * 60 + Long.parseLong(part.trim());
                return sec * 1000;
            }
            return (long) (Double.parseDouble(s) * 1000);
        } catch (Exception ex) {
            return 0;
        }
    }
}
