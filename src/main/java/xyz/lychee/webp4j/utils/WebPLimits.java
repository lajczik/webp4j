package xyz.lychee.webp4j.utils;

import java.io.IOException;

/**
 * Global safety limits applied before any large allocation is made on behalf of untrusted input.
 * <p>
 * A WebP header of a few dozen bytes can declare a 16383x16383 canvas (1 GiB of ARGB pixels) and an
 * animation can declare thousands of such frames. The limits below are checked against the declared
 * dimensions <em>before</em> memory is reserved. They can be raised or lowered at application start-up.
 *
 * @author lajczik
 */
public final class WebPLimits {

    /** Default maximum number of pixels of a single image or animation canvas (100 Mpx). */
    public static final long DEFAULT_MAX_PIXELS = 100_000_000L;
    /** Default maximum number of pixels summed over all frames of an animation (256 Mpx = 1 GiB of ints). */
    public static final long DEFAULT_MAX_TOTAL_PIXELS = 256_000_000L;
    /** Default maximum number of frames of an animation. */
    public static final int DEFAULT_MAX_FRAMES = 20_000;
    /** Default maximum number of bytes read from a stream or file into memory (1 GiB). */
    public static final long DEFAULT_MAX_INPUT_BYTES = 1L << 30;

    private static volatile long maxPixels = DEFAULT_MAX_PIXELS;
    private static volatile long maxTotalPixels = DEFAULT_MAX_TOTAL_PIXELS;
    private static volatile int maxFrames = DEFAULT_MAX_FRAMES;
    private static volatile long maxInputBytes = DEFAULT_MAX_INPUT_BYTES;

    private WebPLimits() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    public static long getMaxPixels() {
        return maxPixels;
    }

    public static void setMaxPixels(long value) {
        maxPixels = requirePositive(value, "maxPixels");
    }

    public static long getMaxTotalPixels() {
        return maxTotalPixels;
    }

    public static void setMaxTotalPixels(long value) {
        maxTotalPixels = requirePositive(value, "maxTotalPixels");
    }

    public static int getMaxFrames() {
        return maxFrames;
    }

    public static void setMaxFrames(int value) {
        maxFrames = (int) requirePositive(value, "maxFrames");
    }

    public static long getMaxInputBytes() {
        return maxInputBytes;
    }

    public static void setMaxInputBytes(long value) {
        maxInputBytes = requirePositive(value, "maxInputBytes");
    }

    /** Restores all limits to their defaults. */
    public static void reset() {
        maxPixels = DEFAULT_MAX_PIXELS;
        maxTotalPixels = DEFAULT_MAX_TOTAL_PIXELS;
        maxFrames = DEFAULT_MAX_FRAMES;
        maxInputBytes = DEFAULT_MAX_INPUT_BYTES;
    }

    /**
     * Verifies that a single image of the given declared size may be allocated.
     *
     * @throws IOException if a dimension is not positive or the pixel count exceeds the limit
     */
    public static void checkImage(long width, long height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IOException("Invalid image dimensions: " + width + "x" + height);
        }
        if (width * height > maxPixels) {
            throw new IOException("Image " + width + "x" + height + " exceeds the pixel limit of " + maxPixels
                    + " (see WebPLimits.setMaxPixels)");
        }
    }

    /**
     * Verifies that an animation of the given declared size may be decoded into memory.
     *
     * @throws IOException if the canvas, the frame count or the total pixel count exceeds a limit
     */
    public static void checkAnimation(long width, long height, long frameCount) throws IOException {
        checkImage(width, height);
        if (frameCount < 0 || frameCount > maxFrames) {
            throw new IOException("Animation has " + frameCount + " frames, the limit is " + maxFrames
                    + " (see WebPLimits.setMaxFrames)");
        }
        if (width * height * Math.max(1L, frameCount) > maxTotalPixels) {
            throw new IOException("Animation " + width + "x" + height + "x" + frameCount
                    + " exceeds the total pixel limit of " + maxTotalPixels + " (see WebPLimits.setMaxTotalPixels)");
        }
    }

    /**
     * Verifies that the given number of bytes may be read into memory.
     *
     * @throws IOException if the size exceeds the limit
     */
    public static void checkInputSize(long bytes) throws IOException {
        if (bytes > maxInputBytes) {
            throw new IOException("Input of " + bytes + " bytes exceeds the limit of " + maxInputBytes
                    + " (see WebPLimits.setMaxInputBytes)");
        }
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
