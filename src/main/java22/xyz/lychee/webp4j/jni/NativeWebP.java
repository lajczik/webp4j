package xyz.lychee.webp4j.jni;

import xyz.lychee.webp4j.ffm.FFMWebP;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

/**
 * Java 22+ Multi-Release variant of {@link NativeWebP}.
 * <p>
 * Routes all low-level WebP operations through the Foreign Function &amp; Memory (FFM)
 * API (Project Panama, {@code java.lang.foreign}), avoiding JNI transition penalties
 * and GC pinning stalls.
 */
public class NativeWebP {

    private NativeWebP() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    public static boolean isFFM() {
        return true;
    }

    public static String getBackendName() {
        return "FFM";
    }

    public static boolean isAvailable() {
        return FFMWebP.isAvailable();
    }

    public static int libWebPVersionNumber() {
        return FFMWebP.getVersion();
    }

    public static String libWebPVersion() {
        int v = libWebPVersionNumber();
        if (v <= 0) return null;
        return ((v >> 16) & 0xff) + "." + ((v >> 8) & 0xff) + "." + (v & 0xff);
    }

    public static Throwable unavailabilityCause() {
        return FFMWebP.getUnavailabilityCause();
    }

    public static boolean getInfo(byte[] data, int[] dimensions) {
        return FFMWebP.getInfo(data, dimensions);
    }

    public static int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features) {
        return FFMWebP.getFeatures(data, dataSize, features);
    }

    public static byte[] encode(int[] pixels, int width, int height,
                                float quality, boolean lossless, boolean hasAlpha,
                                boolean multiThreaded) {
        return FFMWebP.encode(pixels, width, height, quality, lossless, hasAlpha, multiThreaded);
    }

    public static byte[] encodeBgr(byte[] pixels, int width, int height,
                                   float quality, boolean lossless, boolean multiThreaded) {
        return FFMWebP.encodeBgr(pixels, width, height, quality, lossless, multiThreaded);
    }

    public static boolean decodeInto(byte[] data, int[] output, int outputStride) {
        return FFMWebP.decodeInto(data, output, outputStride);
    }

    public static boolean getGifInfo(byte[] gifData, AnimationInfo info) {
        return FFMWebP.getGifInfo(gifData, info);
    }

    public static byte[] encodeGifToWebP(byte[] gifData, float quality, boolean lossless,
                                         int compressionMethod, boolean extractFirstFrameOnly,
                                         int loopCount, int kmin, int kmax,
                                         boolean minimizeSize, boolean allowMixed, boolean multiThreaded) {
        return FFMWebP.encodeGifToWebP(gifData, quality, lossless, compressionMethod,
                extractFirstFrameOnly, loopCount, kmin, kmax, minimizeSize, allowMixed, multiThreaded);
    }

    public static byte[] encodeAnimated(int[][] frames, int[] delays, int width, int height,
                                        float quality, boolean lossless, int compressionMethod,
                                        int loopCount, int kmin, int kmax,
                                        boolean minimizeSize, boolean allowMixed, boolean multiThreaded) {
        return FFMWebP.encodeAnimated(frames, delays, width, height, quality, lossless,
                compressionMethod, loopCount, kmin, kmax, minimizeSize, allowMixed, multiThreaded);
    }

    public static boolean decodeAnimated(byte[] webPData, AnimatedWebPData result) {
        return FFMWebP.decodeAnimated(webPData, result);
    }
}
