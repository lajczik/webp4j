package xyz.lychee.webp4j.model;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

/**
 * Represents a single frame extracted from an animated WebP image.
 * <p>
 * Each frame contains the fully composited image data, its cumulative
 * timestamp in milliseconds from animation start, and its individual display duration.
 */
@Getter
@Setter
public class AnimatedWebPFrame {

    private BufferedImage image;
    private int timestamp;
    private int duration;

    /**
     * Public constructor.
     */
    public AnimatedWebPFrame() {
    }

    /**
     * Creates a frame with the given image and cumulative timestamp.
     *
     * @param image     The fully composited frame image
     * @param timestamp Cumulative timestamp in milliseconds from animation start
     */
    public AnimatedWebPFrame(BufferedImage image, int timestamp) {
        this(image, timestamp, timestamp);
    }

    /**
     * Creates a frame with the given image, cumulative timestamp, and frame duration.
     *
     * @param image     The fully composited frame image
     * @param timestamp Cumulative timestamp in milliseconds from animation start
     * @param duration  Display duration of this specific frame in milliseconds
     */
    public AnimatedWebPFrame(BufferedImage image, int timestamp, int duration) {
        this.image = image;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    @Override
    public String toString() {
        return "AnimatedWebPFrame{" +
                "image=" + (image != null ? image.getWidth() + "x" + image.getHeight() : "null") +
                ", timestamp=" + timestamp +
                ", duration=" + duration +
                '}';
    }
}
