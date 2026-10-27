package com.appoup.videotomp3;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.appoup.videotomp3.ui.ConvertFragment;
import com.appoup.videotomp3.ui.LibraryFragment;
import com.appoup.videotomp3.ui.QueueFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private final Fragment convertFragment = new ConvertFragment();
    private final Fragment queueFragment = new QueueFragment();
    private final Fragment libraryFragment = new LibraryFragment();
    private Fragment active = convertFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, libraryFragment, "library").hide(libraryFragment)
                    .add(R.id.container, queueFragment, "queue").hide(queueFragment)
                    .add(R.id.container, convertFragment, "convert")
                    .commit();
        }

        BottomNavigationView nav = findViewById(R.id.bottomNav);

        // targetSdk 35 draws edge-to-edge: push the bar below the status bar and
        // the bottom navigation above the gesture/navigation bar.
        final View appBar = findViewById(R.id.appBar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootMain), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBar.setPadding(appBar.getPaddingLeft(), bars.top,
                    appBar.getPaddingRight(), appBar.getPaddingBottom());
            nav.setPadding(nav.getPaddingLeft(), nav.getPaddingTop(),
                    nav.getPaddingRight(), bars.bottom);
            return insets;
        });

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_convert) return show(convertFragment);
            if (id == R.id.nav_queue) return show(queueFragment);
            if (id == R.id.nav_library) return show(libraryFragment);
            return false;
        });

        requestNotificationPermission();
    }

    private boolean show(Fragment f) {
        if (f == active) return true;
        getSupportFragmentManager().beginTransaction().hide(active).show(f).commit();
        active = f;
        return true;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
