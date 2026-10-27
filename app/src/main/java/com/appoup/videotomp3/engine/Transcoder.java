package com.appoup.videotomp3.engine;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import com.appoup.videotomp3.model.ConversionOptions;
import com.appoup.videotomp3.model.OutputFormat;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Decodes the first audio track of a video with MediaCodec, applies channel
 * conversion / resampling / gain / fade / trimming, and encodes to the chosen
 * format. All on-device; no network.
 */
public class Transcoder {

    public interface ProgressListener {
        void onProgress(int percent);
        boolean isCancelled();
    }

    private static final int TIMEOUT_US = 10000;

    public static File convert(Context ctx, ConversionOptions opt, File target,
                               ProgressListener listener) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        Encoder encoder = null;
        boolean ok = false;
        try {
            extractor.setDataSource(ctx, opt.sourceUri, null);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i; format = f; break;
                }
            }
            if (track < 0 || format == null) throw new Exception("no audio track");
            extractor.selectTrack(track);

            int srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if (srcChannels < 1) srcChannels = 1;
            if (srcChannels > 2) srcChannels = 2;

            int dstChannels = (opt.channels == 1 || opt.channels == 2) ? opt.channels : srcChannels;
            int dstRate = opt.sampleRate > 0 ? opt.sampleRate : srcRate;

            long startUs = opt.trimStartMs > 0 ? opt.trimStartMs * 1000L : 0;
            long endUs = (opt.trimEndMs > 0 && opt.trimEndMs > opt.trimStartMs)
                    ? opt.trimEndMs * 1000L : 0;
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            long windowUs = (endUs > 0 ? endUs : opt.sourceDurationMs * 1000L) - startUs;
            if (windowUs <= 0) windowUs = opt.sourceDurationMs * 1000L;
            long totalOutFrames = windowUs > 0 ? windowUs * dstRate / 1_000_000L : 0;

            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();

            encoder = createEncoder(opt.format, target, dstRate, dstChannels, opt.bitrateKbps);

            Resampler resampler = new Resampler(dstChannels, srcRate, dstRate);
            PcmProcessor processor = new PcmProcessor(opt.gain, dstChannels, dstRate,
                    opt.fadeInMs, opt.fadeOutMs, totalOutFrames);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            int lastPct = -1;

            while (!outputDone) {
                if (listener != null && listener.isCancelled()) throw new CancelledException();

                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inIndex);
                        int size = inBuf == null ? -1 : extractor.readSampleData(inBuf, 0);
                        long pts = extractor.getSampleTime();
                        if (size < 0 || (endUs > 0 && pts > endUs)) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, pts, 0);
                            extractor.advance();
                            if (windowUs > 0) {
                                int pct = (int) ((pts - startUs) * 100 / windowUs);
                                if (pct != lastPct && pct >= 0 && pct <= 100) {
                                    lastPct = pct;
                                    if (listener != null) listener.onProgress(pct);
                                }
                            }
                        }
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIndex >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    if (info.size > 0) {
                        ByteBuffer outBuf = decoder.getOutputBuffer(outIndex);
                        if (outBuf != null) {
                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            short[] pcm = toShorts(outBuf);
                            short[] conv = convertChannels(pcm, srcChannels, dstChannels);
                            short[] res = resampler.isActive()
                                    ? resampler.process(conv, conv.length) : conv;
                            processor.process(res, res.length);
                            encoder.encode(res, res.length);
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false);
                }
            }

            encoder.finish();
            encoder = null;

            if (opt.format == OutputFormat.MP3) {
                Id3Writer.apply(target, opt.tagTitle, opt.tagArtist, opt.tagAlbum,
                        opt.tagGenre, opt.coverImage, opt.coverMime);
            }
            if (listener != null) listener.onProgress(100);
            ok = true;
            return target;
        } finally {
            if (!ok && encoder != null) encoder.abort();
            try { if (decoder != null) { decoder.stop(); decoder.release(); } } catch (Exception ignored) {}
            try { extractor.release(); } catch (Exception ignored) {}
            if (!ok && target.exists()) target.delete();
        }
    }

    private static Encoder createEncoder(OutputFormat fmt, File target, int rate, int ch, int kbps)
            throws Exception {
        switch (fmt) {
            case WAV: return new WavEncoder(target, rate, ch);
            case M4A: return new AacEncoder(target, rate, ch, kbps);
            case MP3:
            default:  return new Mp3Encoder(target, rate, ch, kbps);
        }
    }

    private static short[] toShorts(ByteBuffer buf) {
        ShortBuffer sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] out = new short[sb.remaining()];
        sb.get(out);
        return out;
    }

    private static short[] convertChannels(short[] in, int src, int dst) {
        if (src == dst) return in;
        if (src == 2 && dst == 1) {
            int frames = in.length / 2;
            short[] out = new short[frames];
            for (int i = 0; i < frames; i++) {
                out[i] = (short) ((in[2 * i] + in[2 * i + 1]) / 2);
            }
            return out;
        }
        if (src == 1 && dst == 2) {
            short[] out = new short[in.length * 2];
            for (int i = 0; i < in.length; i++) {
                out[2 * i] = in[i];
                out[2 * i + 1] = in[i];
            }
            return out;
        }
        return in;
    }

    public static class CancelledException extends Exception {
        public CancelledException() { super("cancelled"); }
    }
}
