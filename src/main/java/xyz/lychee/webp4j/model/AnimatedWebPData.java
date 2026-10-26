package xyz.lychee.webp4j.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Contains all decoded frames and metadata from an animated WebP image.
 * <p>
 * This class holds the complete result of decoding an animated WebP file,
 * including individual frames with their timestamps, canvas dimensions,
 * loop count, and background color.
 * <p>
 * The raw frame data (packed ARGB pixel arrays and timestamps) are populated
 * by the native layer, and then converted to {@link AnimatedWebPFrame} objects
 * by the decoder.
 *
 * @author MrNanko
 * @author lajczik
 */
@Getter
@Setter
public class AnimatedWebPData {

    private List<AnimatedWebPFrame> frames;
    private int canvasWidth;
    private int canvasHeight;
    private int loopCount;
    private int bgcolor;
    private int frameCount;

    // Raw data populated by JNI (before conversion to AnimatedWebPFrame list)
    private int[][] framePixels;
    private int[] timestamps;

    /**
     * Public constructor.
     */
    public AnimatedWebPData() {
    }

    /**
     * Retrieves the frame at the specified index.
     *
     * @param index Frame index (0 to frameCount - 1)
     * @return The AnimatedWebPFrame at the given index
     * @throws IndexOutOfBoundsException If index is out of range
     * @throws IllegalStateException    If frames have not been decoded yet
     */
    public AnimatedWebPFrame getFrame(int index) {
        if (frames == null) {
            throw new IllegalStateException("No frames have been decoded yet");
        }
        return frames.get(index);
    }

    /**
     * Returns total animation duration in milliseconds.
     *
     * @return Total duration in milliseconds, or 0 if empty
     */
    public int getTotalDuration() {
        if (frames == null || frames.isEmpty()) {
            return 0;
        }
        return frames.get(frames.size() - 1).getTimestamp();
    }

    /**
     * Computes per-frame delays from the cumulative timestamps.
     * <p>
     * The delay for each frame is calculated as the difference between
     * consecutive timestamps. The first frame's delay is its timestamp value.
     *
     * @return Array of per-frame delays in milliseconds
     * @throws IllegalStateException If frames have not been decoded yet
     */
    public int[] getDelays() {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalStateException("No frames have been decoded yet");
        }

        int[] delays = new int[frames.size()];
        int prevTimestamp = 0;
        for (int i = 0; i < frames.size(); i++) {
            int currentTimestamp = frames.get(i).getTimestamp();
            delays[i] = currentTimestamp - prevTimestamp;
            prevTimestamp = currentTimestamp;
        }
        return delays;
    }

    @Override
    public String toString() {
        return "AnimatedWebPData{" +
                "frameCount=" + frameCount +
                ", canvasWidth=" + canvasWidth +
                ", canvasHeight=" + canvasHeight +
                ", loopCount=" + loopCount +
                ", bgcolor=0x" + Integer.toHexString(bgcolor) +
                '}';
    }
}
