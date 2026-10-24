package xyz.lychee.webp4j.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds animation metadata extracted from GIF images.
 * <p>
 * This class provides information about a GIF image without performing
 * the full conversion. It's useful for determining whether a GIF is
 * animated, its dimensions, and other properties before conversion.
 * <p>
 * Instances of this class are populated by the native layer or GIF decoder.
 */
@Getter
@Setter
public class AnimationInfo {

    private int frameCount;
    private int width;
    private int height;
    private int loopCount;
    private boolean hasTransparency;

    /**
     * Public constructor.
     * Instances can be created by any code including WebPCodec, native code, or external packages.
     */
    public AnimationInfo() {
    }

    /**
     * Checks if the GIF has transparency (transparent color index).
     *
     * @return true if the GIF has at least one transparent pixel
     */
    public boolean hasTransparency() {
        return hasTransparency;
    }

    /**
     * Checks if the GIF is animated (has multiple frames).
     *
     * @return true if frame count > 1
     */
    public boolean isAnimated() {
        return frameCount > 1;
    }

    @Override
    public String toString() {
        return "AnimationInfo{" +
                "frameCount=" + frameCount +
                ", width=" + width +
                ", height=" + height +
                ", loopCount=" + loopCount +
                ", hasTransparency=" + hasTransparency +
                ", isAnimated=" + isAnimated() +
                '}';
    }
}
