package xyz.lychee.webp4j.common;

import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

/**
 * Common abstraction for WebP native backends (JNI and FFM).
 */
public interface WebPBackend {

    /**
     * Checks if this backend is available and loaded properly.
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();

    /**
     * Returns the name of the backend ("JNI" or "FFM").
     *
     * @return backend name
     */
    String getBackendName();

    /**
     * Returns true if this backend is using the Foreign Function &amp; Memory API.
     *
     * @return true if FFM, false if JNI
     */
    boolean isFFM();

    /**
     * Returns the raw libwebp version number bundled with this backend.
     *
     * @return the version number (e.g. 0x010600)
     */
    int libWebPVersionNumber();

    /**
     * Returns the formatted libwebp version string (e.g. "1.6.0").
     *
     * @return formatted version or null
     */
    String libWebPVersion();

    /**
     * Returns the cause of unavailability if loading failed.
     *
     * @return failure cause or null
     */
    Throwable unavailabilityCause();

    /**
     * Retrieves basic image dimensions without full decoding.
     */
    boolean getInfo(byte[] data, int[] dimensions);

    /**
     * Retrieves bitstream features.
     */
    int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features);

    /**
     * Encodes packed ARGB int[] pixels into WebP.
     */
    byte[] encode(int[] pixels, int width, int height,
                  float quality, boolean lossless, boolean hasAlpha,
                  boolean multiThreaded);

    /**
     * Encodes interleaved BGR byte[] pixels into WebP.
     */
    byte[] encodeBgr(byte[] pixels, int width, int height,
                     float quality, boolean lossless, boolean multiThreaded);

    /**
     * Decodes a WebP bitstream directly into output int[] buffer.
     */
    boolean decodeInto(byte[] data, int[] output, int outputStride);

    /**
     * Retrieves information about a GIF file.
     */
    boolean getGifInfo(byte[] gifData, AnimationInfo info);

    /**
     * Converts GIF bytes directly to WebP bytes.
     */
    byte[] encodeGifToWebP(byte[] gifData,
                           float quality,
                           boolean lossless,
                           int compressionMethod,
                           boolean extractFirstFrameOnly,
                           int loopCount,
                           int kmin,
                           int kmax,
                           boolean minimizeSize,
                           boolean allowMixed,
                           boolean multiThreaded);

    /**
     * Encodes animated WebP frames.
     */
    byte[] encodeAnimated(int[][] frames,
                          int[] delays,
                          int width,
                          int height,
                          float quality,
                          boolean lossless,
                          int compressionMethod,
                          int loopCount,
                          int kmin,
                          int kmax,
                          boolean minimizeSize,
                          boolean allowMixed,
                          boolean multiThreaded);

    /**
     * Decodes animated WebP into individual frames and timestamps.
     */
    boolean decodeAnimated(byte[] webPData, AnimatedWebPData result);
}
