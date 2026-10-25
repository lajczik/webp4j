package xyz.lychee.webp4j.model;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

/**
 * Represents a single frame extracted from an animated WebP image.
 * <p>
 * Each frame contains the fully composited image data and its
 * cumulative timestamp in milliseconds from the start of the animation.
 */
@Getter
@Setter
public class AnimatedWebPFrame {

    private BufferedImage image;
    private int timestamp;

    /**
     * Public constructor.
     */
    public AnimatedWebPFrame() {
    }

    /**
     * Creates a frame with the given image and timestamp.
     *
     * @param image     The fully composited frame image
     * @param timestamp Cumulative timestamp in milliseconds from animation start
     */
    public AnimatedWebPFrame(BufferedImage image, int timestamp) {
        this.image = image;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AnimatedWebPFrame{" +
                "image=" + (image != null ? image.getWidth() + "x" + image.getHeight() : "null") +
                ", timestamp=" + timestamp +
                '}';
    }
}
