package xyz.lychee.webp4j.codec;

import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.model.WebPContainerFlag;
import xyz.lychee.webp4j.model.WebPFrameFlag;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.utils.Util;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encoder for creating animated WebP images from BufferedImage frames.
 * <p>
 * Supports both high-level list-based batch encoding and frame-by-frame composition using RIFF
 * chunks (VP8X, ANIM, ANMF), delegating frame compression to libwebp.
 * <p>
 * <strong>Lifecycle:</strong> frames are collected in memory and the complete container is written to
 * the output stream exactly once, when the encoder is closed (or {@link #finish()} is called).
 * {@link #close()} is idempotent. If encoding a frame fails with an exception, the encoder is marked
 * as failed and {@link #close()} writes nothing, so a half-built animation never reaches the stream.
 *
 * @author MrNanko
 * @author lajczik
 */
public class AnimatedWebPEncoder implements AutoCloseable, Flushable {

    private static final Logger LOG = Logger.getLogger(AnimatedWebPEncoder.class.getName());

    private final OutputStream out;
    private final boolean alpha;
    private final VP8XChunk vp8XChunk;
    private final ANIMChunk animChunk;
    private final ArrayList<ANMFChunk> frames = new ArrayList<>();

    private boolean finished;
    private boolean failed;

    public AnimatedWebPEncoder(OutputStream out, boolean alpha, int width, int height) {
        this(out, alpha, width, height, 0, 0x00000000);
    }

    public AnimatedWebPEncoder(OutputStream out, boolean alpha, int width, int height, int loopCount, int backgroundColor) {
        this.out = out;
        this.alpha = alpha;

        int vp8xFlags = WebPContainerFlag.ANIMATION.getMask() | (alpha ? WebPContainerFlag.ALPHA.getMask() : 0);
        this.vp8XChunk = new VP8XChunk(vp8xFlags, width, height);
        this.animChunk = new ANIMChunk(backgroundColor, loopCount);
    }

    /**
     * @deprecated the frame count is no longer needed, frames are collected dynamically.
     * Use {@link #AnimatedWebPEncoder(OutputStream, boolean, int, int)}.
     */
    @Deprecated
    public AnimatedWebPEncoder(OutputStream out, boolean alpha, int frames, int width, int height) {
        this(out, alpha, width, height);
    }

    /**
     * @deprecated the frame count is no longer needed, frames are collected dynamically.
     * Use {@link #AnimatedWebPEncoder(OutputStream, boolean, int, int, int, int)}.
     */
    @Deprecated
    public AnimatedWebPEncoder(OutputStream out, boolean alpha, int frames, int width, int height, int loopCount, int backgroundColor) {
        this(out, alpha, width, height, loopCount, backgroundColor);
    }

    public boolean isAlpha() {
        return alpha;
    }

    /** Number of frame slots set so far (including unset slots below the highest index). */
    public int getFrameCount() {
        return frames.size();
    }

    /**
     * Returns the container chunks as they would be written: VP8X, ANIM and every frame that is set.
     */
    public Chunk[] getChunks() {
        List<Chunk> chunks = new ArrayList<>(2 + frames.size());
        chunks.add(vp8XChunk);
        chunks.add(animChunk);
        for (ANMFChunk frame : frames) {
            if (frame != null) {
                chunks.add(frame);
            }
        }
        return chunks.toArray(new Chunk[0]);
    }

    public void setFrame(int index, BufferedImage frame, int duration, float quality) throws IOException {
        setFrame(index, frame, duration, quality, false);
    }

    public void setFrame(int index, BufferedImage frame, int duration, float quality, boolean lossless) throws IOException {
        setFrame(index, frame, duration, quality, lossless, new WebPFrameFlag[0]);
    }

    public void setFrame(int index, BufferedImage frame, int duration, float quality, boolean lossless, WebPFrameFlag... flags) throws IOException {
        ANMFChunk chunk = buildFrame(frame, duration, quality, lossless, flags);
        this.setFrame(index, chunk);
    }

    public void setFrame(int index, BufferedImage frame, int duration, float quality, WebPFrameFlag... flags) throws IOException {
        this.setFrame(index, frame, duration, quality, false, flags);
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, int flags, float quality) throws IOException {
        this.setFrame(index, pixels, x, y, width, height, duration, flags, quality, false);
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, int flags, float quality, boolean lossless) throws IOException {
        ensureWritable();
        try {
            byte[] webPData = NativeWebP.encode(pixels, width, height, quality, lossless, this.alpha, true);
            if (webPData == null || webPData.length == 0) {
                throw new IOException("Failed to encode frame " + index);
            }
            Chunk[] subchunks = ChunkFactory.getChunks(webPData);
            this.setFrame(index, new ANMFChunk(x, y, width, height, duration, flags, subchunks));
        } catch (IOException | RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, float quality, boolean lossless, WebPFrameFlag... flags) throws IOException {
        setFrame(index, pixels, x, y, width, height, duration, WebPFrameFlag.toMask(flags), quality, lossless);
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, float quality, WebPFrameFlag... flags) throws IOException {
        setFrame(index, pixels, x, y, width, height, duration, WebPFrameFlag.toMask(flags), quality, false);
    }

    public void setFrame(int index, ANMFChunk frame) {
        ensureWritable();
        if (index < 0) {
            throw new IndexOutOfBoundsException("Index cannot be negative: " + index);
        }
        if (frame == null) {
            throw new IllegalArgumentException("Frame cannot be null");
        }
        while (this.frames.size() <= index) {
            this.frames.add(null);
        }
        this.frames.set(index, frame);
    }

    public void addFrame(BufferedImage frame, int duration, float quality, boolean lossless) throws IOException {
        this.addFrame(frame, duration, quality, lossless, new WebPFrameFlag[0]);
    }

    public void addFrame(BufferedImage frame, int duration, float quality, boolean lossless, WebPFrameFlag... flags) throws IOException {
        this.addFrame(buildFrame(frame, duration, quality, lossless, flags));
    }

    public void addFrame(BufferedImage frame, int duration, float quality, WebPFrameFlag... flags) throws IOException {
        this.addFrame(frame, duration, quality, false, flags);
    }

    public void addFrame(ANMFChunk frame) {
        ensureWritable();
        if (frame == null) {
            throw new IllegalArgumentException("Frame cannot be null");
        }
        this.frames.add(frame);
    }

    private ANMFChunk buildFrame(BufferedImage frame, int duration, float quality, boolean lossless,
                                 WebPFrameFlag[] flags) throws IOException {
        ensureWritable();
        try {
            byte[] webPData = WebPEncoder.encode(frame, quality, lossless, true);
            Chunk[] subchunks = ChunkFactory.getChunks(webPData);
            return new ANMFChunk(0, 0, frame.getWidth(), frame.getHeight(), duration, WebPFrameFlag.toMask(flags), subchunks);
        } catch (IOException | RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    private void ensureWritable() {
        if (finished) {
            throw new IllegalStateException("The animation has already been written, frames can no longer be changed");
        }
    }

    /**
     * Writes the complete animation to the output stream. Subsequent calls do nothing.
     *
     * @throws IOException           if the output cannot be written, a frame slot below the highest index
     *                               was never set, or a frame does not fit on the canvas
     * @throws IllegalStateException if no frame was added
     */
    public void finish() throws IOException {
        if (finished) {
            return;
        }
        if (failed) {
            throw new IllegalStateException("Frame encoding failed earlier, refusing to write an incomplete animation");
        }
        if (out == null) {
            finished = true;
            return;
        }
        if (frames.isEmpty()) {
            throw new IllegalStateException("An animation needs at least one frame");
        }

        List<Chunk> chunks = new ArrayList<>(2 + frames.size());
        chunks.add(vp8XChunk);
        chunks.add(animChunk);
        for (int i = 0; i < frames.size(); i++) {
            ANMFChunk frame = frames.get(i);
            if (frame == null) {
                throw new IOException("Frame " + i + " was never set");
            }
            if ((long) frame.getFrameX() + frame.getFrameWidth() > vp8XChunk.getCanvasWidth()
                    || (long) frame.getFrameY() + frame.getFrameHeight() > vp8XChunk.getCanvasHeight()) {
                throw new IOException("Frame " + i + " does not fit on the "
                        + vp8XChunk.getCanvasWidth() + "x" + vp8XChunk.getCanvasHeight() + " canvas");
            }
            chunks.add(frame);
        }

        finished = true;
        new WebPChunk(chunks.toArray(new Chunk[0])).write(out);
        out.flush();
    }

    /**
     * Writes the animation like {@link #finish()}.
     *
     * @deprecated the container is written once on {@link #close()}; calling this is no longer
     * necessary but remains harmless (it is idempotent).
     */
    @Deprecated
    @Override
    public void flush() throws IOException {
        finish();
    }

    /**
     * Writes the animation once. Does nothing when called again, or when a frame failed to encode.
     */
    @Override
    public void close() throws IOException {
        if (finished) {
            return;
        }
        if (failed) {
            finished = true;
            return;
        }
        finish();
    }

    // ============================================
    // Static helper methods for animated encoding
    // ============================================

    /**
     * Creates an animated WebP from a list of BufferedImage frames.
     * The native WebPAnimEncoder is used when available (inter-frame optimization, sub-rectangle
     * frames, key-frame placement); otherwise the container is assembled in Java with one full-canvas
     * keyframe per input image.
     *
     * @param frames List of BufferedImage frames (must not be empty)
     * @param delays Array of frame delays in milliseconds (must match frame count)
     * @param config Configuration for encoding (quality, compression, etc.)
     * @return Byte array containing the animated WebP data
     * @throws IOException              If encoding fails
     * @throws IllegalArgumentException If frames is empty or delays length doesn't match
     */
    public static byte[] encode(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        validate(frames, delays);
        if (config == null) {
            config = new GifToWebPConfig();
        }

        if (NativeWebP.isAvailable()) {
            try {
                return encodeNative(frames, delays, config);
            } catch (LinkageError | UnsupportedOperationException unavailable) {
                // Only a missing native backend selects the Java path. Real encoding errors are reported.
                LOG.log(Level.WARNING, "Native animated encoder unavailable, using the Java container writer", unavailable);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        encode(baos, frames, delays, config);
        return baos.toByteArray();
    }

    /**
     * Streams an animated WebP directly to an output stream by assembling container chunks in Java.
     * Every frame is stored as a full-canvas keyframe that replaces the canvas (no blending).
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
        validate(frames, delays);
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
        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(out, hasAlpha, width, height, loopCount, 0)) {
            for (int i = 0; i < frames.size(); i++) {
                encoder.setFrame(i, frames.get(i), delays[i], config.getQuality(), config.isLossless(),
                        WebPFrameFlag.NO_BLEND);
            }
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
     * Encodes the whole animation with libwebp's WebPAnimEncoder.
     *
     * @throws UnsupportedOperationException if the native library is unavailable
     */
    public static byte[] encodeNative(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        validate(frames, delays);
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

    private static void validate(List<BufferedImage> frames, int[] delays) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }
        if (delays == null || delays.length != frames.size()) {
            throw new IllegalArgumentException("Delays array length must match frame count");
        }
        long total = 0;
        for (int i = 0; i < delays.length; i++) {
            if (delays[i] < 0) {
                throw new IllegalArgumentException("Delay of frame " + i + " must not be negative: " + delays[i]);
            }
            total += delays[i];
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The summed frame delays exceed " + Integer.MAX_VALUE + " ms");
        }
    }
}
