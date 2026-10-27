package com.appoup.videotomp3.engine;

/**
 * Stateful streaming linear resampler for interleaved 16-bit PCM.
 * Channel count is fixed; sample rate is converted from src to dst.
 */
public class Resampler {
    private final int channels;
    private final double ratio;      // src / dst
    private final boolean active;

    private short[] prev;            // previous source frame
    private long srcCounter = 0;     // index of next source frame to consume
    private double nextPos = 0;      // output position in source-frame units

    public Resampler(int channels, int srcRate, int dstRate) {
        this.channels = channels;
        this.active = srcRate != dstRate && dstRate > 0;
        this.ratio = active ? (double) srcRate / dstRate : 1.0;
    }

    public boolean isActive() {
        return active;
    }

    /** Resample an interleaved buffer; returns a newly sized interleaved buffer. */
    public short[] process(short[] in, int length) {
        if (!active) {
            short[] copy = new short[length];
            System.arraycopy(in, 0, copy, 0, length);
            return copy;
        }
        int frames = length / channels;
        // upper bound on produced frames
        int estOut = (int) (frames / ratio) + 2;
        short[] out = new short[(estOut + 1) * channels];
        int outCount = 0;

        short[] cur = new short[channels];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) cur[c] = in[f * channels + c];
            if (prev == null) {
                prev = cur.clone();
                srcCounter = 1;
                continue;
            }
            while (nextPos < srcCounter) {
                double frac = nextPos - (srcCounter - 1);
                for (int c = 0; c < channels; c++) {
                    double v = prev[c] * (1 - frac) + cur[c] * frac;
                    int iv = (int) Math.round(v);
                    if (iv > 32767) iv = 32767; else if (iv < -32768) iv = -32768;
                    if (outCount * channels + c >= out.length) {
                        out = grow(out);
                    }
                    out[outCount * channels + c] = (short) iv;
                }
                outCount++;
                nextPos += ratio;
            }
            System.arraycopy(cur, 0, prev, 0, channels);
            srcCounter++;
        }
        short[] trimmed = new short[outCount * channels];
        System.arraycopy(out, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private short[] grow(short[] a) {
        short[] b = new short[a.length * 2];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }
}
