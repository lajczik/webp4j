package xyz.lychee.webp4j.jni;

import xyz.lychee.webp4j.common.NativeBridge;
import xyz.lychee.webp4j.common.NativeLibraryLoader;
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
     *
     * @return true if FFM is active, false if JNI is active
     */
    public static boolean isFFM() {
        return NativeBridge.isFFM();
    }

    /**
     * Returns the name of the active native interop backend ("JNI" or "FFM").
     *
     * @return "JNI" or "FFM"
     */
    public static String getBackendName() {
        return NativeBridge.getBackendName();
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
     * <p>
     * The pixel array is pinned with GetPrimitiveArrayCritical and imported
     * directly by libwebp (WebPPictureImportBGRA/BGRX) — no intermediate copy.
     * The array is never written to, so a BufferedImage's live backing array
     * can be passed safely.
     *
     * @param pixels   Packed ARGB pixels, length must equal width * height
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param hasAlpha False to ignore the alpha byte of each pixel
     * @param multiThreaded True to let libwebp multi-thread the encode
     *                      (thread_level=1); output is bit-identical, only faster
     * @return WebP encoded bytes, or null on failure
     */
    public static native byte[] encode(int[] pixels, int width, int height,
                                       float quality, boolean lossless, boolean hasAlpha,
                                       boolean multiThreaded);

    /**
     * Encodes interleaved BGR bytes (a TYPE_3BYTE_BGR backing array, length
     * width * height * 3) to a WebP bitstream via WebPPictureImportBGR.
     * <p>
     * Same zero-copy contract as {@link #encode}: the array is pinned, read
     * directly by libwebp, and never written to.
     *
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param multiThreaded True to let libwebp multi-thread the encode
     *                      (thread_level=1); output is bit-identical, only faster
     * @return WebP encoded bytes, or null on failure
     */
    public static native byte[] encodeBgr(byte[] pixels, int width, int height,
                                          float quality, boolean lossless, boolean multiThreaded);

    /**
     * Decodes a WebP bitstream directly into a packed ARGB int[] — libwebp
     * writes BGRA bytes straight into the pinned Java array, so the array can
     * be a BufferedImage's live backing store.
     * <p>
     * Both arrays stay pinned for the full duration of the decode, which can
     * delay GC for very large images — the accepted cost of zero-copy.
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
     * @param info AnimationInfo object to populate (via JNI field access)
     * @return True on success, false on failure
     */
    public static native boolean getGifInfo(byte[] gifData, AnimationInfo info);

    /**
     * Converts GIF data to WebP format using native giflib decoder.
     * This is the primary (fast) path using native GIF decoding.
     *
     * @param gifData GIF image bytes
     * @param quality Quality factor (0-100)
     * @param lossless True for lossless encoding
     * @param compressionMethod Compression method (0-6)
     * @param extractFirstFrameOnly True to extract only first frame
     * @param loopCount Loop count (0=infinite, -1=use GIF's)
     * @param kmin Minimum key-frame distance
     * @param kmax Maximum key-frame distance
     * @param minimizeSize True to minimize output size
     * @param allowMixed True to allow mixed compression
     * @param multiThreaded True to multi-thread each frame's encode (thread_level=1)
     * @return WebP encoded byte array, or null on failure
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
     * Encodes an animated WebP from packed ARGB frames using the
     * WebPAnimEncoder API.
     * <p>
     * Each frame array is pinned and imported directly by libwebp
     * (WebPPictureImportBGRA) — frames are never written to, so live
     * BufferedImage backing arrays can be passed safely.
     *
     * @param frames Packed ARGB frames, each of length width * height
     * @param delays Array of frame delays in milliseconds
     * @param width Canvas width
     * @param height Canvas height
     * @param quality Quality factor (0-100)
     * @param lossless True for lossless encoding
     * @param compressionMethod Compression method (0-6)
     * @param loopCount Loop count (0=infinite)
     * @param kmin Minimum key-frame distance
     * @param kmax Maximum key-frame distance
     * @param minimizeSize True to minimize output size
     * @param allowMixed True to allow mixed compression
     * @param multiThreaded True to multi-thread each frame's encode (thread_level=1);
     *                      frames are still added in order, output is bit-identical
     * @return WebP encoded byte array, or null on failure
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
     * <p>
     * Uses the libwebp WebPAnimDecoder API to extract all frames as packed
     * ARGB pixels along with their cumulative timestamps.
     * <p>
     * The result object will have its fields populated:
     * - canvasWidth, canvasHeight, loopCount, bgcolor, frameCount (metadata)
     * - framePixels (int[][] of packed ARGB frame pixels)
     * - timestamps (int[] of cumulative timestamps in milliseconds)
     *
     * @param webPData The animated WebP image data
     * @param result   AnimatedWebPData object to populate with decoded frames
     * @return true on success, false on failure
     */
    public static native boolean decodeAnimated(byte[] webPData, AnimatedWebPData result);

    /**
     * Checks if the active backend is available.
     *
     * @return true if available, false otherwise
     */
    public static boolean isAvailable() {
        return NativeBridge.isAvailable();
    }

    /**
     * Returns the raw libwebp version number bundled with the active backend.
     *
     * @return the version number, or 0 if unavailable
     */
    public static int libWebPVersionNumber() {
        return NativeBridge.libWebPVersionNumber();
    }

    /**
     * Returns the libwebp version bundled with the active backend as a
     * {@code "major.minor.patch"} string (e.g. {@code "1.6.0"}).
     *
     * @return the formatted version, or null if unavailable
     */
    public static String libWebPVersion() {
        return NativeBridge.libWebPVersion();
    }

    /**
     * Returns the cause of unavailability if the active backend failed to load.
     *
     * @return The Throwable that caused loading to fail, or null if successful
     */
    public static Throwable unavailabilityCause() {
        return NativeBridge.unavailabilityCause();
    }

    // Direct JNI status queries used by JNIWebPBackend
    public static boolean isJniAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    public static int jniLibWebPVersionNumber() {
        return LIBWEBP_VERSION;
    }

    public static String jniLibWebPVersion() {
        if (LIBWEBP_VERSION <= 0) {
            return null;
        }
        return ((LIBWEBP_VERSION >> 16) & 0xff) + "."
                + ((LIBWEBP_VERSION >> 8) & 0xff) + "."
                + (LIBWEBP_VERSION & 0xff);
    }

    public static Throwable jniUnavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    // Static initializer: attempt to load the native library when class is loaded
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
