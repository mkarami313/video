package com.appoup.videotomp3.engine;

/**
 * Streaming audio encoder. PCM is pushed in as interleaved 16-bit little-endian
 * samples; the encoder writes the compressed/output bytes to its own sink.
 */
public interface Encoder {
    /** @param samples interleaved 16-bit PCM; @param length number of valid shorts */
    void encode(short[] samples, int length) throws Exception;

    /** Flush remaining frames and close the output. */
    void finish() throws Exception;

    /** Release resources without finalizing (on cancel/error). */
    void abort();
}
