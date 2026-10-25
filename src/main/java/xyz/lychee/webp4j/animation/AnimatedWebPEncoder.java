package xyz.lychee.webp4j.animation;

import xyz.lychee.webp4j.gif.GifToWebPConfig;
import xyz.lychee.webp4j.jni.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.utils.WebPEncoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Encoder for creating animated WebP images from BufferedImage frames.
 * <p>
 * This class builds the WebP animation container directly in Java using RIFF chunks
 * (VP8X, ANIM, ANMF), delegating only individual frame compression to libwebp.
 *
 * @author MrNanko
 */
public final class AnimatedWebPEncoder {

    private AnimatedWebPEncoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Creates an animated WebP from a list of BufferedImage frames.
     * Container creation, framing, and chunk composition are handled entirely in Java.
     *
     * @param frames List of BufferedImage frames (must not be empty)
     * @param delays Array of frame delays in milliseconds (must match frame count)
     * @param config Configuration for encoding (quality, compression, etc.)
     * @return Byte array containing the animated WebP data
     * @throws IOException              If encoding fails
     * @throws IllegalArgumentException If frames is empty or delays length doesn't match
     */
    public static byte[] encode(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }
        if (delays == null || delays.length != frames.size()) {
            throw new IllegalArgumentException("Delays array length must match frame count");
        }
        if (config == null) {
            config = new GifToWebPConfig();
        }

        if (NativeWebP.isAvailable()) {
            try {
                return encodeNative(frames, delays, config);
            } catch (Throwable ignored) {
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        encode(baos, frames, delays, config);
        return baos.toByteArray();
    }

    /**
     * Streams an animated WebP directly to an output stream by assembling container chunks in Java.
     *
     * @param out    Destination output stream
     * @param frames List of frames to encode
     * @param delays Delays in milliseconds for each frame
     * @param config Conversion configuration
     * @throws IOException If encoding fails
     */
    public static void encode(OutputStream out, List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }
        if (delays == null || delays.length != frames.size()) {
            throw new IllegalArgumentException("Delays array length must match frame count");
        }
        if (config == null) {
            config = new GifToWebPConfig();
        }

        BufferedImage first = frames.get(0);
        int width = first.getWidth();
        int height = first.getHeight();

        boolean hasAlpha = false;
        for (BufferedImage frame : frames) {
            if (frame.getWidth() != width || frame.getHeight() != height) {
                throw new IllegalArgumentException(
                        String.format("Frame dimensions (%dx%d) don't match canvas (%dx%d)",
                                frame.getWidth(), frame.getHeight(), width, height));
            }
            if (frame.getColorModel().hasAlpha()) {
                hasAlpha = true;
            }
        }

        int loopCount = config.getLoopCount() == -1 ? 0 : config.getLoopCount();
        try (WebPEncoder encoder = new WebPEncoder(out, hasAlpha, frames.size(), width, height, loopCount, 0)) {
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                int duration = delays[i];
                encoder.setFrame(i, frame, duration, config.getQuality(), config.isLossless());
            }
            encoder.flush();
        }
    }

    /**
     * Legacy encoder that delegates the entire multi-frame pipeline to C's WebPAnimEncoder.
     */
    public static byte[] encodeNative(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }
        if (delays == null || delays.length != frames.size()) {
            throw new IllegalArgumentException("Delays array length must match frame count");
        }
        if (config == null) {
            config = new GifToWebPConfig();
        }

        BufferedImage firstFrame = frames.get(0);
        int width = firstFrame.getWidth();
        int height = firstFrame.getHeight();

        int[][] framePixels = new int[frames.size()][];
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);
            if (frame.getWidth() != width || frame.getHeight() != height) {
                throw new IllegalArgumentException(
                        String.format("Frame %d dimensions (%dx%d) don't match canvas (%dx%d)",
                                i, frame.getWidth(), frame.getHeight(), width, height));
            }
            framePixels[i] = PixelConverter.toArgbPixels(frame, true);
        }

        byte[] result = NativeWebP.encodeAnimated(
                framePixels,
                delays,
                width,
                height,
                config.getQuality(),
                config.isLossless(),
                config.getCompressionMethod(),
                config.getLoopCount() == -1 ? 0 : config.getLoopCount(),
                config.getKmin(),
                config.getKmax(),
                config.isMinimizeSize(),
                config.isAllowMixed(),
                config.isMultiThreaded()
        );

        if (result == null || result.length == 0) {
            throw new IOException("Animated WebP native encoding failed");
        }

        return result;
    }

    /**
     * Creates an animated WebP with uniform delay for all frames.
     *
     * @param frames  List of BufferedImage frames (must not be empty)
     * @param delay   Delay in milliseconds for each frame
     * @param config  Configuration for encoding (quality, compression, etc.)
     * @return Byte array containing the animated WebP data
     * @throws IOException              If encoding fails
     * @throws IllegalArgumentException If frames is empty
     */
    public static byte[] encode(List<BufferedImage> frames, int delay, GifToWebPConfig config) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }

        int[] delays = new int[frames.size()];
        Arrays.fill(delays, delay);

        return encode(frames, delays, config);
    }
}
