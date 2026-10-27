package com.appoup.videotomp3.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.appoup.videotomp3.R;
import com.appoup.videotomp3.core.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final View appBar = findViewById(R.id.appBar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBar.setPadding(appBar.getPaddingLeft(), bars.top,
                    appBar.getPaddingRight(), appBar.getPaddingBottom());
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(x -> finish());

        Prefs prefs = new Prefs(this);

        MaterialButtonToggleGroup fmt = findViewById(R.id.toggleFormat);
        fmt.check(formatId(prefs.defaultFormat()));
        fmt.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) prefs.setDefaultFormat(formatName(checkedId));
        });

        MaterialButtonToggleGroup br = findViewById(R.id.toggleBitrate);
        br.check(bitrateId(prefs.defaultBitrate()));
        br.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) prefs.setDefaultBitrate(bitrateValue(checkedId));
        });

        MaterialSwitch autoSave = findViewById(R.id.switchAutoSave);
        autoSave.setChecked(prefs.autoSaveToMusic());
        autoSave.setOnCheckedChangeListener((b, checked) -> prefs.setAutoSaveToMusic(checked));
    }

    private int formatId(String name) {
        if ("M4A".equalsIgnoreCase(name)) return R.id.fmtM4a;
        if ("WAV".equalsIgnoreCase(name)) return R.id.fmtWav;
        return R.id.fmtMp3;
    }

    private String formatName(int id) {
        if (id == R.id.fmtM4a) return "M4A";
        if (id == R.id.fmtWav) return "WAV";
        return "MP3";
    }

    private int bitrateId(int kbps) {
        if (kbps == 128) return R.id.br128;
        if (kbps == 256) return R.id.br256;
        if (kbps == 320) return R.id.br320;
        return R.id.br192;
    }

    private int bitrateValue(int id) {
        if (id == R.id.br128) return 128;
        if (id == R.id.br256) return 256;
        if (id == R.id.br320) return 320;
        return 192;
    }
}
