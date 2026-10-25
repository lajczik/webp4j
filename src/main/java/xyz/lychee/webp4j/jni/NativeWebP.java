package xyz.lychee.webp4j.jni;

import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

public class NativeWebP {

    /**
     * Holds the cause of native library unavailability for JNI.
     * If null, the library loaded successfully and all native methods are available.
     * If not null, contains the exception that prevented loading.
     */
    private static final Throwable UNAVAILABILITY_CAUSE;

    /**
     * The libwebp version captured during the load-time smoke test (e.g.
     * 0x010600 for 1.6.0), or 0 if the native library is unavailable.
     */
    private static final int LIBWEBP_VERSION;

    private NativeWebP() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Indicates whether the Foreign Function &amp; Memory (FFM) API backend is active.
     * Always returns false on Java &lt; 22 (where JNI is used).
     *
     * @return true if FFM is active, false if JNI is active
     */
    public static boolean isFFM() {
        return false;
    }

    /**
     * Returns the name of the active native interop backend ("JNI" on Java &lt; 22).
     *
     * @return "JNI"
     */
    public static String getBackendName() {
        return "JNI";
    }

    /**
     * Simple native method for smoke testing.
     * Returns the libwebp version number to verify JNI bindings work correctly.
     *
     * @return WebP library version number (e.g., 0x010300 for version 1.3.0)
     */
    private static native int getLibWebPVersion();

    /**
     * int WebPGetInfo(const uint8_t* data, size_t data_size, int* width, int* height);
     */
    public static native boolean getInfo(byte[] data, int[] dimensions);

    /**
     * VP8StatusCode WebPGetFeatures(const uint8_t* data, size_t data_size, WebPBitstreamFeatures* features);
     */
    public static native int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features);

    /**
     * Encodes packed ARGB pixels (0xAARRGGBB ints, i.e. BGRA byte order on
     * little-endian) to a WebP bitstream.
     *
     * @param pixels   Packed ARGB pixels, length must equal width * height
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param hasAlpha False to ignore the alpha byte of each pixel
     * @param multiThreaded True to let libwebp multi-thread the encode
     * @return WebP encoded bytes, or null on failure
     */
    public static native byte[] encode(int[] pixels, int width, int height,
                                       float quality, boolean lossless, boolean hasAlpha,
                                       boolean multiThreaded);

    /**
     * Encodes interleaved BGR bytes (a TYPE_3BYTE_BGR backing array, length
     * width * height * 3) to a WebP bitstream via WebPPictureImportBGR.
     *
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param multiThreaded True to let libwebp multi-thread the encode
     * @return WebP encoded bytes, or null on failure
     */
    public static native byte[] encodeBgr(byte[] pixels, int width, int height,
                                          float quality, boolean lossless, boolean multiThreaded);

    /**
     * Decodes a WebP bitstream directly into a packed ARGB int[].
     *
     * @param data         WebP bitstream
     * @param output       Destination, length must equal width * height
     * @param outputStride Row stride in BYTES (width * 4)
     * @return true on success
     */
    public static native boolean decodeInto(byte[] data, int[] output, int outputStride);

    /**
     * Gets information about a GIF file using native giflib.
     *
     * @param gifData GIF image bytes
     * @param info AnimationInfo object to populate
     * @return True on success, false on failure
     */
    public static native boolean getGifInfo(byte[] gifData, AnimationInfo info);

    /**
     * Converts GIF data to WebP format using native giflib decoder.
     */
    public static native byte[] encodeGifToWebP(
            byte[] gifData,
            float quality,
            boolean lossless,
            int compressionMethod,
            boolean extractFirstFrameOnly,
            int loopCount,
            int kmin,
            int kmax,
            boolean minimizeSize,
            boolean allowMixed,
            boolean multiThreaded
    );

    /**
     * Encodes an animated WebP from packed ARGB frames using the WebPAnimEncoder API.
     */
    public static native byte[] encodeAnimated(
            int[][] frames,
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
            boolean multiThreaded
    );

    /**
     * Decodes an animated WebP image into individual frames.
     */
    public static native boolean decodeAnimated(byte[] webPData, AnimatedWebPData result);

    /**
     * Checks if the native library has been successfully loaded.
     *
     * @return true if the native library is loaded and available, false otherwise
     */
    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Returns the raw libwebp version number bundled with this library.
     *
     * @return the version number, or 0 if the native library is unavailable
     */
    public static int libWebPVersionNumber() {
        return LIBWEBP_VERSION;
    }

    /**
     * Returns the libwebp version bundled with this library as a "major.minor.patch" string.
     *
     * @return the formatted version, or null if the native library is unavailable
     */
    public static String libWebPVersion() {
        if (LIBWEBP_VERSION <= 0) {
            return null;
        }
        return ((LIBWEBP_VERSION >> 16) & 0xff) + "."
                + ((LIBWEBP_VERSION >> 8) & 0xff) + "."
                + (LIBWEBP_VERSION & 0xff);
    }

    /**
     * Returns the cause of unavailability if the native library failed to load.
     *
     * @return The Throwable that caused loading to fail, or null if library loaded successfully
     */
    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    static {
        Throwable cause = null;
        int version = 0;
        try {
            NativeLibraryLoader.loadLibrary();
            version = getLibWebPVersion();
            if (version <= 0) {
                throw new IllegalStateException("Invalid libwebp version: " + version);
            }
        } catch (LinkageError | Exception e) {
            cause = e;
            version = 0;
        }
        UNAVAILABILITY_CAUSE = cause;
        LIBWEBP_VERSION = version;
    }
}
