package com.appoup.videotomp3.engine;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/** Decodes an audio track into a small array of normalized peak amplitudes. */
public class WaveformExtractor {

    public interface Callback {
        void onWaveform(float[] amplitudes);  // values 0..1, length == buckets
        void onError();
    }

    private static final int TIMEOUT_US = 10000;

    /** Runs synchronously; call from a background thread. */
    public static float[] extract(Context ctx, Uri uri, int buckets) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(ctx, uri, null);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; format = f; break; }
            }
            if (track < 0 || format == null) throw new Exception("no audio");
            extractor.selectTrack(track);

            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : 0;

            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();

            float[] peaks = new float[buckets];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inIndex);
                        int size = inBuf == null ? -1 : extractor.readSampleData(inBuf, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIndex >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    if (info.size > 0 && durationUs > 0) {
                        ByteBuffer outBuf = decoder.getOutputBuffer(outIndex);
                        if (outBuf != null) {
                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            int bucket = (int) (info.presentationTimeUs * buckets / durationUs);
                            if (bucket < 0) bucket = 0;
                            if (bucket >= buckets) bucket = buckets - 1;
                            float peak = peakOf(outBuf);
                            if (peak > peaks[bucket]) peaks[bucket] = peak;
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false);
                }
            }

            // normalize
            float max = 0.0001f;
            for (float p : peaks) if (p > max) max = p;
            for (int i = 0; i < buckets; i++) peaks[i] = peaks[i] / max;
            // fill any empty buckets with a small baseline for nicer rendering
            for (int i = 0; i < buckets; i++) if (peaks[i] < 0.02f) peaks[i] = 0.02f;
            return peaks;
        } finally {
            try { if (decoder != null) { decoder.stop(); decoder.release(); } } catch (Exception ignored) {}
            try { extractor.release(); } catch (Exception ignored) {}
        }
    }

    private static float peakOf(ByteBuffer buf) {
        ShortBuffer sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int n = sb.remaining();
        int step = Math.max(1, n / 256);  // sample within the buffer for speed
        float peak = 0;
        for (int i = 0; i < n; i += step) {
            float v = Math.abs(sb.get(i)) / 32768f;
            if (v > peak) peak = v;
        }
        return peak;
    }
}
