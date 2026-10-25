package xyz.lychee.webp4j.ffm;

import xyz.lychee.webp4j.common.WebPBackend;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

/**
 * Foreign Function &amp; Memory (FFM) backend implementing {@link WebPBackend}.
 */
public final class FFMWebPBackend implements WebPBackend {

    public FFMWebPBackend() {
    }

    @Override
    public boolean isAvailable() {
        return FFMWebP.isAvailable();
    }

    @Override
    public String getBackendName() {
        return "FFM";
    }

    @Override
    public boolean isFFM() {
        return true;
    }

    @Override
    public int libWebPVersionNumber() {
        return FFMWebP.getVersion();
    }

    @Override
    public String libWebPVersion() {
        int v = FFMWebP.getVersion();
        if (v <= 0) {
            return null;
        }
        return ((v >> 16) & 0xff) + "."
                + ((v >> 8) & 0xff) + "."
                + (v & 0xff);
    }

    @Override
    public Throwable unavailabilityCause() {
        return FFMWebP.getUnavailabilityCause();
    }

    @Override
    public boolean getInfo(byte[] data, int[] dimensions) {
        return FFMWebP.getInfo(data, dimensions);
    }

    @Override
    public int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features) {
        return FFMWebP.getFeatures(data, dataSize, features);
    }

    @Override
    public byte[] encode(int[] pixels, int width, int height,
                         float quality, boolean lossless, boolean hasAlpha,
                         boolean multiThreaded) {
        return FFMWebP.encode(pixels, width, height, quality, lossless, hasAlpha, multiThreaded);
    }

    @Override
    public byte[] encodeBgr(byte[] pixels, int width, int height,
                            float quality, boolean lossless, boolean multiThreaded) {
        return FFMWebP.encodeBgr(pixels, width, height, quality, lossless, multiThreaded);
    }

    @Override
    public boolean decodeInto(byte[] data, int[] output, int outputStride) {
        return FFMWebP.decodeInto(data, output, outputStride);
    }

    @Override
    public boolean getGifInfo(byte[] gifData, AnimationInfo info) {
        return FFMWebP.getGifInfo(gifData, info);
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
        return FFMWebP.encodeGifToWebP(
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
        return FFMWebP.encodeAnimated(
                frames, delays, width, height, quality,
                lossless, compressionMethod, loopCount, kmin, kmax,
                minimizeSize, allowMixed, multiThreaded
        );
    }

    @Override
    public boolean decodeAnimated(byte[] webPData, AnimatedWebPData result) {
        return FFMWebP.decodeAnimated(webPData, result);
    }
}
