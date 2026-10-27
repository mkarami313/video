package com.appoup.videotomp3.core;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.appoup.videotomp3.engine.Transcoder;
import com.appoup.videotomp3.model.ConversionOptions;
import com.appoup.videotomp3.model.HistoryItem;
import com.appoup.videotomp3.model.Job;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/** Singleton queue + worker that runs conversions sequentially in the background. */
public class ConversionManager {

    public interface Listener {
        void onQueueChanged();
        void onJobProgress(Job job);
    }

    private static ConversionManager instance;

    public static synchronized ConversionManager get(Context ctx) {
        if (instance == null) instance = new ConversionManager(ctx.getApplicationContext());
        return instance;
    }

    private final Context appCtx;
    private final HistoryStore history;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Job> jobs = new CopyOnWriteArrayList<>();
    private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Job current;
    private Thread worker;

    private ConversionManager(Context ctx) {
        this.appCtx = ctx;
        this.history = new HistoryStore(ctx);
        startWorker();
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public List<Job> getJobs() { return new ArrayList<>(jobs); }

    public boolean hasActiveWork() {
        for (Job j : jobs) {
            if (j.state == Job.State.QUEUED || j.state == Job.State.RUNNING) return true;
        }
        return false;
    }

    public Job enqueue(ConversionOptions options) {
        Job job = new Job(options);
        jobs.add(0, job);
        queue.add(job);
        notifyQueue();
        return job;
    }

    public void cancel(Job job) {
        job.cancelRequested = true;
        if (job.state == Job.State.QUEUED) {
            job.state = Job.State.CANCELLED;
            queue.remove(job);
            notifyQueue();
        }
    }

    public void clearFinished() {
        for (Job j : new ArrayList<>(jobs)) {
            if (j.state == Job.State.DONE || j.state == Job.State.FAILED
                    || j.state == Job.State.CANCELLED) {
                jobs.remove(j);
            }
        }
        notifyQueue();
    }

    private void startWorker() {
        worker = new Thread(() -> {
            while (true) {
                try {
                    Job job = queue.take();
                    if (job.cancelRequested || job.state == Job.State.CANCELLED) continue;
                    current = job;
                    runJob(job);
                    current = null;
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable t) {
                    // keep the worker alive no matter what
                }
            }
        }, "conversion-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void runJob(Job job) {
        job.state = Job.State.RUNNING;
        job.progress = 0;
        notifyQueue();

        File outDir = new File(appCtx.getExternalFilesDir(null), "output");
        if (!outDir.exists()) outDir.mkdirs();
        File target = uniqueFile(outDir, job.displayName());

        try {
            Transcoder.convert(appCtx, job.options, target, new Transcoder.ProgressListener() {
                @Override public void onProgress(int percent) {
                    job.progress = percent;
                    notifyProgress(job);
                }
                @Override public boolean isCancelled() {
                    return job.cancelRequested;
                }
            });

            job.outputPath = target.getAbsolutePath();
            job.progress = 100;
            job.state = Job.State.DONE;

            HistoryItem item = new HistoryItem();
            item.id = job.id;
            item.name = target.getName();
            item.path = target.getAbsolutePath();
            item.format = job.options.format.ext;
            item.sizeBytes = target.length();
            item.durationMs = probeDuration(target);
            item.createdAt = System.currentTimeMillis();
            history.add(item);

            if (new Prefs(appCtx).autoSaveToMusic()) {
                MediaStoreSaver.saveToMusic(appCtx, target, job.options.format.mime);
            }
        } catch (Transcoder.CancelledException ce) {
            job.state = Job.State.CANCELLED;
            if (target.exists()) target.delete();
        } catch (Throwable t) {
            job.state = Job.State.FAILED;
            job.error = t.getMessage();
            if (target.exists()) target.delete();
        }
        notifyQueue();
    }

    private long probeDuration(File f) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(f.getAbsolutePath());
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mmr.release();
            return d != null ? Long.parseLong(d) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 1;
        while (f.exists()) {
            f = new File(dir, base + " (" + i + ")" + ext);
            i++;
        }
        return f;
    }

    private void notifyQueue() {
        main.post(() -> { for (Listener l : listeners) l.onQueueChanged(); });
    }

    private void notifyProgress(Job job) {
        main.post(() -> { for (Listener l : listeners) l.onJobProgress(job); });
    }
}
