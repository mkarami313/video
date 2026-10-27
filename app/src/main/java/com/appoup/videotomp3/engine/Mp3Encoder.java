package com.appoup.videotomp3.engine;

import com.naman14.androidlame.AndroidLame;
import com.naman14.androidlame.LameBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Encodes interleaved PCM to MP3 using the native LAME encoder. */
public class Mp3Encoder implements Encoder {
    private final AndroidLame lame;
    private final OutputStream out;
    private final int channels;
    private short[] left, right;
    private byte[] mp3buf;

    public Mp3Encoder(File target, int sampleRate, int channels, int bitrateKbps) throws Exception {
        this.channels = channels;
        this.out = new BufferedOutputStream(new FileOutputStream(target));
        this.lame = new LameBuilder()
                .setInSampleRate(sampleRate)
                .setOutChannels(channels)
                .setOutBitrate(bitrateKbps)
                .setOutSampleRate(sampleRate)
                .setQuality(2)
                .build();
    }

    @Override
    public void encode(short[] samples, int length) throws Exception {
        int perChannel = channels == 2 ? length / 2 : length;
        if (perChannel <= 0) return;
        ensure(perChannel);
        int encoded;
        if (channels == 2) {
            for (int i = 0; i < perChannel; i++) {
                left[i] = samples[2 * i];
                right[i] = samples[2 * i + 1];
            }
            encoded = lame.encode(left, right, perChannel, mp3buf);
        } else {
            System.arraycopy(samples, 0, left, 0, perChannel);
            encoded = lame.encode(left, left, perChannel, mp3buf);
        }
        if (encoded > 0) out.write(mp3buf, 0, encoded);
    }

    private void ensure(int perChannel) {
        if (left == null || left.length < perChannel) {
            left = new short[perChannel];
            right = new short[perChannel];
            mp3buf = new byte[(int) (perChannel * 1.25) + 7200];
        }
    }

    @Override
    public void finish() throws Exception {
        byte[] flush = new byte[7200];
        int n = lame.flush(flush);
        if (n > 0) out.write(flush, 0, n);
        out.flush();
        try { lame.close(); } catch (Exception ignored) {}
        out.close();
    }

    @Override
    public void abort() {
        try { lame.close(); } catch (Exception ignored) {}
        try { out.close(); } catch (Exception ignored) {}
    }
}
