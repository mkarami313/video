package com.appoup.videotomp3.engine;

/** Applies volume gain and fade in/out to interleaved 16-bit PCM in place. */
public class PcmProcessor {
    private final float gain;
    private final int channels;
    private final long fadeInSamples;    // per-channel sample count
    private final long fadeOutSamples;   // per-channel sample count
    private final long totalSamples;     // per-channel total (0 = unknown -> no fade-out)
    private long processedSamples = 0;   // per-channel processed so far

    public PcmProcessor(float gain, int channels, int sampleRate,
                        int fadeInMs, int fadeOutMs, long totalPerChannelSamples) {
        this.gain = gain;
        this.channels = Math.max(1, channels);
        this.fadeInSamples = (long) fadeInMs * sampleRate / 1000L;
        this.fadeOutSamples = (long) fadeOutMs * sampleRate / 1000L;
        this.totalSamples = totalPerChannelSamples;
    }

    public void process(short[] samples, int length) {
        boolean doGain = Math.abs(gain - 1.0f) > 0.001f;
        boolean doFadeIn = fadeInSamples > 0;
        boolean doFadeOut = fadeOutSamples > 0 && totalSamples > 0;
        if (!doGain && !doFadeIn && !doFadeOut) {
            processedSamples += length / channels;
            return;
        }
        int frames = length / channels;
        for (int f = 0; f < frames; f++) {
            long frameIndex = processedSamples + f;
            float factor = gain;
            if (doFadeIn && frameIndex < fadeInSamples) {
                factor *= (float) frameIndex / fadeInSamples;
            }
            if (doFadeOut) {
                long remaining = totalSamples - frameIndex;
                if (remaining < fadeOutSamples && remaining >= 0) {
                    factor *= (float) remaining / fadeOutSamples;
                }
            }
            if (Math.abs(factor - 1.0f) > 0.0001f) {
                for (int c = 0; c < channels; c++) {
                    int idx = f * channels + c;
                    int v = Math.round(samples[idx] * factor);
                    if (v > 32767) v = 32767;
                    else if (v < -32768) v = -32768;
                    samples[idx] = (short) v;
                }
            }
        }
        processedSamples += frames;
    }
}
