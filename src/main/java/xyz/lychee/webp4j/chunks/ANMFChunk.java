package xyz.lychee.webp4j.chunks;

import xyz.lychee.webp4j.model.WebPFrameFlag;

import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;

import static xyz.lychee.webp4j.utils.Util.checkUInt24;

/**
 * Animation frame chunk (ANMF) representing a single frame with offset, duration, and flags.
 *
 * @author lajczik
 */
public class ANMFChunk extends LISTChunk {

    public static final byte[] ANMF = new byte[]{'A', 'N', 'M', 'F'};
    private final int x, y, width, height, duration, reservedBD;

    /**
     * Creates an animation frame chunk.
     *
     * @param x        Horizontal frame offset in pixels; must be even (the container stores {@code x / 2})
     * @param y        Vertical frame offset in pixels; must be even (the container stores {@code y / 2})
     * @param width    Frame width in pixels (1 .. 2^24)
     * @param height   Frame height in pixels (1 .. 2^24)
     * @param duration Display time in milliseconds (0 .. 2^24-1)
     * @param flags    Frame flag mask, see {@link WebPFrameFlag}
     * @param subchunks Bitstream chunks of the frame
     * @throws IllegalArgumentException if a value is out of range or an offset is odd
     */
    public ANMFChunk(int x, int y, int width, int height, int duration, int flags, Chunk... subchunks) {
        super(ANMF, 3 + 3 + 3 + 3 + 3 + 1, subchunks);
        this.x = checkOffset(x, "x");
        this.y = checkOffset(y, "y");
        this.width = checkDimension(width, "width");
        this.height = checkDimension(height, "height");
        this.duration = checkUInt24(duration);
        this.reservedBD = flags;
    }

    private static int checkOffset(int value, String name) {
        if (value < 0 || (value & 1) != 0) {
            throw new IllegalArgumentException("Frame " + name + " offset must be a non-negative even number, but was " + value);
        }
        checkUInt24(value >>> 1);
        return value;
    }

    private static int checkDimension(int value, String name) {
        if (value < 1 || value > (1 << 24)) {
            throw new IllegalArgumentException("Frame " + name + " must be between 1 and 16777216, but was " + value);
        }
        return value;
    }

    public ANMFChunk(int x, int y, int width, int height, int duration, WebPFrameFlag[] flags, Chunk... subchunks) {
        this(x, y, width, height, duration, WebPFrameFlag.toMask(flags), subchunks);
    }

    public int getFrameX() {
        return x;
    }

    public int getFrameY() {
        return y;
    }

    public int getFrameWidth() {
        return width;
    }

    public int getFrameHeight() {
        return height;
    }

    public int getFrameDuration() {
        return duration;
    }

    public int getFlags() {
        return reservedBD;
    }

    /**
     * Returns the set of active frame flags for this ANMF chunk.
     *
     * @return EnumSet of active WebPFrameFlag instances
     */
    public EnumSet<WebPFrameFlag> getFrameFlags() {
        return WebPFrameFlag.fromMask(reservedBD);
    }

    public int getBlendingFlag() {
        return reservedBD & WebPFrameFlag.NO_BLEND.getMask();
    }

    public int getDisposalMethodFlag() {
        return reservedBD & WebPFrameFlag.DISPOSE_TO_BACKGROUND.getMask();
    }

    public boolean hasFlag(WebPFrameFlag flag) {
        return flag != null && flag.isSet(reservedBD);
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        byte[] payload = new byte[16];
        int x2 = x >>> 1;
        payload[0] = (byte) (x2 & 0xFF);
        payload[1] = (byte) ((x2 >>> 8) & 0xFF);
        payload[2] = (byte) ((x2 >>> 16) & 0xFF);

        int y2 = y >>> 1;
        payload[3] = (byte) (y2 & 0xFF);
        payload[4] = (byte) ((y2 >>> 8) & 0xFF);
        payload[5] = (byte) ((y2 >>> 16) & 0xFF);

        int w1 = width - 1;
        payload[6] = (byte) (w1 & 0xFF);
        payload[7] = (byte) ((w1 >>> 8) & 0xFF);
        payload[8] = (byte) ((w1 >>> 16) & 0xFF);

        int h1 = height - 1;
        payload[9] = (byte) (h1 & 0xFF);
        payload[10] = (byte) ((h1 >>> 8) & 0xFF);
        payload[11] = (byte) ((h1 >>> 16) & 0xFF);

        payload[12] = (byte) (duration & 0xFF);
        payload[13] = (byte) ((duration >>> 8) & 0xFF);
        payload[14] = (byte) ((duration >>> 16) & 0xFF);

        payload[15] = (byte) (reservedBD & 0xFF);

        out.write(payload, 0, 16);
        writeSubchunks(out);
    }

}
