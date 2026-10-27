package com.appoup.videotomp3.model;

import java.util.concurrent.atomic.AtomicInteger;

/** A queued conversion job with live status. */
public class Job {
    public enum State { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    public final String id = "job-" + SEQ.getAndIncrement();
    public final ConversionOptions options;
    public volatile State state = State.QUEUED;
    public volatile int progress = 0;      // 0..100
    public volatile String error;
    public volatile String outputPath;     // set on success
    public volatile boolean cancelRequested = false;

    public Job(ConversionOptions options) {
        this.options = options;
    }

    public String displayName() {
        String n = options.outputName != null && !options.outputName.isEmpty()
                ? options.outputName : options.sourceName;
        return n + "." + options.format.ext;
    }
}
