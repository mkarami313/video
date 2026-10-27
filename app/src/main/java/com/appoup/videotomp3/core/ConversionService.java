package com.appoup.videotomp3.core;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.appoup.videotomp3.App;
import com.appoup.videotomp3.R;
import com.appoup.videotomp3.model.Job;

/** Foreground service that keeps conversions alive and shows live progress. */
public class ConversionService extends Service implements ConversionManager.Listener {

    private static final int NOTIF_ID = 1001;
    private ConversionManager manager;

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, ConversionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        manager = ConversionManager.get(this);
        manager.addListener(this);
        startForegroundSafely(buildNotification("Starting…", 0, false));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!manager.hasActiveWork()) {
            stopSelfSafely();
        }
        return START_NOT_STICKY;
    }

    private void startForegroundSafely(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            try { startForeground(NOTIF_ID, n); } catch (Exception ignored) {}
        }
    }

    private Notification buildNotification(String text, int progress, boolean indeterminate) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, App.CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_convert)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (progress > 0 || indeterminate) {
            b.setProgress(100, progress, indeterminate);
        }
        return b.build();
    }

    private void update() {
        Job running = null;
        int queued = 0;
        for (Job j : manager.getJobs()) {
            if (j.state == Job.State.RUNNING) running = j;
            else if (j.state == Job.State.QUEUED) queued++;
        }
        if (running == null && queued == 0) {
            stopSelfSafely();
            return;
        }
        String text;
        int progress;
        boolean indeterminate;
        if (running != null) {
            String extra = queued > 0 ? "  (+" + queued + " queued)" : "";
            text = "Converting: " + running.displayName() + extra;
            progress = running.progress;
            indeterminate = running.progress <= 0;
        } else {
            text = queued + " in queue";
            progress = 0;
            indeterminate = true;
        }
        try {
            androidx.core.app.NotificationManagerCompat.from(this)
                    .notify(NOTIF_ID, buildNotification(text, progress, indeterminate));
        } catch (SecurityException ignored) {
        }
    }

    private void stopSelfSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {}
        stopSelf();
    }

    @Override
    public void onQueueChanged() { update(); }

    @Override
    public void onJobProgress(Job job) { update(); }

    @Override
    public void onDestroy() {
        if (manager != null) manager.removeListener(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
