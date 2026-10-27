package com.appoup.videotomp3.engine;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;

/** Encodes interleaved 16-bit PCM to AAC inside an .m4a container (MediaCodec + MediaMuxer). */
public class AacEncoder implements Encoder {
    private final MediaCodec codec;
    private final MediaMuxer muxer;
    private final int channels;
    private final int bytesPerSecond;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private int trackIndex = -1;
    private boolean muxerStarted = false;
    private long totalBytesFed = 0;

    public AacEncoder(File target, int sampleRate, int channels, int bitrateKbps) throws Exception {
        this.channels = channels;
        this.bytesPerSecond = sampleRate * channels * 2;

        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();

        muxer = new MediaMuxer(target.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @Override
    public void encode(short[] samples, int length) throws Exception {
        if (length <= 0) return;
        byte[] bytes = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            bytes[2 * i] = (byte) (samples[i] & 0xff);
            bytes[2 * i + 1] = (byte) ((samples[i] >> 8) & 0xff);
        }
        feed(bytes);
    }

    /** Push an arbitrary run of PCM bytes through the encoder's input buffers. */
    private void feed(byte[] data) throws Exception {
        int pos = 0;
        int end = data.length;
        while (pos < end) {
            int inIndex = codec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                inBuf.clear();
                int chunk = Math.min(inBuf.capacity(), end - pos);
                inBuf.put(data, pos, chunk);
                long ptsUs = bytesToUs(totalBytesFed);
                codec.queueInputBuffer(inIndex, 0, chunk, ptsUs, 0);
                pos += chunk;
                totalBytesFed += chunk;
            } else {
                // No input buffer free yet; drain some output to make room.
                drain(false);
            }
        }
        drain(false);
    }

    private void signalEndOfStream() {
        while (true) {
            int inIndex = codec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                codec.queueInputBuffer(inIndex, 0, 0, bytesToUs(totalBytesFed),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return;
            }
            drain(false);
        }
    }

    private void drain(boolean endOfStream) {
        while (true) {
            int outIndex = codec.dequeueOutputBuffer(info, 10000);
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    trackIndex = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                }
            } else if (outIndex >= 0) {
                ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (info.size > 0 && muxerStarted && outBuf != null) {
                    outBuf.position(info.offset);
                    outBuf.limit(info.offset + info.size);
                    muxer.writeSampleData(trackIndex, outBuf, info);
                }
                codec.releaseOutputBuffer(outIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        }
    }

    private long bytesToUs(long bytes) {
        return bytesPerSecond > 0 ? (bytes * 1_000_000L) / bytesPerSecond : 0;
    }

    @Override
    public void finish() throws Exception {
        signalEndOfStream();
        drain(true);
        try { codec.stop(); } catch (Exception ignored) {}
        try { codec.release(); } catch (Exception ignored) {}
        try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
        try { muxer.release(); } catch (Exception ignored) {}
    }

    @Override
    public void abort() {
        try { codec.stop(); } catch (Exception ignored) {}
        try { codec.release(); } catch (Exception ignored) {}
        try { muxer.release(); } catch (Exception ignored) {}
    }
}
