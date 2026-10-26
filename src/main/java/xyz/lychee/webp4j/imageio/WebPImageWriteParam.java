package xyz.lychee.webp4j.imageio;

import xyz.lychee.webp4j.model.WebPFrameFlag;

import javax.imageio.ImageWriteParam;
import java.util.Locale;

/**
 * ImageWriteParam implementation for configuring WebP compression quality, lossless mode,
 * and animated WebP sequence parameters (delay, loop count, frame flags, background color).
 *
 * @author lajczik
 */
public class WebPImageWriteParam extends ImageWriteParam {

    public static final String COMPRESSION_TYPE_LOSSY = "Lossy";
    public static final String COMPRESSION_TYPE_LOSSLESS = "Lossless";

    private static final String[] COMPRESSION_TYPES = {
            COMPRESSION_TYPE_LOSSY,
            COMPRESSION_TYPE_LOSSLESS
    };

    private int delay = 100;
    private int loopCount = 0;
    private int backgroundColor = 0x00000000;
    private WebPFrameFlag[] frameFlags = new WebPFrameFlag[0];

    public WebPImageWriteParam(Locale locale) {
        super(locale);
        this.canWriteCompressed = true;
        this.compressionTypes = COMPRESSION_TYPES;
        this.compressionType = COMPRESSION_TYPE_LOSSY;
        this.compressionMode = MODE_EXPLICIT;
        this.compressionQuality = 0.75f;
    }

    /**
     * Checks if lossless WebP compression is selected.
     *
     * @return true if lossless, false if lossy
     */
    public boolean isLossless() {
        return COMPRESSION_TYPE_LOSSLESS.equalsIgnoreCase(getCompressionType());
    }

    /**
     * Toggles between lossless and lossy WebP compression.
     *
     * @param lossless true for lossless (VP8L), false for lossy (VP8)
     */
    public void setLossless(boolean lossless) {
        setCompressionType(lossless ? COMPRESSION_TYPE_LOSSLESS : COMPRESSION_TYPE_LOSSY);
    }

    /**
     * Returns the frame display delay in milliseconds for animation sequence writing.
     *
     * @return Delay in milliseconds (default: 100ms)
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the frame display delay in milliseconds for animation sequence writing.
     *
     * @param delay Delay in milliseconds (must be non-negative)
     */
    public void setDelay(int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("Delay cannot be negative: " + delay);
        }
        this.delay = delay;
    }

    /**
     * Returns the animation loop count (0 = infinite).
     *
     * @return Loop count
     */
    public int getLoopCount() {
        return loopCount;
    }

    /**
     * Sets the animation loop count (0 = infinite).
     *
     * @param loopCount Loop count
     */
    public void setLoopCount(int loopCount) {
        if (loopCount < 0) {
            throw new IllegalArgumentException("Loop count cannot be negative: " + loopCount);
        }
        this.loopCount = loopCount;
    }

    /**
     * Returns the canvas background color as packed ARGB int.
     *
     * @return Packed ARGB background color
     */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Sets the canvas background color as packed ARGB int.
     *
     * @param backgroundColor Packed ARGB background color
     */
    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    /**
     * Returns the active frame flags (blending, disposal) for this frame.
     *
     * @return Array of WebPFrameFlag
     */
    public WebPFrameFlag[] getFrameFlags() {
        return frameFlags != null ? frameFlags : new WebPFrameFlag[0];
    }

    /**
     * Sets the active frame flags (blending, disposal) for this frame.
     *
     * @param frameFlags Varargs of WebPFrameFlag
     */
    public void setFrameFlags(WebPFrameFlag... frameFlags) {
        this.frameFlags = frameFlags != null ? frameFlags : new WebPFrameFlag[0];
    }
}
