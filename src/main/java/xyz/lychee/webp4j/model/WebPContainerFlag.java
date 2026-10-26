package xyz.lychee.webp4j.model;

import java.util.EnumSet;

/**
 * Extended container feature flags for WebP images (VP8X chunk).
 *
 * @author MrNanko
 */
public enum WebPContainerFlag {

    /**
     * Indicates that the file is an animated image and contains ANIM and ANMF chunks.
     */
    ANIMATION(1 << 1),

    /**
     * Indicates that the file contains an XMP metadata chunk.
     */
    XMP(1 << 2),

    /**
     * Indicates that the file contains an EXIF metadata chunk.
     */
    EXIF(1 << 3),

    /**
     * Indicates that the image bitstream contains transparency information.
     */
    ALPHA(1 << 4),

    /**
     * Indicates that the file contains an ICC color profile chunk.
     */
    ICC_PROFILE(1 << 5);

    private final int mask;

    WebPContainerFlag(int mask) {
        this.mask = mask;
    }

    /**
     * Returns the raw bitmask value of this container flag.
     *
     * @return Integer bitmask
     */
    public int getMask() {
        return mask;
    }

    /**
     * Checks if this container flag is set in the given bitmask.
     *
     * @param mask Bitmask to test
     * @return true if this flag is set in the mask
     */
    public boolean isSet(int mask) {
        return (mask & this.mask) == this.mask;
    }

    /**
     * Extracts all container flags that are set in the given bitmask.
     *
     * @param mask Bitmask to extract flags from
     * @return EnumSet containing all matching WebPContainerFlag instances
     */
    public static EnumSet<WebPContainerFlag> fromMask(int mask) {
        EnumSet<WebPContainerFlag> set = EnumSet.noneOf(WebPContainerFlag.class);
        for (WebPContainerFlag f : values()) {
            if (f.isSet(mask)) {
                set.add(f);
            }
        }
        return set;
    }

    /**
     * Combines multiple container flags into a single bitmask integer.
     *
     * @param flags Varargs of WebPContainerFlag
     * @return Integer bitmask for VP8X chunk
     */
    public static int toMask(WebPContainerFlag... flags) {
        if (flags == null) {
            return 0;
        }
        int m = 0;
        for (WebPContainerFlag f : flags) {
            if (f != null) {
                m |= f.mask;
            }
        }
        return m;
    }

    /**
     * Combines a collection of container flags into a single bitmask integer.
     *
     * @param flags Iterable of WebPContainerFlag
     * @return Integer bitmask for VP8X chunk
     */
    public static int toMask(Iterable<WebPContainerFlag> flags) {
        if (flags == null) {
            return 0;
        }
        int m = 0;
        for (WebPContainerFlag f : flags) {
            if (f != null) {
                m |= f.mask;
            }
        }
        return m;
    }

    /**
     * Checks if the given bitmask has the specified container flag.
     *
     * @param mask Bitmask to test
     * @param flag Flag to check
     * @return true if flag is set
     */
    public static boolean hasFlag(int mask, WebPContainerFlag flag) {
        return flag != null && flag.isSet(mask);
    }
}
