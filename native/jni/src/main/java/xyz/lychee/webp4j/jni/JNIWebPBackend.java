package xyz.lychee.webp4j.jni;

import xyz.lychee.webp4j.common.WebPBackend;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

/**
 * Standard JNI backend implementation delegating to {@link NativeWebP}.
 */
public final class JNIWebPBackend implements WebPBackend {

    public JNIWebPBackend() {
    }

    @Override
    public boolean isAvailable() {
        return NativeWebP.isJniAvailable();
    }

    @Override
    public String getBackendName() {
        return "JNI";
    }

    @Override
    public boolean isFFM() {
        return false;
    }

    @Override
    public int libWebPVersionNumber() {
        return NativeWebP.jniLibWebPVersionNumber();
    }

    @Override
    public String libWebPVersion() {
        return NativeWebP.jniLibWebPVersion();
    }

    @Override
    public Throwable unavailabilityCause() {
        return NativeWebP.jniUnavailabilityCause();
    }

    @Override
    public boolean getInfo(byte[] data, int[] dimensions) {
        return NativeWebP.getInfo(data, dimensions);
    }

    @Override
    public int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features) {
        return NativeWebP.getFeatures(data, dataSize, features);
    }

    @Override
    public byte[] encode(int[] pixels, int width, int height,
                         float quality, boolean lossless, boolean hasAlpha,
                         boolean multiThreaded) {
        return NativeWebP.encode(pixels, width, height, quality, lossless, hasAlpha, multiThreaded);
    }

    @Override
    public byte[] encodeBgr(byte[] pixels, int width, int height,
                            float quality, boolean lossless, boolean multiThreaded) {
        return NativeWebP.encodeBgr(pixels, width, height, quality, lossless, multiThreaded);
    }

    @Override
    public boolean decodeInto(byte[] data, int[] output, int outputStride) {
        return NativeWebP.decodeInto(data, output, outputStride);
    }

    @Override
    public boolean getGifInfo(byte[] gifData, AnimationInfo info) {
        return NativeWebP.getGifInfo(gifData, info);
    }

    @Override
    public byte[] encodeGifToWebP(byte[] gifData,
                                  float quality,
                                  boolean lossless,
                                  int compressionMethod,
                                  boolean extractFirstFrameOnly,
                                  int loopCount,
                                  int kmin,
                                  int kmax,
                                  boolean minimizeSize,
                                  boolean allowMixed,
                                  boolean multiThreaded) {
        return NativeWebP.encodeGifToWebP(
                gifData, quality, lossless, compressionMethod,
                extractFirstFrameOnly, loopCount, kmin, kmax,
                minimizeSize, allowMixed, multiThreaded
        );
    }

    @Override
    public byte[] encodeAnimated(int[][] frames,
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
                                 boolean multiThreaded) {
        return NativeWebP.encodeAnimated(
                frames, delays, width, height, quality,
                lossless, compressionMethod, loopCount, kmin, kmax,
                minimizeSize, allowMixed, multiThreaded
        );
    }

    @Override
    public boolean decodeAnimated(byte[] webPData, AnimatedWebPData result) {
        return NativeWebP.decodeAnimated(webPData, result);
    }
}
