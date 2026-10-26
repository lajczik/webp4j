package xyz.lychee.webp4j.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

/**
 * Holds animation metadata extracted from WebP or GIF containers without decoding pixel buffers.
 * <p>
 * Instances of this class are populated by chunk inspection or native probing.
 *
 * @author MrNanko
 * @author lajczik
 */
@Getter
@Setter
public class AnimationInfo {

    private int frameCount;
    private int width;
    private int height;
    private int loopCount;
    private boolean hasTransparency;
    private int totalDuration;
    private int[] delays;

    /**
     * Public constructor.
     */
    public AnimationInfo() {
    }

    /**
     * Checks if the image has transparency (alpha channel or transparent color index).
     *
     * @return true if image contains transparency
     */
    public boolean hasTransparency() {
        return hasTransparency;
    }

    /**
     * Checks if the image is animated (has multiple frames).
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
                ", totalDuration=" + totalDuration +
                ", delays=" + Arrays.toString(delays) +
                ", isAnimated=" + isAnimated() +
                '}';
    }
}
