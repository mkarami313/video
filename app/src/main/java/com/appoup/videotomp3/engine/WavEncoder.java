package com.appoup.videotomp3.engine;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/** Writes interleaved 16-bit PCM to an uncompressed WAV file (header patched on finish). */
public class WavEncoder implements Encoder {
    private final File target;
    private final OutputStream out;
    private final int sampleRate;
    private final int channels;
    private long dataBytes = 0;
    private byte[] scratch;

    public WavEncoder(File target, int sampleRate, int channels) throws Exception {
        this.target = target;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.out = new BufferedOutputStream(new FileOutputStream(target));
        out.write(new byte[44]); // placeholder header
    }

    @Override
    public void encode(short[] samples, int length) throws Exception {
        if (length <= 0) return;
        int bytes = length * 2;
        if (scratch == null || scratch.length < bytes) scratch = new byte[bytes];
        for (int i = 0; i < length; i++) {
            scratch[2 * i] = (byte) (samples[i] & 0xff);
            scratch[2 * i + 1] = (byte) ((samples[i] >> 8) & 0xff);
        }
        out.write(scratch, 0, bytes);
        dataBytes += bytes;
    }

    @Override
    public void finish() throws Exception {
        out.flush();
        out.close();
        writeHeader();
    }

    private void writeHeader() throws Exception {
        long totalDataLen = dataBytes + 36;
        int byteRate = sampleRate * channels * 2;
        byte[] h = new byte[44];
        putStr(h, 0, "RIFF");
        putInt(h, 4, (int) totalDataLen);
        putStr(h, 8, "WAVE");
        putStr(h, 12, "fmt ");
        putInt(h, 16, 16);              // PCM chunk size
        putShort(h, 20, (short) 1);     // PCM
        putShort(h, 22, (short) channels);
        putInt(h, 24, sampleRate);
        putInt(h, 28, byteRate);
        putShort(h, 32, (short) (channels * 2)); // block align
        putShort(h, 34, (short) 16);    // bits per sample
        putStr(h, 36, "data");
        putInt(h, 40, (int) dataBytes);
        RandomAccessFile raf = new RandomAccessFile(target, "rw");
        raf.seek(0);
        raf.write(h);
        raf.close();
    }

    @Override
    public void abort() {
        try { out.close(); } catch (Exception ignored) {}
    }

    private static void putStr(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }
    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }
    private static void putShort(byte[] b, int off, short v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }
}
