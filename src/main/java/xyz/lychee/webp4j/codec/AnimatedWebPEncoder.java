package xyz.lychee.webp4j.codec;

import lombok.Getter;
import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.utils.Util;
import xyz.lychee.webp4j.codec.WebPEncoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encoder for creating animated WebP images from BufferedImage frames.
 * <p>
 * Supports both high-level list-based batch encoding and streaming frame-by-frame
 * composition using RIFF chunks (VP8X, ANIM, ANMF), delegating frame compression to libwebp.
 *
 * @author MrNanko
 */
@Getter
public class AnimatedWebPEncoder implements AutoCloseable, Flushable {

    public static final int FLAG_NO_BLEND = 1 << 1;
    public static final int FLAG_ALPHA_BLENDING = 0 << 1;
    public static final int FLAG_NO_DISPOSE = 0 << 0;
    public static final int FLAG_DISPOSE_TO_BACKGROUND = 1 << 0;

    private final OutputStream out;
    private final boolean alpha;
    private final List<Chunk> chunks;

    public AnimatedWebPEncoder(OutputStream out, boolean alpha, int frames, int width, int height) {
        this(out, alpha, frames, width, height, 0, 0x00000000);
    }

    public AnimatedWebPEncoder(OutputStream out, boolean alpha, int frames, int width, int height, int loopCount, int backgroundColor) {
        this.out = out;
        this.alpha = alpha;

        this.chunks = new ArrayList<>(2 + Math.max(0, frames));
        this.chunks.add(new VP8XChunk(VP8XChunk.FLAG_ANIM | (alpha ? VP8XChunk.FLAG_ALPH : 0), width, height));
        this.chunks.add(new ANIMChunk(backgroundColor, loopCount));
        for (int i = 0; i < frames; i++) {
            this.chunks.add(null);
        }
    }

    public Chunk[] getChunks() {
        return chunks.toArray(new Chunk[0]);
    }

    public void setFrame(int index, BufferedImage frame, int duration, float quality) throws IOException {
        setFrame(index, frame, duration, quality, false);
    }

    public void setFrame(int index, BufferedImage frame, int duration, float quality, boolean lossless) throws IOException {
        byte[] webPData = WebPEncoder.encode(frame, quality, lossless, true);
        Chunk[] subchunks = ChunkFactory.getChunks(webPData);
        setChunkAt(index + 2, new ANMFChunk(0, 0, frame.getWidth(), frame.getHeight(), duration, 0, subchunks));
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, int flags, float quality) throws IOException {
        setFrame(index, pixels, x, y, width, height, duration, flags, quality, false);
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, int flags, float quality, boolean lossless) throws IOException {
        byte[] webPData = NativeWebP.encode(pixels, width, height, quality, lossless, this.alpha, true);
        if (webPData == null || webPData.length == 0) {
            throw new IOException("Failed to encode frame " + index);
        }

        Chunk[] subchunks = ChunkFactory.getChunks(webPData);
        setChunkAt(index + 2, new ANMFChunk(x, y, width, height, duration, flags, subchunks));
    }

    public void addFrame(BufferedImage frame, int duration, float quality, boolean lossless) throws IOException {
        byte[] webPData = WebPEncoder.encode(frame, quality, lossless, true);
        Chunk[] subchunks = ChunkFactory.getChunks(webPData);
        chunks.add(new ANMFChunk(0, 0, frame.getWidth(), frame.getHeight(), duration, 0, subchunks));
    }

    public void setChunkAt(int index, Chunk chunk) {
        while (chunks.size() <= index) {
            chunks.add(null);
        }
        chunks.set(index, chunk);
    }

    @Override
    public void flush() throws IOException {
        if (this.out == null) return;
        List<Chunk> nonNull = new ArrayList<>(chunks.size());
        for (Chunk c : chunks) {
            if (c != null) {
                nonNull.add(c);
            }
        }
        WebPChunk webp = new WebPChunk(nonNull.toArray(new Chunk[0]));
        webp.write(this.out);
        this.out.flush();
    }

    @Override
    public void close() throws IOException {
        flush();
    }

    // ============================================
    // Static helper methods for animated encoding
    // ============================================

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
        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(out, hasAlpha, frames.size(), width, height, loopCount, 0)) {
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                int duration = delays[i];
                encoder.setFrame(i, frame, duration, config.getQuality(), config.isLossless());
            }
            encoder.flush();
        }
    }

    /**
     * Creates an animated WebP with uniform delay for all frames.
     *
     * @param frames List of BufferedImage frames (must not be empty)
     * @param delay  Delay in milliseconds for each frame
     * @param config Configuration for encoding (quality, compression, etc.)
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

    public static void encode(File file, List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        Util.writeBytes(file, encode(frames, delays, config));
    }

    public static void encode(Path path, List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        Util.writeBytes(path, encode(frames, delays, config));
    }

    public static void encode(File file, List<BufferedImage> frames, int delay, GifToWebPConfig config) throws IOException {
        Util.writeBytes(file, encode(frames, delay, config));
    }

    public static void encode(Path path, List<BufferedImage> frames, int delay, GifToWebPConfig config) throws IOException {
        Util.writeBytes(path, encode(frames, delay, config));
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
}
