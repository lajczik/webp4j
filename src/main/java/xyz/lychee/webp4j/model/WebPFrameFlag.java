package xyz.lychee.webp4j.model;

import java.util.EnumSet;

/**
 * Flags for configuring frame blending and disposal in animated WebP images (ANMF chunk).
 *
 * @author MrNanko
 */
public enum WebPFrameFlag {

    /**
     * Do not dispose frame; leave current canvas state intact for the next frame.
     */
    NO_DISPOSE(0),

    /**
     * Dispose the frame's canvas area to the background color before rendering the next frame.
     */
    DISPOSE_TO_BACKGROUND(1 << 0),

    /**
     * Alpha-blend the current frame's pixels with the prior canvas content.
     */
    ALPHA_BLENDING(0),

    /**
     * Overwrite prior canvas pixels without alpha blending.
     */
    NO_BLEND(1 << 1);

    private final int mask;

    WebPFrameFlag(int mask) {
        this.mask = mask;
    }

    /**
     * Returns the raw bitmask value of this flag.
     *
     * @return Integer bitmask
     */
    public int getMask() {
        return mask;
    }

    /**
     * Checks if this frame flag is active in the given bitmask.
     *
     * @param mask Bitmask to test
     * @return true if this flag is active in the mask
     */
    public boolean isSet(int mask) {
        if (this.mask == 0) {
            if (this == NO_DISPOSE) {
                return (mask & DISPOSE_TO_BACKGROUND.mask) == 0;
            } else if (this == ALPHA_BLENDING) {
                return (mask & NO_BLEND.mask) == 0;
            }
            return true;
        }
        return (mask & this.mask) == this.mask;
    }

    /**
     * Extracts all frame flags that are active in the given bitmask.
     *
     * @param mask Bitmask to extract flags from
     * @return EnumSet containing all matching WebPFrameFlag instances
     */
    public static EnumSet<WebPFrameFlag> fromMask(int mask) {
        EnumSet<WebPFrameFlag> set = EnumSet.noneOf(WebPFrameFlag.class);
        for (WebPFrameFlag f : values()) {
            if (f.isSet(mask)) {
                set.add(f);
            }
        }
        return set;
    }

    /**
     * Combines multiple frame flags into a single bitmask integer.
     *
     * @param flags Varargs of WebPFrameFlag
     * @return Integer bitmask for ANMF chunk
     */
    public static int toMask(WebPFrameFlag... flags) {
        if (flags == null) {
            return 0;
        }
        int m = 0;
        for (WebPFrameFlag f : flags) {
            if (f != null) {
                m |= f.mask;
            }
        }
        return m;
    }

    /**
     * Combines a collection of frame flags into a single bitmask integer.
     *
     * @param flags Iterable of WebPFrameFlag
     * @return Integer bitmask for ANMF chunk
     */
    public static int toMask(Iterable<WebPFrameFlag> flags) {
        if (flags == null) {
            return 0;
        }
        int m = 0;
        for (WebPFrameFlag f : flags) {
            if (f != null) {
                m |= f.mask;
            }
        }
        return m;
    }

    /**
     * Checks if the given bitmask has the specified flag.
     *
     * @param mask Bitmask to test
     * @param flag Flag to check
     * @return true if flag is active
     */
    public static boolean hasFlag(int mask, WebPFrameFlag flag) {
        return flag != null && flag.isSet(mask);
    }
}
